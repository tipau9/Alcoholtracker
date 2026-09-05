-- Jam participants privacy hardening
--
-- Run this once in the Supabase SQL Editor (New query -> paste -> Run).
--
-- THE PROBLEM this fixes
-- ----------------------
-- The Jam roster needs to read OTHER members' live data (current_bac,
-- has_sos_active, status, name). Until now the app did that with a direct table
-- read:
--     GET /rest/v1/jam_participants?jam_id=eq.<id>&select=*
-- For that to work, jam_participants RLS had to let an authenticated user SELECT
-- rows that are not theirs. But RLS predicates are row-level, not query-level:
-- a logged-in user could drop the jam_id filter and dump EVERY jam's members and
-- their live BAC/SOS. Smaller blast radius than the profiles leak (you at least
-- need to be signed in), but it is the same class of bug. The anon key ships in
-- the IPA, so RLS is the only real protection.
--
-- THE FIX (mirrors supabase/profiles_security.sql)
-- ------------------------------------------------
-- 1. Lock jam_participants down: you may read/insert/update/delete only your OWN
--    row; the jam host may additionally delete rows (kick) from their own jam.
-- 2. Expose the roster ONLY through a SECURITY DEFINER function that returns the
--    members of a jam you are yourself a member of (or host). No jam_id you are
--    not part of returns anything, so there is nothing to enumerate.
-- 3. Lock the jams table itself down the same way (sections 3-4 below). Until now
--    `jams` was read with a direct `select=*` (find-by-code, friends-only feed),
--    so any signed-in user could drop the filter and dump EVERY jam's code, host
--    identity, visibility and settings. Now: you may write/read only jams you
--    HOST; joining by code and the friends-only feed go through SECURITY DEFINER
--    functions that require the exact code (a secret) or a known host id (an
--    unguessable UUID you only have for friends), so nothing can be enumerated.

-- =========================================================================
-- 0. Make sure the tables and every column the app uses exist, so the policies
--    and function below work even on a fresh project. create table if not
--    exists is skipped on existing projects, preserving their FKs/cascade.
-- =========================================================================

create table if not exists public.jams (
    id           uuid primary key,
    code         text,
    host_user_id text,
    host_name    text,
    visibility   text,
    settings     jsonb,
    created_at   timestamptz default now(),
    ended_at     timestamptz
);

create table if not exists public.jam_participants (
    id              uuid primary key,
    jam_id          uuid references public.jams(id) on delete cascade,
    user_id         text,
    display_name    text,
    connection_type text,
    current_bac     double precision,
    current_status  text,
    has_sos_active  boolean default false,
    joined_at       timestamptz default now(),
    last_updated    timestamptz default now()
);

alter table public.jam_participants add column if not exists current_status text;
alter table public.jam_participants add column if not exists has_sos_active boolean default false;
alter table public.jam_participants add column if not exists joined_at      timestamptz default now();
alter table public.jam_participants add column if not exists last_updated   timestamptz default now();

delete from public.jam_participants a
using public.jam_participants b
where a.jam_id = b.jam_id
  and a.user_id = b.user_id
  and (
      a.joined_at > b.joined_at
      or (a.joined_at = b.joined_at and a.ctid > b.ctid)
  );

-- Drop the old partial index (WHERE user_id IS NOT NULL) — PostgREST cannot
-- match on_conflict=jam_id,user_id against a partial index, causing HTTP 400:
-- "there is no unique or exclusion constraint matching the ON CONFLICT specification".
drop index if exists public.jam_participants_one_row_per_user;

-- Re-create as a FULL (non-partial) unique index so PostgREST can resolve it.
create unique index if not exists jam_participants_one_row_per_user
    on public.jam_participants (jam_id, user_id);

create table if not exists public.jam_lookup_events (
    caller_id  uuid not null,
    kind       text not null,
    created_at timestamptz not null default now()
);

create index if not exists jam_lookup_events_caller_idx
    on public.jam_lookup_events (caller_id, kind, created_at);

-- =========================================================================
-- 1. Base-table RLS: self-only writes + host-kick. Drop EVERY existing policy
--    first (we cannot know their names), then recreate the intended set.
--    Reading other members now goes exclusively through the function in
--    section 2 (SECURITY DEFINER bypasses these policies in a controlled way).
-- =========================================================================

alter table public.jam_participants enable row level security;

do $$
declare pol record;
begin
    for pol in
        select policyname from pg_policies
        where schemaname = 'public' and tablename = 'jam_participants'
    loop
        execute format('drop policy if exists %I on public.jam_participants', pol.policyname);
    end loop;
end $$;

-- All identity comparisons cast to ::uuid on both sides. This is robust whether
-- the id columns are typed uuid or text in a given project, AND it normalises
-- case: the client stores jam/participant ids from Swift's UUID().uuidString
-- (UPPERCASE), while auth.uid() / a uuid param render lowercase, so a plain text
-- compare would miss. (auth.uid() already returns uuid.)

-- You can see only your own row directly; the full roster comes from the
-- function below. (Keeping a self-select keeps any direct self lookups working.)
create policy "jam_participants_select_own" on public.jam_participants
    for select using (user_id::uuid = auth.uid());

-- You may only insert yourself into a jam.
create policy "jam_participants_insert_self" on public.jam_participants
    for insert with check (user_id::uuid = auth.uid());

-- You may only update your own status (BAC / SOS / status).
create policy "jam_participants_update_own" on public.jam_participants
    for update using (user_id::uuid = auth.uid())
            with check (user_id::uuid = auth.uid());

-- You may delete yourself (leave), and the jam's host may delete anyone (kick).
-- NOTE: this subselect reads public.jams. To avoid infinite recursion, jams
-- must NOT have an RLS policy that in turn reads jam_participants.
create policy "jam_participants_delete_self_or_host" on public.jam_participants
    for delete using (
        user_id::uuid = auth.uid()
        or auth.uid() = (
            select j.host_user_id::uuid from public.jams j
            where j.id::uuid = jam_id::uuid
        )
    );

-- =========================================================================
-- 2. Roster projection. Returns the members of a jam you belong to (or host).
--    Same column shape the client's JamParticipantRow decodes.
-- =========================================================================

create or replace function public.jam_participants_for_member(p_jam_id uuid)
returns table (
    id              uuid,
    user_id         text,
    display_name    text,
    connection_type text,
    current_bac     double precision,
    current_status  text,
    has_sos_active  boolean,
    joined_at       timestamptz,
    last_updated    timestamptz
)
language sql
security definer
set search_path = public
stable
as $$
    select
        p.id,
        p.user_id,
        p.display_name,
        p.connection_type,
        p.current_bac,
        p.current_status,
        p.has_sos_active,
        p.joined_at,
        p.last_updated
    from public.jam_participants p
    where p.jam_id::uuid = p_jam_id
      and (
          -- caller is a member of this jam ...
          exists (
              select 1 from public.jam_participants me
              where me.jam_id::uuid = p_jam_id
                and me.user_id::uuid = auth.uid()
          )
          -- ... or the jam's host (covers the moment before anyone else joins)
          or auth.uid() = (
              select j.host_user_id::uuid from public.jams j where j.id::uuid = p_jam_id
          )
      )
$$;

-- Only signed-in users may call this; never the anon role.
revoke all on function public.jam_participants_for_member(uuid) from public, anon;
grant execute on function public.jam_participants_for_member(uuid) to authenticated;

-- =========================================================================
-- 3. jams base-table RLS: you may read/write only jams you HOST. Drop EVERY
--    existing policy first, then recreate the intended set. Joining a jam you do
--    not host, and the friends-only feed, go exclusively through the functions
--    in section 4 (SECURITY DEFINER bypasses these policies in a controlled way).
--
--    RECURSION NOTE: these policies reference ONLY host_user_id / auth.uid(),
--    never jam_participants. jam_participants' kick policy (section 1) in turn
--    subselects jams. Keeping the dependency one-directional (participants ->
--    jams, never jams -> participants) avoids the infinite-recursion HTTP 500.
-- =========================================================================

alter table public.jams enable row level security;

do $$
declare pol record;
begin
    for pol in
        select policyname from pg_policies
        where schemaname = 'public' and tablename = 'jams'
    loop
        execute format('drop policy if exists %I on public.jams', pol.policyname);
    end loop;
end $$;

-- You can read only your own (hosted) jams directly. Members got their Jam
-- object from jam_by_code at join time and keep it locally; they never re-SELECT
-- the jams table, so host-only reads do not break the member experience.
create policy "jams_select_own" on public.jams
    for select using (host_user_id::uuid = auth.uid());

-- You may only create a jam with yourself as host.
create policy "jams_insert_host" on public.jams
    for insert with check (host_user_id::uuid = auth.uid());

-- Only the host may modify or end/delete their jam.
-- WITH CHECK (true) allows the current host to hand off the jam to another user
-- (transfer host / ghost-jam auto-election).
drop policy if exists "jams_update_host" on public.jams;
create policy "jams_update_host" on public.jams
    for update using (host_user_id::uuid = auth.uid())
            with check (true);
create policy "jams_delete_host" on public.jams
    for delete using (host_user_id::uuid = auth.uid());

-- =========================================================================
-- 4. Jam lookups. Same column shape the client's JamRow decodes.
-- =========================================================================

-- Join-by-code: returns the single active jam whose code matches exactly. The
-- code is a secret you can only have by being told it, so this cannot enumerate
-- strangers' jams. Code is normalised (uppercase, alnum only) on both sides so
-- client-side and server-side sanitising agree regardless of how it was stored.
create or replace function public.jam_by_code(p_code text)
returns table (
    id            uuid,
    code          text,
    host_user_id  text,
    host_name     text,
    visibility    text,
    settings      jsonb,
    created_at    timestamptz
)
language plpgsql
security definer
set search_path = public
as $$
#variable_conflict use_column
declare
    v_recent integer;
begin
    if auth.uid() is null then
        return;
    end if;

    delete from public.jam_lookup_events e
    where e.created_at < now() - interval '7 days';

    select count(*) into v_recent
    from public.jam_lookup_events e
    where e.caller_id = auth.uid()
      and e.kind = 'code'
      and e.created_at > now() - interval '1 hour';

    if v_recent >= 120 then
        raise exception 'jam_lookup_rate_limited';
    end if;

    insert into public.jam_lookup_events(caller_id, kind)
    values (auth.uid(), 'code');

    return query
    select c.id, c.code, c.host_user_id::text, c.host_name, c.visibility, c.settings, c.created_at
    from public.jams c
    where c.ended_at is null
      and upper(regexp_replace(c.code,   '[^A-Za-z0-9]', '', 'g'))
        = upper(regexp_replace(p_code,   '[^A-Za-z0-9]', '', 'g'))
    limit 1;
end;
$$;

-- Friends-only feed: returns active "Nur Freunde" jams hosted by one of the
-- given host ids. The client resolves those ids from friend codes first
-- (friend_profiles_by_codes), so you only ever pass ids of people you added;
-- UUIDs are unguessable, so this cannot enumerate strangers' jams either. The
-- caller's own jams are excluded (already shown as currentJam).
create or replace function public.friend_jams(p_host_ids uuid[])
returns table (
    id            uuid,
    code          text,
    host_user_id  text,
    host_name     text,
    visibility    text,
    settings      jsonb,
    created_at    timestamptz
)
language sql
security definer
set search_path = public
stable
as $$
    select c.id, c.code, c.host_user_id::text, c.host_name, c.visibility, c.settings, c.created_at
    from public.jams c
    where c.ended_at is null
      and c.visibility = 'Nur Freunde'
      and c.host_user_id::uuid = any (p_host_ids)
      and c.host_user_id::uuid <> auth.uid()
$$;

-- Only signed-in users may call these; never the anon role.
revoke all on function public.jam_by_code(text)    from public, anon;
revoke all on function public.friend_jams(uuid[])  from public, anon;
revoke all on table public.jam_lookup_events from public, anon, authenticated;
grant execute on function public.jam_by_code(text)   to authenticated;
grant execute on function public.friend_jams(uuid[]) to authenticated;

-- =========================================================================
-- 5. Empty-jam automatic cleanup trigger
-- =========================================================================

-- Deletes empty jams or jams when the host departs without transferring
create or replace function public.check_jam_cleanup()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    v_host_id text;
begin
    -- Check if the jam still exists
    select host_user_id into v_host_id from public.jams where id = old.jam_id;
    if not found then
        return old;
    end if;

    -- If departing participant was host or no participants remain, delete the jam
    if (v_host_id = old.user_id) or not exists (select 1 from public.jam_participants where jam_id = old.jam_id) then
        delete from public.jams where id = old.jam_id;
    end if;
    return old;
end;
$$;

drop trigger if exists trg_check_jam_cleanup on public.jam_participants;
create trigger trg_check_jam_cleanup
after delete on public.jam_participants
for each row execute function public.check_jam_cleanup();

-- Delete abandoned jams older than 24 hours
delete from public.jams
where created_at < now() - interval '24 hours';

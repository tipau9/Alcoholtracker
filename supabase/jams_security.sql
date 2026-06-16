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

-- You can see only your own row directly; the full roster comes from the
-- function below. (Keeping a self-select keeps any direct self lookups working.)
create policy "jam_participants_select_own" on public.jam_participants
    for select using (user_id = auth.uid()::text);

-- You may only insert yourself into a jam.
create policy "jam_participants_insert_self" on public.jam_participants
    for insert with check (user_id = auth.uid()::text);

-- You may only update your own status (BAC / SOS / status).
create policy "jam_participants_update_own" on public.jam_participants
    for update using (user_id = auth.uid()::text)
            with check (user_id = auth.uid()::text);

-- You may delete yourself (leave), and the jam's host may delete anyone (kick).
-- NOTE: this subselect reads public.jams. To avoid infinite recursion, jams
-- must NOT have an RLS policy that in turn reads jam_participants.
create policy "jam_participants_delete_self_or_host" on public.jam_participants
    for delete using (
        user_id = auth.uid()::text
        or auth.uid()::text = (
            select j.host_user_id from public.jams j where j.id = jam_id
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
    where p.jam_id = p_jam_id
      and (
          -- caller is a member of this jam ...
          exists (
              select 1 from public.jam_participants me
              where me.jam_id = p_jam_id
                and me.user_id = auth.uid()::text
          )
          -- ... or the jam's host (covers the moment before anyone else joins)
          or auth.uid()::text = (
              select j.host_user_id from public.jams j where j.id = p_jam_id
          )
      )
$$;

-- Only signed-in users may call this; never the anon role.
revoke all on function public.jam_participants_for_member(uuid) from public, anon;
grant execute on function public.jam_participants_for_member(uuid) to authenticated;

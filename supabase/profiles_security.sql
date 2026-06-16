-- Profiles privacy hardening
--
-- Run this once in the Supabase SQL Editor (New query -> paste -> Run).
--
-- THE PROBLEM this fixes
-- ----------------------
-- The Crew/Jam features need to read *other* users' profiles (a friend's live
-- BAC, SOS flag, display name) by their friend_code. Until now the app did that
-- with a direct table read:
--     GET /rest/v1/profiles?friend_code=in.(...)&is_sharing=eq.true&select=*
-- For that to work, the profiles RLS policy has to let an authenticated user
-- SELECT other people's rows. But RLS predicates are row-level, not
-- query-level: any logged-in user could simply DROP the friend_code filter and
-- dump EVERY sharing user's current_bac / sos_active / friend_code / name. The
-- anon key ships inside the IPA, so RLS is the only real protection.
--
-- THE FIX
-- -------
-- 1. Lock the base table down to "you can only read/write your OWN row".
-- 2. Expose friends' data ONLY through SECURITY DEFINER functions that REQUIRE
--    the exact friend_code(s) or user id(s) as an argument and return just a
--    safe column projection. friend_code is a random secret you only have for
--    people you deliberately added, and user ids are unguessable UUIDs, so
--    there is no way to enumerate strangers anymore.
-- 3. Continuously-shared live data (current_bac / bac_updated_at) is nulled out
--    for users who turned sharing off; identity + the explicitly user-triggered
--    SOS flag are still returned to people who hold the code.

-- =========================================================================
-- 0. Make sure every column the app uses actually exists, so the functions
--    below compile even on a fresh project.
-- =========================================================================

alter table public.profiles add column if not exists display_name    text;
alter table public.profiles add column if not exists friend_code     text;
alter table public.profiles add column if not exists current_bac     double precision;
alter table public.profiles add column if not exists bac_updated_at  timestamptz;
alter table public.profiles add column if not exists is_sharing      boolean not null default true;
alter table public.profiles add column if not exists achievements    jsonb   not null default '[]'::jsonb;
alter table public.profiles add column if not exists sos_active      boolean not null default false;
alter table public.profiles add column if not exists sos_updated_at  timestamptz;
alter table public.profiles add column if not exists is_probationary boolean not null default false;

-- =========================================================================
-- 1. Base-table RLS: self-only. Drop EVERY existing policy on profiles first
--    (we cannot know their names), then recreate exactly the intended set.
--    Reading other users' rows now goes exclusively through the functions in
--    section 2 (SECURITY DEFINER bypasses these policies in a controlled way).
-- =========================================================================

alter table public.profiles enable row level security;

do $$
declare pol record;
begin
    for pol in
        select policyname from pg_policies
        where schemaname = 'public' and tablename = 'profiles'
    loop
        execute format('drop policy if exists %I on public.profiles', pol.policyname);
    end loop;
end $$;

create policy "profiles_select_own" on public.profiles
    for select using (auth.uid() = id);
create policy "profiles_insert_own" on public.profiles
    for insert with check (auth.uid() = id);
create policy "profiles_update_own" on public.profiles
    for update using (auth.uid() = id) with check (auth.uid() = id);

-- =========================================================================
-- 2. Friend-facing projection. Same column shape the client's FriendProfile
--    decodes. current_bac / bac_updated_at are hidden unless the user shares.
-- =========================================================================

-- By exact friend code(s): used for friend lookup ("add by code") and the
-- friends' BAC poll. Codes are normalised (uppercase, alnum only) to match how
-- they are stored, so client-side and server-side sanitising agree.
create or replace function public.friend_profiles_by_codes(p_codes text[])
returns table (
    id              uuid,
    display_name    text,
    friend_code     text,
    current_bac     double precision,
    bac_updated_at  timestamptz,
    is_sharing      boolean,
    achievements    jsonb,
    sos_active      boolean,
    is_probationary boolean
)
language sql
security definer
set search_path = public
stable
as $$
    select
        p.id,
        p.display_name,
        p.friend_code,
        case when p.is_sharing then p.current_bac    end,
        case when p.is_sharing then p.bac_updated_at end,
        p.is_sharing,
        p.achievements,
        p.sos_active,
        p.is_probationary
    from public.profiles p
    where p.friend_code = any (
        select upper(regexp_replace(c, '[^A-Za-z0-9]', '', 'g'))
        from unnest(p_codes) c
        where c is not null
    )
$$;

-- By user id(s): used for the mutual-friends display and friend-code lookup of
-- a known user id. UUIDs are unguessable, so this is safe without a code.
create or replace function public.friend_profiles_by_ids(p_ids uuid[])
returns table (
    id              uuid,
    display_name    text,
    friend_code     text,
    current_bac     double precision,
    bac_updated_at  timestamptz,
    is_sharing      boolean,
    achievements    jsonb,
    sos_active      boolean,
    is_probationary boolean
)
language sql
security definer
set search_path = public
stable
as $$
    select
        p.id,
        p.display_name,
        p.friend_code,
        case when p.is_sharing then p.current_bac    end,
        case when p.is_sharing then p.bac_updated_at end,
        p.is_sharing,
        p.achievements,
        p.sos_active,
        p.is_probationary
    from public.profiles p
    where p.id = any (p_ids)
$$;

-- Only signed-in users may call these; never the anon role.
revoke all on function public.friend_profiles_by_codes(text[]) from public, anon;
revoke all on function public.friend_profiles_by_ids(uuid[])  from public, anon;
grant execute on function public.friend_profiles_by_codes(text[]) to authenticated;
grant execute on function public.friend_profiles_by_ids(uuid[])  to authenticated;

-- =========================================================================
-- NOTE on jam_participants (not changed automatically)
-- =========================================================================
-- jam_participants also stores current_bac / has_sos_active per member. Joining
-- a jam already requires its code, so the exposure is far smaller than the
-- profiles enumeration above, and a safe policy there can recurse into jams
-- (see the comment in SupabaseService.fetchJamParticipants). If you want it
-- locked down too, the intended model is "a participant may read rows of a jam
-- they are themselves a member of" via a SECURITY DEFINER helper - ask and it
-- can be added here.

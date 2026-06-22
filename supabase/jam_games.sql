-- Jam mini-games over the server: water-chug leaderboard + round roulette.
--
-- Run this once in the Supabase SQL Editor (New query -> paste -> Run).
--
-- WHY
-- ---
-- The water contest and the round-roulette draw used to travel ONLY over the
-- Bluetooth/Multipeer channel, so participants who joined a jam online (by code,
-- not physically nearby) never saw the leaderboard or the spin. This adds a
-- server transport so every member of a server-backed jam takes part, polled on
-- the same 5 s tick as the participant roster.
--
-- SECURITY (mirrors supabase/jams_security.sql)
-- ---------------------------------------------
-- Both tables are fully locked by RLS with NO policies: nothing in the anon or
-- authenticated role may read or write them directly. All access goes through the
-- SECURITY DEFINER functions below, each of which first checks that the caller is
-- a member (or the host) of the jam in question. So there is nothing to
-- enumerate: you can only touch games of a jam you are actually in.

-- =========================================================================
-- 0. Tables. create table if not exists keeps existing projects intact.
-- =========================================================================

create table if not exists public.jam_water_scores (
    jam_id         uuid not null references public.jams(id) on delete cascade,
    participant_id uuid not null,
    user_id        text,
    name           text,
    ms             integer not null,
    updated_at     timestamptz default now(),
    primary key (jam_id, participant_id)
);

create table if not exists public.jam_roulette (
    jam_id        uuid primary key references public.jams(id) on delete cascade,
    draw_id       uuid not null,
    participants  jsonb not null,
    winner_index  integer not null,
    starter_name  text,
    created_at    timestamptz default now()
);

-- =========================================================================
-- 1. Lock both tables: RLS on, drop EVERY existing policy, add none. Only the
--    SECURITY DEFINER functions below (which run as the table owner and bypass
--    RLS) may read or write. Same pattern the roster read uses.
-- =========================================================================

alter table public.jam_water_scores enable row level security;
alter table public.jam_roulette     enable row level security;

do $$
declare pol record;
begin
    for pol in
        select tablename, policyname from pg_policies
        where schemaname = 'public'
          and tablename in ('jam_water_scores', 'jam_roulette')
    loop
        execute format('drop policy if exists %I on public.%I', pol.policyname, pol.tablename);
    end loop;
end $$;

-- =========================================================================
-- 2. Functions. Drop first so a re-run with changed signatures/return types
--    never hits "cannot change return type of existing function".
-- =========================================================================

drop function if exists public.jam_submit_water(uuid, uuid, text, integer);  -- pre-fix signature
drop function if exists public.jam_submit_water(uuid, text, integer);
drop function if exists public.jam_reset_water(uuid);
drop function if exists public.jam_water_board(uuid);
drop function if exists public.jam_set_roulette(uuid, uuid, jsonb, integer, text);
drop function if exists public.jam_roulette(uuid);

-- True when the caller is a member of, or hosts, the given jam. Used as the gate
-- in every function below so games cannot be read or written across jams.
create or replace function public.is_jam_member(p_jam_id uuid)
returns boolean
language sql
security definer
set search_path = public
stable
as $$
    select exists (
        select 1 from public.jam_participants me
        where me.jam_id::uuid = p_jam_id
          and me.user_id::uuid = auth.uid()
    ) or auth.uid() = (
        select j.host_user_id::uuid from public.jams j where j.id::uuid = p_jam_id
    )
$$;

-- Submit (or improve) the caller's water-chug time. Keeps the best (lowest) ms.
--
-- The participant id is derived server-side from the caller's own jam_participants
-- row, never taken from the client: otherwise a member could write rows under
-- anyone else's id, or flood the board with unlimited fake participant ids. The
-- lookup doubles as the membership check (you must hold a participant row).
create or replace function public.jam_submit_water(
    p_jam_id uuid, p_name text, p_ms integer
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare v_participant_id uuid;
begin
    select me.id::uuid into v_participant_id
    from public.jam_participants me
    where me.jam_id::uuid = p_jam_id
      and me.user_id::uuid = auth.uid()
    limit 1;
    if v_participant_id is null then
        raise exception 'not a participant of this jam';
    end if;
    -- 1 ms .. 10 min guards garbage / overflow.
    if p_ms is null or p_ms < 1 or p_ms > 600000 then
        raise exception 'invalid time';
    end if;
    insert into public.jam_water_scores (jam_id, participant_id, user_id, name, ms, updated_at)
    values (p_jam_id, v_participant_id, auth.uid()::text, left(coalesce(p_name, ''), 40), p_ms, now())
    on conflict (jam_id, participant_id) do update
        set ms         = least(public.jam_water_scores.ms, excluded.ms),
            name       = excluded.name,
            updated_at = now();
end $$;

-- Clear the whole leaderboard for a jam (any member may reset, as in the app).
create or replace function public.jam_reset_water(p_jam_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if not public.is_jam_member(p_jam_id) then
        raise exception 'not a member of this jam';
    end if;
    delete from public.jam_water_scores where jam_id = p_jam_id;
end $$;

-- Read the leaderboard. Member-only, so nothing can be enumerated. (Named
-- *_board, not *_scores, to avoid sharing a name with the table it reads.)
create or replace function public.jam_water_board(p_jam_id uuid)
returns table (participant_id uuid, name text, ms integer)
language sql
security definer
set search_path = public
stable
as $$
    select s.participant_id, s.name, s.ms
    from public.jam_water_scores s
    where s.jam_id = p_jam_id
      and public.is_jam_member(p_jam_id)
    order by s.ms asc
$$;

-- Publish a roulette draw (one current draw per jam; a new one replaces it).
-- The winner index is picked client-side and stored as-is; every member reads
-- the same draw so the wheel lands on the same person everywhere.
create or replace function public.jam_set_roulette(
    p_jam_id uuid, p_draw_id uuid, p_participants jsonb,
    p_winner_index integer, p_starter_name text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare n integer;
begin
    if not public.is_jam_member(p_jam_id) then
        raise exception 'not a member of this jam';
    end if;
    n := jsonb_array_length(p_participants);
    if n is null or n < 2 or n > 50 then
        raise exception 'invalid participant count';
    end if;
    if p_winner_index < 0 or p_winner_index >= n then
        raise exception 'winner index out of range';
    end if;
    insert into public.jam_roulette (jam_id, draw_id, participants, winner_index, starter_name, created_at)
    values (p_jam_id, p_draw_id, p_participants, p_winner_index, left(coalesce(p_starter_name, ''), 40), now())
    on conflict (jam_id) do update
        set draw_id      = excluded.draw_id,
            participants = excluded.participants,
            winner_index = excluded.winner_index,
            starter_name = excluded.starter_name,
            created_at   = now();
end $$;

-- Read the current draw, but only if it is fresh (last 120 s). A member joining
-- an old jam therefore does not suddenly see a stale spin; the client also
-- de-duplicates on draw_id so a draw is presented at most once per device.
create or replace function public.jam_roulette(p_jam_id uuid)
returns table (
    draw_id uuid, participants jsonb, winner_index integer,
    starter_name text, created_at timestamptz
)
language sql
security definer
set search_path = public
stable
as $$
    select r.draw_id, r.participants, r.winner_index, r.starter_name, r.created_at
    from public.jam_roulette r
    where r.jam_id = p_jam_id
      and r.created_at > now() - interval '120 seconds'
      and public.is_jam_member(p_jam_id)
    limit 1
$$;

-- =========================================================================
-- 3. Grants. Signed-in users only; never the anon role.
-- =========================================================================

revoke all on function public.is_jam_member(uuid)                              from public, anon;
revoke all on function public.jam_submit_water(uuid, text, integer)            from public, anon;
revoke all on function public.jam_reset_water(uuid)                            from public, anon;
revoke all on function public.jam_water_board(uuid)                            from public, anon;
revoke all on function public.jam_set_roulette(uuid, uuid, jsonb, integer, text) from public, anon;
revoke all on function public.jam_roulette(uuid)                               from public, anon;

grant execute on function public.is_jam_member(uuid)                              to authenticated;
grant execute on function public.jam_submit_water(uuid, text, integer)            to authenticated;
grant execute on function public.jam_reset_water(uuid)                            to authenticated;
grant execute on function public.jam_water_board(uuid)                            to authenticated;
grant execute on function public.jam_set_roulette(uuid, uuid, jsonb, integer, text) to authenticated;
grant execute on function public.jam_roulette(uuid)                               to authenticated;

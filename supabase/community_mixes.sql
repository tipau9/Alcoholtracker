-- ============================================================================
-- Self-learning community mixes (user-generated cocktail recipes).
-- Same model as community_drinks: crowd confirmation + manual approval.
-- Run once in the Supabase SQL editor.
--
--   * The app calls contribute_mix() when a user shares a mix.
--   * The payload is validated server-side (the anon key ships in the app, so
--     the values cannot be trusted). Out-of-range data is rejected outright.
--   * Stored as 'pending'; each voter casts one vote.
--   * Auto-approves after CONFIRM_THRESHOLD distinct voters share the same mix
--     (matched by normalised name). You can also approve/reject manually in the
--     dashboard; a 'rejected' mix is never auto-approved.
--   * The app only ever reads status = 'approved'.
--
-- Anti-abuse mirrors community_drinks: validation + a SERVER-derived voter
-- identity (community_voter_id) + a per-voter hourly cap, so the anon key alone
-- cannot fake distinct devices to self-approve or flood the catalogue. The
-- helper is defined here too so this file stays standalone and idempotent.
-- ============================================================================

create table if not exists public.community_mixes (
    id              uuid primary key default gen_random_uuid(),
    name            text not null,
    name_key        text unique not null,          -- lower(trim(name)) for dedupe
    ingredients     jsonb not null,                -- [{id,name,abv,volume}, ...]
    total_volume    double precision not null default 0,
    total_abv       double precision not null default 0,
    calories        integer not null default 0,
    status          text not null default 'pending',   -- pending | approved | rejected
    confirmed_count integer not null default 0,
    created_at      timestamptz not null default now()
);

alter table public.community_mixes add column if not exists status          text    not null default 'pending';
alter table public.community_mixes add column if not exists confirmed_count integer not null default 0;

create table if not exists public.community_mix_votes (
    name_key   text not null,
    voter      text not null,
    created_at timestamptz not null default now(),
    primary key (name_key, voter)
);

create index if not exists community_mix_votes_voter_idx
    on public.community_mix_votes (voter, created_at);

-- Server-derived voter identity (signed-in user id, else request IP, else the
-- client fallback). Same definition as in community_drinks.sql; create or
-- replace keeps re-running either file safe. Internal only; never granted to
-- anon directly.
create or replace function public.community_voter_id(p_fallback text)
returns text
language sql
security definer
set search_path = public
stable
as $$
    select coalesce(
        auth.uid()::text,
        nullif(trim(split_part(
            current_setting('request.headers', true)::json ->> 'x-forwarded-for',
            ',', 1)), ''),
        nullif(trim(p_fallback), ''),
        'anon'
    );
$$;

create or replace function public.contribute_mix(
    p_name         text,
    p_ingredients  jsonb,
    p_total_volume double precision,
    p_total_abv    double precision,
    p_calories     integer,
    p_voter        text
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_threshold  constant integer := 3;
    v_hourly_cap constant integer := 40;
    v_key        text := lower(trim(coalesce(p_name, '')));
    v_count      integer;
    v_recent     integer;
    v_voter      text;
begin
    -- --- Validate the payload (never trust the anon caller) -----------------
    if v_key is null or length(v_key) = 0 or length(v_key) > 80 then
        return;
    end if;
    if p_ingredients is null
       or jsonb_typeof(p_ingredients) <> 'array'
       or jsonb_array_length(p_ingredients) = 0
       or jsonb_array_length(p_ingredients) > 50 then
        return;
    end if;
    if p_total_abv is null or p_total_abv < 0 or p_total_abv > 100 then
        return;
    end if;
    if p_total_volume is null or p_total_volume <= 0 or p_total_volume > 10000 then
        return;
    end if;
    if p_calories is null or p_calories < 0 or p_calories > 10000 then
        return;
    end if;

    -- --- Trusted voter + flood control -------------------------------------
    v_voter := public.community_voter_id(p_voter);

    select count(*) into v_recent
        from public.community_mix_votes
        where voter = v_voter
          and created_at > now() - interval '1 hour';
    if v_recent >= v_hourly_cap then
        return;
    end if;

    -- --- Insert-or-vote ----------------------------------------------------
    insert into public.community_mixes
        (name, name_key, ingredients, total_volume, total_abv, calories, status, confirmed_count)
    values
        (trim(p_name), v_key, p_ingredients, p_total_volume, p_total_abv, p_calories, 'pending', 0)
    on conflict (name_key) do nothing;

    insert into public.community_mix_votes (name_key, voter)
    values (v_key, v_voter)
    on conflict (name_key, voter) do nothing;

    select count(*) into v_count
        from public.community_mix_votes where name_key = v_key;

    update public.community_mixes
        set confirmed_count = v_count
        where name_key = v_key;

    update public.community_mixes
        set status = 'approved'
        where name_key = v_key
          and status = 'pending'
          and v_count >= v_threshold;
end;
$$;

alter table public.community_mixes      enable row level security;
alter table public.community_mix_votes  enable row level security;

drop policy if exists "community_mixes read approved" on public.community_mixes;
create policy "community_mixes read approved"
    on public.community_mixes
    for select
    to anon
    using (status = 'approved');

revoke all on function public.community_voter_id(text) from public, anon;
-- Anon may contribute; signed-in users contribute with their own token (vote
-- keys on a real account id, harder to sybil than an IP). See community_drinks.
grant execute on function public.contribute_mix(
    text, jsonb, double precision, double precision, integer, text
) to anon, authenticated;

-- ============================================================================
-- Self-learning community mixes (user-generated cocktail recipes).
-- Same model as community_drinks: crowd confirmation + manual approval.
-- Run once in the Supabase SQL editor.
--
--   * The app calls contribute_mix() when a user shares a mix.
--   * Stored as 'pending'; each device casts one vote.
--   * Auto-approves after CONFIRM_THRESHOLD distinct devices share the same mix
--     (matched by normalised name). You can also approve/reject manually in the
--     dashboard; a 'rejected' mix is never auto-approved.
--   * The app only ever reads status = 'approved'.
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
    v_threshold constant integer := 3;
    v_key text := lower(trim(p_name));
    v_count integer;
begin
    if v_key is null or length(v_key) = 0 then
        return;
    end if;

    insert into public.community_mixes
        (name, name_key, ingredients, total_volume, total_abv, calories, status, confirmed_count)
    values
        (p_name, v_key, p_ingredients, p_total_volume, p_total_abv, p_calories, 'pending', 0)
    on conflict (name_key) do nothing;

    insert into public.community_mix_votes (name_key, voter)
    values (v_key, coalesce(nullif(trim(p_voter), ''), 'anon'))
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

grant execute on function public.contribute_mix(
    text, jsonb, double precision, double precision, integer, text
) to anon;

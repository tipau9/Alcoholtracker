-- ============================================================================
-- Self-learning community drinks: crowd confirmation + manual approval
-- Run this once in the Supabase SQL editor.
--
-- Flow:
--   * The app calls the contribute_drink() RPC after a barcode scan.
--   * The payload is validated server-side (the anon key ships in the app, so
--     the values cannot be trusted). Out-of-range data is rejected outright.
--   * The drink is stored as 'pending' and the scanning device casts one vote.
--   * Once CONFIRM_THRESHOLD distinct voters confirmed the same barcode, the
--     row auto-flips to 'approved' and becomes visible to everyone.
--   * You can manually set status = 'approved' (show now, even with 1 vote) or
--     'rejected' (block it) in the dashboard. A 'rejected' row is never
--     auto-approved by the crowd.
--   * The app only ever reads status = 'approved'.
--
-- ANTI-ABUSE (why this is not just the naive insert-and-count)
-- ------------------------------------------------------------
-- The contribution RPC is callable with the anon key, so anyone who extracts it
-- from the IPA can call it. Two things stop that from poisoning everyone's BAC
-- maths (the app feeds approved ABV values straight into the Widmark engine):
--   1. Value validation: abv/volume/calories/name/category are range- and
--      whitelist-checked here. A junk scan (abv 999, negative calories, a made
--      up category) is dropped, never stored.
--   2. A *server-derived* voter identity (see community_voter_id). The vote is
--      keyed on the signed-in user id, else the request IP, NOT the client
--      supplied string. So one caller cannot fake N distinct "devices" to self
--      approve a drink, and a per-voter hourly cap throttles floods.
-- ============================================================================

-- 1) Tables -----------------------------------------------------------------

create table if not exists public.community_drinks (
    id              uuid primary key default gen_random_uuid(),
    barcode         text unique not null,
    name            text not null,
    category        text not null,
    volume          double precision not null,
    abv             double precision not null,
    calories        integer not null default 0,
    icon_name       text not null default 'wineglass.fill',
    status          text not null default 'pending',   -- pending | approved | rejected
    admin_locked    boolean not null default false,
    confirmed_count integer not null default 0,
    created_at      timestamptz not null default now()
);

-- Make sure the moderation columns exist if the table predates this script.
alter table public.community_drinks add column if not exists status          text    not null default 'pending';
alter table public.community_drinks add column if not exists admin_locked    boolean not null default false;
alter table public.community_drinks add column if not exists confirmed_count integer not null default 0;

-- One vote per (barcode, voter). The unique PK makes re-scans idempotent.
create table if not exists public.community_drink_votes (
    barcode    text not null,
    voter      text not null,
    is_authenticated boolean not null default false,
    created_at timestamptz not null default now(),
    primary key (barcode, voter)
);

alter table public.community_drink_votes add column if not exists is_authenticated boolean not null default false;

create table if not exists public.community_drink_submissions (
    barcode          text not null,
    voter            text not null,
    is_authenticated boolean not null default false,
    name             text not null,
    category         text not null,
    volume           double precision not null,
    abv              double precision not null,
    calories         integer not null default 0,
    icon_name        text not null default 'wineglass.fill',
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    primary key (barcode, voter)
);

alter table public.community_drink_submissions add column if not exists is_authenticated boolean not null default false;
alter table public.community_drink_submissions add column if not exists updated_at timestamptz not null default now();

-- Lets the hourly anti-flood count and the per-voter lookups stay cheap.
create index if not exists community_drink_votes_voter_idx
    on public.community_drink_votes (voter, created_at);

create index if not exists community_drink_submissions_barcode_auth_idx
    on public.community_drink_submissions (barcode, is_authenticated, category);

create table if not exists public.admin_blocked_voters (
    voter      text primary key,
    reason     text not null default '',
    created_at timestamptz not null default now(),
    created_by uuid references auth.users(id) on delete set null
);

alter table public.admin_blocked_voters enable row level security;
alter table public.community_drink_submissions enable row level security;

-- 2) Trusted voter identity -------------------------------------------------
-- Derive who is voting on the SERVER instead of trusting a client string:
--   * the signed-in user id if the request carries a user JWT, else
--   * the originating client IP from the x-forwarded-for header, else
--   * the client-supplied fallback (self-hosted / no proxy), else 'anon'.
-- SECURITY DEFINER + reads request GUCs that PostgREST sets per request. Not
-- granted to anon directly; it is only ever called from the RPCs below.
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

-- Safety-critical plausibility guard. Community submissions can be wrong or
-- malicious, but impossible category/ABV pairs must never reach auto-approval.
create or replace function public.community_drink_abv_plausible(
    p_category text,
    p_abv double precision
) returns boolean
language sql
security definer
set search_path = public
stable
as $$
    select case lower(trim(coalesce(p_category, '')))
        when 'beer'      then p_abv between 0 and 12
        when 'wine'      then p_abv between 8 and 22
        when 'sparkling' then p_abv between 5 and 16
        when 'spirits'   then p_abv between 15 and 80
        when 'liqueur'   then p_abv between 10 and 55
        when 'shot'      then p_abv between 15 and 60
        when 'cider'     then p_abv between 1 and 12
        when 'fortified' then p_abv between 8 and 30
        when 'cocktail'  then p_abv between 0 and 45
        when 'mixed'     then p_abv between 0 and 30
        when 'other'     then p_abv between 0 and 80
        else false
    end;
$$;

-- 3) RPC: validate -> insert-or-vote -> crowd auto-approval ------------------

create or replace function public.contribute_drink(
    p_barcode   text,
    p_name      text,
    p_category  text,
    p_volume    double precision,
    p_abv       double precision,
    p_calories  integer,
    p_icon_name text,
    p_voter     text
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_threshold  constant integer := 5;    -- distinct voters needed for auto-approval
    v_hourly_cap constant integer := 40;   -- max votes per voter per hour (anti-flood)
    v_count      integer;
    v_recent     integer;
    v_voter      text;
    v_is_authenticated boolean;
    v_name       text;
    v_category   text;
    v_consensus_category text;
    v_icon       text;
begin
    delete from public.community_drink_submissions s
    using public.community_drinks d
    where d.barcode = s.barcode
      and d.status = 'pending'
      and d.created_at < now() - interval '30 days';

    delete from public.community_drink_votes v
    using public.community_drinks d
    where d.barcode = v.barcode
      and d.status = 'pending'
      and d.created_at < now() - interval '30 days';

    delete from public.community_drinks
    where status = 'pending'
      and created_at < now() - interval '30 days';

    -- --- Validate the payload (never trust the anon caller) -----------------
    if p_barcode is null or length(trim(p_barcode)) = 0 or length(p_barcode) > 64 then
        return;
    end if;

    v_name := nullif(trim(p_name), '');
    if v_name is null or length(v_name) > 80 then
        return;
    end if;

    v_category := lower(trim(coalesce(p_category, '')));
    if v_category not in (
        'beer','wine','sparkling','spirits','liqueur',
        'cocktail','mixed','shot','cider','fortified','other'
    ) then
        return;
    end if;

    if p_abv is null or p_abv < 0 or p_abv > 100 then
        return;
    end if;
    if not public.community_drink_abv_plausible(v_category, p_abv) then
        return;
    end if;
    if p_volume is null or p_volume <= 0 or p_volume > 10000 then
        return;
    end if;
    if p_calories is null or p_calories < 0 or p_calories > 10000 then
        return;
    end if;

    v_icon := nullif(trim(p_icon_name), '');
    if v_icon is null or length(v_icon) > 64 then
        v_icon := 'wineglass.fill';
    end if;

    -- --- Trusted voter + flood control -------------------------------------
    v_voter := public.community_voter_id(p_voter);
    v_is_authenticated := auth.uid() is not null;
    if exists (select 1 from public.admin_blocked_voters where voter = v_voter) then
        return;
    end if;

    select count(*) into v_recent
        from public.community_drink_votes
        where voter = v_voter
          and created_at > now() - interval '1 hour';
    if v_recent >= v_hourly_cap then
        return;
    end if;

    -- --- Insert-or-vote ----------------------------------------------------
    -- Insert the drink once; keep the first contributor's values so a later bad
    -- scan cannot overwrite good data.
    insert into public.community_drinks
        (barcode, name, category, volume, abv, calories, icon_name, status, confirmed_count)
    values
        (p_barcode, v_name, v_category, p_volume, p_abv, p_calories, v_icon, 'pending', 0)
    on conflict (barcode) do nothing;

    -- Record this voter's vote (idempotent per trusted identity).
    insert into public.community_drink_votes (barcode, voter, is_authenticated)
    values (p_barcode, v_voter, v_is_authenticated)
    on conflict (barcode, voter) do update
        set is_authenticated = community_drink_votes.is_authenticated or excluded.is_authenticated;

    insert into public.community_drink_submissions
        (barcode, voter, is_authenticated, name, category, volume, abv, calories, icon_name)
    values
        (p_barcode, v_voter, v_is_authenticated, v_name, v_category, p_volume, p_abv, p_calories, v_icon)
    on conflict (barcode, voter) do update
        set is_authenticated = community_drink_submissions.is_authenticated or excluded.is_authenticated,
            name = excluded.name,
            category = excluded.category,
            volume = excluded.volume,
            abv = excluded.abv,
            calories = excluded.calories,
            icon_name = excluded.icon_name,
            updated_at = now();

    select s.category, count(*)::integer into v_consensus_category, v_count
    from public.community_drink_submissions s
    left join public.admin_blocked_voters b on b.voter = s.voter
    where s.barcode = p_barcode
      and s.is_authenticated = true
      and b.voter is null
    group by s.category
    order by count(*) desc, max(s.updated_at) desc
    limit 1;

    v_count := coalesce(v_count, 0);

    update public.community_drinks
        set confirmed_count = v_count
        where barcode = p_barcode
          and status <> 'rejected';

    -- Crowd auto-approval: only promotes 'pending' rows. Never resurrects a
    -- manually 'rejected' row, never touches an already 'approved' one.
    update public.community_drinks
        set status = 'approved',
            name = consensus.name,
            category = consensus.category,
            volume = consensus.volume,
            abv = consensus.abv,
            calories = consensus.calories,
            icon_name = consensus.icon_name,
            confirmed_count = v_count
        from (
            with trusted as (
                select s.*
                from public.community_drink_submissions s
                left join public.admin_blocked_voters b on b.voter = s.voter
                where s.barcode = p_barcode
                  and s.is_authenticated = true
                  and b.voter is null
                  and s.category = v_consensus_category
            ),
            medians as (
                select
                    percentile_cont(0.5) within group (order by volume)::double precision as volume,
                    percentile_cont(0.5) within group (order by abv)::double precision as abv,
                    round(percentile_cont(0.5) within group (order by calories))::integer as calories
                from trusted
            ),
            mode_name as (
                select name from trusted group by name order by count(*) desc, max(updated_at) desc limit 1
            ),
            mode_icon as (
                select icon_name from trusted group by icon_name order by count(*) desc, max(updated_at) desc limit 1
            )
            select
                (select name from mode_name) as name,
                v_consensus_category as category,
                medians.volume,
                medians.abv,
                medians.calories,
                coalesce((select icon_name from mode_icon), 'wineglass.fill') as icon_name
            from medians
        ) consensus
        where barcode = p_barcode
          and status = 'pending'
          and admin_locked = false
          and v_count >= v_threshold;
end;
$$;

-- 4) Permissions / RLS ------------------------------------------------------
-- The app uses the anon key. Anon may read approved drinks and call the RPC,
-- but cannot write the tables directly (writes go through the SECURITY DEFINER
-- function only), so the data cannot be spammed by raw inserts.

alter table public.community_drinks       enable row level security;
alter table public.community_drink_votes  enable row level security;

drop policy if exists "community_drinks read approved" on public.community_drinks;
create policy "community_drinks read approved"
    on public.community_drinks
    for select
    to anon
    using (status = 'approved');

-- No direct insert/update/select policies for anon on the votes table or for
-- writing community_drinks: everything goes through contribute_drink().

-- community_voter_id is internal; do NOT expose it to anon. Only the RPC is.
revoke all on function public.community_voter_id(text) from public, anon;
revoke all on function public.community_drink_abv_plausible(text, double precision) from public, anon, authenticated;
revoke all on table public.community_drink_submissions from anon, authenticated;
revoke all on table public.admin_blocked_voters from anon, authenticated;
-- Anon may contribute (offline-first users who never sign in). Signed-in users
-- contribute with their own token so the vote keys on a real account id, which
-- is far harder to sybil than an IP, hence the grant to authenticated too.
grant execute on function public.contribute_drink(
    text, text, text, double precision, double precision, integer, text, text
) to anon, authenticated;

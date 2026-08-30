-- ============================================================================
-- City drink trends: anonymous "what's being drunk near you" popularity feed
-- Run this once in the Supabase SQL editor.
--
-- Flow:
--   * Every time a user logs a drink, the app calls ping_city_drink() with the
--     current city, drink metadata and bounded aggregate inputs. Nothing identifying is
--     stored alongside the trend -- the feed is anonymous (the voter column is
--     server-derived and only used for flood control, never exposed).
--   * TrendsView calls city_drink_trends() (anon key) to show the top drinks
--     logged in that city over the last p_hours hours.
--
-- The anon key ships inside the app, so both RPCs validate their input and the
-- table is only writable through the SECURITY DEFINER functions (RLS on, no
-- direct policies). A generous per-identity hourly cap stops a single device
-- from flooding the popularity list without penalising a real heavy night.
-- ============================================================================

-- 1) Table ------------------------------------------------------------------

create table if not exists public.city_drink_pings (
    id         uuid        primary key default gen_random_uuid(),
    city       text        not null,          -- normalised: lower(trim(city))
    drink_name text        not null,
    category   text        not null,
    voter      text        not null,          -- server-derived, never exposed
    current_bac double precision,
    session_duration_minutes integer,
    drink_duration_minutes integer,
    local_hour smallint,
    created_at timestamptz not null default now()
);

-- Idempotent backfill: if an earlier/partial city_drink_pings table already
-- exists (a previous attempt), `create table if not exists` above is a no-op,
-- so make sure every column the indexes and RPCs need is present.
alter table public.city_drink_pings add column if not exists city       text        not null default '';
alter table public.city_drink_pings add column if not exists drink_name text        not null default '';
alter table public.city_drink_pings add column if not exists category   text        not null default 'other';
alter table public.city_drink_pings add column if not exists voter      text        not null default 'anon';
alter table public.city_drink_pings add column if not exists current_bac double precision;
alter table public.city_drink_pings add column if not exists session_duration_minutes integer;
alter table public.city_drink_pings add column if not exists drink_duration_minutes integer;
alter table public.city_drink_pings add column if not exists local_hour smallint;
alter table public.city_drink_pings add column if not exists created_at timestamptz not null default now();

-- Trend aggregation reads (city, recent window); the flood cap reads
-- (voter, recent window). Both stay cheap with these composite indexes.
create index if not exists city_drink_pings_city_time_idx
    on public.city_drink_pings (city, created_at);
create index if not exists city_drink_pings_voter_time_idx
    on public.city_drink_pings (voter, created_at);

alter table public.city_drink_pings enable row level security;
-- No direct policies: all access goes through the SECURITY DEFINER RPCs below.

-- Drop any earlier versions first: `create or replace` cannot change a
-- function's return type, so a prior city_drink_trends() with different OUT
-- columns must be removed before recreating it. The grants are re-applied at
-- the end of this script.
drop function if exists public.ping_city_drink(text, text, text);
drop function if exists public.ping_city_drink(text, text, text, double precision, integer, integer, integer);
drop function if exists public.city_drink_trends(text, integer);
drop function if exists public.city_drink_insights(text, integer);

-- 2) RPC: record one anonymous ping -----------------------------------------

create or replace function public.ping_city_drink(
    p_city       text,
    p_drink_name text,
    p_category   text,
    p_current_bac double precision,
    p_session_duration_minutes integer,
    p_drink_duration_minutes integer,
    p_local_hour integer
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_hourly_cap constant integer := 120;  -- max pings per identity per hour
    v_city       text;
    v_name       text;
    v_category   text;
    v_voter      text;
    v_voter_identity text;
    v_recent     integer;
begin
    -- --- Validate the payload (never trust the anon caller) -----------------
    v_city := lower(trim(coalesce(p_city, '')));
    if v_city = '' or length(v_city) > 80 then
        return;
    end if;

    v_name := nullif(trim(p_drink_name), '');
    if v_name is null or length(v_name) > 80 then
        return;
    end if;

    v_category := lower(trim(coalesce(p_category, '')));
    if v_category not in (
        'beer','wine','sparkling','spirits','liqueur',
        'cocktail','mixed','shot','cider','fortified','other'
    ) then
        v_category := 'other';
    end if;

    -- --- Trusted identity (server-derived) + flood control ------------------
    -- The signed-in user id if a user JWT is present, else the originating
    -- client IP, else 'anon'. Never the client-supplied value.
    v_voter_identity := coalesce(
        auth.uid()::text,
        nullif(trim(split_part(
            current_setting('request.headers', true)::json ->> 'x-forwarded-for',
            ',', 1)), ''),
        'anon'
    );
    -- Keep only a stable one-way pseudonym for flood control and the five-person
    -- privacy threshold; never store the raw account UUID or network address.
    v_voter := md5('city-trends-v2:' || v_voter_identity);

    select count(*) into v_recent
        from public.city_drink_pings
        where voter = v_voter
          and created_at > now() - interval '1 hour';
    if v_recent >= v_hourly_cap then
        return;
    end if;

    insert into public.city_drink_pings (
        city, drink_name, category, voter, current_bac,
        session_duration_minutes, drink_duration_minutes, local_hour
    ) values (
        v_city,
        v_name,
        v_category,
        v_voter,
        case when p_current_bac between 0 and 5 then p_current_bac else null end,
        case when p_session_duration_minutes between 0 and 1440 then p_session_duration_minutes else null end,
        case when p_drink_duration_minutes between 1 and 480 then p_drink_duration_minutes else null end,
        case when p_local_hour between 0 and 23 then p_local_hour::smallint else null end
    );

    -- Rolling cleanup (probabilistic so it isn't a full scan on every ping):
    -- the feed only ever looks back a few days, so drop anything older.
    if random() < 0.02 then
        delete from public.city_drink_pings
            where created_at < now() - interval '7 days';
    end if;
end;
$$;

-- Compatibility wrapper for older app versions. New numeric fields remain null.
create or replace function public.ping_city_drink(
    p_city text,
    p_drink_name text,
    p_category text
) returns void
language sql
security definer
set search_path = public
as $$
    select public.ping_city_drink(
        p_city, p_drink_name, p_category,
        null::double precision, null::integer, null::integer, null::integer
    );
$$;

-- 3) RPC: top drinks in a city over the last p_hours hours -------------------

create or replace function public.city_drink_trends(
    p_city  text,
    p_hours integer default 24
) returns table (
    drink_name text,
    category   text,
    ping_count integer
)
language sql
security definer
set search_path = public
stable
as $$
    select p.drink_name,
           p.category,
           count(*)::int as ping_count
    from public.city_drink_pings p
    where p.city = lower(trim(coalesce(p_city, '')))
      and p.created_at > now()
          - (least(greatest(coalesce(p_hours, 24), 1), 168) || ' hours')::interval
    group by p.drink_name, p.category
    order by ping_count desc, p.drink_name asc
    limit 20;
$$;

-- 4) RPC: privacy-thresholded city overview ---------------------------------
-- Detailed averages and time patterns are returned only when at least five
-- distinct server-derived identities contributed inside the selected window.

create or replace function public.city_drink_insights(
    p_city text,
    p_hours integer default 168
) returns jsonb
language sql
security definer
set search_path = public
stable
as $$
with filtered as (
    select *
    from public.city_drink_pings p
    where p.city = lower(trim(coalesce(p_city, '')))
      and p.created_at > now()
          - (least(greatest(coalesce(p_hours, 168), 1), 168) || ' hours')::interval
), per_contributor as (
    -- Average each person first so one person logging many drinks cannot dominate
    -- the city-wide BAC and duration figures.
    select voter,
           avg(current_bac) as avg_bac,
           avg(session_duration_minutes) as avg_session_minutes,
           avg(drink_duration_minutes) as avg_drink_minutes
    from filtered
    group by voter
), stats as (
    select (select count(*)::int from filtered) as total_drinks,
           count(*)::int as contributors,
           round(avg(avg_bac)::numeric, 2) as avg_bac,
           round(avg(avg_session_minutes)::numeric, 0) as avg_session_minutes,
           round(avg(avg_drink_minutes)::numeric, 0) as avg_drink_minutes
    from per_contributor
), top_drinks as (
    select drink_name, category, count(*)::int as ping_count
    from filtered
    group by drink_name, category
    order by ping_count desc, drink_name asc
    limit 5
), hourly as (
    select local_hour::int as hour, count(*)::int as ping_count
    from filtered
    where local_hour is not null
    group by local_hour
    order by local_hour
), categories as (
    select category, count(*)::int as ping_count
    from filtered
    group by category
    order by ping_count desc, category
)
select case
    when stats.contributors < 5 then
        jsonb_build_object(
            'sample_sufficient', false,
            'minimum_contributors', 5,
            'total_drinks', 0,
            'top_drinks', '[]'::jsonb,
            'hourly', '[]'::jsonb,
            'categories', '[]'::jsonb
        )
    else
        jsonb_build_object(
            'sample_sufficient', true,
            'minimum_contributors', 5,
            'contributor_count', stats.contributors,
            'total_drinks', stats.total_drinks,
            'average_bac', stats.avg_bac,
            'average_session_minutes', stats.avg_session_minutes,
            'average_drink_minutes', stats.avg_drink_minutes,
            'top_drinks', coalesce((select jsonb_agg(to_jsonb(t)) from top_drinks t), '[]'::jsonb),
            'hourly', coalesce((select jsonb_agg(to_jsonb(h)) from hourly h), '[]'::jsonb),
            'categories', coalesce((select jsonb_agg(to_jsonb(c)) from categories c), '[]'::jsonb)
        )
end
from stats;
$$;

-- 5) Permissions ------------------------------------------------------------
-- Anon may read trends and ping (offline-first users who never sign in); the
-- app pings with the signed-in token when available so the cap keys on a real
-- account id. The table stays locked (RLS on, no policies) -- writes only ever
-- happen through ping_city_drink().

grant execute on function public.ping_city_drink(text, text, text) to anon, authenticated;
grant execute on function public.ping_city_drink(text, text, text, double precision, integer, integer, integer) to anon, authenticated;
grant execute on function public.city_drink_trends(text, integer)  to anon, authenticated;
grant execute on function public.city_drink_insights(text, integer) to anon, authenticated;

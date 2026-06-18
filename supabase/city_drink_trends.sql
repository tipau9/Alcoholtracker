-- ============================================================================
-- City drink trends: anonymous "what's being drunk near you" popularity feed
-- Run this once in the Supabase SQL editor.
--
-- Flow:
--   * Every time a user logs a drink, the app calls ping_city_drink() with the
--     current city, the drink name and its category. Nothing identifying is
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
    created_at timestamptz not null default now()
);

-- Trend aggregation reads (city, recent window); the flood cap reads
-- (voter, recent window). Both stay cheap with these composite indexes.
create index if not exists city_drink_pings_city_time_idx
    on public.city_drink_pings (city, created_at);
create index if not exists city_drink_pings_voter_time_idx
    on public.city_drink_pings (voter, created_at);

alter table public.city_drink_pings enable row level security;
-- No direct policies: all access goes through the SECURITY DEFINER RPCs below.

-- 2) RPC: record one anonymous ping -----------------------------------------

create or replace function public.ping_city_drink(
    p_city       text,
    p_drink_name text,
    p_category   text
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
    v_voter := coalesce(
        auth.uid()::text,
        nullif(trim(split_part(
            current_setting('request.headers', true)::json ->> 'x-forwarded-for',
            ',', 1)), ''),
        'anon'
    );

    select count(*) into v_recent
        from public.city_drink_pings
        where voter = v_voter
          and created_at > now() - interval '1 hour';
    if v_recent >= v_hourly_cap then
        return;
    end if;

    insert into public.city_drink_pings (city, drink_name, category, voter)
    values (v_city, v_name, v_category, v_voter);

    -- Rolling cleanup (probabilistic so it isn't a full scan on every ping):
    -- the feed only ever looks back a few days, so drop anything older.
    if random() < 0.02 then
        delete from public.city_drink_pings
            where created_at < now() - interval '7 days';
    end if;
end;
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

-- 4) Permissions ------------------------------------------------------------
-- Anon may read trends and ping (offline-first users who never sign in); the
-- app pings with the signed-in token when available so the cap keys on a real
-- account id. The table stays locked (RLS on, no policies) -- writes only ever
-- happen through ping_city_drink().

grant execute on function public.ping_city_drink(text, text, text) to anon, authenticated;
grant execute on function public.city_drink_trends(text, integer)  to anon, authenticated;

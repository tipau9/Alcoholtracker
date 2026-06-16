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
    confirmed_count integer not null default 0,
    created_at      timestamptz not null default now()
);

-- Make sure the moderation columns exist if the table predates this script.
alter table public.community_drinks add column if not exists status          text    not null default 'pending';
alter table public.community_drinks add column if not exists confirmed_count integer not null default 0;

-- One vote per (barcode, voter). The unique PK makes re-scans idempotent.
create table if not exists public.community_drink_votes (
    barcode    text not null,
    voter      text not null,
    created_at timestamptz not null default now(),
    primary key (barcode, voter)
);

-- Lets the hourly anti-flood count and the per-voter lookups stay cheap.
create index if not exists community_drink_votes_voter_idx
    on public.community_drink_votes (voter, created_at);

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
    v_threshold  constant integer := 3;    -- distinct voters needed for auto-approval
    v_hourly_cap constant integer := 40;   -- max votes per voter per hour (anti-flood)
    v_count      integer;
    v_recent     integer;
    v_voter      text;
    v_name       text;
    v_category   text;
    v_icon       text;
begin
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
    insert into public.community_drink_votes (barcode, voter)
    values (p_barcode, v_voter)
    on conflict (barcode, voter) do nothing;

    select count(*) into v_count
        from public.community_drink_votes
        where barcode = p_barcode;

    update public.community_drinks
        set confirmed_count = v_count
        where barcode = p_barcode;

    -- Crowd auto-approval: only promotes 'pending' rows. Never resurrects a
    -- manually 'rejected' row, never touches an already 'approved' one.
    update public.community_drinks
        set status = 'approved'
        where barcode = p_barcode
          and status = 'pending'
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
-- Anon may contribute (offline-first users who never sign in). Signed-in users
-- contribute with their own token so the vote keys on a real account id, which
-- is far harder to sybil than an IP, hence the grant to authenticated too.
grant execute on function public.contribute_drink(
    text, text, text, double precision, double precision, integer, text, text
) to anon, authenticated;

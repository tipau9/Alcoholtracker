-- ============================================================================
-- Self-learning community drinks: crowd confirmation + manual approval
-- Run this once in the Supabase SQL editor.
--
-- Flow:
--   * The app calls the contribute_drink() RPC after a barcode scan.
--   * The drink is stored as 'pending' and the scanning device casts one vote.
--   * Once CONFIRM_THRESHOLD distinct devices confirmed the same barcode, the
--     row auto-flips to 'approved' and becomes visible to everyone.
--   * You can manually set status = 'approved' (show now, even with 1 vote) or
--     'rejected' (block it) in the dashboard. A 'rejected' row is never
--     auto-approved by the crowd.
--   * The app only ever reads status = 'approved'.
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

-- One vote per (barcode, device). The unique PK makes re-scans idempotent.
create table if not exists public.community_drink_votes (
    barcode    text not null,
    voter      text not null,
    created_at timestamptz not null default now(),
    primary key (barcode, voter)
);

-- 2) RPC: insert-or-vote + crowd auto-approval ------------------------------

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
    v_threshold constant integer := 3;   -- distinct devices needed for auto-approval
    v_count     integer;
begin
    if p_barcode is null or length(trim(p_barcode)) = 0 then
        return;
    end if;

    -- Insert the drink once; keep the first contributor's values so a later bad
    -- scan cannot overwrite good data.
    insert into public.community_drinks
        (barcode, name, category, volume, abv, calories, icon_name, status, confirmed_count)
    values
        (p_barcode, p_name, p_category, p_volume, p_abv, p_calories, p_icon_name, 'pending', 0)
    on conflict (barcode) do nothing;

    -- Record this device's vote (idempotent per device).
    insert into public.community_drink_votes (barcode, voter)
    values (p_barcode, coalesce(nullif(trim(p_voter), ''), 'anon'))
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

-- 3) Permissions / RLS ------------------------------------------------------
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

grant execute on function public.contribute_drink(
    text, text, text, double precision, double precision, integer, text, text
) to anon;

-- Account-based history backup (drinks + day notes)
--
-- Run this once in the Supabase SQL Editor (New query -> paste -> Run).
-- Backs up each signed-in user's on-device drinking history so it can be
-- restored after a reinstall or on a new device. Row Level Security ensures a
-- user can only ever read or write their own rows.

-- =========================================================================
-- drink_history
-- =========================================================================

create table if not exists public.drink_history (
    id                      uuid primary key,                  -- same UUID as the local Drink
    user_id                 uuid not null references auth.users(id) on delete cascade,
    name                    text not null default '',
    volume                  double precision not null default 0,   -- ml
    abv                     double precision not null default 0,   -- %
    calories                integer not null default 0,
    icon_name               text not null default '',
    category                text not null default 'other',
    mixer_volume            double precision not null default 0,
    mixer_water_content     double precision not null default 0,
    drink_duration_minutes  double precision not null default 0,
    template_id             uuid,
    consumed_at             timestamptz not null,
    updated_at              timestamptz not null default now()
);

create index if not exists drink_history_user_consumed_idx
    on public.drink_history (user_id, consumed_at);

alter table public.drink_history enable row level security;

drop policy if exists "drink_history_select_own" on public.drink_history;
drop policy if exists "drink_history_insert_own" on public.drink_history;
drop policy if exists "drink_history_update_own" on public.drink_history;
drop policy if exists "drink_history_delete_own" on public.drink_history;

create policy "drink_history_select_own" on public.drink_history
    for select using (auth.uid() = user_id);
create policy "drink_history_insert_own" on public.drink_history
    for insert with check (auth.uid() = user_id);
create policy "drink_history_update_own" on public.drink_history
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "drink_history_delete_own" on public.drink_history
    for delete using (auth.uid() = user_id);

-- =========================================================================
-- day_notes  (one note + mood per calendar day)
-- =========================================================================

create table if not exists public.day_notes (
    user_id     uuid not null references auth.users(id) on delete cascade,
    day_start   date not null,
    text        text not null default '',
    mood        integer not null default 0,
    updated_at  timestamptz not null default now(),
    primary key (user_id, day_start)
);

alter table public.day_notes enable row level security;

drop policy if exists "day_notes_select_own" on public.day_notes;
drop policy if exists "day_notes_insert_own" on public.day_notes;
drop policy if exists "day_notes_update_own" on public.day_notes;
drop policy if exists "day_notes_delete_own" on public.day_notes;

create policy "day_notes_select_own" on public.day_notes
    for select using (auth.uid() = user_id);
create policy "day_notes_insert_own" on public.day_notes
    for insert with check (auth.uid() = user_id);
create policy "day_notes_update_own" on public.day_notes
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "day_notes_delete_own" on public.day_notes
    for delete using (auth.uid() = user_id);

-- =========================================================================
-- user_backup  (single JSON document per user: profile / settings,
--               water log, custom mixes, custom drink templates)
-- =========================================================================

create table if not exists public.user_backup (
    user_id     uuid primary key references auth.users(id) on delete cascade,
    data        jsonb not null default '{}'::jsonb,
    updated_at  timestamptz not null default now()
);

alter table public.user_backup enable row level security;

drop policy if exists "user_backup_select_own" on public.user_backup;
drop policy if exists "user_backup_insert_own" on public.user_backup;
drop policy if exists "user_backup_update_own" on public.user_backup;
drop policy if exists "user_backup_delete_own" on public.user_backup;

create policy "user_backup_select_own" on public.user_backup
    for select using (auth.uid() = user_id);
create policy "user_backup_insert_own" on public.user_backup
    for insert with check (auth.uid() = user_id);
create policy "user_backup_update_own" on public.user_backup
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "user_backup_delete_own" on public.user_backup
    for delete using (auth.uid() = user_id);

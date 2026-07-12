-- Profiles privacy hardening
-- Run once in Supabase SQL Editor.

-- Base columns used by the app.
alter table public.profiles add column if not exists display_name text not null default '';
alter table public.profiles add column if not exists friend_code text;
alter table public.profiles add column if not exists current_bac double precision not null default 0;
alter table public.profiles add column if not exists bac_updated_at timestamptz not null default now();
alter table public.profiles add column if not exists is_sharing boolean not null default true;
alter table public.profiles add column if not exists achievements jsonb not null default '[]'::jsonb;
alter table public.profiles add column if not exists sos_active boolean not null default false;
alter table public.profiles add column if not exists sos_updated_at timestamptz;
alter table public.profiles add column if not exists is_probationary boolean not null default false;

create table if not exists public.friendships (
    follower_id uuid not null references public.profiles(id) on delete cascade,
    friend_id   uuid not null references public.profiles(id) on delete cascade,
    created_at  timestamptz not null default now(),
    primary key (follower_id, friend_id)
);

create table if not exists public.profile_lookup_events (
    caller_id  uuid not null,
    kind       text not null,
    created_at timestamptz not null default now()
);

create index if not exists profile_lookup_events_caller_idx
    on public.profile_lookup_events (caller_id, kind, created_at);

create or replace function public.generate_friend_code(p_length integer default 10)
returns text
language sql
security definer
set search_path = public
as $$
    select upper(substr(replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', ''), 1, greatest(8, least(p_length, 16))));
$$;

update public.profiles
set friend_code = public.generate_friend_code(10)
where friend_code is null or length(trim(friend_code)) < 8;

with duplicate_codes as (
    select
        id,
        row_number() over (partition by upper(friend_code) order by id) as rn
    from public.profiles
    where friend_code is not null
)
update public.profiles p
set friend_code = public.generate_friend_code(10)
from duplicate_codes d
where p.id = d.id
  and d.rn > 1;

create unique index if not exists profiles_friend_code_upper_idx
    on public.profiles (upper(friend_code))
    where friend_code is not null;

alter table public.profiles enable row level security;
alter table public.friendships enable row level security;
alter table public.profile_lookup_events enable row level security;

do $$
declare
    pol record;
begin
    for pol in
        select policyname
        from pg_policies
        where schemaname = 'public' and tablename = 'profiles'
    loop
        execute format('drop policy if exists %I on public.profiles', pol.policyname);
    end loop;

    for pol in
        select policyname
        from pg_policies
        where schemaname = 'public' and tablename = 'friendships'
    loop
        execute format('drop policy if exists %I on public.friendships', pol.policyname);
    end loop;

    for pol in
        select policyname
        from pg_policies
        where schemaname = 'public' and tablename = 'profile_lookup_events'
    loop
        execute format('drop policy if exists %I on public.profile_lookup_events', pol.policyname);
    end loop;
end $$;

create policy "profiles_select_own"
    on public.profiles
    for select
    to authenticated
    using (auth.uid() = id);

create policy "profiles_insert_own"
    on public.profiles
    for insert
    to authenticated
    with check (auth.uid() = id);

create policy "profiles_update_own"
    on public.profiles
    for update
    to authenticated
    using (auth.uid() = id)
    with check (auth.uid() = id);

create policy "friendships_read_own"
    on public.friendships
    for select
    to authenticated
    using (auth.uid() = follower_id or auth.uid() = friend_id);

create policy "friendships_insert_own"
    on public.friendships
    for insert
    to authenticated
    with check (auth.uid() = follower_id and follower_id <> friend_id);

create policy "friendships_delete_own"
    on public.friendships
    for delete
    to authenticated
    using (auth.uid() = follower_id);

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    v_code text;
begin
    loop
        v_code := public.generate_friend_code(10);
        exit when not exists (
            select 1 from public.profiles p where upper(p.friend_code) = upper(v_code)
        );
    end loop;

    insert into public.profiles (id, display_name, friend_code)
    values (
        new.id,
        coalesce(new.raw_user_meta_data->>'display_name', ''),
        v_code
    )
    on conflict (id) do nothing;

    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

create or replace function public.profile_lookup_rate_limit(p_kind text, p_max_per_hour integer)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_recent integer;
begin
    if auth.uid() is null then
        raise exception 'not_signed_in';
    end if;

    select count(*) into v_recent
    from public.profile_lookup_events e
    where e.caller_id = auth.uid()
      and e.kind = p_kind
      and e.created_at > now() - interval '1 hour';

    if v_recent >= p_max_per_hour then
        raise exception 'profile_lookup_rate_limited';
    end if;

    insert into public.profile_lookup_events(caller_id, kind)
    values (auth.uid(), p_kind);
end;
$$;

create or replace function public.is_mutual_friend(p_other uuid)
returns boolean
language sql
security definer
set search_path = public
stable
as $$
    select exists (
        select 1
        from public.friendships a
        join public.friendships b
          on b.follower_id = a.friend_id
         and b.friend_id = a.follower_id
        where a.follower_id = auth.uid()
          and a.friend_id = p_other
    );
$$;

create or replace function public.friend_profiles_by_codes(p_codes text[])
returns table (
    id uuid,
    display_name text,
    friend_code text,
    current_bac double precision,
    bac_updated_at timestamptz,
    is_sharing boolean,
    achievements jsonb,
    sos_active boolean,
    is_probationary boolean
)
language plpgsql
security definer
set search_path = public
as $$
begin
    if p_codes is null or coalesce(array_length(p_codes, 1), 0) = 0 then
        return;
    end if;

    if array_length(p_codes, 1) > 50 then
        raise exception 'too_many_friend_codes';
    end if;

    perform public.profile_lookup_rate_limit('friend_code', 120);

    return query
    with clean(code) as (
        select distinct upper(regexp_replace(c, '[^A-Za-z0-9]', '', 'g'))
        from unnest(p_codes) as c
        where length(regexp_replace(c, '[^A-Za-z0-9]', '', 'g')) between 6 and 16
    )
    select
        p.id,
        p.display_name,
        p.friend_code,
        case when p.is_sharing then p.current_bac end,
        case when p.is_sharing then p.bac_updated_at end,
        p.is_sharing,
        case when public.is_mutual_friend(p.id) then p.achievements else '[]'::jsonb end,
        case when public.is_mutual_friend(p.id) then p.sos_active else false end,
        case when public.is_mutual_friend(p.id) then p.is_probationary else false end
    from public.profiles p
    join clean c on upper(p.friend_code) = c.code
    limit 50;
end;
$$;

create or replace function public.friend_profiles_by_ids(p_ids uuid[])
returns table (
    id uuid,
    display_name text,
    friend_code text,
    current_bac double precision,
    bac_updated_at timestamptz,
    is_sharing boolean,
    achievements jsonb,
    sos_active boolean,
    is_probationary boolean
)
language plpgsql
security definer
set search_path = public
as $$
begin
    if p_ids is null or coalesce(array_length(p_ids, 1), 0) = 0 then
        return;
    end if;

    if array_length(p_ids, 1) > 50 then
        raise exception 'too_many_profile_ids';
    end if;

    perform public.profile_lookup_rate_limit('profile_id', 240);

    return query
    select
        p.id,
        p.display_name,
        case when public.is_mutual_friend(p.id) then p.friend_code end,
        case when p.is_sharing then p.current_bac end,
        case when p.is_sharing then p.bac_updated_at end,
        p.is_sharing,
        case when public.is_mutual_friend(p.id) then p.achievements else '[]'::jsonb end,
        case when public.is_mutual_friend(p.id) then p.sos_active else false end,
        case when public.is_mutual_friend(p.id) then p.is_probationary else false end
    from public.profiles p
    where p.id = any (p_ids)
    limit 50;
end;
$$;

revoke all on function public.generate_friend_code(integer) from public, anon, authenticated;
revoke all on function public.handle_new_user() from public, anon, authenticated;
revoke all on function public.profile_lookup_rate_limit(text, integer) from public, anon, authenticated;
revoke all on function public.is_mutual_friend(uuid) from public, anon, authenticated;
revoke all on function public.friend_profiles_by_codes(text[]) from public, anon, authenticated;
revoke all on function public.friend_profiles_by_ids(uuid[]) from public, anon, authenticated;
revoke all on table public.profile_lookup_events from public, anon, authenticated;

grant select, insert, update on public.profiles to authenticated;
grant select, insert, delete on public.friendships to authenticated;
grant execute on function public.friend_profiles_by_codes(text[]) to authenticated;
grant execute on function public.friend_profiles_by_ids(uuid[]) to authenticated;

-- ============================================================================
-- Admin module security
-- Run once in the Supabase SQL editor.
--
-- The iOS app only shows the admin UI when is_admin() returns true. That is a
-- convenience only. All real permissions are enforced here, inside SECURITY
-- DEFINER RPCs that check auth.uid() against admin_users.
-- ============================================================================

create table if not exists public.admin_users (
    user_id    uuid primary key references auth.users(id) on delete cascade,
    role       text not null default 'moderator',
    created_at timestamptz not null default now(),
    check (role in ('super_admin', 'moderator', 'support', 'readonly'))
);

create table if not exists public.admin_audit_log (
    id         uuid primary key default gen_random_uuid(),
    actor_id   uuid references auth.users(id) on delete set null,
    action     text not null,
    item_type  text,
    item_id    uuid,
    before     jsonb,
    after      jsonb,
    note       text,
    created_at timestamptz not null default now()
);

create table if not exists public.admin_reports (
    id          uuid primary key default gen_random_uuid(),
    reporter_id uuid references auth.users(id) on delete set null,
    item_type   text not null,
    item_id     uuid,
    reason      text not null,
    status      text not null default 'open',
    details     jsonb not null default '{}'::jsonb,
    created_at  timestamptz not null default now(),
    resolved_at timestamptz,
    resolved_by uuid references auth.users(id) on delete set null,
    check (status in ('open', 'resolved', 'dismissed'))
);

create table if not exists public.admin_feature_flags (
    key         text primary key,
    enabled     boolean not null default false,
    is_public   boolean not null default false,
    value       jsonb not null default '{}'::jsonb,
    description text not null default '',
    updated_at  timestamptz not null default now(),
    updated_by  uuid references auth.users(id) on delete set null
);

create table if not exists public.admin_blocked_voters (
    voter      text primary key,
    reason     text not null default '',
    created_at timestamptz not null default now(),
    created_by uuid references auth.users(id) on delete set null
);

alter table public.admin_users          enable row level security;
alter table public.admin_audit_log      enable row level security;
alter table public.admin_reports        enable row level security;
alter table public.admin_feature_flags  enable row level security;
alter table public.admin_blocked_voters enable row level security;

alter table public.admin_feature_flags add column if not exists is_public boolean not null default false;

alter table public.community_drinks add column if not exists admin_locked boolean not null default false;
alter table public.community_mixes  add column if not exists admin_locked boolean not null default false;

create or replace function public.is_admin()
returns boolean
language sql
security definer
set search_path = public
stable
as $$
    select exists (
        select 1 from public.admin_users
        where user_id = auth.uid()
    );
$$;

create or replace function public.admin_role()
returns text
language sql
security definer
set search_path = public
stable
as $$
    select role from public.admin_users where user_id = auth.uid();
$$;

create or replace function public.require_admin(p_permission text default 'readonly')
returns text
language plpgsql
security definer
set search_path = public
stable
as $$
declare
    v_role text;
begin
    select role into v_role from public.admin_users where user_id = auth.uid();
    if v_role is null then
        raise exception 'not_admin';
    end if;

    if p_permission = 'readonly' then
        return v_role;
    end if;
    if p_permission = 'moderate' and v_role in ('super_admin', 'moderator') then
        return v_role;
    end if;
    if p_permission = 'support' and v_role in ('super_admin', 'support') then
        return v_role;
    end if;
    if p_permission = 'super_admin' and v_role = 'super_admin' then
        return v_role;
    end if;

    raise exception 'admin_permission_denied';
end;
$$;

create or replace function public.prevent_last_super_admin_loss()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    v_super_admins integer;
begin
    if old.role = 'super_admin'
       and (tg_op = 'DELETE' or (tg_op = 'UPDATE' and new.role <> 'super_admin')) then
        select count(*) into v_super_admins
        from public.admin_users
        where role = 'super_admin';

        if v_super_admins <= 1 then
            raise exception 'cannot_remove_last_super_admin';
        end if;
    end if;

    if tg_op = 'DELETE' then
        return old;
    end if;
    return new;
end;
$$;

drop trigger if exists trg_prevent_last_super_admin_loss on public.admin_users;
create trigger trg_prevent_last_super_admin_loss
    before update or delete on public.admin_users
    for each row execute function public.prevent_last_super_admin_loss();

create or replace function public.admin_log(
    p_action text,
    p_item_type text default null,
    p_item_id uuid default null,
    p_before jsonb default null,
    p_after jsonb default null,
    p_note text default null
) returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.admin_audit_log(actor_id, action, item_type, item_id, before, after, note)
    values (auth.uid(), p_action, p_item_type, p_item_id, p_before, p_after, p_note);
end;
$$;

create or replace function public.admin_moderation_queue()
returns table (
    item_type text,
    id uuid,
    title text,
    subtitle text,
    status text,
    confirmed_count integer,
    created_at timestamptz,
    payload jsonb
)
language plpgsql
security definer
set search_path = public
stable
as $$
begin
    perform public.require_admin('readonly');

    return query
    select
        'drink'::text,
        d.id,
        d.name,
        coalesce(d.barcode, '') || ' · ' || d.category || ' · ' || round(d.volume::numeric, 0)::text || ' ml · ' || round(d.abv::numeric, 1)::text || '%',
        d.status,
        d.confirmed_count,
        d.created_at,
        to_jsonb(d)
    from public.community_drinks d
    where d.status in ('pending', 'rejected')

    union all

    select
        'mix'::text,
        m.id,
        m.name,
        'Mix · ' || round(m.total_volume::numeric, 0)::text || ' ml · ' || round(m.total_abv::numeric, 1)::text || '%',
        m.status,
        m.confirmed_count,
        m.created_at,
        to_jsonb(m)
    from public.community_mixes m
    where m.status in ('pending', 'rejected')
    order by created_at desc
    limit 200;
end;
$$;

create or replace function public.admin_set_moderation_status(
    p_item_type text,
    p_id uuid,
    p_status text,
    p_reason text default null
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_before jsonb;
    v_after jsonb;
begin
    perform public.require_admin('moderate');
    if p_status not in ('pending', 'approved', 'rejected') then
        raise exception 'invalid_status';
    end if;

    if p_item_type = 'drink' then
        select to_jsonb(d) into v_before from public.community_drinks d where d.id = p_id;
        update public.community_drinks
        set status = p_status,
            admin_locked = (p_status <> 'approved')
        where id = p_id;
        select to_jsonb(d) into v_after from public.community_drinks d where d.id = p_id;
    elsif p_item_type = 'mix' then
        select to_jsonb(m) into v_before from public.community_mixes m where m.id = p_id;
        update public.community_mixes
        set status = p_status,
            admin_locked = (p_status <> 'approved')
        where id = p_id;
        select to_jsonb(m) into v_after from public.community_mixes m where m.id = p_id;
    else
        raise exception 'invalid_item_type';
    end if;

    if v_before is null then
        raise exception 'item_not_found';
    end if;

    perform public.admin_log('moderation_' || p_status, p_item_type, p_id, v_before, v_after, p_reason);
end;
$$;

create or replace function public.admin_content_list(
    p_status text default 'approved',
    p_search text default '',
    p_limit integer default 200,
    p_offset integer default 0
)
returns table (
    item_type text,
    id uuid,
    title text,
    subtitle text,
    status text,
    confirmed_count integer,
    created_at timestamptz,
    payload jsonb
)
language plpgsql
security definer
set search_path = public
stable
as $$
declare
    v_limit integer := least(greatest(coalesce(p_limit, 200), 1), 500);
    v_offset integer := greatest(coalesce(p_offset, 0), 0);
    v_search text := '%' || lower(trim(coalesce(p_search, ''))) || '%';
begin
    perform public.require_admin('readonly');
    if p_status is not null and p_status not in ('pending', 'approved', 'rejected') then
        raise exception 'invalid_status';
    end if;

    return query
    select
        'drink'::text,
        d.id,
        d.name,
        coalesce(d.barcode, '') || ' · ' || d.category || ' · ' || round(d.volume::numeric, 0)::text || ' ml · ' || round(d.abv::numeric, 1)::text || '%',
        d.status,
        d.confirmed_count,
        d.created_at,
        to_jsonb(d)
    from public.community_drinks d
    where (p_status is null or d.status = p_status)
      and (trim(coalesce(p_search, '')) = ''
           or lower(d.name) like v_search
           or lower(d.id::text) like v_search
           or lower(d.barcode) like v_search
           or lower(d.category) like v_search)

    union all

    select
        'mix'::text,
        m.id,
        m.name,
        'Mix · ' || round(m.total_volume::numeric, 0)::text || ' ml · ' || round(m.total_abv::numeric, 1)::text || '%',
        m.status,
        m.confirmed_count,
        m.created_at,
        to_jsonb(m)
    from public.community_mixes m
    where (p_status is null or m.status = p_status)
      and (trim(coalesce(p_search, '')) = ''
           or lower(m.name) like v_search
           or lower(m.id::text) like v_search)
    order by created_at desc
    limit v_limit offset v_offset;
end;
$$;

create or replace function public.admin_update_drink(
    p_id uuid,
    p_name text,
    p_category text,
    p_volume double precision,
    p_abv double precision,
    p_calories integer,
    p_icon_name text default null
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_before jsonb;
    v_after jsonb;
    v_name text := trim(coalesce(p_name, ''));
    v_category text := trim(coalesce(p_category, ''));
    v_icon text := nullif(trim(coalesce(p_icon_name, '')), '');
begin
    perform public.require_admin('moderate');
    if length(v_name) = 0 or length(v_name) > 80 then
        raise exception 'invalid_name';
    end if;
    if lower(v_category) not in (
        'beer','wine','sparkling','spirits','liqueur',
        'cocktail','mixed','shot','cider','fortified',
        'water','softdrink','juice','coffeetea','milk','other'
    ) then
        raise exception 'invalid_category';
    end if;
    if p_abv is null or p_abv < 0 or p_abv > 100 then
        raise exception 'invalid_abv';
    end if;
    if p_volume is null or p_volume <= 0 or p_volume > 10000 then
        raise exception 'invalid_volume';
    end if;
    if p_calories is null or p_calories < 0 or p_calories > 10000 then
        raise exception 'invalid_calories';
    end if;

    select to_jsonb(d) into v_before from public.community_drinks d where d.id = p_id;
    if v_before is null then
        raise exception 'item_not_found';
    end if;

    update public.community_drinks
    set name = v_name,
        category = v_category,
        volume = p_volume,
        abv = p_abv,
        calories = p_calories,
        icon_name = coalesce(v_icon, icon_name),
        admin_locked = false
    where id = p_id;

    select to_jsonb(d) into v_after from public.community_drinks d where d.id = p_id;
    perform public.admin_log('drink_updated', 'drink', p_id, v_before, v_after, null);
end;
$$;

create or replace function public.admin_metrics()
returns table(metric text, value integer)
language plpgsql
security definer
set search_path = public
stable
as $$
begin
    perform public.require_admin('readonly');

    return query
    select 'pending_drinks', count(*)::integer from public.community_drinks where status = 'pending'
    union all select 'pending_mixes', count(*)::integer from public.community_mixes where status = 'pending'
    union all select 'open_reports', count(*)::integer from public.admin_reports where status = 'open'
    union all select 'approved_drinks', count(*)::integer from public.community_drinks where status = 'approved'
    union all select 'approved_mixes', count(*)::integer from public.community_mixes where status = 'approved'
    union all select 'blocked_voters', count(*)::integer from public.admin_blocked_voters;
end;
$$;

-- PostgreSQL cannot change the row type of an existing RETURNS TABLE function
-- via CREATE OR REPLACE. Drop the no-argument overload first so this script can
-- be rerun after the feature-flag result columns have changed.
drop function if exists public.admin_feature_flags_list();

create or replace function public.admin_feature_flags_list()
returns table(key text, enabled boolean, is_public boolean, value jsonb, description text, updated_at timestamptz)
language plpgsql
security definer
set search_path = public
stable
as $$
begin
    perform public.require_admin('readonly');

    return query
    select f.key, f.enabled, f.is_public, f.value, f.description, f.updated_at
    from public.admin_feature_flags f
    order by f.key;
end;
$$;

drop function if exists public.admin_set_feature_flag(text, boolean, jsonb, text);

create or replace function public.admin_set_feature_flag(
    p_key text,
    p_enabled boolean,
    p_value jsonb default '{}'::jsonb,
    p_description text default '',
    p_is_public boolean default false
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_before jsonb;
    v_after jsonb;
begin
    perform public.require_admin('super_admin');
    if p_key is null or length(trim(p_key)) = 0 or length(p_key) > 80 then
        raise exception 'invalid_key';
    end if;
    if length(coalesce(p_value, '{}'::jsonb)::text) > 4096 then
        raise exception 'feature_flag_value_too_large';
    end if;

    select to_jsonb(f) into v_before from public.admin_feature_flags f where f.key = trim(p_key);
    insert into public.admin_feature_flags(key, enabled, is_public, value, description, updated_at, updated_by)
    values (trim(p_key), p_enabled, coalesce(p_is_public, false), coalesce(p_value, '{}'::jsonb), coalesce(p_description, ''), now(), auth.uid())
    on conflict (key) do update set
        enabled = excluded.enabled,
        is_public = excluded.is_public,
        value = excluded.value,
        description = excluded.description,
        updated_at = now(),
        updated_by = auth.uid();
    select to_jsonb(f) into v_after from public.admin_feature_flags f where f.key = trim(p_key);

    perform public.admin_log('feature_flag_set', 'feature_flag', null, v_before, v_after, trim(p_key));
end;
$$;

create or replace function public.admin_reports_list()
returns table (
    id uuid,
    item_type text,
    item_id uuid,
    reason text,
    status text,
    details jsonb,
    created_at timestamptz
)
language plpgsql
security definer
set search_path = public
stable
as $$
begin
    perform public.require_admin('readonly');

    return query
    select r.id, r.item_type, r.item_id, r.reason, r.status, r.details, r.created_at
    from public.admin_reports r
    order by r.created_at desc
    limit 200;
end;
$$;

create or replace function public.admin_resolve_report(
    p_id uuid,
    p_status text,
    p_note text default null
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_before jsonb;
    v_after jsonb;
begin
    perform public.require_admin('support');
    if p_status not in ('resolved', 'dismissed') then
        raise exception 'invalid_status';
    end if;

    select to_jsonb(r) into v_before from public.admin_reports r where r.id = p_id;
    update public.admin_reports
    set status = p_status, resolved_at = now(), resolved_by = auth.uid()
    where id = p_id;
    select to_jsonb(r) into v_after from public.admin_reports r where r.id = p_id;

    perform public.admin_log('report_' || p_status, 'report', p_id, v_before, v_after, p_note);
end;
$$;

drop function if exists public.admin_audit_log_list();

create or replace function public.admin_audit_log_list()
returns table (
    id uuid,
    actor_id uuid,
    action text,
    item_type text,
    item_id uuid,
    before jsonb,
    after jsonb,
    note text,
    created_at timestamptz
)
language plpgsql
security definer
set search_path = public
stable
as $$
begin
    perform public.require_admin('readonly');

    return query
    select a.id, a.actor_id, a.action, a.item_type, a.item_id, a.before, a.after, a.note, a.created_at
    from public.admin_audit_log a
    order by a.created_at desc
    limit 200;
end;
$$;

drop function if exists public.admin_users_list();

create or replace function public.admin_users_list()
returns table(user_id uuid, role text, created_at timestamptz)
language plpgsql
security definer
set search_path = public
stable
as $$
begin
    perform public.require_admin('super_admin');

    return query
    select u.user_id, u.role, u.created_at
    from public.admin_users u
    order by u.created_at desc;
end;
$$;

create or replace function public.admin_set_user_role(
    p_user_id uuid,
    p_role text
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_before jsonb;
    v_after jsonb;
    v_old_role text;
    v_super_admins integer;
begin
    perform public.require_admin('super_admin');
    perform pg_advisory_xact_lock(hashtext('admin_users_super_admin_guard'));
    if p_role not in ('super_admin', 'moderator', 'support', 'readonly', 'none') then
        raise exception 'invalid_role';
    end if;

    select role into v_old_role from public.admin_users where user_id = p_user_id;
    if v_old_role = 'super_admin' and p_role <> 'super_admin' then
        select count(*) into v_super_admins from public.admin_users where role = 'super_admin';
        if v_super_admins <= 1 then
            raise exception 'cannot_remove_last_super_admin';
        end if;
    end if;

    select to_jsonb(u) into v_before from public.admin_users u where u.user_id = p_user_id;
    if p_role = 'none' then
        delete from public.admin_users where user_id = p_user_id;
    else
        insert into public.admin_users(user_id, role)
        values (p_user_id, p_role)
        on conflict (user_id) do update set role = excluded.role;
    end if;
    select to_jsonb(u) into v_after from public.admin_users u where u.user_id = p_user_id;

    perform public.admin_log('admin_role_set', 'admin_user', p_user_id, v_before, v_after, p_role);
end;
$$;

drop function if exists public.admin_blocked_voters_list();

create or replace function public.admin_blocked_voters_list()
returns table(voter text, reason text, created_at timestamptz)
language plpgsql
security definer
set search_path = public
stable
as $$
begin
    perform public.require_admin('moderate');

    return query
    select b.voter, b.reason, b.created_at
    from public.admin_blocked_voters b
    order by b.created_at desc;
end;
$$;

create or replace function public.admin_set_voter_block(
    p_voter text,
    p_blocked boolean,
    p_reason text default ''
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_before jsonb;
    v_after jsonb;
begin
    perform public.require_admin('moderate');
    if p_voter is null or length(trim(p_voter)) = 0 or length(p_voter) > 160 then
        raise exception 'invalid_voter';
    end if;

    select to_jsonb(b) into v_before from public.admin_blocked_voters b where b.voter = trim(p_voter);
    if p_blocked then
        insert into public.admin_blocked_voters(voter, reason, created_by)
        values (trim(p_voter), coalesce(p_reason, ''), auth.uid())
        on conflict (voter) do update set reason = excluded.reason, created_by = auth.uid(), created_at = now();
    else
        delete from public.admin_blocked_voters where voter = trim(p_voter);
    end if;
    select to_jsonb(b) into v_after from public.admin_blocked_voters b where b.voter = trim(p_voter);

    perform public.admin_log(
        case when p_blocked then 'voter_blocked' else 'voter_unblocked' end,
        'blocked_voter',
        null,
        v_before,
        v_after,
        trim(p_voter)
    );
end;
$$;

create or replace function public.submit_report(
    p_item_type text,
    p_reason text,
    p_item_id uuid default null,
    p_details jsonb default '{}'::jsonb
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_recent integer;
    v_reason text;
begin
    if auth.uid() is null then
        raise exception 'not_signed_in';
    end if;
    if p_item_type not in ('drink', 'mix', 'user', 'bug', 'other') then
        raise exception 'invalid_item_type';
    end if;

    v_reason := trim(coalesce(p_reason, ''));
    if length(v_reason) = 0 or length(v_reason) > 240 then
        raise exception 'invalid_reason';
    end if;
    if length(coalesce(p_details, '{}'::jsonb)::text) > 4096 then
        raise exception 'report_details_too_large';
    end if;

    select count(*) into v_recent
    from public.admin_reports
    where reporter_id = auth.uid()
      and created_at > now() - interval '1 hour';

    if v_recent >= 10 then
        raise exception 'report_rate_limited';
    end if;

    insert into public.admin_reports(reporter_id, item_type, item_id, reason, details)
    values (auth.uid(), p_item_type, p_item_id, v_reason, coalesce(p_details, '{}'::jsonb));
end;
$$;

create or replace function public.admin_moderation_queue()
returns table (
    item_type text,
    id uuid,
    title text,
    subtitle text,
    status text,
    confirmed_count integer,
    created_at timestamptz,
    payload jsonb
)
language plpgsql
security definer
set search_path = public
stable
as $$
begin
    perform public.require_admin('readonly');

    return query
    select
        'drink'::text,
        d.id,
        d.name,
        coalesce(d.barcode, '') || ' · ' || d.category || ' · ' || round(d.volume::numeric, 0)::text || ' ml · ' || round(d.abv::numeric, 1)::text || '%',
        d.status,
        d.confirmed_count,
        d.created_at,
        to_jsonb(d) || jsonb_build_object(
            'moderation_voter', s.voter,
            'moderation_voter_is_authenticated', coalesce(s.is_authenticated, false)
        )
    from public.community_drinks d
    left join lateral (
        select s.voter, s.is_authenticated
        from public.community_drink_submissions s
        where s.barcode = d.barcode
        order by s.is_authenticated desc, s.updated_at desc
        limit 1
    ) s on true
    where d.status in ('pending', 'rejected')

    union all

    select
        'mix'::text,
        m.id,
        m.name,
        'Mix · ' || round(m.total_volume::numeric, 0)::text || ' ml · ' || round(m.total_abv::numeric, 1)::text || '%',
        m.status,
        m.confirmed_count,
        m.created_at,
        to_jsonb(m) || jsonb_build_object(
            'moderation_voter', v.voter,
            'moderation_voter_is_authenticated', coalesce(v.is_authenticated, false)
        )
    from public.community_mixes m
    left join lateral (
        select v.voter, v.is_authenticated
        from public.community_mix_votes v
        where v.name_key = m.name_key
        order by v.is_authenticated desc, v.created_at desc
        limit 1
    ) v on true
    where m.status in ('pending', 'rejected')
    order by created_at desc
    limit 200;
end;
$$;

create or replace function public.admin_content_list(
    p_status text default 'approved',
    p_search text default '',
    p_limit integer default 200,
    p_offset integer default 0
)
returns table (
    item_type text,
    id uuid,
    title text,
    subtitle text,
    status text,
    confirmed_count integer,
    created_at timestamptz,
    payload jsonb
)
language plpgsql
security definer
set search_path = public
stable
as $$
declare
    v_limit integer := least(greatest(coalesce(p_limit, 200), 1), 500);
    v_offset integer := greatest(coalesce(p_offset, 0), 0);
    v_search text := '%' || lower(trim(coalesce(p_search, ''))) || '%';
begin
    perform public.require_admin('readonly');
    if p_status is not null and p_status not in ('pending', 'approved', 'rejected') then
        raise exception 'invalid_status';
    end if;

    return query
    select
        'drink'::text,
        d.id,
        d.name,
        coalesce(d.barcode, '') || ' · ' || d.category || ' · ' || round(d.volume::numeric, 0)::text || ' ml · ' || round(d.abv::numeric, 1)::text || '%',
        d.status,
        d.confirmed_count,
        d.created_at,
        to_jsonb(d) || jsonb_build_object(
            'moderation_voter', s.voter,
            'moderation_voter_is_authenticated', coalesce(s.is_authenticated, false)
        )
    from public.community_drinks d
    left join lateral (
        select s.voter, s.is_authenticated
        from public.community_drink_submissions s
        where s.barcode = d.barcode
        order by s.is_authenticated desc, s.updated_at desc
        limit 1
    ) s on true
    where (p_status is null or d.status = p_status)
      and (trim(coalesce(p_search, '')) = ''
           or lower(d.name) like v_search
           or lower(d.id::text) like v_search
           or lower(d.barcode) like v_search
           or lower(d.category) like v_search)

    union all

    select
        'mix'::text,
        m.id,
        m.name,
        'Mix · ' || round(m.total_volume::numeric, 0)::text || ' ml · ' || round(m.total_abv::numeric, 1)::text || '%',
        m.status,
        m.confirmed_count,
        m.created_at,
        to_jsonb(m) || jsonb_build_object(
            'moderation_voter', v.voter,
            'moderation_voter_is_authenticated', coalesce(v.is_authenticated, false)
        )
    from public.community_mixes m
    left join lateral (
        select v.voter, v.is_authenticated
        from public.community_mix_votes v
        where v.name_key = m.name_key
        order by v.is_authenticated desc, v.created_at desc
        limit 1
    ) v on true
    where (p_status is null or m.status = p_status)
      and (trim(coalesce(p_search, '')) = ''
           or lower(m.name) like v_search
           or lower(m.id::text) like v_search)
    order by created_at desc
    limit v_limit offset v_offset;
end;
$$;

drop function if exists public.public_feature_flags();

create or replace function public.public_feature_flags()
returns table(key text, value jsonb)
language sql
security definer
set search_path = public
stable
as $$
    select f.key, f.value
    from public.admin_feature_flags f
    where f.enabled = true
      and f.is_public = true;
$$;

revoke all on function public.is_admin() from public, anon;
revoke all on function public.admin_role() from public, anon;
revoke all on function public.require_admin(text) from public, anon, authenticated;
revoke all on function public.prevent_last_super_admin_loss() from public, anon, authenticated;
revoke all on function public.admin_log(text, text, uuid, jsonb, jsonb, text) from public, anon, authenticated;
revoke all on function public.admin_moderation_queue() from public, anon, authenticated;
revoke all on function public.admin_set_moderation_status(text, uuid, text, text) from public, anon, authenticated;
revoke all on function public.admin_content_list(text, text, integer, integer) from public, anon, authenticated;
revoke all on function public.admin_update_drink(uuid, text, text, double precision, double precision, integer, text) from public, anon, authenticated;
revoke all on function public.admin_metrics() from public, anon, authenticated;
revoke all on function public.admin_feature_flags_list() from public, anon, authenticated;
revoke all on function public.admin_set_feature_flag(text, boolean, jsonb, text, boolean) from public, anon, authenticated;
revoke all on function public.admin_reports_list() from public, anon, authenticated;
revoke all on function public.admin_resolve_report(uuid, text, text) from public, anon, authenticated;
revoke all on function public.admin_audit_log_list() from public, anon, authenticated;
revoke all on function public.admin_users_list() from public, anon, authenticated;
revoke all on function public.admin_set_user_role(uuid, text) from public, anon, authenticated;
revoke all on function public.admin_blocked_voters_list() from public, anon, authenticated;
revoke all on function public.admin_set_voter_block(text, boolean, text) from public, anon, authenticated;
revoke all on function public.submit_report(text, text, uuid, jsonb) from public, anon, authenticated;
revoke all on function public.public_feature_flags() from public;

revoke all on table public.admin_users from anon, authenticated;
revoke all on table public.admin_audit_log from anon, authenticated;
revoke all on table public.admin_reports from anon, authenticated;
revoke all on table public.admin_feature_flags from public, anon, authenticated;
revoke all on table public.admin_blocked_voters from anon, authenticated;

grant execute on function public.is_admin() to authenticated;
grant execute on function public.admin_role() to authenticated;
grant execute on function public.admin_moderation_queue() to authenticated;
grant execute on function public.admin_set_moderation_status(text, uuid, text, text) to authenticated;
grant execute on function public.admin_content_list(text, text, integer, integer) to authenticated;
grant execute on function public.admin_update_drink(uuid, text, text, double precision, double precision, integer, text) to authenticated;
grant execute on function public.admin_metrics() to authenticated;
grant execute on function public.admin_feature_flags_list() to authenticated;
grant execute on function public.admin_set_feature_flag(text, boolean, jsonb, text, boolean) to authenticated;
grant execute on function public.admin_reports_list() to authenticated;
grant execute on function public.admin_resolve_report(uuid, text, text) to authenticated;
grant execute on function public.admin_audit_log_list() to authenticated;
grant execute on function public.admin_users_list() to authenticated;
grant execute on function public.admin_set_user_role(uuid, text) to authenticated;
grant execute on function public.admin_blocked_voters_list() to authenticated;
grant execute on function public.admin_set_voter_block(text, boolean, text) to authenticated;
grant execute on function public.submit_report(text, text, uuid, jsonb) to authenticated;
grant execute on function public.public_feature_flags() to anon, authenticated;

create or replace function public.admin_update_mix(
    p_id uuid,
    p_name text,
    p_ingredients jsonb,
    p_total_volume double precision,
    p_total_abv double precision,
    p_calories integer
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_before jsonb;
    v_after jsonb;
    v_name text := trim(coalesce(p_name, ''));
    v_key text;
begin
    perform public.require_admin('moderate');

    if length(v_name) = 0 or length(v_name) > 80 then
        raise exception 'invalid_name';
    end if;
    if p_ingredients is null
       or jsonb_typeof(p_ingredients) <> 'array'
       or jsonb_array_length(p_ingredients) = 0
       or jsonb_array_length(p_ingredients) > 50 then
        raise exception 'invalid_ingredients';
    end if;
    if p_total_abv is null or p_total_abv < 0 or p_total_abv > 100 then
        raise exception 'invalid_abv';
    end if;
    if p_total_volume is null or p_total_volume <= 0 or p_total_volume > 10000 then
        raise exception 'invalid_volume';
    end if;
    if p_calories is null or p_calories < 0 or p_calories > 10000 then
        raise exception 'invalid_calories';
    end if;

    v_key := public.community_mix_key(v_name, p_ingredients, p_total_volume, p_total_abv);

    select to_jsonb(m) into v_before
    from public.community_mixes m
    where m.id = p_id;

    if v_before is null then
        raise exception 'item_not_found';
    end if;

    update public.community_mixes
    set name = v_name,
        name_key = v_key,
        ingredients = p_ingredients,
        total_volume = p_total_volume,
        total_abv = p_total_abv,
        calories = p_calories,
        admin_locked = true
    where id = p_id;

    select to_jsonb(m) into v_after
    from public.community_mixes m
    where m.id = p_id;

    perform public.admin_log('mix_updated', 'mix', p_id, v_before, v_after, null);
end;
$$;

revoke all on function public.admin_update_mix(uuid, text, jsonb, double precision, double precision, integer) from public, anon, authenticated;
grant execute on function public.admin_update_mix(uuid, text, jsonb, double precision, double precision, integer) to authenticated;

delete from public.admin_reports r
using public.admin_reports keep
where r.status = 'open'
  and keep.status = 'open'
  and r.id <> keep.id
  and r.reporter_id = keep.reporter_id
  and r.item_type = keep.item_type
  and coalesce(r.item_id, '00000000-0000-0000-0000-000000000000'::uuid)
      = coalesce(keep.item_id, '00000000-0000-0000-0000-000000000000'::uuid)
  and lower(r.reason) = lower(keep.reason)
  and (
      keep.created_at > r.created_at
      or (keep.created_at = r.created_at and keep.id > r.id)
  );

create unique index if not exists admin_reports_open_dedupe_idx
    on public.admin_reports (
        reporter_id,
        item_type,
        coalesce(item_id, '00000000-0000-0000-0000-000000000000'::uuid),
        lower(reason)
    )
    where status = 'open';

create or replace function public.admin_set_voter_block(
    p_voter text,
    p_blocked boolean,
    p_reason text default ''
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_before jsonb;
    v_after jsonb;
begin
    perform public.require_admin('moderate');
    if p_voter is null or length(trim(p_voter)) = 0 or length(p_voter) > 160 then
        raise exception 'invalid_voter';
    end if;

    select to_jsonb(b) into v_before
    from public.admin_blocked_voters b
    where b.voter = trim(p_voter);

    if p_blocked then
        insert into public.admin_blocked_voters(voter, reason, created_by)
        values (trim(p_voter), coalesce(p_reason, ''), auth.uid())
        on conflict (voter) do update
            set reason = excluded.reason,
                created_by = auth.uid(),
                created_at = now();
    else
        delete from public.admin_blocked_voters where voter = trim(p_voter);
    end if;

    select to_jsonb(b) into v_after
    from public.admin_blocked_voters b
    where b.voter = trim(p_voter);

    update public.community_drinks d
    set confirmed_count = coalesce(c.votes, 0)
    from (
        select d2.barcode, count(s.*)::integer as votes
        from public.community_drinks d2
        left join public.community_drink_submissions s
          on s.barcode = d2.barcode
         and s.is_authenticated = true
        left join public.admin_blocked_voters b
          on b.voter = s.voter
        where b.voter is null
        group by d2.barcode
    ) c
    where d.barcode = c.barcode;

    update public.community_mixes m
    set confirmed_count = coalesce(c.votes, 0)
    from (
        select m2.name_key, count(v.*)::integer as votes
        from public.community_mixes m2
        left join public.community_mix_votes v
          on v.name_key = m2.name_key
         and v.is_authenticated = true
        left join public.admin_blocked_voters b
          on b.voter = v.voter
        where b.voter is null
        group by m2.name_key
    ) c
    where m.name_key = c.name_key;

    perform public.admin_log(
        case when p_blocked then 'voter_blocked' else 'voter_unblocked' end,
        'blocked_voter',
        null,
        v_before,
        v_after,
        trim(p_voter)
    );
end;
$$;

create or replace function public.submit_report(
    p_item_type text,
    p_reason text,
    p_item_id uuid default null,
    p_details jsonb default '{}'::jsonb
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_recent integer;
    v_reason text;
    v_reporters integer;
begin
    if auth.uid() is null then
        raise exception 'not_signed_in';
    end if;
    if p_item_type not in ('drink', 'mix', 'user', 'bug', 'other') then
        raise exception 'invalid_item_type';
    end if;

    v_reason := trim(coalesce(p_reason, ''));
    if length(v_reason) = 0 or length(v_reason) > 240 then
        raise exception 'invalid_reason';
    end if;
    if length(coalesce(p_details, '{}'::jsonb)::text) > 4096 then
        raise exception 'report_details_too_large';
    end if;

    if p_item_type in ('drink', 'mix') and p_item_id is null then
        raise exception 'item_required';
    end if;
    if p_item_type = 'drink'
       and not exists (select 1 from public.community_drinks where id = p_item_id) then
        raise exception 'item_not_found';
    end if;
    if p_item_type = 'mix'
       and not exists (select 1 from public.community_mixes where id = p_item_id) then
        raise exception 'item_not_found';
    end if;

    select count(*) into v_recent
    from public.admin_reports
    where reporter_id = auth.uid()
      and created_at > now() - interval '1 hour';

    if v_recent >= 10 then
        raise exception 'report_rate_limited';
    end if;

    insert into public.admin_reports(reporter_id, item_type, item_id, reason, details)
    values (auth.uid(), p_item_type, p_item_id, v_reason, coalesce(p_details, '{}'::jsonb))
    on conflict (
        reporter_id,
        item_type,
        coalesce(item_id, '00000000-0000-0000-0000-000000000000'::uuid),
        lower(reason)
    ) where status = 'open'
    do update set
        details = excluded.details,
        created_at = now();

    if p_item_type in ('drink', 'mix') then
        select count(distinct reporter_id) into v_reporters
        from public.admin_reports
        where item_type = p_item_type
          and item_id = p_item_id
          and status = 'open'
          and reporter_id is not null;

        if v_reporters >= 3 then
            if p_item_type = 'drink' then
                update public.community_drinks
                set status = 'pending',
                    admin_locked = true
                where id = p_item_id
                  and status = 'approved';
            else
                update public.community_mixes
                set status = 'pending',
                    admin_locked = true
                where id = p_item_id
                  and status = 'approved';
            end if;
        end if;
    end if;
end;
$$;

-- Bootstrap your first admin manually, replacing the UUID:
-- insert into public.admin_users(user_id, role)
-- values ('00000000-0000-0000-0000-000000000000', 'super_admin')
-- on conflict (user_id) do update set role = excluded.role;

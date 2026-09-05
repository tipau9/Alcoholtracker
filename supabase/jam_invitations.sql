-- jam_invitations: peer-to-peer jam invites via friend code
-- Run once in the Supabase SQL Editor.

create table if not exists public.jam_invitations (
    id            uuid primary key default gen_random_uuid(),
    inviter_code  text not null,
    invitee_code  text not null,
    jam_id        uuid not null,
    jam_code      text not null,
    host_name     text not null default '',
    created_at    timestamptz not null default now(),
    seen_at       timestamptz,
    unique (invitee_code, jam_id)
);

create index if not exists jam_invitations_invitee_idx
    on public.jam_invitations (invitee_code);

create table if not exists public.jam_invitation_events (
    inviter_id uuid not null,
    created_at timestamptz not null default now()
);

create index if not exists jam_invitation_events_inviter_idx
    on public.jam_invitation_events (inviter_id, created_at);

alter table public.jam_invitations enable row level security;
alter table public.jam_invitation_events enable row level security;

create or replace function public.send_jam_invitation(
    p_invitee_code text,
    p_jam_id       uuid,
    p_jam_code     text,
    p_host_name    text default ''
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_my_code text;
    v_recent integer;
    v_real_code text;
    v_real_host text;
begin
    if auth.uid() is null then
        return;
    end if;

    -- Security check: Caller MUST belong to p_jam_id (as participant or host)
    if not exists (
        select 1 from public.jam_participants
        where jam_id = p_jam_id and user_id = auth.uid()::text
    ) and not exists (
        select 1 from public.jams
        where id = p_jam_id and host_user_id = auth.uid()::text
    ) then
        return;
    end if;

    -- Pull verified jam_code and host_name from public.jams if present
    select code, host_name into v_real_code, v_real_host
    from public.jams
    where id = p_jam_id;

    delete from public.jam_invitation_events
    where created_at < now() - interval '7 days';

    select count(*) into v_recent
    from public.jam_invitation_events
    where inviter_id = auth.uid()
      and created_at > now() - interval '1 hour';

    if v_recent >= 30 then
        raise exception 'jam_invitation_rate_limited';
    end if;

    select friend_code into v_my_code
    from public.profiles
    where id = auth.uid();

    if v_my_code is null or v_my_code = '' then
        return;
    end if;
    if upper(trim(p_invitee_code)) = upper(trim(v_my_code)) then
        return;
    end if;

    insert into public.jam_invitation_events(inviter_id)
    values (auth.uid());

    insert into public.jam_invitations (inviter_code, invitee_code, jam_id, jam_code, host_name)
    values (
        upper(trim(v_my_code)),
        upper(trim(p_invitee_code)),
        p_jam_id,
        upper(trim(coalesce(nullif(v_real_code, ''), p_jam_code))),
        coalesce(nullif(trim(v_real_host), ''), nullif(trim(p_host_name), ''), 'Jemand')
    )
    on conflict (invitee_code, jam_id) do update
        set seen_at    = null,
            created_at = now(),
            host_name  = excluded.host_name;

    delete from public.jam_invitations
    where created_at < now() - interval '48 hours';
end;
$$;

create or replace function public.my_jam_invitations()
returns table (
    id            uuid,
    inviter_code  text,
    jam_id        uuid,
    jam_code      text,
    host_name     text,
    created_at    timestamptz
)
language plpgsql
security definer
set search_path = public
as $$
declare
    v_my_code text;
begin
    select friend_code into v_my_code
    from public.profiles
    where id = auth.uid();

    if v_my_code is null or v_my_code = '' then
        return;
    end if;

    return query
    select ji.id, ji.inviter_code, ji.jam_id, ji.jam_code, ji.host_name, ji.created_at
    from public.jam_invitations ji
    where ji.invitee_code = upper(trim(v_my_code))
      and ji.seen_at is null
      and ji.created_at > now() - interval '24 hours'
    order by ji.created_at desc;
end;
$$;

create or replace function public.mark_invitation_seen(p_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_my_code text;
begin
    select friend_code into v_my_code
    from public.profiles
    where id = auth.uid();

    if v_my_code is null or v_my_code = '' then
        return;
    end if;

    update public.jam_invitations
    set seen_at = now()
    where id = p_id
      and invitee_code = upper(trim(v_my_code));
end;
$$;

revoke all on function public.send_jam_invitation(text, uuid, text, text) from public, anon, authenticated;
revoke all on function public.my_jam_invitations() from public, anon, authenticated;
revoke all on function public.mark_invitation_seen(uuid) from public, anon, authenticated;
revoke all on table public.jam_invitations from public, anon, authenticated;
revoke all on table public.jam_invitation_events from public, anon, authenticated;

grant execute on function public.send_jam_invitation(text, uuid, text, text) to authenticated;
grant execute on function public.my_jam_invitations() to authenticated;
grant execute on function public.mark_invitation_seen(uuid) to authenticated;

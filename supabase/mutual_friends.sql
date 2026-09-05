-- supabase/mutual_friends.sql
-- Fix for finding A22: server-side intersection for mutual friends between auth.uid() and p_other.
-- Avoids leaking whole-table social graph via RLS widening while returning valid profiles.

create or replace function public.mutual_friends_with(p_other uuid)
returns table (
    id uuid,
    display_name text,
    friend_code text,
    current_bac double precision,
    bac_updated_at timestamptz,
    is_sharing boolean,
    achievements jsonb,
    sos_active boolean,
    is_probationary boolean,
    is_mutual boolean
)
language plpgsql
security definer
set search_path = public
as $$
begin
    if auth.uid() is null then
        raise exception 'not_signed_in';
    end if;

    if p_other is null or p_other = auth.uid() then
        return;
    end if;

    perform public.profile_lookup_rate_limit('profile_id', 240);

    return query
    with mutual_ids as (
        select f1.friend_id as user_id
        from public.friendships f1
        join public.friendships f2 on f1.friend_id = f2.friend_id
        where f1.follower_id = auth.uid()
          and f2.follower_id = p_other
          and f1.friend_id not in (auth.uid(), p_other)
        limit 50
    )
    select
        p.id,
        p.display_name,
        case when public.is_mutual_friend(p.id) then p.friend_code end,
        case when p.is_sharing then p.current_bac end,
        case when p.is_sharing then p.bac_updated_at end,
        p.is_sharing,
        case when public.is_mutual_friend(p.id) then p.achievements else '[]'::jsonb end,
        case when public.is_mutual_friend(p.id) then p.sos_active else false end,
        case when public.is_mutual_friend(p.id) then p.is_probationary else false end,
        public.is_mutual_friend(p.id) as is_mutual
    from public.profiles p
    join mutual_ids m on p.id = m.user_id
    order by p.display_name asc
    limit 50;
end;
$$;

revoke all on function public.mutual_friends_with(uuid) from public, anon;
grant execute on function public.mutual_friends_with(uuid) to authenticated;

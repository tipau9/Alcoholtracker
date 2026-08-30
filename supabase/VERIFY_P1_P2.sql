-- P1/P2 verification helpers for Supabase SQL editor / psql.
-- Run after applying the community/admin/jam migrations.

-- 1) Required objects exist.
select to_regclass('public.community_drink_submissions') is not null as has_drink_submissions;
select to_regclass('public.admin_reports_open_dedupe_idx') is not null as has_report_dedupe_idx;
select to_regclass('public.jam_lookup_events') is not null as has_jam_lookup_events;
select to_regclass('public.jam_invitation_events') is not null as has_jam_invitation_events;

-- 2) Public/anon must not execute admin/private RPCs.
select
    has_function_privilege('anon', 'public.admin_moderation_queue()', 'execute') as anon_can_admin_queue,
    has_function_privilege('anon', 'public.admin_content_list(text,text,integer,integer)', 'execute') as anon_can_admin_content,
    has_function_privilege('anon', 'public.admin_update_mix(uuid,text,jsonb,double precision,double precision,integer)', 'execute') as anon_can_update_mix,
    has_function_privilege('anon', 'public.jam_by_code(text)', 'execute') as anon_can_jam_by_code,
    has_function_privilege('anon', 'public.send_jam_invitation(text,uuid,text,text)', 'execute') as anon_can_invite;

-- 3) Public feature flags expose only explicitly public enabled flags.
-- Expect no rows where is_public=false through the RPC result.
select *
from public.public_feature_flags();

-- 4) Admin list payloads should include moderation_voter for content with votes/submissions.
-- Run as an admin JWT via PostgREST/RPC, not as raw DB owner, to verify auth path:
--   /rest/v1/rpc/admin_moderation_queue
--   /rest/v1/rpc/admin_content_list

-- 5) XFF spoof verification must be done through the real Edge/PostgREST URL:
--   curl -i "$SUPABASE_URL/rest/v1/rpc/ping_city_drink" \
--     -H "apikey: $ANON_KEY" \
--     -H "Authorization: Bearer $ANON_KEY" \
--     -H "Content-Type: application/json" \
--     -H "x-forwarded-for: 1.2.3.4" \
--     --data '{"p_city":"xff-test","p_category":"beer","p_voter":"fallback-test"}'
--
-- Then inspect the inserted voter in city trend/debug data. If it stores 1.2.3.4,
-- XFF is client-controlled and anon rate limits/blocklist are only soft controls.

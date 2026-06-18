-- jam_invitations: peer-to-peer jam invites via friend code
-- Run once in the Supabase SQL Editor.

CREATE TABLE IF NOT EXISTS jam_invitations (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    inviter_code text        NOT NULL,
    invitee_code text        NOT NULL,
    jam_id       uuid        NOT NULL,
    jam_code     text        NOT NULL,
    host_name    text        NOT NULL DEFAULT '',
    created_at   timestamptz NOT NULL DEFAULT now(),
    seen_at      timestamptz,
    UNIQUE (invitee_code, jam_id)
);

CREATE INDEX IF NOT EXISTS jam_invitations_invitee_idx ON jam_invitations (invitee_code);

ALTER TABLE jam_invitations ENABLE ROW LEVEL SECURITY;
-- All access goes through SECURITY DEFINER RPCs below; no direct RLS policies.

-- -----------------------------------------------------------------------
-- Send an invitation.
-- Upserts on (invitee_code, jam_id): re-inviting refreshes created_at so
-- the recipient sees it again if they previously dismissed it.
-- -----------------------------------------------------------------------
CREATE OR REPLACE FUNCTION send_jam_invitation(
    p_invitee_code text,
    p_jam_id       uuid,
    p_jam_code     text,
    p_host_name    text DEFAULT ''
) RETURNS void LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_my_code text;
BEGIN
    SELECT friend_code INTO v_my_code FROM profiles WHERE id = auth.uid();
    IF v_my_code IS NULL OR v_my_code = '' THEN RETURN; END IF;
    IF upper(trim(p_invitee_code)) = upper(trim(v_my_code)) THEN RETURN; END IF;

    INSERT INTO jam_invitations (inviter_code, invitee_code, jam_id, jam_code, host_name)
    VALUES (
        upper(trim(v_my_code)),
        upper(trim(p_invitee_code)),
        p_jam_id,
        upper(trim(p_jam_code)),
        coalesce(nullif(trim(p_host_name), ''), 'Jemand')
    )
    ON CONFLICT (invitee_code, jam_id) DO UPDATE
        SET seen_at    = NULL,
            created_at = now(),
            host_name  = EXCLUDED.host_name;

    -- Rolling cleanup: remove invitations older than 48 h.
    DELETE FROM jam_invitations WHERE created_at < now() - interval '48 hours';
END;
$$;
GRANT EXECUTE ON FUNCTION send_jam_invitation TO authenticated;

-- -----------------------------------------------------------------------
-- Fetch all unseen invitations for the calling user.
-- -----------------------------------------------------------------------
CREATE OR REPLACE FUNCTION my_jam_invitations()
RETURNS TABLE (
    id           uuid,
    inviter_code text,
    jam_id       uuid,
    jam_code     text,
    host_name    text,
    created_at   timestamptz
) LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_my_code text;
BEGIN
    SELECT friend_code INTO v_my_code FROM profiles WHERE id = auth.uid();
    IF v_my_code IS NULL OR v_my_code = '' THEN RETURN; END IF;

    RETURN QUERY
        SELECT ji.id, ji.inviter_code, ji.jam_id, ji.jam_code, ji.host_name, ji.created_at
        FROM jam_invitations ji
        WHERE ji.invitee_code = upper(trim(v_my_code))
          AND ji.seen_at IS NULL
          AND ji.created_at > now() - interval '24 hours'
        ORDER BY ji.created_at DESC;
END;
$$;
GRANT EXECUTE ON FUNCTION my_jam_invitations TO authenticated;

-- -----------------------------------------------------------------------
-- Mark a single invitation as seen (idempotent).
-- -----------------------------------------------------------------------
CREATE OR REPLACE FUNCTION mark_invitation_seen(p_id uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_my_code text;
BEGIN
    SELECT friend_code INTO v_my_code FROM profiles WHERE id = auth.uid();
    IF v_my_code IS NULL OR v_my_code = '' THEN RETURN; END IF;
    UPDATE jam_invitations
    SET seen_at = now()
    WHERE id = p_id AND invitee_code = upper(trim(v_my_code));
END;
$$;
GRANT EXECUTE ON FUNCTION mark_invitation_seen TO authenticated;

-- =============================================================================
-- OziChat — MySQL Initialization Script
-- =============================================================================
-- Runs automatically when the MySQL Docker container first starts.
-- Also safe to run manually against an AWS RDS instance:
--
--   mysql -h <rds-endpoint> -u ozichat -p ozichat < docker/mysql/init.sql
--
-- Hibernate (ddl-auto: update) creates and updates all table definitions on
-- every app startup. This script handles what Hibernate does NOT manage:
-- charset, collation, timezone, and a full canonical DDL reference so you can
-- switch to ddl-auto: validate in production when ready.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Database-level settings
-- ---------------------------------------------------------------------------

ALTER DATABASE ozichat
    CHARACTER SET  = utf8mb4
    COLLATE        = utf8mb4_unicode_ci;

SET GLOBAL time_zone = '+00:00';
SET time_zone        = '+00:00';

USE ozichat;

-- ---------------------------------------------------------------------------
-- 2. Full Schema DDL
-- ---------------------------------------------------------------------------
-- All CREATE TABLE statements use IF NOT EXISTS — safe to re-run at any time.
-- They mirror the JPA entity definitions exactly and serve as both
-- documentation and a production bootstrap script.
-- ---------------------------------------------------------------------------


-- ── Users ──────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS users (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    phone           VARCHAR(20)     DEFAULT NULL,
    email           VARCHAR(255)    DEFAULT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    display_name    VARCHAR(100)    NOT NULL,
    avatar_url      VARCHAR(500)    DEFAULT NULL,
    about           VARCHAR(500)    DEFAULT 'Hey there! I am using OziChat',
    role            ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER',
    is_verified     TINYINT(1)      NOT NULL DEFAULT 0,
    last_seen_at    DATETIME(6)     DEFAULT NULL,
    created_at      DATETIME(6)     NOT NULL,
    updated_at      DATETIME(6)     NOT NULL,
    deleted_at      DATETIME(6)     DEFAULT NULL,

    PRIMARY KEY (id),
    UNIQUE  KEY uq_users_phone        (phone),
    UNIQUE  KEY uq_users_email        (email),
    KEY         idx_users_deleted_at  (deleted_at),
    KEY         idx_users_display_name(display_name)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Core user accounts';


-- ── User Privacy Settings ──────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS user_privacy_settings (
    user_id                     BIGINT      NOT NULL,
    last_seen_visibility        ENUM('EVERYONE','CONTACTS','NOBODY') NOT NULL DEFAULT 'EVERYONE',
    profile_photo_visibility    ENUM('EVERYONE','CONTACTS','NOBODY') NOT NULL DEFAULT 'EVERYONE',
    about_visibility            ENUM('EVERYONE','CONTACTS','NOBODY') NOT NULL DEFAULT 'EVERYONE',
    read_receipts_enabled       TINYINT(1)  NOT NULL DEFAULT 1,

    PRIMARY KEY (user_id),
    CONSTRAINT fk_privacy_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Per-user privacy preferences (last seen, profile photo, about, read receipts)';


-- ── User Sessions (Refresh Tokens) ────────────────────────────────────────

CREATE TABLE IF NOT EXISTS user_sessions (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    user_id             BIGINT          NOT NULL,
    refresh_token_hash  VARCHAR(255)    NOT NULL,
    device_fingerprint  VARCHAR(255)    DEFAULT NULL,
    platform            ENUM('ANDROID','IOS','WEB') DEFAULT NULL,
    ip_address          VARCHAR(45)     DEFAULT NULL,
    is_revoked          TINYINT(1)      NOT NULL DEFAULT 0,
    expires_at          DATETIME(6)     NOT NULL,
    created_at          DATETIME(6)     NOT NULL,

    PRIMARY KEY (id),
    KEY idx_sessions_user_id (user_id),
    KEY idx_sessions_token   (refresh_token_hash),
    KEY idx_sessions_expires (expires_at),
    CONSTRAINT fk_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Active device sessions; stores hashed refresh tokens';


-- ── Device Tokens (FCM Push Notifications) ────────────────────────────────

CREATE TABLE IF NOT EXISTS device_tokens (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_id         BIGINT          NOT NULL,
    token           TEXT            NOT NULL,
    platform        ENUM('ANDROID','IOS','WEB') NOT NULL DEFAULT 'ANDROID',
    device_name     VARCHAR(100)    DEFAULT NULL,
    is_active       TINYINT(1)      NOT NULL DEFAULT 1,
    created_at      DATETIME(6)     NOT NULL,
    last_used_at    DATETIME(6)     DEFAULT NULL,

    PRIMARY KEY (id),
    KEY idx_device_tokens_user_id (user_id),
    KEY idx_device_tokens_active  (is_active),
    CONSTRAINT fk_device_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='FCM device registration tokens for push notifications';


-- ── Conversations ──────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS conversations (
    id          BIGINT                  NOT NULL AUTO_INCREMENT,
    type        ENUM('DIRECT','GROUP')  NOT NULL,
    created_at  DATETIME(6)             NOT NULL,
    updated_at  DATETIME(6)             NOT NULL,

    PRIMARY KEY (id),
    KEY idx_conversations_type       (type),
    KEY idx_conversations_updated_at (updated_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Chat rooms — both 1-to-1 DIRECT and GROUP conversations';


-- ── Conversation Members ───────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS conversation_members (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    conversation_id         BIGINT          NOT NULL,
    user_id                 BIGINT          NOT NULL,
    role                    ENUM('OWNER','ADMIN','MEMBER') NOT NULL DEFAULT 'MEMBER',
    last_read_message_id    VARCHAR(255)    DEFAULT NULL,
    last_read_at            DATETIME(6)     DEFAULT NULL,
    is_muted                TINYINT(1)      NOT NULL DEFAULT 0,
    mute_until              DATETIME(6)     DEFAULT NULL,
    joined_at               DATETIME(6)     NOT NULL,
    left_at                 DATETIME(6)     DEFAULT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uq_conv_member        (conversation_id, user_id),
    KEY        idx_conv_member_user  (user_id),
    CONSTRAINT fk_conv_members_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Members of each conversation; tracks read position and mute state';


-- ── Pinned Messages ────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS pinned_messages (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT      NOT NULL,
    message_id      VARCHAR(64) NOT NULL,   -- MongoDB ObjectId
    pinned_by       BIGINT      NOT NULL,
    pinned_at       DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_pinned_conv_msg (conversation_id, message_id),
    KEY        idx_pinned_by      (pinned_by),
    CONSTRAINT fk_pinned_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Messages pinned within a conversation; message_id references MongoDB';


-- ── Group Metadata ─────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS groups_metadata (
    conversation_id             BIGINT          NOT NULL,
    group_name                  VARCHAR(100)    NOT NULL,
    group_description           VARCHAR(500)    DEFAULT NULL,
    group_avatar_url            VARCHAR(500)    DEFAULT NULL,
    max_members                 INT             NOT NULL DEFAULT 1024,
    only_admins_can_send        TINYINT(1)      NOT NULL DEFAULT 0,
    only_admins_can_edit_info   TINYINT(1)      NOT NULL DEFAULT 1,
    announcement_text           VARCHAR(1000)   DEFAULT NULL,
    announcement_at             DATETIME(6)     DEFAULT NULL,
    announcement_by             BIGINT          DEFAULT NULL,
    created_by                  BIGINT          NOT NULL,
    created_at                  DATETIME(6)     NOT NULL,
    updated_at                  DATETIME(6)     NOT NULL,

    PRIMARY KEY (conversation_id),
    CONSTRAINT fk_groups_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Extra metadata for GROUP-type conversations';


-- ── Group Invite Links ─────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS group_invite_links (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT          NOT NULL,
    token           VARCHAR(100)    NOT NULL,
    created_by      BIGINT          NOT NULL,
    max_uses        INT             DEFAULT NULL,   -- NULL = unlimited
    use_count       INT             NOT NULL DEFAULT 0,
    is_revoked      TINYINT(1)      NOT NULL DEFAULT 0,
    expires_at      DATETIME(6)     DEFAULT NULL,   -- NULL = never expires
    created_at      DATETIME(6)     NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uq_invite_token       (token),
    KEY        idx_invite_conv       (conversation_id),
    KEY        idx_invite_created_by (created_by),
    CONSTRAINT fk_invite_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Shareable invite links for group conversations';


-- ── Reel Likes ─────────────────────────────────────────────────────────────
-- Stored in MySQL (not MongoDB) for O(1) EXISTS checks without aggregation.

CREATE TABLE IF NOT EXISTS reel_likes (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    reel_id     VARCHAR(64) NOT NULL,   -- MongoDB ObjectId of the reel
    user_id     BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_reel_likes_reel_user (reel_id, user_id),
    KEY        idx_reel_likes_user     (user_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Who liked which reel; reel_id references MongoDB reels collection';


-- =============================================================================
-- 3. Verify
-- =============================================================================

SELECT
    table_name                                       AS `table`,
    IFNULL(table_rows, 0)                            AS approx_rows,
    ROUND((data_length + index_length) / 1024, 1)   AS total_kb,
    table_comment                                    AS comment
FROM information_schema.tables
WHERE table_schema = 'ozichat'
ORDER BY table_name;

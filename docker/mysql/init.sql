-- MySQL init script — runs once when the container is first created.
-- Hibernate (ddl-auto: update) creates all tables automatically,
-- so this script only sets character encoding and timezone.

ALTER DATABASE ozichat
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

SET GLOBAL time_zone = '+00:00';
SET time_zone = '+00:00';

-- =============================================================================
-- Vinyl — OPTIONAL: synthetic "stranger" accounts for demo data
-- Owner: Ivan (guangyu11)
--
-- seed.sql attributes its songs to whoever has already signed in. That is fine
-- once the four of us have logged in, but the matchmaker never recommends you
-- your own song, so a solo demo can look empty. This file creates six fake
-- accounts that exist only to own seed submissions. They can never sign in
-- (no usable password, no linked identity).
--
-- Run this BEFORE seed.sql. Only needed on the shared dev project.
--
-- Writing to auth.users directly is normally a bad idea — GoTrue owns that
-- table and its columns change between releases. If this script errors on a
-- NOT NULL column, add that column to the insert; nothing else depends on it.
--
-- DO NOT run this against anything you would call production.
-- =============================================================================

insert into auth.users (
  instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
  raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
  confirmation_token, email_change, email_change_token_new, recovery_token,
  is_anonymous, is_sso_user
)
select
  '00000000-0000-0000-0000-000000000000',
  gen_random_uuid(),
  'authenticated',
  'authenticated',
  format('vinyl-demo-%s@example.invalid', n),
  '',                       -- unusable password: these accounts cannot log in
  now(),
  '{"provider":"demo","providers":["demo"]}'::jsonb,
  jsonb_build_object('full_name', format('Demo Listener %s', n), 'seed', true),
  now(), now(),
  '', '', '', '',
  false, false
from generate_series(1, 6) as n
where not exists (
  select 1 from auth.users u
  where u.email = format('vinyl-demo-%s@example.invalid', n)
);

-- public.profiles rows are created automatically by the on_auth_user_created
-- trigger from 0001_schema.sql.

select count(*) as demo_accounts
from auth.users
where email like 'vinyl-demo-%@example.invalid';


-- -----------------------------------------------------------------------------
-- To remove them again (cascades to their profiles and submissions):
--
--   delete from auth.users where email like 'vinyl-demo-%@example.invalid';
-- -----------------------------------------------------------------------------

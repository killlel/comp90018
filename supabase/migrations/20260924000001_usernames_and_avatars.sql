-- =============================================================================
-- Vinyl — 0009: pre-generated usernames and avatars; drop profiles.settings
-- Owner: Ivan (guangyu11)  |  Sprint 2
--
-- Onboarding lets the user pick a username and a profile picture from sets we
-- provide. Free text is deliberately not offered: someone could use it to
-- identify themselves, which defeats the anonymity model the whole app rests on.
--
-- WHY LOOKUP TABLES AND NOT ENUMS
--
-- Same reasoning as public.genres. Enum values cannot be removed or reordered,
-- so every icon added, swapped or retired during a design pass would be a
-- migration plus an app release. A lookup table makes it one INSERT.
--
-- These are better off than genres, though: a user picks exactly ONE of each,
-- so both get a real foreign key enforced by Postgres rather than the
-- validation trigger an array is forced to use.
--
-- RELATIONSHIP TO display_name / avatar_url
--
-- Those two hold the Google name and picture, copied by the signup trigger, and
-- are private to their owner. They are deliberately NOT reused here — one
-- column meaning both "your real Google name" and "the alias you chose" is a
-- guaranteed source of confusion, and a leak waiting to happen.
--
-- Whether the Google name/picture are still worth storing at all is a separate
-- question for the team; nothing reads them today.
--
-- Idempotent: safe to re-run.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. Username pool
--
-- slug  = stored and matched on
-- label = shown to the user
--
-- NOT unique per user. Two people may hold the same alias, which is harmless
-- because these are display-only and are never attached to a record a recipient
-- sees. Making them exclusive would mean the picker has to know which are free,
-- and the pool would have to outgrow the user count.
--
-- To make them exclusive later:
--   create unique index profiles_username_slug_key
--     on public.profiles (username_slug) where username_slug is not null;
-- -----------------------------------------------------------------------------

create table if not exists public.usernames (
  slug       text primary key check (slug ~ '^[a-z0-9_]+$'),
  label      text not null,
  sort_order integer not null default 100,
  is_active  boolean not null default true
);

comment on table public.usernames is
  'Pool of pre-generated aliases. The app should read this rather than hardcode a list.';

insert into public.usernames (slug, label, sort_order) values
  ('velvet_fox',      'Velvet Fox',       10),
  ('midnight_tape',   'Midnight Tape',    20),
  ('paper_moon',      'Paper Moon',       30),
  ('slow_static',     'Slow Static',      40),
  ('blue_hour',       'Blue Hour',        50),
  ('north_platform',  'North Platform',   60),
  ('quiet_engine',    'Quiet Engine',     70),
  ('amber_signal',    'Amber Signal',     80),
  ('second_side',     'Second Side',      90),
  ('long_player',     'Long Player',     100),
  ('needle_drop',     'Needle Drop',     110),
  ('warm_vinyl',      'Warm Vinyl',      120),
  ('early_pressing',  'Early Pressing',  130),
  ('spare_room',      'Spare Room',      140),
  ('window_seat',     'Window Seat',     150),
  ('last_train',      'Last Train',      160),
  ('open_window',     'Open Window',     170),
  ('city_rain',       'City Rain',       180),
  ('soft_focus',      'Soft Focus',      190),
  ('coastal_road',    'Coastal Road',    200),
  ('third_verse',     'Third Verse',     210),
  ('borrowed_light',  'Borrowed Light',  220),
  ('half_speed',      'Half Speed',      230),
  ('someone_else',    'Someone Else',    240)
on conflict (slug) do update
  set label = excluded.label, sort_order = excluded.sort_order;


-- -----------------------------------------------------------------------------
-- 2. Avatar set
--
-- Images are remote, so the table carries the URL. Supabase Storage is the
-- expected home; any stable public URL works.
--
-- Seeded empty on purpose. Inserting placeholder URLs would make the feature
-- look finished while every icon 404s. Add rows once the files are uploaded:
--
--   insert into public.avatars (slug, url, sort_order) values
--     ('sleeve_01', 'https://<project>.supabase.co/storage/v1/object/public/avatars/sleeve_01.png', 10),
--     ('sleeve_02', 'https://<project>.supabase.co/storage/v1/object/public/avatars/sleeve_02.png', 20);
--
-- Until then profiles.avatar_slug can only be null — the foreign key has
-- nothing to point at, which is the correct and visible failure.
-- -----------------------------------------------------------------------------

create table if not exists public.avatars (
  slug       text primary key check (slug ~ '^[a-z0-9_]+$'),
  url        text not null check (url <> ''),
  sort_order integer not null default 100,
  is_active  boolean not null default true
);

comment on table public.avatars is
  'Provided profile pictures. url points at remote storage; the app renders it directly.';


-- -----------------------------------------------------------------------------
-- 3. The chosen values
--
-- Real foreign keys: Postgres rejects an unknown slug outright, no trigger
-- needed. Both nullable — a user who skips onboarding has neither.
--
-- on delete restrict: removing an avatar someone is using should fail loudly
-- rather than silently blank their profile. Retire it with is_active = false
-- instead.
-- -----------------------------------------------------------------------------

alter table public.profiles
  add column if not exists username_slug text
    references public.usernames (slug) on update cascade on delete restrict,
  add column if not exists avatar_slug text
    references public.avatars (slug) on update cascade on delete restrict;

comment on column public.profiles.username_slug is
  'Chosen alias, from public.usernames. Display-only; never attached to a record a recipient sees.';
comment on column public.profiles.avatar_slug is
  'Chosen profile picture, from public.avatars. Display-only.';


-- -----------------------------------------------------------------------------
-- 4. is_active is NOT enforced on the profile
--
-- A foreign key checks the slug exists, not that it is still offered. That is
-- deliberate: retiring an icon should hide it from the picker, not invalidate
-- the profiles of everyone who already chose it.
--
-- The app filters the picker:  ... where is_active order by sort_order
-- -----------------------------------------------------------------------------


-- -----------------------------------------------------------------------------
-- 5. Drop profiles.settings
--
-- Added in 0001 as a Sprint 3 escape hatch and never read or written. Every
-- preference we expected to keep there ended up as a real column instead:
-- favorite_genres, lat/lng, username_slug, avatar_slug. A notification toggle,
-- if it turns out to need storing at all, should be a real column too — jsonb
-- gives it no type, no default and no NOT NULL, and a string key anyone can
-- typo.
--
-- Same reasoning that removed default_mood and default_context in 0007: an
-- unused column invites someone to wire it up assuming it was designed for a
-- purpose. An empty jsonb is worse, because it is somewhere to dump untyped
-- state that nothing validates and nobody can query properly.
-- -----------------------------------------------------------------------------

alter table public.profiles
  drop column if exists settings;


-- -----------------------------------------------------------------------------
-- 6. Access
--
-- Public vocabularies: any signed-in user may read them, nobody may write them
-- from the app. Adding an avatar is an INSERT from the SQL editor or migration.
-- -----------------------------------------------------------------------------

alter table public.usernames enable row level security;
alter table public.avatars   enable row level security;

drop policy if exists usernames_select_authenticated on public.usernames;
create policy usernames_select_authenticated on public.usernames
  for select to authenticated
  using (true);

drop policy if exists avatars_select_authenticated on public.avatars;
create policy avatars_select_authenticated on public.avatars
  for select to authenticated
  using (true);

grant select on public.usernames to authenticated;
grant select on public.avatars   to authenticated;
revoke all on public.usernames from anon;
revoke all on public.avatars   from anon;


-- =============================================================================
-- CLIENT NOTE
--
-- Read both vocabularies the same way as genres:
--
--   from("usernames").select("slug,label") { order("sort_order", ASCENDING) }
--   from("avatars").select("slug,url")     { order("sort_order", ASCENDING) }
--
-- Filter on is_active when building the picker.
--
-- Save the choice with a direct upsert on public.profiles — RLS confines it to
-- the caller's own row. Store the SLUG, not the label or the URL.
--
-- display_name and avatar_url still hold the Google identity and stay private
-- to their owner. Do not show them anywhere a stranger can see.
-- =============================================================================

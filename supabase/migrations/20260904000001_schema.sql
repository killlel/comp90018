-- =============================================================================
-- Vinyl — 0001: core schema
-- Owner: Ivan (guangyu11)  |  Sprint 1
--
-- Tables: profiles, tracks, submissions, recommendations, shelf_items, reactions
--
-- This file is idempotent: it is safe to run more than once, so you can paste it
-- into the Supabase SQL editor while iterating without having to reset the DB.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. Enum types
--
-- Moods/contexts are a small fixed vocabulary shared by the onboarding
-- questionnaire, the submission form and the matchmaking algorithm. Using real
-- enum types (instead of free text) means a typo fails loudly at insert time
-- rather than silently matching nothing.
--
-- To add a value later:  alter type public.mood_tag add value 'wistful';
-- (Enum values cannot be removed or reordered — agree the list with the team.)
-- -----------------------------------------------------------------------------

do $$ begin
  create type public.mood_tag as enum (
    'happy', 'sad', 'calm', 'energetic', 'nostalgic',
    'anxious', 'romantic', 'angry', 'hopeful', 'lonely'
  );
exception when duplicate_object then null; end $$;

do $$ begin
  create type public.context_tag as enum (
    'commuting', 'studying', 'working_out', 'relaxing', 'sleeping',
    'partying', 'heartbroken', 'celebrating', 'late_night'
  );
exception when duplicate_object then null; end $$;

do $$ begin
  create type public.reaction_kind as enum (
    'heart', 'tears', 'fire', 'hug', 'goosebumps', 'smile'
  );
exception when duplicate_object then null; end $$;


-- -----------------------------------------------------------------------------
-- 2. profiles
--
-- Supabase owns auth.users and we must never write to it directly, so the app's
-- own per-user data lives here, keyed by the same uuid.
--
-- display_name / avatar_url are copied from the Google identity for the user's
-- OWN settings screen only. They are private: no policy and no RPC in this
-- schema ever exposes them to another user. Vinyl's anonymity is enforced here,
-- not in the UI.
-- -----------------------------------------------------------------------------

create table if not exists public.profiles (
  id                   uuid primary key references auth.users (id) on delete cascade,
  display_name         text,
  avatar_url           text,
  onboarding_completed boolean not null default false,
  default_mood         public.mood_tag,
  default_context      public.context_tag,
  settings             jsonb not null default '{}'::jsonb,
  created_at           timestamptz not null default now()
);

comment on table  public.profiles is 'App-side user record, 1:1 with auth.users. Private to its owner.';
comment on column public.profiles.settings is 'Free-form user preferences (Sprint 3 settings screen).';


-- Create a profile automatically whenever anyone signs in for the first time.
-- Without this trigger every foreign key to profiles fails for a brand new user.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id, display_name, avatar_url)
  values (
    new.id,
    nullif(new.raw_user_meta_data ->> 'full_name', ''),
    nullif(new.raw_user_meta_data ->> 'avatar_url', '')
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- Backfill anyone who already signed in before this migration ran
-- (e.g. Scott's account from the auth spike).
insert into public.profiles (id, display_name, avatar_url)
select u.id,
       nullif(u.raw_user_meta_data ->> 'full_name', ''),
       nullif(u.raw_user_meta_data ->> 'avatar_url', '')
from auth.users u
on conflict (id) do nothing;


-- -----------------------------------------------------------------------------
-- 3. tracks
--
-- Cached song metadata from whatever music API the submission feature uses.
-- Deduplicated on (provider, provider_track_id) so the same song submitted 50
-- times is stored once, which keeps genre-based matchmaking cheap.
-- -----------------------------------------------------------------------------

create table if not exists public.tracks (
  id                uuid primary key default gen_random_uuid(),
  provider          text not null check (provider in ('spotify', 'deezer', 'itunes', 'musicbrainz', 'manual')),
  provider_track_id text not null,
  title             text not null check (char_length(btrim(title)) between 1 and 300),
  artist            text not null check (char_length(btrim(artist)) between 1 and 300),
  album             text,
  artwork_url       text,
  preview_url       text,
  duration_ms       integer check (duration_ms is null or duration_ms > 0),
  genres            text[] not null default '{}',
  created_at        timestamptz not null default now(),
  constraint tracks_provider_uniq unique (provider, provider_track_id)
);

comment on table public.tracks is 'Deduplicated song metadata cached from the music API.';


-- -----------------------------------------------------------------------------
-- 4. submissions
--
-- One song passed anonymously into the pool. sender_id is the single most
-- sensitive column in the database: it must never reach the recipient. Direct
-- SELECT on this table is restricted to the sender (see 0002_rls.sql); everyone
-- else reads submissions through the RPCs in 0003_functions.sql, which return a
-- column list that does not include sender_id at all.
-- -----------------------------------------------------------------------------

create table if not exists public.submissions (
  id         uuid primary key default gen_random_uuid(),
  sender_id  uuid not null references public.profiles (id) on delete cascade,
  track_id   uuid not null references public.tracks (id) on delete restrict,
  message    text not null check (char_length(btrim(message)) between 1 and 280),
  mood       public.mood_tag not null,
  context    public.context_tag,
  genres     text[] not null default '{}',
  lat        double precision check (lat is null or lat between -90 and 90),
  lng        double precision check (lng is null or lng between -180 and 180),
  is_active  boolean not null default true,
  created_at timestamptz not null default now(),
  -- a half-supplied coordinate is a bug, not a partial location
  constraint submissions_location_pairing check ((lat is null) = (lng is null))
);

comment on column public.submissions.sender_id is
  'NEVER expose to a recipient. Restricted by RLS and omitted from every RPC return type.';
comment on column public.submissions.lat is
  'Coarse latitude, rounded to 2dp (~1.1km) by trigger on write. Exact coordinates are never stored.';


-- Privacy by design: round the coordinate before it is ever written to disk, so
-- a precise home location does not exist in the database even if a policy is
-- later misconfigured. 2 decimal places is roughly a 1.1 km grid cell.
create or replace function public.round_submission_location()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.lat := round(new.lat::numeric, 2)::double precision;
  new.lng := round(new.lng::numeric, 2)::double precision;
  return new;
end;
$$;

drop trigger if exists submissions_round_location on public.submissions;
create trigger submissions_round_location
  before insert or update of lat, lng on public.submissions
  for each row execute function public.round_submission_location();


-- -----------------------------------------------------------------------------
-- 5. recommendations
--
-- The matchmaker's output, persisted. This is what makes the record room
-- survive an app restart, and the unique constraint is what stops the shake
-- gesture from serving the same record twice.
-- -----------------------------------------------------------------------------

create table if not exists public.recommendations (
  id            uuid primary key default gen_random_uuid(),
  recipient_id  uuid not null references public.profiles (id) on delete cascade,
  submission_id uuid not null references public.submissions (id) on delete cascade,
  mood          public.mood_tag not null,      -- the request that produced this match
  context       public.context_tag,
  score         real,                          -- Sprint 2: match quality, for tuning
  created_at    timestamptz not null default now(),
  constraint recommendations_uniq unique (recipient_id, submission_id)
);

comment on table public.recommendations is
  'Which submissions have been shown to which user. Doubles as the "already seen" history.';


-- -----------------------------------------------------------------------------
-- 6. shelf_items  (the record shelf)
-- -----------------------------------------------------------------------------

create table if not exists public.shelf_items (
  id            uuid primary key default gen_random_uuid(),
  owner_id      uuid not null references public.profiles (id) on delete cascade,
  submission_id uuid not null references public.submissions (id) on delete cascade,
  note          text check (note is null or char_length(note) <= 280),
  saved_at      timestamptz not null default now(),
  constraint shelf_items_uniq unique (owner_id, submission_id)
);


-- -----------------------------------------------------------------------------
-- 7. reactions
--
-- reactor_id has to exist (it is how we deduplicate and how RLS decides who may
-- edit a reaction), but the submitter must never be able to read it. That is
-- handled in 0002_rls.sql: there is deliberately NO policy granting the
-- submitter SELECT on this table. Submitters read aggregate counts via
-- public.get_reactions() instead.
-- -----------------------------------------------------------------------------

create table if not exists public.reactions (
  id            uuid primary key default gen_random_uuid(),
  submission_id uuid not null references public.submissions (id) on delete cascade,
  reactor_id    uuid not null references public.profiles (id) on delete cascade,
  kind          public.reaction_kind not null,
  created_at    timestamptz not null default now(),
  -- one reaction per person per record; changing your mind updates in place
  constraint reactions_uniq unique (submission_id, reactor_id)
);

-- A check constraint cannot look at another table, so reject self-reactions
-- with a trigger. Defence in depth — add_reaction() also refuses them.
create or replace function public.reject_self_reaction()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if exists (
    select 1 from public.submissions s
    where s.id = new.submission_id and s.sender_id = new.reactor_id
  ) then
    raise exception 'cannot react to your own submission' using errcode = '23514';
  end if;
  return new;
end;
$$;

drop trigger if exists reactions_no_self on public.reactions;
create trigger reactions_no_self
  before insert or update on public.reactions
  for each row execute function public.reject_self_reaction();


-- -----------------------------------------------------------------------------
-- 8. Indexes
-- -----------------------------------------------------------------------------

create index if not exists submissions_sender_idx
  on public.submissions (sender_id, created_at desc);

-- the matchmaker's main lookup: active submissions by mood/context
create index if not exists submissions_match_idx
  on public.submissions (mood, context)
  where is_active;

create index if not exists submissions_genres_idx
  on public.submissions using gin (genres);

create index if not exists recommendations_recipient_idx
  on public.recommendations (recipient_id, created_at desc);

create index if not exists recommendations_submission_idx
  on public.recommendations (submission_id);

create index if not exists shelf_items_owner_idx
  on public.shelf_items (owner_id, saved_at desc);

create index if not exists reactions_submission_idx
  on public.reactions (submission_id);

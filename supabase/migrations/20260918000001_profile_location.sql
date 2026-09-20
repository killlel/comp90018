-- =============================================================================
-- Vinyl — 0004: location lives on the profile
-- Owner: Ivan (guangyu11)  |  Sprint 2
--
-- Agreed with Scott's GPS plan. The old model took raw coordinates from the
-- client on every submission. The new model:
--
--   * the user stores ONE coarse location on their profile (a city centroid),
--     set at onboarding and updatable from settings — no continuous tracking;
--   * a submission COPIES that location at send time, so it is a snapshot and
--     old records do not move when the sender changes city;
--   * the client no longer sends coordinates at all, it sends a yes/no flag.
--
-- The client cannot misreport a submission's location any more, because the
-- server reads it from the profile rather than trusting the request.
--
-- BREAKING: submit_song() no longer accepts p_lat / p_lng. See the note at the
-- bottom of this file for the one-line client change this requires.
--
-- Idempotent: safe to re-run.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. Location columns on profiles
--
-- city is stored deliberately. Scott's plan had the frontend reverse-geocode the
-- centroid for display, but his own edge-case list includes "no network
-- (geocoding fails)" — so every client would need a working Geocoder just to
-- render a label. Storing it removes that failure mode and leaks nothing extra:
-- a city centroid IS the city.
-- -----------------------------------------------------------------------------

alter table public.profiles
  add column if not exists lat                 double precision,
  add column if not exists lng                 double precision,
  add column if not exists city                text,
  add column if not exists location_updated_at timestamptz;

do $$ begin
  alter table public.profiles
    add constraint profiles_lat_range check (lat is null or lat between -90 and 90);
exception when duplicate_object then null; end $$;

do $$ begin
  alter table public.profiles
    add constraint profiles_lng_range check (lng is null or lng between -180 and 180);
exception when duplicate_object then null; end $$;

do $$ begin
  alter table public.profiles
    add constraint profiles_location_pairing check ((lat is null) = (lng is null));
exception when duplicate_object then null; end $$;

comment on column public.profiles.lat is
  'Coarse home location (city centroid), rounded to 2dp on write. Owner-readable only.';
comment on column public.profiles.city is
  'Display label for the centroid. Stored so the UI does not need a working Geocoder.';


-- -----------------------------------------------------------------------------
-- 2. Coordinate rounding, now shared by both tables
--
-- The client is supposed to send a city centroid, but the server cannot verify
-- that it did. Rounding to 2dp (~1.1km) on write is a backstop that costs
-- nothing: a precise coordinate never reaches disk even if a client misbehaves.
-- -----------------------------------------------------------------------------

create or replace function public.round_location_columns()
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
  for each row execute function public.round_location_columns();

drop trigger if exists profiles_round_location on public.profiles;
create trigger profiles_round_location
  before insert or update of lat, lng on public.profiles
  for each row execute function public.round_location_columns();

-- superseded by round_location_columns(); kept out of the way
drop function if exists public.round_submission_location();


-- -----------------------------------------------------------------------------
-- 3. update_my_location() — onboarding and the settings screen
--
-- Pass coordinates to set a location, or nulls to clear it (e.g. the user
-- revokes permission and wants their location forgotten).
-- -----------------------------------------------------------------------------

create or replace function public.update_my_location(
  p_lat  double precision default null,
  p_lng  double precision default null,
  p_city text default null
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;

  if (p_lat is null) <> (p_lng is null) then
    raise exception 'latitude and longitude must be supplied together'
      using errcode = '22023';
  end if;

  update public.profiles
     set lat  = p_lat,
         lng  = p_lng,
         city = case when p_lat is null then null else p_city end,
         location_updated_at = case when p_lat is null then null else now() end
   where id = v_uid;
end;
$$;


-- -----------------------------------------------------------------------------
-- 4. submit_song() — reads location from the sender's profile
--
-- The old signature took p_lat / p_lng. It is dropped rather than left in place
-- accepting-and-ignoring them: a parameter that silently does nothing is worse
-- than one that fails loudly, because someone will pass coordinates and spend
-- an afternoon wondering why they vanish.
-- -----------------------------------------------------------------------------

drop function if exists public.submit_song(
  text, text, text, text, text, public.mood_tag, text, text, text,
  integer, text[], public.context_tag, text[], double precision, double precision
);

create or replace function public.submit_song(
  p_provider          text,
  p_provider_track_id text,
  p_title             text,
  p_artist            text,
  p_message           text,
  p_mood              public.mood_tag,
  p_album             text                default null,
  p_artwork_url       text                default null,
  p_preview_url       text                default null,
  p_duration_ms       integer             default null,
  p_track_genres      text[]              default '{}',
  p_context           public.context_tag  default null,
  p_genres            text[]              default '{}',
  p_attach_location   boolean             default false
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid      uuid := auth.uid();
  v_track_id uuid;
  v_id       uuid;
  v_lat      double precision;
  v_lng      double precision;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;

  if btrim(coalesce(p_message, '')) = '' then
    raise exception 'message is required' using errcode = '22023';
  end if;
  if btrim(coalesce(p_title, '')) = '' or btrim(coalesce(p_artist, '')) = '' then
    raise exception 'a song must have a title and an artist' using errcode = '22023';
  end if;

  -- Snapshot the sender's saved location. If they never set one, the submission
  -- simply carries no location: the UI disables the toggle, and a user who
  -- flips it anyway gets a record without coordinates rather than an error.
  if coalesce(p_attach_location, false) then
    select p.lat, p.lng into v_lat, v_lng
    from public.profiles p
    where p.id = v_uid;
  end if;

  insert into public.tracks as t (
    provider, provider_track_id, title, artist,
    album, artwork_url, preview_url, duration_ms, genres
  )
  values (
    p_provider, p_provider_track_id, btrim(p_title), btrim(p_artist),
    p_album, p_artwork_url, p_preview_url, p_duration_ms, coalesce(p_track_genres, '{}')
  )
  on conflict (provider, provider_track_id) do update set
    title       = excluded.title,
    artist      = excluded.artist,
    album       = coalesce(excluded.album, t.album),
    artwork_url = coalesce(excluded.artwork_url, t.artwork_url),
    preview_url = coalesce(excluded.preview_url, t.preview_url),
    duration_ms = coalesce(excluded.duration_ms, t.duration_ms),
    genres      = case
                    when coalesce(array_length(excluded.genres, 1), 0) > 0
                      then excluded.genres
                    else t.genres
                  end
  returning t.id into v_track_id;

  insert into public.submissions (
    sender_id, track_id, message, mood, context, genres, lat, lng
  )
  values (
    v_uid, v_track_id, btrim(p_message), p_mood, p_context,
    coalesce(p_genres, '{}'), v_lat, v_lng
  )
  returning id into v_id;

  return v_id;
end;
$$;


-- -----------------------------------------------------------------------------
-- 5. Grants
-- -----------------------------------------------------------------------------

do $$
declare
  f record;
begin
  for f in
    select format('public.%I(%s)', p.proname,
                  pg_get_function_identity_arguments(p.oid)) as sig
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname in ('submit_song', 'update_my_location')
  loop
    execute format('revoke execute on function %s from public, anon', f.sig);
    execute format('grant execute on function %s to authenticated', f.sig);
  end loop;
end $$;


-- =============================================================================
-- CLIENT CHANGE REQUIRED — SubmissionRepository.kt
--
-- Remove these two lines:
--     lat?.let { put("p_lat", it) } ?: put("p_lat", JsonNull)
--     lng?.let { put("p_lng", it) } ?: put("p_lng", JsonNull)
--
-- Replace with:
--     put("p_attach_location", attachLocation)
--
-- The submitSong() lat/lng parameters can go; WriteCardViewModel already has
-- the attachLocation boolean it needs in state.attachLocation.
-- =============================================================================

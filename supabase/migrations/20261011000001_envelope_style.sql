-- =============================================================================
-- Vinyl — 0017: the sender's envelope style travels with the record
--
-- The Write screen lets the sender pick an envelope look (Rainbow, Midnight,
-- Sweetheart...). It was never sent, so every recipient saw the same envelope.
--
--   * submissions.envelope_style   the chosen slug, null on older records
--   * submit_song()                takes p_envelope_style (default null)
--   * get_envelope_styles()        returns the style for records in YOUR room
--
-- WHY A SEPARATE FUNCTION AND NOT A room_card FIELD
--
-- room_card is frozen at 16 fields: adding one means dropping every function
-- that returns it, and builds without ignoreUnknownKeys would throw on every
-- get_room / get_shelf / request_recommendations call. Same reasoning as 0012
-- and 0013. The app asks for styles by submission id and merges them in.
--
-- A style is a look, not an identity, so exposing it to a recipient leaks
-- nothing about the sender.
--
-- Old app builds keep working: p_envelope_style has a default, and PostgREST
-- matches arguments by name.
--
-- Idempotent: safe to re-run.
-- =============================================================================

begin;

-- -----------------------------------------------------------------------------
-- 1. The column
--
-- Nullable: records sent before this have no style, and the app falls back to
-- its default. The check lists the slugs EnvelopeStyle.slug produces. Adding a
-- style in the app means adding its slug here (a new migration).
-- -----------------------------------------------------------------------------

alter table public.submissions
  add column if not exists envelope_style text;

do $$ begin
  alter table public.submissions
    add constraint submissions_envelope_style_valid
    check (envelope_style is null or envelope_style in (
      'rainbow', 'sunset', 'ocean', 'berry',
      'cute', 'midnight', 'simple', 'sweetheart'
    ));
exception when duplicate_object then null; end $$;

comment on column public.submissions.envelope_style is
  'EnvelopeStyle slug the sender chose (rainbow, midnight...). Null on records sent before styles were saved. Not identifying.';


-- -----------------------------------------------------------------------------
-- 2. submit_song() — one new optional argument
--
-- The 14-argument version from 0004 is dropped first: leaving it next to the
-- 15-argument one would give PostgREST two candidates for the same named call.
-- -----------------------------------------------------------------------------

drop function if exists public.submit_song(
  text, text, text, text, text, public.mood_tag, text, text, text,
  integer, text[], public.context_tag, text[], boolean
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
  p_attach_location   boolean             default false,
  p_envelope_style    text                default null
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
  v_style    text := nullif(lower(btrim(coalesce(p_envelope_style, ''))), '');
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

  -- Snapshot the sender's saved location, as in 0004.
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
    sender_id, track_id, message, mood, context, genres, lat, lng, envelope_style
  )
  values (
    v_uid, v_track_id, btrim(p_message), p_mood, p_context,
    coalesce(p_genres, '{}'), v_lat, v_lng, v_style
  )
  returning id into v_id;

  return v_id;
end;
$$;


-- -----------------------------------------------------------------------------
-- 3. get_envelope_styles() — the style of records you have been given
--
-- Only submissions that are in the caller's own recommendations come back, so
-- it cannot be used to probe the pool by guessing ids. Capped at 50 ids a call.
-- Returns no sender_id, by construction.
-- -----------------------------------------------------------------------------

create or replace function public.get_envelope_styles(p_submission_ids uuid[])
returns table (submission_id uuid, envelope_style text)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;

  if coalesce(array_length(p_submission_ids, 1), 0) = 0 then
    return;
  end if;

  return query
    select s.id, s.envelope_style
    from public.submissions s
    where s.id = any (p_submission_ids[1:50])
      and exists (
        select 1 from public.recommendations r
        where r.recipient_id = v_uid
          and r.submission_id = s.id
      );
end;
$$;


-- -----------------------------------------------------------------------------
-- 4. Grants
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
      and p.proname in ('submit_song', 'get_envelope_styles')
  loop
    execute format('revoke execute on function %s from public, anon', f.sig);
    execute format('grant execute on function %s to authenticated', f.sig);
  end loop;
end $$;

commit;
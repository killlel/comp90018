-- =============================================================================
-- Vinyl — 0003: the API surface (SECURITY DEFINER functions)
-- Owner: Ivan (guangyu11)  |  Sprint 1
--
-- RLS (0002) locks every table down to "your own rows". These functions are the
-- controlled openings in that wall. Each one runs as the function owner, so it
-- can see across users — which means each one is responsible for:
--   1. checking the caller is authenticated,
--   2. checking the caller is entitled to what they asked for,
--   3. returning a column list that contains NO sender_id and NO reactor_id.
--
-- Every function is `set search_path = ''` with fully-qualified names, so a
-- caller cannot shadow a table or operator to hijack the elevated privileges.
--
-- Idempotent: safe to re-run.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- Private helper schema. PostgREST only exposes `public`, so nothing in here is
-- reachable from the app — these are building blocks for the functions below.
-- -----------------------------------------------------------------------------

create schema if not exists app_private;
revoke all on schema app_private from public;


-- -----------------------------------------------------------------------------
-- room_card — the anonymised shape of a song as a recipient sees it.
--
-- Note what is absent: sender_id. That omission is the whole privacy model, and
-- keeping it in one shared type means a future screen cannot re-add it by
-- accident in one query.
--
-- To change this type you must drop the functions that return it first:
--   drop function if exists public.get_room(integer);
--   drop function if exists public.get_shelf(integer);
--   drop function if exists public.request_recommendations(public.mood_tag, public.context_tag, integer);
--   drop function if exists app_private.room_cards(uuid, uuid[], integer, boolean);
--   drop type if exists public.room_card;
-- -----------------------------------------------------------------------------

do $$ begin
  create type public.room_card as (
    recommendation_id uuid,
    submission_id     uuid,
    message           text,
    mood              public.mood_tag,
    context           public.context_tag,
    genres            text[],
    lat               double precision,
    lng               double precision,
    submitted_at      timestamptz,
    track_title       text,
    track_artist      text,
    track_album       text,
    artwork_url       text,
    preview_url       text,
    reaction_count    integer,
    saved             boolean
  );
exception when duplicate_object then null; end $$;


-- Column order below must match the type definition above exactly — a SQL
-- function returning a composite maps columns positionally, not by name.
create or replace function app_private.room_cards(
  p_uid        uuid,
  p_rec_ids    uuid[]  default null,   -- null = all of this user's recommendations
  p_limit      integer default 100,
  p_saved_only boolean default false
)
returns setof public.room_card
language sql
stable
set search_path = ''
as $$
  select
    r.id,
    s.id,
    s.message,
    s.mood,
    s.context,
    s.genres,
    s.lat,
    s.lng,
    s.created_at,
    t.title,
    t.artist,
    t.album,
    t.artwork_url,
    t.preview_url,
    coalesce(rc.total, 0),
    (sh.owner_id is not null)
  from public.recommendations r
  join public.submissions s on s.id = r.submission_id
  join public.tracks      t on t.id = s.track_id
  left join lateral (
    select count(*)::integer as total
    from public.reactions x
    where x.submission_id = s.id
  ) rc on true
  left join public.shelf_items sh
    on sh.submission_id = s.id and sh.owner_id = p_uid
  where r.recipient_id = p_uid
    and (p_rec_ids is null or r.id = any (p_rec_ids))
    and (not p_saved_only or sh.owner_id is not null)
  order by (case when p_saved_only then sh.saved_at else r.created_at end) desc
  limit greatest(coalesce(p_limit, 100), 1);
$$;


-- -----------------------------------------------------------------------------
-- get_room(limit) — the record room.
-- The caller's most recent recommendations, anonymised.
-- -----------------------------------------------------------------------------

create or replace function public.get_room(p_limit integer default 3)
returns setof public.room_card
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

  return query
    select * from app_private.room_cards(
      v_uid, null, least(greatest(coalesce(p_limit, 3), 1), 50), false
    );
end;
$$;


-- -----------------------------------------------------------------------------
-- get_shelf(limit) — the record shelf, newest save first.
-- -----------------------------------------------------------------------------

create or replace function public.get_shelf(p_limit integer default 50)
returns setof public.room_card
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

  return query
    select * from app_private.room_cards(
      v_uid, null, least(greatest(coalesce(p_limit, 50), 1), 200), true
    );
end;
$$;


-- -----------------------------------------------------------------------------
-- request_recommendations(mood, context, limit) — the matchmaker.
--
-- SPRINT 1: a deliberately simple additive score. It already satisfies the
-- Sprint 2 acceptance criteria for degenerate input, because a submission that
-- matches nothing still scores 0 rather than being filtered out — so a sparse
-- pool returns weaker matches instead of an empty room.
--
-- SPRINT 2 (your next task): replace only the `score` expression. Candidates
-- for the real version: genre overlap via `array_length(s.genres & p_genres)`,
-- geographic proximity, submission age, and how many times a record has already
-- been passed on. Nothing outside this CTE has to change.
-- -----------------------------------------------------------------------------

create or replace function public.request_recommendations(
  p_mood    public.mood_tag,
  p_context public.context_tag default null,
  p_limit   integer default 3
)
returns setof public.room_card
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_new uuid[];
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;

  p_limit := least(greatest(coalesce(p_limit, 3), 1), 10);

  with candidates as (
    select
      s.id,
      (case when s.mood = p_mood then 2 else 0 end)
      + (case when p_context is not null and s.context = p_context then 1 else 0 end)
      + (case when s.created_at > now() - interval '30 days' then 0.5 else 0 end)
      as score
    from public.submissions s
    where s.is_active
      and s.sender_id <> v_uid                    -- never recommend your own song
      and not exists (                            -- never repeat a record
        select 1 from public.recommendations r
        where r.recipient_id = v_uid
          and r.submission_id = s.id
      )
    order by score desc, random()
    limit p_limit
  ),
  inserted as (
    insert into public.recommendations (recipient_id, submission_id, mood, context, score)
    select v_uid, c.id, p_mood, p_context, c.score
    from candidates c
    on conflict (recipient_id, submission_id) do nothing
    returning id
  )
  select coalesce(array_agg(id), '{}'::uuid[]) into v_new from inserted;

  -- Empty pool is a normal state, not an error: the caller gets zero rows and
  -- the record room shows its empty state.
  return query
    select * from app_private.room_cards(v_uid, v_new, p_limit, false);
end;
$$;


-- -----------------------------------------------------------------------------
-- submit_song(...) — create a submission, upserting the track in one shot.
--
-- Doing this as one call means the (provider, provider_track_id) upsert cannot
-- race two users submitting the same song at the same moment, and the client
-- never has to know the tracks table exists.
--
-- PostgREST passes RPC arguments by name, so argument order does not matter to
-- the Kotlin caller; the defaults simply make those fields optional.
-- -----------------------------------------------------------------------------

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
  p_lat               double precision    default null,
  p_lng               double precision    default null
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
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;

  -- Cheap guards so the client gets a readable error rather than a raw
  -- constraint violation. The table constraints are still the real authority.
  if btrim(coalesce(p_message, '')) = '' then
    raise exception 'message is required' using errcode = '22023';
  end if;
  if btrim(coalesce(p_title, '')) = '' or btrim(coalesce(p_artist, '')) = '' then
    raise exception 'a song must have a title and an artist' using errcode = '22023';
  end if;
  if (p_lat is null) <> (p_lng is null) then
    raise exception 'latitude and longitude must be supplied together' using errcode = '22023';
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
    coalesce(p_genres, '{}'), p_lat, p_lng
  )
  returning id into v_id;

  return v_id;
end;
$$;


-- -----------------------------------------------------------------------------
-- add_reaction(submission_id, kind) — react to a record in your room.
-- Returns the submission's new total reaction count.
--
-- Reacting twice updates in place instead of erroring, which is what the
-- "duplicate reaction attempts are handled without crashing" criterion needs.
-- -----------------------------------------------------------------------------

create or replace function public.add_reaction(
  p_submission_id uuid,
  p_kind          public.reaction_kind
)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid   uuid := auth.uid();
  v_total integer;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;

  -- You may only react to a record that was actually given to you. Without this
  -- check a caller could react to any uuid they guessed and probe the pool.
  if not exists (
    select 1 from public.recommendations r
    where r.recipient_id = v_uid
      and r.submission_id = p_submission_id
  ) then
    raise exception 'that record is not in your room' using errcode = '42501';
  end if;

  insert into public.reactions (submission_id, reactor_id, kind)
  values (p_submission_id, v_uid, p_kind)
  on conflict (submission_id, reactor_id) do update
    set kind = excluded.kind, created_at = now();

  select count(*)::integer into v_total
  from public.reactions x
  where x.submission_id = p_submission_id;

  return v_total;
end;
$$;


-- -----------------------------------------------------------------------------
-- get_reactions(submission_id) — what a submitter is allowed to see.
--
-- Counts per kind, and nothing else. Deliberately no reactor_id and no
-- timestamps: a per-reaction timestamp could be correlated against when a
-- record was handed out to work back to who sent it, which would defeat the
-- point of locking reactor_id away in the first place.
-- -----------------------------------------------------------------------------

create or replace function public.get_reactions(p_submission_id uuid)
returns table (kind public.reaction_kind, total integer)
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

  if not exists (
    select 1 from public.submissions s
    where s.id = p_submission_id
      and s.sender_id = v_uid
  ) then
    raise exception 'you can only view reactions to your own submissions'
      using errcode = '42501';
  end if;

  return query
    select x.kind, count(*)::integer
    from public.reactions x
    where x.submission_id = p_submission_id
    group by x.kind
    order by 2 desc, 1;
end;
$$;


-- -----------------------------------------------------------------------------
-- get_my_submissions() — the "records I've sent" screen, with reaction totals.
-- -----------------------------------------------------------------------------

create or replace function public.get_my_submissions(p_limit integer default 50)
returns table (
  submission_id  uuid,
  message        text,
  mood           public.mood_tag,
  context        public.context_tag,
  is_active      boolean,
  created_at     timestamptz,
  track_title    text,
  track_artist   text,
  artwork_url    text,
  reaction_count integer
)
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

  return query
    select s.id, s.message, s.mood, s.context, s.is_active, s.created_at,
           t.title, t.artist, t.artwork_url,
           coalesce(rc.total, 0)
    from public.submissions s
    join public.tracks t on t.id = s.track_id
    left join lateral (
      select count(*)::integer as total
      from public.reactions x
      where x.submission_id = s.id
    ) rc on true
    where s.sender_id = v_uid
    order by s.created_at desc
    limit least(greatest(coalesce(p_limit, 50), 1), 200);
end;
$$;


-- -----------------------------------------------------------------------------
-- Grants
--
-- Postgres grants EXECUTE on new public-schema functions to PUBLIC by default,
-- which would let the `anon` role call these elevated functions. Revoke that and
-- hand execute rights to signed-in users only.
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
      and p.proname in (
        'get_room', 'get_shelf', 'request_recommendations', 'submit_song',
        'add_reaction', 'get_reactions', 'get_my_submissions'
      )
  loop
    execute format('revoke execute on function %s from public, anon', f.sig);
    execute format('grant execute on function %s to authenticated', f.sig);
  end loop;
end $$;

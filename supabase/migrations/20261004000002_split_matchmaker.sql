-- =============================================================================
-- Vinyl — 0012: split the matchmaker across server and client
-- Owner: Ivan (guangyu11)  |  Sprint 2
--
-- 0011 ranks entirely in SQL. This adds a second path that keeps the half which
-- MUST be server-side on the server, and lets the app do the arithmetic.
--
--
-- WHERE THE LINE IS
--
-- Server, always — these are security properties, not ranking:
--     * never your own submission
--     * never one you have already been shown
--     * only active submissions
--     * the anonymised projection with no sender_id in it
--
-- Either side — pure arithmetic over already-anonymised rows:
--     * genre overlap, freshness, circulation, diversity, exploration
--
-- Moving the filters client-side would mean handing the client the whole pool
-- so it could filter it, which is exactly what RLS exists to stop. Moving the
-- arithmetic costs nothing: a candidate carries no identity, so ranking it on
-- the phone reveals nothing the server did not already agree to send.
--
--
-- WHY room_card IS NOT TOUCHED
--
-- The obvious design was to add times_recommended to room_card. That would
-- break every teammate's build the moment it was pushed: supabase-kt's default
-- serializer REJECTS unknown keys, so an older RoomCard throws
--
--     JsonDecodingException: Encountered an unknown key 'times_recommended'
--
-- on every get_room / get_shelf / request_recommendations call. Verified, not
-- assumed. So candidates get their own composite instead, and room_card keeps
-- exactly the sixteen fields the rest of the app already decodes.
--
-- The two are genuinely different things anyway. A candidate has no
-- recommendation_id, because no match exists yet, and cannot be `saved`,
-- because you cannot shelve what you were never given.
--
--
-- THE TRAP THIS AVOIDS
--
-- request_recommendations() writes everything it returns. Over-fetch twenty,
-- show three, and the other seventeen are marked seen and never offered again.
-- Hence the split into a read-only fetch and an explicit commit.
--
-- 0011's all-in-SQL path is untouched and still works, so the app can move over
-- gradually and fall back without a migration.
--
-- Idempotent: safe to re-run.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. candidate_card — a letter that has been offered but not yet delivered
--
-- Column order must match this definition exactly: a SQL function returning a
-- composite maps positionally, not by name.
-- -----------------------------------------------------------------------------

do $$ begin
  create type public.candidate_card as (
    submission_id      uuid,
    message            text,
    mood               public.mood_tag,
    context            public.context_tag,
    genres             text[],
    lat                double precision,
    lng                double precision,
    submitted_at       timestamptz,
    track_title        text,
    track_artist       text,
    track_album        text,
    artwork_url        text,
    preview_url        text,
    reaction_count     integer,
    -- How many people have been shown this record, across everyone. A global
    -- count the client cannot see for itself, and the input to the fairness
    -- term. A number, never a list of who.
    times_recommended  integer
  );
exception when duplicate_object then null; end $$;

comment on type public.candidate_card is
  'A letter offered for ranking but not yet delivered. No sender_id, by construction. Separate from room_card so adding fields here cannot break clients that decode room_card.';


-- -----------------------------------------------------------------------------
-- 2. app_private.candidate_cards()
--
-- The eligibility rules are identical to the ones request_recommendations()
-- applies, because they are the part that may never move to the client.
-- -----------------------------------------------------------------------------

create or replace function app_private.candidate_cards(
  p_uid   uuid,
  p_limit integer default 20
)
returns setof public.candidate_card
language sql
stable
set search_path = ''
as $$
  select
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
    coalesce(tr.total, 0)
  from public.submissions s
  join public.tracks t on t.id = s.track_id
  left join lateral (
    select count(*)::integer as total
    from public.reactions x
    where x.submission_id = s.id
  ) rc on true
  left join lateral (
    select count(*)::integer as total
    from public.recommendations x
    where x.submission_id = s.id
  ) tr on true
  where s.is_active
    and s.sender_id <> p_uid
    and not exists (
      select 1 from public.recommendations r
      where r.recipient_id = p_uid
        and r.submission_id = s.id
    )
  -- Random rather than ranked: ranking is the client's job now, and taking the
  -- "best" twenty by some server-side order would quietly re-impose a ranking
  -- the client then cannot undo.
  order by random()
  limit greatest(coalesce(p_limit, 20), 1);
$$;


-- -----------------------------------------------------------------------------
-- 3. get_candidates() — read-only. Writes nothing, burns nothing.
-- -----------------------------------------------------------------------------

create or replace function public.get_candidates(p_limit integer default 20)
returns setof public.candidate_card
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

  -- Capped so a client cannot pull the whole pool one call at a time and
  -- reconstruct it. Twenty is comfortably more than the three it will show.
  return query
    select * from app_private.candidate_cards(v_uid, least(coalesce(p_limit, 20), 50));
end;
$$;


-- -----------------------------------------------------------------------------
-- 4. get_genre_weights() — inverse document frequency over the whole pool
--
-- The client cannot compute this honestly: it only ever sees the candidates it
-- was handed, so its idf would be an estimate from a biased sample. Exported as
-- plain numbers — a genre and how rare it is leaks nothing about anyone.
-- -----------------------------------------------------------------------------

create or replace function public.get_genre_weights()
returns table (slug text, weight double precision)
language sql
security definer
stable
set search_path = ''
as $$
  select
    g.slug,
    ln(1 + (select count(*) from public.submissions where is_active)::double precision
          / greatest(count(s.id), 1)) as weight
  from public.genres g
  left join public.submissions s
    on s.is_active and g.slug = any (s.genres)
  where g.is_active
  group by g.slug
  order by g.slug;
$$;


-- -----------------------------------------------------------------------------
-- 5. commit_recommendations() — record what the client chose to show
--
-- Re-checks eligibility rather than trusting the ids. Without this a client
-- could hand itself any submission in the database simply by naming it, which
-- is the exact hole the "only request_recommendations may write here" rule was
-- closing. Ineligible ids are dropped silently, not raised: a card that became
-- inactive between fetch and commit is a race, not an attack.
--
-- Returns room_card, unchanged, so the delivered cards decode with the type the
-- app already uses everywhere else.
-- -----------------------------------------------------------------------------

create or replace function public.commit_recommendations(
  p_submission_ids uuid[],
  p_mood           public.mood_tag,
  p_context        public.context_tag default null,
  p_scores         double precision[] default null
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

  if coalesce(array_length(p_submission_ids, 1), 0) = 0 then
    return;
  end if;

  if array_length(p_submission_ids, 1) > 10 then
    raise exception 'too many submissions in one commit' using errcode = '22023';
  end if;

  with requested as (
    select id, ordinality
    from unnest(p_submission_ids) with ordinality as u(id, ordinality)
  ),
  eligible as (
    select rq.id, rq.ordinality
    from requested rq
    join public.submissions s on s.id = rq.id
    where s.is_active
      and s.sender_id <> v_uid
      and not exists (
        select 1 from public.recommendations r
        where r.recipient_id = v_uid and r.submission_id = s.id
      )
  ),
  inserted as (
    insert into public.recommendations (recipient_id, submission_id, mood, context, score)
    select
      v_uid,
      e.id,
      p_mood,
      p_context,
      case
        when p_scores is not null and array_length(p_scores, 1) >= e.ordinality
          then p_scores[e.ordinality]::real
        else null
      end
    from eligible e
    on conflict (recipient_id, submission_id) do nothing
    returning id
  )
  select coalesce(array_agg(id), '{}'::uuid[]) into v_new from inserted;

  return query select * from app_private.room_cards(v_uid, v_new, 10, false);
end;
$$;


-- -----------------------------------------------------------------------------
-- 6. Grants
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
      and p.proname in ('get_candidates', 'get_genre_weights', 'commit_recommendations')
  loop
    execute format('revoke execute on function %s from public, anon', f.sig);
    execute format('grant execute on function %s to authenticated', f.sig);
  end loop;
end $$;


-- =============================================================================
-- CLIENT NOTE — two ways to fill the room, pick one
--
--   All server-side (unchanged, still works):
--       request_recommendations(p_mood, p_context?, p_limit?, p_genres?)
--
--   Split:
--       get_genre_weights()                       once, cacheable
--       get_candidates(p_limit => 20)             read-only, writes nothing
--       ...rank on device...
--       commit_recommendations(ids, mood, ...)    returns the delivered cards
--
-- get_candidates returns candidate_card, NOT room_card — decode it into its own
-- class. room_card is untouched, so nothing that exists today needs changing.
--
-- Worth doing anyway: supabase-kt's serializer rejects unknown keys by default.
-- Set `ignoreUnknownKeys = true` on the client and a future column addition
-- degrades a field instead of throwing on every call.
-- =============================================================================

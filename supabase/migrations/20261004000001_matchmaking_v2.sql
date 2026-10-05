-- =============================================================================
-- Vinyl — 0011: the real matchmaker
-- Owner: Ivan (guangyu11)  |  Sprint 2
--
-- Replaces the Sprint 1 placeholder, which was three hand-picked constants:
--
--     mood match 2.0  +  context match 1.0  +  posted in 30 days 0.5
--
-- That ranked by mood and little else. This version keeps the same contract —
-- same return type, same exclusions, same persistence — and replaces only the
-- scoring, which is what 0003 was written to allow.
--
--
-- WHY THESE TECHNIQUES AND NOT A MODEL
--
-- A learned recommender needs interaction history to fit against. With a
-- handful of accounts and two dozen submissions it would fit noise, score worse
-- than a hand-written rule, and leave nothing honest to say in the report. The
-- four ideas below are established, cheap, and chosen because they work at
-- SMALL scale — which is the regime this app is actually in.
--
--   1. IDF genre weighting        (inverse document frequency, from BM25)
--   2. Smooth freshness decay     (the Hacker News ranking formula)
--   3. Circulation fairness       (anti-rich-get-richer / exposure fairness)
--   4. Epsilon-greedy exploration (multi-armed bandits)
--   plus a one-per-artist rule, the cheap half of MMR diversification.
--
-- Deliberately NOT included: collaborative filtering, matrix factorisation and
-- learning-to-rank all need thousands of interactions before they beat a rule,
-- and geographic proximity does nothing while every account is in Melbourne.
--
--
-- ADDITIVE, NOT BREAKING: p_genres is new but has a default, and PostgREST
-- matches by argument name, so the existing RoomRepository call keeps working
-- untouched.
--
-- Idempotent: safe to re-run.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. request_recommendations() — scoring rewritten
-- -----------------------------------------------------------------------------

create or replace function public.request_recommendations(
  p_mood    public.mood_tag,
  p_context public.context_tag default null,
  p_limit   integer            default 3,
  p_genres  text[]             default '{}'
)
returns setof public.room_card
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid    uuid := auth.uid();
  v_new    uuid[];
  v_genres text[];
  v_pool   integer;

  -- WEIGHTS. Nobody tuned these against data — there isn't enough data to tune
  -- against — so they encode a stated opinion about what matters, in order:
  -- how you feel, then what you like, then what is new, then what is fair.
  -- They are the first thing to change if the room feels wrong.
  w_mood     constant double precision := 3.0;   -- the question the user answered
  w_genre    constant double precision := 2.0;   -- scaled by rarity, see below
  w_context  constant double precision := 1.0;   -- usually null today
  w_fresh    constant double precision := 1.5;   -- recency, decayed
  w_crowded  constant double precision := 0.8;   -- penalty, not a bonus

  -- Hacker News uses 1.8 on an hours scale. Days here, so a gentler slope:
  -- a week-old record keeps roughly a third of its freshness rather than none.
  gravity    constant double precision := 1.2;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;

  p_limit := least(greatest(coalesce(p_limit, 3), 1), 10);

  -- Today's answer wins; a user who skipped the genre chips falls back to the
  -- taste they set at onboarding. Both null and '{}' mean "no genre filter".
  v_genres := case
                when coalesce(array_length(p_genres, 1), 0) > 0 then p_genres
                else (select pr.favorite_genres from public.profiles pr where pr.id = v_uid)
              end;
  v_genres := coalesce(v_genres, '{}');

  select count(*)::integer into v_pool
  from public.submissions
  where is_active;

  with eligible as (
    select s.id, s.track_id, s.genres, s.mood, s.context, s.created_at
    from public.submissions s
    where s.is_active
      and s.sender_id <> v_uid                   -- never your own song
      and not exists (                           -- never a repeat
        select 1 from public.recommendations r
        where r.recipient_id = v_uid
          and r.submission_id = s.id
      )
  ),

  -- 1. INVERSE DOCUMENT FREQUENCY
  --
  -- A match on a genre half the pool carries says almost nothing; a match on a
  -- rare one is a strong signal. This is the idf half of BM25, over the active
  -- pool rather than the eligible subset so a user's own history doesn't move
  -- everyone else's weights.
  --
  --     idf(g) = ln(1 + N / df(g))
  --
  -- The 1 + keeps it positive when a genre appears in every submission.
  idf as (
    select g as slug,
           ln(1 + v_pool::double precision / greatest(count(*), 1)) as weight
    from public.submissions s, unnest(s.genres) as g
    where s.is_active
    group by g
  ),

  scored as (
    select
      e.id,
      t.artist,
      (
        -- mood: the dominant term, because it is the question actually asked
        (case when e.mood = p_mood then w_mood else 0 end)

        -- context: small, and null on every call until the accelerometer lands
        + (case when p_context is not null and e.context = p_context
                then w_context else 0 end)

        -- 2. GENRE OVERLAP, rarity-weighted and normalised
        --
        -- Divided by the number of genres the user picked, so selecting eight
        -- doesn't simply outscore selecting one. Jaccard-ish without the cost
        -- of a full union.
        + w_genre * coalesce((
            select sum(i.weight)
            from unnest(e.genres) as g
            join idf i on i.slug = g
            where g = any (v_genres)
          ), 0) / greatest(array_length(v_genres, 1), 1)

        -- 3. FRESHNESS, smoothly
        --
        -- The old rule was a cliff: full marks at 29 days, nothing at 31. This
        -- is the Hacker News shape — decays continuously and never reaches
        -- zero, so an old record stays reachable instead of being buried.
        --
        --     w / (age_days + 2) ^ gravity
        + w_fresh / power(
            extract(epoch from (now() - e.created_at)) / 86400.0 + 2.0,
            gravity
          )

        -- 4. CIRCULATION FAIRNESS
        --
        -- Without this the first few submissions win every round and newer ones
        -- are never seen — with four users, one person's songs would fill
        -- everybody's room. Logarithmic so it nudges rather than banishes.
        - w_crowded * ln(1 + (
            select count(*)
            from public.recommendations r
            where r.submission_id = e.id
          ))
      ) as score
    from eligible e
    join public.tracks t on t.id = e.track_id
  ),

  -- 5. DIVERSITY — one record per artist
  --
  -- The cheap half of MMR: rather than penalising similarity across the whole
  -- ranking, just refuse to return the same artist twice. At this pool size
  -- three cards by one artist is a likely outcome and looks broken, even when
  -- the ranking is technically correct.
  deduped as (
    select distinct on (s.artist) s.id, s.score
    from scored s
    order by s.artist, s.score desc, random()
  ),

  -- 6. EXPLORATION — keep one slot honest
  --
  -- Epsilon-greedy, with epsilon fixed at one slot. Exploiting the score every
  -- time means a user only ever sees their own mood reflected back, and a new
  -- submission has to out-score established ones before anyone sees it at all.
  -- One deliberately random pick breaks both.
  exploit as (
    select d.id, d.score
    from deduped d
    order by d.score desc, random()
    limit greatest(p_limit - 1, 1)
  ),
  explore as (
    select d.id, d.score
    from deduped d
    where p_limit > 1
      and d.id not in (select e.id from exploit e)
    order by random()
    limit 1
  ),
  chosen as (
    select id, score from exploit
    union
    select id, score from explore
  ),

  inserted as (
    insert into public.recommendations (recipient_id, submission_id, mood, context, score)
    select v_uid, c.id, p_mood, p_context, c.score
    from chosen c
    on conflict (recipient_id, submission_id) do nothing
    returning id
  )
  select coalesce(array_agg(id), '{}'::uuid[]) into v_new from inserted;

  -- An empty pool is a normal state, not an error: the caller gets zero rows
  -- and the record room shows its empty state.
  return query
    select * from app_private.room_cards(v_uid, v_new, p_limit, false);
end;
$$;


-- -----------------------------------------------------------------------------
-- 2. Index for the circulation count
--
-- The fairness term counts rows per submission on every candidate. There is
-- already an index on (recipient_id, created_at); this is the other direction.
-- -----------------------------------------------------------------------------

create index if not exists recommendations_submission_count_idx
  on public.recommendations (submission_id);


-- -----------------------------------------------------------------------------
-- 3. Grants
--
-- The new overload is a separate function as far as Postgres is concerned, so
-- it needs its own grant. The old 3-argument signature is dropped: leaving both
-- would make a call with three named arguments ambiguous to PostgREST.
-- -----------------------------------------------------------------------------

drop function if exists public.request_recommendations(
  public.mood_tag, public.context_tag, integer
);

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
      and p.proname = 'request_recommendations'
  loop
    execute format('revoke execute on function %s from public, anon', f.sig);
    execute format('grant execute on function %s to authenticated', f.sig);
  end loop;
end $$;


-- =============================================================================
-- CLIENT NOTE
--
-- Nothing breaks. p_genres has a default and PostgREST matches arguments by
-- name, so the existing call keeps working.
--
-- To use it, add the questionnaire's genre slugs:
--
--     put("p_genres", JsonArray(genres.map { JsonPrimitive(it) }))
--
-- Slugs, not labels — 'k_pop', not 'K-pop'. Omit it entirely and the server
-- falls back to the user's favorite_genres from onboarding.
-- =============================================================================

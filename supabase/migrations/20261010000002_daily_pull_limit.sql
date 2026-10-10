-- =============================================================================
-- Vinyl — 0016: one pull of three music cards a day, new ones at 6:00
-- Owner: Ivan (guangyu11)  |  Sprint 3
--
-- THE RULE
--
-- A user pulls once a day and is dealt at most three music cards. Once a pull
-- has dealt anything, the next one is refused until the day turns over, at
-- 06:00 in the user's own time zone — not at midnight: someone opening the
-- app at 1am is still on yesterday's cards. A pull that finds nothing to deal
-- (an exhausted pool) doesn't count, so it can be tried again.
--
-- Until now the only limit lived on the phone (the app remembered today's
-- hand). Reinstalling, clearing app data or signing in on a second phone
-- reset it, and the server would deal as many cards as it was asked for.
--
-- HOW
--
-- The cards dealt are already recorded: one public.recommendations row each,
-- stamped with created_at. So no new table is needed — a user's "pull status"
-- is whether any of their recommendations were created since the last 06:00.
--
--   app_private.pull_window(tz, at)   the current day: last 06:00 → next 06:00
--   app_private.pull_status(uid, tz)  cards dealt in it, cards a pull may deal
--   app_private.claim_pulls(uid, tz)  locks the user, raises once none are left
--   public.get_pull_status(tz)        the same, for the app's button
--
-- Both ways of dealing cards now go through claim_pulls():
--
--   request_recommendations  the server picks the cards (what the app uses)
--   commit_recommendations   the client picked them (the split matchmaker)
--
-- Rather than copy their long bodies, the existing functions are moved into
-- app_private unchanged (as deal_recommendations and
-- commit_recommendations_unchecked) and the public names become thin wrappers
-- that check the allowance, cap how many are dealt, and call through. The
-- scoring in 0014 is untouched. Anyone changing the matchmaker from now on
-- edits app_private.deal_recommendations.
--
-- TIME ZONE
--
-- The app sends its IANA zone ('Australia/Melbourne') as p_tz. A missing or
-- unknown zone falls back to Australia/Melbourne, so app builds from before
-- this migration — which don't send one — still work, on Melbourne time.
--
-- Concurrency: claim_pulls() takes a per-user transaction lock, so two pulls
-- sent at once can't both see "not pulled yet" and deal six.
-- =============================================================================

begin;

-- -----------------------------------------------------------------------------
-- 1. The day, and how much of it is left
-- -----------------------------------------------------------------------------

create or replace function app_private.pull_window(
  p_tz text,
  p_at timestamptz default now()
)
returns table (starts_at timestamptz, ends_at timestamptz)
language sql
stable
set search_path = ''
as $$
  with zone as (
    select coalesce(
      (select n.name from pg_catalog.pg_timezone_names n where n.name = p_tz limit 1),
      'Australia/Melbourne'
    ) as tz
  ),
  local_day as (
    -- Shift back six hours, take the date, shift forward again: the most
    -- recent 06:00 on the local wall clock.
    select z.tz,
           date_trunc('day', (p_at at time zone z.tz) - interval '6 hours')
             + interval '6 hours' as local_start
    from zone z
  )
  select d.local_start at time zone d.tz,
         (d.local_start + interval '1 day') at time zone d.tz
  from local_day d;
$$;

create or replace function app_private.pull_status(
  p_uid uuid,
  p_tz  text,
  p_at  timestamptz default now()
)
returns table (
  dealt_today     integer,
  remaining       integer,
  next_refresh_at timestamptz
)
language sql
stable
set search_path = ''
as $$
  select c.n,
         case when c.n = 0 then 3 else 0 end,
         w.ends_at
  from app_private.pull_window(p_tz, p_at) w
  cross join lateral (
    select count(*)::integer as n
    from public.recommendations r
    where r.recipient_id = p_uid
      and r.created_at >= w.starts_at
      and r.created_at <  w.ends_at
  ) c;
$$;

create or replace function app_private.claim_pulls(p_uid uuid, p_tz text)
returns integer
language plpgsql
set search_path = ''
as $$
declare
  v_left integer;
begin
  perform pg_advisory_xact_lock(hashtextextended('daily_pull:' || p_uid::text, 0));

  select s.remaining into v_left from app_private.pull_status(p_uid, p_tz) s;

  if v_left <= 0 then
    raise exception 'daily pull limit reached'
      using errcode = 'P0001',
            hint = 'One pull of three music cards a day; new ones arrive at 06:00 local time.';
  end if;

  return v_left;
end;
$$;

revoke execute on function app_private.pull_window(text, timestamptz) from public, anon, authenticated;
revoke execute on function app_private.pull_status(uuid, text, timestamptz) from public, anon, authenticated;
revoke execute on function app_private.claim_pulls(uuid, text) from public, anon, authenticated;


-- -----------------------------------------------------------------------------
-- 2. What the app asks before showing the button
-- -----------------------------------------------------------------------------

create or replace function public.get_pull_status(p_tz text default null)
returns table (
  dealt_today     integer,
  remaining       integer,
  next_refresh_at timestamptz
)
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

  return query select * from app_private.pull_status(v_uid, p_tz);
end;
$$;

revoke execute on function public.get_pull_status(text) from public, anon;
grant  execute on function public.get_pull_status(text) to authenticated;


-- -----------------------------------------------------------------------------
-- 3. Move the dealers out of the API, unchanged
-- -----------------------------------------------------------------------------

alter function public.request_recommendations(public.mood_tag, public.context_tag, integer, text[])
  rename to deal_recommendations;
alter function public.deal_recommendations(public.mood_tag, public.context_tag, integer, text[])
  set schema app_private;

alter function public.commit_recommendations(uuid[], public.mood_tag, public.context_tag, double precision[])
  rename to commit_recommendations_unchecked;
alter function public.commit_recommendations_unchecked(uuid[], public.mood_tag, public.context_tag, double precision[])
  set schema app_private;

revoke execute on function app_private.deal_recommendations(public.mood_tag, public.context_tag, integer, text[])
  from public, anon, authenticated;
revoke execute on function app_private.commit_recommendations_unchecked(uuid[], public.mood_tag, public.context_tag, double precision[])
  from public, anon, authenticated;


-- -----------------------------------------------------------------------------
-- 4. The public names, now with the allowance in front
-- -----------------------------------------------------------------------------

create function public.request_recommendations(
  p_mood    public.mood_tag,
  p_context public.context_tag default null,
  p_limit   integer            default 3,
  p_genres  text[]             default '{}',
  p_tz      text               default null
)
returns setof public.room_card
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid  uuid := auth.uid();
  v_left integer;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;

  v_left := app_private.claim_pulls(v_uid, p_tz);

  return query
    select * from app_private.deal_recommendations(
      p_mood, p_context, least(coalesce(p_limit, 3), v_left), p_genres
    );
end;
$$;

create function public.commit_recommendations(
  p_submission_ids uuid[],
  p_mood           public.mood_tag,
  p_context        public.context_tag default null,
  p_scores         double precision[] default null,
  p_tz             text               default null
)
returns setof public.room_card
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid  uuid := auth.uid();
  v_left integer;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;

  -- Nothing asked for costs nothing, even once the day's cards are used up.
  if coalesce(array_length(p_submission_ids, 1), 0) = 0 then
    return;
  end if;

  v_left := app_private.claim_pulls(v_uid, p_tz);

  -- Only as many as are left today; the client listed its best first.
  return query
    select * from app_private.commit_recommendations_unchecked(
      p_submission_ids[1:v_left], p_mood, p_context, p_scores[1:v_left]
    );
end;
$$;

revoke execute on function public.request_recommendations(public.mood_tag, public.context_tag, integer, text[], text) from public, anon;
grant  execute on function public.request_recommendations(public.mood_tag, public.context_tag, integer, text[], text) to authenticated;

revoke execute on function public.commit_recommendations(uuid[], public.mood_tag, public.context_tag, double precision[], text) from public, anon;
grant  execute on function public.commit_recommendations(uuid[], public.mood_tag, public.context_tag, double precision[], text) to authenticated;

commit;

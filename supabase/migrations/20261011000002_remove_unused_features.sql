-- =============================================================================
-- Vinyl — 0018: remove the features we decided not to ship
-- Owner: Ivan (guangyu11)  |  Sprint 3
--
-- DesignDecision.md §13 listed schema left over from features that were never
-- built into the app. Nothing calls any of it, and leaving it in place suggests
-- it works. This removes three of them:
--
--   Reactions      the reactions table, reaction_kind, add_reaction(),
--                  get_reactions(), and reaction_count on room_card and on
--                  get_my_submissions(). No screen ever used them; the live
--                  table was empty.
--
--   Context        context_tag ("studying", "commuting"...), the context columns
--                  on submissions and recommendations, p_context on
--                  submit_song() and request_recommendations(), and context on
--                  room_card. The app never sent one, so the matcher's context
--                  term always scored zero.
--
--   Split matcher  get_candidates(), get_genre_weights(), commit_recommendations()
--                  and candidate_card: ranking on the device, which the app
--                  never switched to. The matcher the app uses is the server's
--                  (app_private.deal_recommendations).
--
-- NOTHING THAT RUNS CHANGES
--
-- Every function that stays is re-created from its current body with only the
-- removed parts cut: the same scoring weights (minus context's, which was always
-- zero), the same eligibility rules, the same one-pull-a-day limit, the same
-- envelope styles and preview URLs. get_room() and get_shelf() are untouched;
-- they return room_card through room_cards().
--
-- OLDER APP BUILDS
--
-- Builds from before this send p_context, which these functions no longer
-- accept, so their sends and pulls would fail. Deploy this only once the
-- matching app change is merged, and rebuild from main.
--
-- Re-runnable: every drop names its exact signature and uses IF EXISTS.
-- =============================================================================

begin;

-- -----------------------------------------------------------------------------
-- 1. Split matcher
-- -----------------------------------------------------------------------------

drop function if exists public.commit_recommendations(uuid[], public.mood_tag, public.context_tag, double precision[], text);
drop function if exists app_private.commit_recommendations_unchecked(uuid[], public.mood_tag, public.context_tag, double precision[]);
drop function if exists public.get_candidates(integer);
drop function if exists app_private.candidate_cards(uuid, integer);
drop function if exists public.get_genre_weights();
drop type if exists public.candidate_card;


-- -----------------------------------------------------------------------------
-- 2. Reactions — the table takes its policies, trigger and indexes with it
-- -----------------------------------------------------------------------------

drop function if exists public.add_reaction(uuid, public.reaction_kind);
drop function if exists public.get_reactions(uuid);
drop table if exists public.reactions;
drop function if exists public.reject_self_reaction();
drop type if exists public.reaction_kind;


-- -----------------------------------------------------------------------------
-- 3. room_card loses context and reaction_count
--
-- The dealer and the pull change signature (no p_context), so they are dropped
-- and created again. room_cards() keeps its signature; only its select list
-- shrinks to match the type.
-- -----------------------------------------------------------------------------

drop function if exists public.request_recommendations(public.mood_tag, public.context_tag, integer, text[], text);
drop function if exists public.request_recommendations(public.mood_tag, integer, text[], text);
drop function if exists app_private.deal_recommendations(public.mood_tag, public.context_tag, integer, text[]);
drop function if exists app_private.deal_recommendations(public.mood_tag, integer, text[]);

alter type public.room_card
  drop attribute if exists context,
  drop attribute if exists reaction_count;

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
    s.genres,
    s.lat,
    s.lng,
    s.created_at,
    t.title,
    t.artist,
    t.album,
    t.artwork_url,
    t.preview_url,
    (sh.owner_id is not null)
  from public.recommendations r
  join public.submissions s on s.id = r.submission_id
  join public.tracks      t on t.id = s.track_id
  left join public.shelf_items sh
    on sh.submission_id = s.id and sh.owner_id = p_uid
  where r.recipient_id = p_uid
    and (p_rec_ids is null or r.id = any (p_rec_ids))
    and (not p_saved_only or sh.owner_id is not null)
  order by (case when p_saved_only then sh.saved_at else r.created_at end) desc
  limit greatest(coalesce(p_limit, 100), 1);
$$;

create function app_private.deal_recommendations(
  p_mood   public.mood_tag,
  p_limit  integer default 3,
  p_genres text[]  default '{}'
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

  -- See 0011 for why each weight is what it is.
  w_mood     constant double precision := 3.0;
  w_genre    constant double precision := 2.0;   -- now a hard ceiling, see idf
  w_fresh    constant double precision := 1.5;
  w_crowded  constant double precision := 0.8;
  gravity    constant double precision := 1.2;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;

  p_limit := least(greatest(coalesce(p_limit, 3), 1), 10);

  -- Today's chips win; none picked falls back to the onboarding taste.
  v_genres := case
                when coalesce(array_length(p_genres, 1), 0) > 0 then p_genres
                else (select pr.favorite_genres from public.profiles pr where pr.id = v_uid)
              end;
  v_genres := coalesce(v_genres, '{}');

  select count(*)::integer into v_pool
  from public.submissions
  where is_active;

  with eligible as (
    select s.id, s.track_id, s.genres, s.mood, s.created_at
    from public.submissions s
    where s.is_active
      and s.sender_id <> v_uid
      and not exists (
        select 1 from public.recommendations r
        where r.recipient_id = v_uid
          and r.submission_id = s.id
      )
  ),

  -- Normalised idf, in (0, 1]. 1.0 is a genre only one record carries.
  idf as (
    select g as slug,
           ln(1 + v_pool::double precision / greatest(count(*), 1))
             / ln(1 + greatest(v_pool, 1)::double precision) as weight
    from public.submissions s, unnest(s.genres) as g
    where s.is_active
    group by g
  ),

  scored as (
    select
      e.id,
      t.artist,
      (
        (case when e.mood = p_mood then w_mood else 0 end)

        -- At most w_genre: every weight is <= 1 and the sum is divided by
        -- the number of genres picked.
        + w_genre * coalesce((
            select sum(i.weight)
            from unnest(e.genres) as g
            join idf i on i.slug = g
            where g = any (v_genres)
          ), 0) / greatest(array_length(v_genres, 1), 1)

        + w_fresh / power(
            extract(epoch from (now() - e.created_at)) / 86400.0 + 2.0,
            gravity
          )

        - w_crowded * ln(1 + (
            select count(*)
            from public.recommendations r
            where r.submission_id = e.id
          ))
      ) as score
    from eligible e
    join public.tracks t on t.id = e.track_id
  ),

  deduped as (
    select distinct on (s.artist) s.id, s.score
    from scored s
    order by s.artist, s.score desc, random()
  ),

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
    insert into public.recommendations (recipient_id, submission_id, mood, score)
    select v_uid, c.id, p_mood, c.score
    from chosen c
    on conflict (recipient_id, submission_id) do nothing
    returning id
  )
  select coalesce(array_agg(id), '{}'::uuid[]) into v_new from inserted;

  return query
    select * from app_private.room_cards(v_uid, v_new, p_limit, false);
end;
$$;

create function public.request_recommendations(
  p_mood   public.mood_tag,
  p_limit  integer default 3,
  p_genres text[]  default '{}',
  p_tz     text    default null
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
      p_mood, least(coalesce(p_limit, 3), v_left), p_genres
    );
end;
$$;


-- -----------------------------------------------------------------------------
-- 4. Context everywhere else
-- -----------------------------------------------------------------------------

drop function if exists public.get_my_submissions(integer);

create function public.get_my_submissions(p_limit integer default 50)
returns table (
  submission_id  uuid,
  message        text,
  mood           public.mood_tag,
  is_active      boolean,
  created_at     timestamptz,
  track_title    text,
  track_artist   text,
  artwork_url    text,
  preview_url    text
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
    select s.id, s.message, s.mood, s.is_active, s.created_at,
           t.title, t.artist, t.artwork_url,
           t.preview_url
    from public.submissions s
    join public.tracks t on t.id = s.track_id
    where s.sender_id = v_uid
    order by s.created_at desc
    limit least(greatest(coalesce(p_limit, 50), 1), 200);
end;
$$;

drop function if exists public.submit_song(
  text, text, text, text, text, public.mood_tag, text, text, text,
  integer, text[], public.context_tag, text[], boolean, text
);
drop function if exists public.submit_song(
  text, text, text, text, text, public.mood_tag, text, text, text,
  integer, text[], text[], boolean, text
);

create function public.submit_song(
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
    sender_id, track_id, message, mood, genres, lat, lng, envelope_style
  )
  values (
    v_uid, v_track_id, btrim(p_message), p_mood,
    coalesce(p_genres, '{}'), v_lat, v_lng, v_style
  )
  returning id into v_id;

  return v_id;
end;
$$;

-- The matching index covered (mood, context); it goes with the column and comes
-- back on mood alone, so pulls are no slower.
drop index if exists public.submissions_match_idx;
alter table public.submissions drop column if exists context;
alter table public.recommendations drop column if exists context;
create index if not exists submissions_match_idx on public.submissions (mood) where is_active;

drop type if exists public.context_tag;


-- -----------------------------------------------------------------------------
-- 5. Grants, as before
-- -----------------------------------------------------------------------------

revoke execute on function app_private.deal_recommendations(public.mood_tag, integer, text[])
  from public, anon, authenticated;

revoke execute on function public.request_recommendations(public.mood_tag, integer, text[], text) from public, anon;
grant  execute on function public.request_recommendations(public.mood_tag, integer, text[], text) to authenticated;

revoke execute on function public.get_my_submissions(integer) from public, anon;
grant  execute on function public.get_my_submissions(integer) to authenticated;

revoke execute on function public.submit_song(
  text, text, text, text, text, public.mood_tag, text, text, text, integer, text[], text[], boolean, text
) from public, anon;
grant  execute on function public.submit_song(
  text, text, text, text, text, public.mood_tag, text, text, text, integer, text[], text[], boolean, text
) to authenticated;

commit;

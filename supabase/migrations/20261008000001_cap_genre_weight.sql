-- =============================================================================
-- Vinyl — 0014: cap the genre term so it can never outweigh mood
-- Owner: Ivan (guangyu11)  |  Sprint 3
--
-- THE PROBLEM
--
-- 0011 weights a genre match by its raw idf, ln(1 + N / df). With 24 records
-- and one genre picked, a match on a genre carried by six or fewer records
-- scored more than a mood match (2.0 × 1.61 = 3.22 > 3.0). That was tolerable
-- while only onboarding favourites reached the matcher. Now that the daily
-- genre chips are sent too, people will often pick a single genre, and genre
-- would quietly start outranking the question the user actually answered.
--
-- THE FIX
--
-- Divide every idf by the largest idf possible in this pool — that of a genre
-- carried by exactly one record:
--
--     weight(g) = ln(1 + N / df(g)) / ln(1 + N)
--
-- Every weight now sits in (0, 1], so the genre term is at most w_genre = 2.0
-- and mood (3.0) always wins. Rarer genres still weigh more: the order of the
-- weights is unchanged, only their scale.
--
-- The denominator is the theoretical maximum, not the rarest genre actually
-- present, so both paths agree: the SQL path only sees genres in use, while
-- get_genre_weights() also lists unused ones.
--
-- N is floored at 1 so an empty pool divides by ln 2, not ln 1 = 0.
--
-- get_genre_weights() changes meaning (0..1 instead of raw idf). Nothing calls
-- it yet, and Matchmaker.kt expects the normalised values.
--
-- Signatures and return types are unchanged, so no client breaks.
-- Idempotent: safe to re-run.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. request_recommendations() — same as 0011 except the idf CTE
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

  -- See 0011 for why each weight is what it is.
  w_mood     constant double precision := 3.0;
  w_genre    constant double precision := 2.0;   -- now a hard ceiling, see idf
  w_context  constant double precision := 1.0;
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
    select s.id, s.track_id, s.genres, s.mood, s.context, s.created_at
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

        + (case when p_context is not null and e.context = p_context
                then w_context else 0 end)

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
    insert into public.recommendations (recipient_id, submission_id, mood, context, score)
    select v_uid, c.id, p_mood, p_context, c.score
    from chosen c
    on conflict (recipient_id, submission_id) do nothing
    returning id
  )
  select coalesce(array_agg(id), '{}'::uuid[]) into v_new from inserted;

  return query
    select * from app_private.room_cards(v_uid, v_new, p_limit, false);
end;
$$;


-- -----------------------------------------------------------------------------
-- 2. get_genre_weights() — the same normalised idf, for the split path
-- -----------------------------------------------------------------------------

create or replace function public.get_genre_weights()
returns table (slug text, weight double precision)
language sql
security definer
stable
set search_path = ''
as $$
  with pool as (
    select count(*)::double precision as n
    from public.submissions
    where is_active
  )
  select
    g.slug,
    ln(1 + (select n from pool) / greatest(count(s.id), 1))
      / ln(1 + greatest((select n from pool), 1)) as weight
  from public.genres g
  left join public.submissions s
    on s.is_active and g.slug = any (s.genres)
  where g.is_active
  group by g.slug
  order by g.slug;
$$;


-- -----------------------------------------------------------------------------
-- 3. Grants — create or replace keeps existing grants, but re-assert them so
--    this file is correct on its own.
-- -----------------------------------------------------------------------------

revoke execute on function public.request_recommendations(public.mood_tag, public.context_tag, integer, text[]) from public, anon;
grant  execute on function public.request_recommendations(public.mood_tag, public.context_tag, integer, text[]) to authenticated;

revoke execute on function public.get_genre_weights() from public, anon;
grant  execute on function public.get_genre_weights() to authenticated;

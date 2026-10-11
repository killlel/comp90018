-- =============================================================================
-- Vinyl — 0019: mark the wildcard card in each hand
-- Owner: Ivan (guangyu11)  |  Sprint 3
--
-- Each pull deals the top two matches plus one card picked at random from the
-- rest (the "explore" slot, matching.md §4). The app wants to tell the user
-- which one that is, so it can say "a wildcard, picked at random". Nothing
-- recorded it, and it can't be worked out afterwards: scores can tie.
--
--   * recommendations.is_wildcard  true for the random pick, false otherwise;
--                                  every card dealt before this is false
--   * room_card.wildcard           the same flag, last, so a card says so
--
-- The dealer is otherwise unchanged: the same scores, the same picks, the same
-- random draws. Only what it writes down grows by one column.
--
-- Older app builds ignore the extra room_card field (ignoreUnknownKeys), so
-- this is safe to deploy before or after the app.
--
-- Re-runnable.
-- =============================================================================

begin;

alter table public.recommendations
  add column if not exists is_wildcard boolean not null default false;

comment on column public.recommendations.is_wildcard is
  'True for the card picked at random to fill the last slot of a hand (the explore slot).';

do $$ begin
  if not exists (
    select 1 from pg_attribute
    where attrelid = 'public.room_card'::regclass and attname = 'wildcard' and not attisdropped
  ) then
    alter type public.room_card add attribute wildcard boolean;
  end if;
end $$;

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
    (sh.owner_id is not null),
    r.is_wildcard
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

create or replace function app_private.deal_recommendations(
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
    select id, score, false as wildcard from exploit
    union
    select id, score, true from explore
  ),

  inserted as (
    insert into public.recommendations (recipient_id, submission_id, mood, score, is_wildcard)
    select v_uid, c.id, p_mood, c.score, c.wildcard
    from chosen c
    on conflict (recipient_id, submission_id) do nothing
    returning id
  )
  select coalesce(array_agg(id), '{}'::uuid[]) into v_new from inserted;

  return query
    select * from app_private.room_cards(v_uid, v_new, p_limit, false);
end;
$$;

revoke execute on function app_private.deal_recommendations(public.mood_tag, integer, text[])
  from public, anon, authenticated;

commit;

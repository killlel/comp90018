-- =============================================================================
-- Vinyl — 0015: sent records carry their song preview
-- Owner: Ivan (guangyu11)  |  Sprint 3
--
-- THE PROBLEM
--
-- A record you sent couldn't be played from the Collection: its music card had
-- no preview to play, while the person who received it could play it. The
-- recipient's cards come from room_card, which has always included
-- tracks.preview_url; get_my_submissions() (0003) never selected it.
--
-- THE FIX
--
-- Return preview_url from get_my_submissions(), as the last column so the
-- existing columns keep their order.
--
-- A function's result columns can't be changed in place, so it is dropped and
-- created again, with the same grants as 0003. App builds from before this
-- ignore the extra field, so it is safe to deploy ahead of the app.
-- =============================================================================

begin;

drop function if exists public.get_my_submissions(integer);

create function public.get_my_submissions(p_limit integer default 50)
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
  reaction_count integer,
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
    select s.id, s.message, s.mood, s.context, s.is_active, s.created_at,
           t.title, t.artist, t.artwork_url,
           coalesce(rc.total, 0),
           t.preview_url
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

revoke execute on function public.get_my_submissions(integer) from public, anon;
grant execute on function public.get_my_submissions(integer) to authenticated;

commit;

-- =============================================================================
-- Vinyl — schema smoke test
-- Owner: Ivan (guangyu11)  |  Sprint 1
--
-- Exercises CRUD against every table as a real signed-in user would, and proves
-- the privacy guarantee the app is built on: a recipient can never read the
-- sender's identity.
--
-- It impersonates users by setting the same JWT claim that PostgREST sets, then
-- switching to the `authenticated` role — so RLS applies exactly as it does to
-- the Android client.
--
-- Prerequisites: run every migration, then seed.sql. At least 2 profiles and
-- 1 submission must exist.
--
-- The whole thing runs inside a transaction that is ROLLED BACK at the end, so
-- it leaves no data behind. Paste the entire file into the Supabase SQL editor,
-- or: psql "$DATABASE_URL" -f supabase/tests/smoke_test.sql
--
-- Success looks like a run with no ERROR and a final "ALL CHECKS PASSED" notice.
-- =============================================================================

begin;

do $$
declare
  v_a        uuid;      -- the recipient under test
  v_b        uuid;      -- the sender of the record A gets
  v_sub      uuid;      -- a submission in A's room
  v_own_cnt  integer;
  v_cnt      integer;
  v_leaked   integer;
  v_lat      double precision;
  v_lng      double precision;
  v_name     text;       -- generated username before a reroll
  v_name2    text;       -- and after
  v_dealt    integer;    -- cards CHECK 2 dealt to A
  v_left     integer;
  v_refused  boolean;
  v_from     timestamptz;
  v_to       timestamptz;
begin
  -- ---------------------------------------------------------------------
  -- Setup (as the migration role — RLS is bypassed here on purpose)
  -- ---------------------------------------------------------------------
  select p.id into v_a
  from public.profiles p
  where exists (
    select 1 from public.submissions s
    where s.sender_id <> p.id and s.is_active
  )
  order by p.created_at
  limit 1;

  if v_a is null then
    raise exception
      'SETUP FAILED: need a profile plus at least one active submission from a different user. Run seed.sql first.';
  end if;

  select count(*)::integer into v_own_cnt
  from public.submissions where sender_id = v_a;

  raise notice 'Testing as recipient %', v_a;

  -- ---------------------------------------------------------------------
  -- Structural check: the anonymised card type must not carry an identity
  -- ---------------------------------------------------------------------
  select count(*)::integer into v_leaked
  from pg_attribute a
  join pg_class c on c.oid = a.attrelid
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public'
    and c.relname = 'room_card'
    and a.attnum > 0
    and (a.attname like '%sender%' or a.attname like '%reactor%' or a.attname = 'user_id');

  if v_leaked > 0 then
    raise exception 'CHECK 1 FAILED: public.room_card exposes an identity column';
  end if;
  raise notice 'CHECK 1 ok — room_card carries no sender/reactor identity';

  -- One pull a day (0016) looks at the cards dealt since 06:00. The test
  -- shouldn't depend on whether this account already pulled today, so its
  -- recent cards are pushed back two days — undone by the final rollback.
  update public.recommendations
     set created_at = created_at - interval '2 days'
   where recipient_id = v_a
     and created_at > now() - interval '2 days';

  -- ---------------------------------------------------------------------
  -- Become user A
  -- ---------------------------------------------------------------------
  perform set_config(
    'request.jwt.claims',
    json_build_object('sub', v_a, 'role', 'authenticated')::text,
    true
  );
  execute 'set local role authenticated';

  if auth.uid() <> v_a then
    raise exception 'SETUP FAILED: impersonation did not take effect (auth.uid() = %)', auth.uid();
  end if;

  -- ---------------------------------------------------------------------
  -- CHECK 2 — matchmaking returns a room
  -- ---------------------------------------------------------------------
  select count(*)::integer into v_cnt
  from public.request_recommendations('calm'::public.mood_tag, 3);

  if v_cnt = 0 then
    raise exception 'CHECK 2 FAILED: request_recommendations() returned nothing (is the pool empty?)';
  end if;
  raise notice 'CHECK 2 ok — request_recommendations() returned % card(s)', v_cnt;
  v_dealt := v_cnt;

  -- ---------------------------------------------------------------------
  -- CHECK 3 — the room persists and is readable
  -- ---------------------------------------------------------------------
  select submission_id into v_sub from public.get_room(3) limit 1;

  if v_sub is null then
    raise exception 'CHECK 3 FAILED: get_room() returned no rows after a successful match';
  end if;
  raise notice 'CHECK 3 ok — get_room() replays the room, sample submission %', v_sub;

  -- ---------------------------------------------------------------------
  -- CHECK 4 — direct table reads leak nothing
  -- ---------------------------------------------------------------------
  select count(*)::integer into v_cnt from public.submissions;
  if v_cnt <> v_own_cnt then
    raise exception
      'CHECK 4 FAILED: recipient can read % submission rows directly, expected only their own (%)',
      v_cnt, v_own_cnt;
  end if;
  raise notice 'CHECK 4 ok — direct select on submissions returns own rows only (%)', v_cnt;

  -- ---------------------------------------------------------------------
  -- CHECK 5 — a client cannot hand itself a match
  -- ---------------------------------------------------------------------
  begin
    insert into public.recommendations (recipient_id, submission_id, mood)
    values (v_a, v_sub, 'happy');
    raise exception 'CHECK 5 FAILED: client inserted into recommendations directly';
  exception
    when insufficient_privilege then
      raise notice 'CHECK 5 ok — direct insert into recommendations rejected by RLS';
  end;

  -- ---------------------------------------------------------------------
  -- CHECK 6 — shelf create + read
  -- ---------------------------------------------------------------------
  insert into public.shelf_items (owner_id, submission_id, note)
  values (v_a, v_sub, 'smoke test')
  on conflict (owner_id, submission_id) do nothing;

  select count(*)::integer into v_cnt from public.get_shelf(50);
  if v_cnt < 1 then
    raise exception 'CHECK 6 FAILED: get_shelf() did not return the item just saved';
  end if;
  raise notice 'CHECK 6 ok — shelf insert + get_shelf() returned % item(s)', v_cnt;

  -- ---------------------------------------------------------------------
  -- Become the SENDER of that record
  -- ---------------------------------------------------------------------
  execute 'reset role';

  select s.sender_id into v_b from public.submissions s where s.id = v_sub;

  perform set_config(
    'request.jwt.claims',
    json_build_object('sub', v_b, 'role', 'authenticated')::text,
    true
  );
  execute 'set local role authenticated';

  -- ---------------------------------------------------------------------
  -- CHECK 7 — update_my_location() stores a COARSE location
  -- A precise coordinate must never reach disk, even if the client sends one.
  -- ---------------------------------------------------------------------
  perform public.update_my_location(-37.7987654, 144.9612345);

  select p.lat, p.lng into v_lat, v_lng
  from public.profiles p where p.id = v_b;

  if v_lat <> -37.80 or v_lng <> 144.96 then
    raise exception 'CHECK 7 FAILED: profile location stored as %,% — expected 2dp rounding', v_lat, v_lng;
  end if;
  raise notice 'CHECK 7 ok — update_my_location() rounded %,% to %,%',
    -37.7987654, 144.9612345, v_lat, v_lng;

  -- ---------------------------------------------------------------------
  -- CHECK 8 — submit_song() snapshots location from the sender's PROFILE
  -- The client never sends coordinates, so it cannot misreport them.
  -- ---------------------------------------------------------------------
  select public.submit_song(
    p_provider          => 'manual',
    p_provider_track_id => 'smoke-test-track',
    p_title             => 'Test Pressing',
    p_artist            => 'Smoke Test',
    p_message           => 'Written by the smoke test, rolled back immediately.',
    p_mood              => 'hopeful'::public.mood_tag,
    p_genres            => array['rock', 'shoegaze'],
    p_attach_location   => true
  ) into v_sub;

  select count(*)::integer into v_cnt
  from public.submissions
  where id = v_sub and lat = -37.80 and lng = 144.96;

  if v_cnt <> 1 then
    raise exception 'CHECK 8 FAILED: submission did not inherit the profile location';
  end if;
  raise notice 'CHECK 8 ok — submission % snapshotted the sender profile location', v_sub;

  -- ---------------------------------------------------------------------
  -- CHECK 9 — opting out leaves the record with no location
  -- ---------------------------------------------------------------------
  select public.submit_song(
    p_provider          => 'manual',
    p_provider_track_id => 'smoke-test-track-3',
    p_title             => 'No Location',
    p_artist            => 'Smoke Test',
    p_message           => 'Sent without a location.',
    p_mood              => 'calm'::public.mood_tag,
    p_attach_location   => false
  ) into v_sub;

  select count(*)::integer into v_cnt
  from public.submissions
  where id = v_sub and lat is null and lng is null;

  if v_cnt <> 1 then
    raise exception 'CHECK 9 FAILED: location was attached despite p_attach_location => false';
  end if;
  raise notice 'CHECK 9 ok — opting out stored no coordinates';

  -- ---------------------------------------------------------------------
  -- CHECK 10 — clearing location works (user revokes permission)
  -- ---------------------------------------------------------------------
  perform public.update_my_location();

  select count(*)::integer into v_cnt
  from public.profiles
  where id = v_b and lat is null and lng is null;

  if v_cnt <> 1 then
    raise exception 'CHECK 10 FAILED: update_my_location() with no arguments did not clear the location';
  end if;
  raise notice 'CHECK 10 ok — location cleared on request';

  -- ---------------------------------------------------------------------
  -- CHECK 11 — the genre vocabulary is readable and non-empty
  -- ---------------------------------------------------------------------
  select count(*)::integer into v_cnt from public.genres where is_active;
  if v_cnt < 1 then
    raise exception 'CHECK 11 FAILED: public.genres is empty or unreadable';
  end if;
  raise notice 'CHECK 11 ok — % active genres readable by the app', v_cnt;

  -- ---------------------------------------------------------------------
  -- CHECK 12 — an unknown genre is rejected
  -- ---------------------------------------------------------------------
  begin
    perform public.submit_song(
      p_provider          => 'manual',
      p_provider_track_id => 'smoke-test-track-4',
      p_title             => 'Bad Genre',
      p_artist            => 'Smoke Test',
      p_message           => 'This should not be stored.',
      p_mood              => 'happy'::public.mood_tag,
      p_genres            => array['definitely_not_a_genre']
    );
    raise exception 'CHECK 12 FAILED: an unknown genre was accepted';
  exception
    when check_violation then
      raise notice 'CHECK 12 ok — unknown genre rejected';
  end;

  -- ---------------------------------------------------------------------
  -- CHECK 13 — validation rejects a blank message
  -- ---------------------------------------------------------------------
  begin
    perform public.submit_song(
      p_provider          => 'manual',
      p_provider_track_id => 'smoke-test-track-5',
      p_title             => 'No Message',
      p_artist            => 'Smoke Test',
      p_message           => '   ',
      p_mood              => 'happy'::public.mood_tag
    );
    raise exception 'CHECK 13 FAILED: an empty message was accepted';
  exception
    when invalid_parameter_value then
      raise notice 'CHECK 13 ok — empty message rejected';
  end;

  -- ---------------------------------------------------------------------
  -- CHECK 14 — favorite_genres accepts the three legal states
  -- ---------------------------------------------------------------------
  update public.profiles set favorite_genres = array['jazz','soul'] where id = v_b;
  update public.profiles set favorite_genres = '{}'                  where id = v_b;
  update public.profiles set favorite_genres = null                  where id = v_b;

  select count(*)::integer into v_cnt
  from public.profiles where id = v_b and favorite_genres is null;

  if v_cnt <> 1 then
    raise exception 'CHECK 14 FAILED: favorite_genres did not round-trip its three states';
  end if;
  raise notice 'CHECK 14 ok — favorite_genres accepts null, {} and a slug list';

  -- ---------------------------------------------------------------------
  -- CHECK 15 — a display label is rejected, so the column cannot drift
  --            from the vocabulary the matchmaker reads
  -- ---------------------------------------------------------------------
  begin
    update public.profiles set favorite_genres = array['K-pop'] where id = v_b;
    raise exception 'CHECK 15 FAILED: a display label was accepted as a genre slug';
  exception
    when check_violation then
      raise notice 'CHECK 15 ok — display label rejected, slugs only';
  end;

  -- ---------------------------------------------------------------------
  -- CHECK 16 — another user's taste is not readable
  -- ---------------------------------------------------------------------
  select count(*)::integer into v_cnt
  from public.profiles where id <> v_b;

  if v_cnt <> 0 then
    raise exception 'CHECK 16 FAILED: % other profile(s) readable — taste is not private', v_cnt;
  end if;
  raise notice 'CHECK 16 ok — favorite_genres stays private to its owner';

  -- ---------------------------------------------------------------------
  -- CHECK 17 — every profile is given a generated username at signup
  -- ---------------------------------------------------------------------
  select username into v_name from public.profiles where id = v_b;

  if coalesce(btrim(v_name), '') = '' then
    raise exception 'CHECK 17 FAILED: profile has no generated username';
  end if;
  raise notice 'CHECK 17 ok — username generated at signup: %', v_name;

  -- ---------------------------------------------------------------------
  -- CHECK 18 — a client cannot rename itself
  --
  -- The lock trigger tests current_user, so a direct update from the app role
  -- is refused while reroll_username() (SECURITY DEFINER) is let through.
  -- ---------------------------------------------------------------------
  begin
    update public.profiles set username = 'Chosen By Hand' where id = v_b;
    raise exception 'CHECK 18 FAILED: a client renamed itself directly';
  exception
    when insufficient_privilege then
      raise notice 'CHECK 18 ok — direct rename refused, only the server names you';
  end;

  -- ---------------------------------------------------------------------
  -- CHECK 19 — reroll_username() issues a different name while onboarding
  --            is still open
  -- ---------------------------------------------------------------------
  select public.reroll_username() into v_name2;

  if v_name2 is null or v_name2 = v_name then
    raise exception 'CHECK 19 FAILED: reroll returned % (was %)', v_name2, v_name;
  end if;
  raise notice 'CHECK 19 ok — rerolled % to %', v_name, v_name2;

  -- ---------------------------------------------------------------------
  -- CHECK 20 — onboarding cannot be reopened, so the name stays fixed
  --
  -- Without this guard a client could flip the flag back and reroll forever.
  -- ---------------------------------------------------------------------
  update public.profiles set onboarding_completed = true where id = v_b;

  begin
    update public.profiles set onboarding_completed = false where id = v_b;
    raise exception 'CHECK 20 FAILED: a client reopened onboarding';
  exception
    when insufficient_privilege then
      raise notice 'CHECK 20 ok — onboarding is one-way, so the username is final';
  end;

  -- ---------------------------------------------------------------------
  -- CHECK 21 — an unknown avatar is rejected by the foreign key
  -- ---------------------------------------------------------------------
  begin
    update public.profiles set avatar_slug = 'not_a_real_avatar' where id = v_b;
    raise exception 'CHECK 21 FAILED: an unknown avatar_slug was accepted';
  exception
    when foreign_key_violation then
      raise notice 'CHECK 21 ok — unknown avatar rejected by the foreign key';
  end;

  -- ---------------------------------------------------------------------
  -- Back to user A for the daily limit
  -- ---------------------------------------------------------------------
  perform set_config(
    'request.jwt.claims',
    json_build_object('sub', v_a, 'role', 'authenticated')::text,
    true
  );

  -- ---------------------------------------------------------------------
  -- CHECK 22 — the pull status counts the cards dealt today
  -- ---------------------------------------------------------------------
  select dealt_today, remaining into v_cnt, v_left
  from public.get_pull_status('Australia/Melbourne');

  if v_cnt <> v_dealt or v_left <> 0 then
    raise exception 'CHECK 22 FAILED: status says % dealt, % left; CHECK 2 dealt %', v_cnt, v_left, v_dealt;
  end if;
  raise notice 'CHECK 22 ok — % card(s) dealt today, none left to pull', v_cnt;

  -- ---------------------------------------------------------------------
  -- CHECK 23 — a second pull the same day is refused
  -- ---------------------------------------------------------------------
  v_refused := false;
  begin
    perform public.request_recommendations('happy'::public.mood_tag, 3, '{}', 'Australia/Melbourne');
  exception when raise_exception then
    v_refused := sqlerrm = 'daily pull limit reached';
  end;

  select dealt_today into v_cnt from public.get_pull_status('Australia/Melbourne');
  if not v_refused or v_cnt <> v_dealt then
    raise exception 'CHECK 23 FAILED: refused = %, dealt today = %', v_refused, v_cnt;
  end if;
  raise notice 'CHECK 23 ok — second pull refused, still % dealt today', v_cnt;

  execute 'reset role';

  -- ---------------------------------------------------------------------
  -- CHECK 24 — the day turns over at 06:00 local time, not at midnight
  -- ---------------------------------------------------------------------
  select starts_at, ends_at into v_from, v_to
  from app_private.pull_window('Australia/Melbourne', '2026-10-10 05:59:00+11');
  if v_from <> '2026-10-09 06:00:00+11' or v_to <> '2026-10-10 06:00:00+11' then
    raise exception 'CHECK 24 FAILED: 05:59 fell in % to %', v_from, v_to;
  end if;

  select starts_at into v_from
  from app_private.pull_window('Australia/Melbourne', '2026-10-10 06:00:00+11');
  if v_from <> '2026-10-10 06:00:00+11' then
    raise exception 'CHECK 24 FAILED: 06:00 fell in a day starting %', v_from;
  end if;

  -- An unknown zone falls back to Melbourne rather than failing the pull.
  select starts_at into v_from
  from app_private.pull_window('Not/AZone', '2026-10-10 05:59:00+11');
  if v_from <> '2026-10-09 06:00:00+11' then
    raise exception 'CHECK 24 FAILED: unknown zone gave a day starting %', v_from;
  end if;
  raise notice 'CHECK 24 ok — 05:59 is yesterday''s cards, 06:00 is today''s';

  raise notice '=== ALL CHECKS PASSED ===';
end $$;

rollback;

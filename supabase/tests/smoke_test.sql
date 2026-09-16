-- =============================================================================
-- Vinyl — schema smoke test
-- Owner: Ivan (guangyu11)  |  Sprint 1
--
-- Exercises CRUD against every table as a real signed-in user would, and proves
-- the two privacy guarantees the app is built on:
--
--   * a recipient can never read the sender's identity
--   * a submitter can never read who reacted to their song
--
-- It impersonates users by setting the same JWT claim that PostgREST sets, then
-- switching to the `authenticated` role — so RLS applies exactly as it does to
-- the Android client.
--
-- Prerequisites: run the three migrations, then seed.sql (and optionally
-- seed_demo_users.sql). At least 2 profiles and 1 submission must exist.
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
  v_total    integer;
  v_leaked   integer;
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
  from public.request_recommendations('calm'::public.mood_tag, 'studying'::public.context_tag, 3);

  if v_cnt = 0 then
    raise exception 'CHECK 2 FAILED: request_recommendations() returned nothing (is the pool empty?)';
  end if;
  raise notice 'CHECK 2 ok — request_recommendations() returned % card(s)', v_cnt;

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
  -- CHECK 7 — reacting, twice, is idempotent rather than an error
  -- ---------------------------------------------------------------------
  select public.add_reaction(v_sub, 'heart'::public.reaction_kind) into v_total;
  select public.add_reaction(v_sub, 'fire'::public.reaction_kind)  into v_total;
  raise notice 'CHECK 7 ok — reaction recorded and updated, total now %', v_total;

  select count(*)::integer into v_cnt
  from public.reactions where submission_id = v_sub;
  if v_cnt <> 1 then
    raise exception 'CHECK 7 FAILED: expected exactly 1 reaction row visible to the reactor, got %', v_cnt;
  end if;

  -- ---------------------------------------------------------------------
  -- CHECK 8 — A cannot read reactions to someone else's submission
  -- ---------------------------------------------------------------------
  begin
    perform * from public.get_reactions(v_sub);
    raise exception 'CHECK 8 FAILED: non-owner was able to call get_reactions()';
  exception
    when insufficient_privilege then
      raise notice 'CHECK 8 ok — get_reactions() refuses a non-owner';
  end;

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
  -- CHECK 9 — THE ANONYMITY GUARANTEE
  -- The submitter must not be able to read the reactions table at all.
  -- ---------------------------------------------------------------------
  select count(*)::integer into v_leaked
  from public.reactions where submission_id = v_sub;

  if v_leaked <> 0 then
    raise exception
      'CHECK 9 FAILED: submitter can read % reaction row(s) — reactor identity is exposed', v_leaked;
  end if;
  raise notice 'CHECK 9 ok — submitter is locked out of the reactions table';

  -- ---------------------------------------------------------------------
  -- CHECK 10 — but they can still see anonymous counts
  -- ---------------------------------------------------------------------
  select coalesce(sum(total), 0)::integer into v_total
  from public.get_reactions(v_sub);

  if v_total < 1 then
    raise exception 'CHECK 10 FAILED: get_reactions() returned no counts to the submitter';
  end if;
  raise notice 'CHECK 10 ok — submitter sees % anonymous reaction(s)', v_total;

  -- ---------------------------------------------------------------------
  -- CHECK 11 — submitting a song end to end
  -- ---------------------------------------------------------------------
  select public.submit_song(
    p_provider          => 'manual',
    p_provider_track_id => 'smoke-test-track',
    p_title             => 'Test Pressing',
    p_artist            => 'Smoke Test',
    p_message           => 'Written by the smoke test, rolled back immediately.',
    p_mood              => 'hopeful'::public.mood_tag,
    p_context           => 'studying'::public.context_tag,
    p_lat               => -37.7987654,
    p_lng               => 144.9612345
  ) into v_sub;

  select count(*)::integer into v_cnt
  from public.submissions
  where id = v_sub and lat = -37.80 and lng = 144.96;

  if v_cnt <> 1 then
    raise exception 'CHECK 11 FAILED: submit_song() did not store a coarse (2dp) location';
  end if;
  raise notice 'CHECK 11 ok — submit_song() stored submission % with rounded coordinates', v_sub;

  -- ---------------------------------------------------------------------
  -- CHECK 12 — validation rejects a blank message
  -- ---------------------------------------------------------------------
  begin
    perform public.submit_song(
      p_provider          => 'manual',
      p_provider_track_id => 'smoke-test-track-2',
      p_title             => 'No Message',
      p_artist            => 'Smoke Test',
      p_message           => '   ',
      p_mood              => 'happy'::public.mood_tag
    );
    raise exception 'CHECK 12 FAILED: an empty message was accepted';
  exception
    when invalid_parameter_value then
      raise notice 'CHECK 12 ok — empty message rejected';
  end;

  execute 'reset role';
  raise notice '=== ALL CHECKS PASSED ===';
end $$;

rollback;

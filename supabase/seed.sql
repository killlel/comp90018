-- =============================================================================
-- Vinyl — seed data
-- Owner: Ivan (guangyu11)  |  Sprint 1
--
-- 24 submissions spread across every mood, so that:
--   * request_recommendations() returns something on a fresh database,
--   * Natalie's onboarding questionnaire has a non-empty first result set,
--   * the Sprint 2 matchmaker has data to tune against.
--
-- Submissions are attributed to whichever profiles already exist, because a
-- submission needs a real auth.users row behind it. Have every team member sign
-- in once BEFORE running this — the matchmaker never recommends you your own
-- song, so with only one account signed in that account sees an empty room.
--
-- If you want a larger pool of "strangers", run seed_demo_users.sql first.
--
-- Safe to re-run: it will not duplicate submissions.
-- =============================================================================

do $$
declare
  v_senders uuid[];
  v_n       integer;
  v_added   integer;
begin
  select coalesce(array_agg(id order by created_at), '{}'::uuid[])
    into v_senders
  from public.profiles;

  v_n := coalesce(array_length(v_senders, 1), 0);

  if v_n = 0 then
    raise notice 'No profiles found. Sign in from the app at least once, then re-run this seed.';
    return;
  end if;

  if v_n = 1 then
    raise notice 'Only 1 profile exists. Seeded songs will all belong to that user, who will therefore see an empty record room. Get the rest of the team to sign in.';
  end if;

  create temp table _seed (
    tid    text,
    title  text,
    artist text,
    album  text,
    genres text[],
    mood   public.mood_tag,
    ctx    public.context_tag,
    msg    text
  ) on commit drop;

  insert into _seed (tid, title, artist, album, genres, mood, ctx, msg) values
    ('seed-01', 'Lovely Day',            'Bill Withers',       'Menagerie',                    '{soul,funk}',        'happy',      'commuting',   'Play this on the tram and try not to smile. Impossible.'),
    ('seed-02', 'September',             'Earth, Wind & Fire', 'The Best of EWF Vol. 1',       '{funk,disco}',       'happy',      'celebrating', 'Whatever you are celebrating, it deserves horns.'),
    ('seed-03', 'Good Day Sunshine',     'The Beatles',        'Revolver',                     '{rock,pop}',         'happy',      'relaxing',    'Two minutes of unearned optimism. You are welcome.'),
    ('seed-04', 'Motion Picture Soundtrack','Radiohead',       'Kid A',                        '{alternative}',      'sad',        'late_night',  'For the nights you want to feel it rather than fix it.'),
    ('seed-05', 're: Stacks',            'Bon Iver',           'For Emma, Forever Ago',        '{folk,indie}',       'sad',        'heartbroken', 'Someone sent this to me once. Passing it on.'),
    ('seed-06', 'The Night We Met',      'Lord Huron',         'Strange Trails',               '{indie,folk}',       'lonely',     'late_night',  'If you are up too late thinking about someone.'),
    ('seed-07', 'Nights',                'Frank Ocean',        'Blonde',                       '{rnb,hiphop}',       'lonely',     'commuting',   'The beat switch halfway through is the whole point.'),
    ('seed-08', 'Weightless',            'Marconi Union',      'Weightless',                   '{ambient}',          'calm',       'sleeping',    'Supposedly the most relaxing song ever recorded. Test it.'),
    ('seed-09', 'Clair de Lune',         'Claude Debussy',     'Suite Bergamasque',            '{classical}',        'calm',       'studying',    'For when the reading list is longer than the day.'),
    ('seed-10', 'Saturn',                'Sleeping At Last',   'Atlas: Space',                 '{indie,ambient}',    'calm',       'relaxing',    'Put headphones on for this one. It is worth the ceremony.'),
    ('seed-11', 'Blinding Lights',       'The Weeknd',         'After Hours',                  '{pop,synthpop}',     'energetic',  'working_out', 'Non-negotiable last-kilometre song.'),
    ('seed-12', 'Titanium',              'David Guetta',       'Nothing but the Beat',         '{edm,pop}',          'energetic',  'working_out', 'For the set you did not think you had in you.'),
    ('seed-13', 'Since U Been Gone',     'Kelly Clarkson',     'Breakaway',                    '{pop,rock}',         'energetic',  'partying',    'Shout the chorus. That is the instruction.'),
    ('seed-14', 'Yesterday Once More',   'Carpenters',         'Now & Then',                   '{pop,soft rock}',    'nostalgic',  'relaxing',    'My mum played this every Sunday. Now it is yours.'),
    ('seed-15', 'The Boys of Summer',    'Don Henley',         'Building the Perfect Beast',   '{rock}',             'nostalgic',  'commuting',   'Summer ending music, even in the middle of winter.'),
    ('seed-16', 'Landslide',             'Fleetwood Mac',      'Fleetwood Mac',                '{rock,folk}',        'nostalgic',  'late_night',  'Hits differently depending on how old you are.'),
    ('seed-17', 'Idioteque',             'Radiohead',          'Kid A',                        '{electronic}',       'anxious',    'studying',    'Anxiety, but with a beat you can work to.'),
    ('seed-18', 'Zombie',                'The Cranberries',    'No Need to Argue',             '{rock,alternative}', 'anxious',    'commuting',   'Loud enough to drown out whatever is in your head.'),
    ('seed-19', 'At Last',               'Etta James',         'At Last!',                     '{soul,jazz}',        'romantic',   'celebrating', 'Three minutes of someone meaning every word.'),
    ('seed-20', 'Lover',                 'Taylor Swift',       'Lover',                        '{pop}',              'romantic',   'relaxing',    'Unashamedly soft. Send it to someone.'),
    ('seed-21', 'Killing in the Name',   'Rage Against the Machine', 'Rage Against the Machine','{rock,metal}',      'angry',      'working_out', 'Volume up. You know the part.'),
    ('seed-22', 'You Oughta Know',       'Alanis Morissette',  'Jagged Little Pill',           '{rock,alternative}', 'angry',      'heartbroken', 'Better out than in.'),
    ('seed-23', 'Here Comes the Sun',    'The Beatles',        'Abbey Road',                   '{rock,pop}',         'hopeful',    'relaxing',    'It has been a long cold lonely winter. It is nearly over.'),
    ('seed-24', 'Rise Up',               'Andra Day',          'Cheers to the Fall',           '{soul,rnb}',         'hopeful',    'studying',    'For week 11, when the semester is winning.');

  insert into public.tracks (provider, provider_track_id, title, artist, album, genres)
  select 'manual', s.tid, s.title, s.artist, s.album, s.genres
  from _seed s
  on conflict (provider, provider_track_id) do nothing;

  -- Coordinates are scattered around Melbourne so the GPS distance display has
  -- something plausible to render. The BEFORE INSERT trigger rounds them to 2dp.
  with rows_to_add as (
    select
      t.id as track_id,
      s.msg, s.mood, s.ctx, s.genres,
      row_number() over (order by s.tid) as rn
    from _seed s
    join public.tracks t
      on t.provider = 'manual' and t.provider_track_id = s.tid
    where not exists (
      select 1 from public.submissions x where x.track_id = t.id
    )
  )
  insert into public.submissions (sender_id, track_id, message, mood, context, genres, lat, lng)
  select
    v_senders[1 + ((r.rn - 1)::integer % v_n)],
    r.track_id,
    r.msg,
    r.mood,
    r.ctx,
    r.genres,
    -37.81 + (random() - 0.5) * 0.6,
    144.96 + (random() - 0.5) * 0.6
  from rows_to_add r;

  get diagnostics v_added = row_count;
  raise notice 'Seed complete: % submissions added across % sender(s).', v_added, v_n;
end $$;

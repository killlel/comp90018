-- =============================================================================
-- Vinyl — seed data
-- Owner: Ivan (guangyu11)  |  Sprint 3
--
-- 60 demo records sent by 10 demo accounts, so the record room is never empty
-- and every feature has something real to show:
--
--   * every record is a real iTunes track (US storefront, like the app's search),
--     with its cover and 30-second preview — the turntable plays them;
--   * 6 records per mood, every one by a different artist, so a pull is three
--     different artists and an account can pull ~20 times before running dry;
--   * 22 genres, from common (rock, electronic) to rare (musical, avant_garde),
--     so the matchmaker's genre rarity weighting has something to work with;
--   * sent over the past 20 days, so freshness actually separates them;
--   * the senders live in 10 cities (Melbourne x2, Sydney, Brisbane, Perth,
--     Adelaide, Hobart, Auckland, Tokyo, London), so distance and the compass
--     show a real range.
--
-- The demo accounts can never sign in (no password, no linked identity). Real
-- accounts never send seed records, so everyone on the team is a recipient.
--
-- This also deletes the Sprint 1 hand-typed seed (tracks 'manual' / 'seed-NN'),
-- which had no covers or previews. Deleting those submissions cascades to any
-- room, shelf item or reaction that pointed at them. Records sent from the app
-- are never touched.
--
-- Safe to re-run: nothing is duplicated, and an existing demo account keeps
-- its generated username.
--
-- To refresh the songs: edit the VALUES list (iTunes ids, URLs and genres come
-- from https://itunes.apple.com/search?country=US&entity=song&term=...).
-- Writing to auth.users is a dev-only shortcut — never run this on production.
-- =============================================================================

begin;

-- -----------------------------------------------------------------------------
-- 1. Demo senders
--
-- public.profiles rows (and their "Adjective Animal" usernames) are created by
-- the on_auth_user_created trigger. If this errors on a NOT NULL column after a
-- Supabase upgrade, add that column to the insert; nothing else depends on it.
-- -----------------------------------------------------------------------------

insert into auth.users (
  instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
  raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
  confirmation_token, email_change, email_change_token_new, recovery_token,
  is_anonymous, is_sso_user
)
select
  '00000000-0000-0000-0000-000000000000',
  gen_random_uuid(),
  'authenticated',
  'authenticated',
  format('vinyl-demo-%s@example.invalid', n),
  '',                       -- unusable password: these accounts cannot log in
  now(),
  '{"provider":"demo","providers":["demo"]}'::jsonb,
  jsonb_build_object('full_name', format('Demo Listener %s', n), 'seed', true),
  now(), now(),
  '', '', '', '',
  false, false
from generate_series(1, 10) as n
where not exists (
  select 1 from auth.users u
  where u.email = format('vinyl-demo-%s@example.invalid', n)
);

-- A lived-in profile: a home city (submissions copy it) and an avatar if the
-- avatar set is loaded. onboarding_completed stays false on purpose: these
-- accounts never sign in, and smoke_test.sql rerolls the username of a record
-- sender, which only works while onboarding is still open.
with city (n, lat, lng) as (values
  ( 1, -37.81, 144.96),   -- Melbourne CBD
  ( 2, -37.80, 144.98),   -- Fitzroy, Melbourne
  ( 3, -33.87, 151.21),   -- Sydney
  ( 4, -27.47, 153.03),   -- Brisbane
  ( 5, -31.95, 115.86),   -- Perth
  ( 6, -34.93, 138.60),   -- Adelaide
  ( 7, -42.88, 147.33),   -- Hobart
  ( 8, -36.85, 174.76),   -- Auckland
  ( 9,  35.68, 139.69),   -- Tokyo
  (10,  51.51,  -0.13)    -- London
)
update public.profiles p
   set lat                  = c.lat,
       lng                  = c.lng,
       location_updated_at  = now(),
       avatar_slug          = (select a.slug from public.avatars a
                               where a.slug = format('icon_%s', 1 + (c.n - 1) % 8))
  from city c
  join auth.users u on u.email = format('vinyl-demo-%s@example.invalid', c.n)
 where p.id = u.id;


-- -----------------------------------------------------------------------------
-- 2. Remove the Sprint 1 hand-typed seed
-- -----------------------------------------------------------------------------

delete from public.submissions s
 using public.tracks t
 where s.track_id = t.id
   and t.provider = 'manual'
   and t.provider_track_id like 'seed-%';

delete from public.tracks t
 where t.provider = 'manual'
   and t.provider_track_id like 'seed-%'
   and not exists (select 1 from public.submissions s where s.track_id = t.id);


-- -----------------------------------------------------------------------------
-- 3. The records
--
-- track columns are stored exactly as the app stores a song it found on iTunes:
-- the 100px artwork URL (the app requests bigger sizes itself) and the iTunes
-- genre name. mood / genres / message are what the sender chose.
-- -----------------------------------------------------------------------------

create temp table _seed (
  n        integer,
  tid      text,
  title    text,
  artist   text,
  album    text,
  artwork  text,
  preview  text,
  ms       integer,
  itunes   text,
  mood     public.mood_tag,
  genres   text[],
  msg      text
) on commit drop;

insert into _seed (n, tid, title, artist, album, artwork, preview, ms, itunes, mood, genres, msg) values
    ( 1, '697195462', 'One More Time', 'Daft Punk', 'Discovery',
         'https://is1-ssl.mzstatic.com/image/thumb/Music221/v4/fd/4a/77/fd4a77db-0ebc-d043-41a2-f32fa1bb0fb4/dj.qrikkdwj.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/5d/93/d8/5d93d83f-ad1e-da4d-1d79-9937bdff24ec/mzaf_14396932211949300852.plus.aac.p.m4a',
         320357, 'Dance', 'happy', '{electronic,disco}',
         'If this doesn''t fix your day, at least it''ll fix the next four minutes.'),
    ( 2, '1440788542', 'Sir Duke', 'Stevie Wonder', 'Songs in the Key of Life',
         'https://is1-ssl.mzstatic.com/image/thumb/Music118/v4/eb/1f/12/eb1f12ec-474c-63aa-43af-09282f423b9d/00602537004737.rgb.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/19/f1/cc/19f1ccc5-f10c-8b88-ddfa-b97acf5009b2/mzaf_16382814554090706144.plus.aac.p.m4a',
         233606, 'R&B/Soul', 'happy', '{soul,funk}',
         'The horn line alone could power a small city. Turn it up.'),
    ( 3, '1440841243', 'Wouldn''t It Be Nice', 'The Beach Boys', 'Pet Sounds',
         'https://is1-ssl.mzstatic.com/image/thumb/Music116/v4/35/bb/c4/35bbc4eb-9387-97b0-b138-64b7a949ea43/13UABIM03512.rgb.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/d0/fb/61/d0fb618b-d390-e28d-73f6-473fdc789e46/mzaf_17739354464510580563.plus.aac.p.m4a',
         154773, 'Rock', 'happy', '{pop,rock}',
         'Two and a half minutes of pure daydreaming. No skipping allowed.'),
    ( 4, '270425151', 'A-Punk', 'Vampire Weekend', 'A-Punk - Single of the Week',
         'https://is1-ssl.mzstatic.com/image/thumb/Music112/v4/ba/01/17/ba01176d-e46f-aac5-e1be-4256c26c173e/634904831868.png/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/f3/0a/bd/f30abde0-5c89-7ee8-e08e-640acd639de8/mzaf_9840506936884085964.plus.aac.p.m4a',
         137760, 'Alternative', 'happy', '{indie,rock}',
         'Short, bouncy, and impossible to sit still through.'),
    ( 5, '1054525017', 'Mr. Blue Sky', 'Electric Light Orchestra', 'Out of the Blue',
         'https://is1-ssl.mzstatic.com/image/thumb/Music221/v4/2c/0f/f9/2c0ff9b3-693b-5690-a17d-641076ae3140/886445593945.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/97/1e/46/971e4694-70f7-1ce7-1296-a679baca2c7d/mzaf_8404578978997779091.plus.aac.p.m4a',
         303820, 'Rock', 'happy', '{rock,pop}',
         'Written after two weeks of grey weather. The sun came out. So will yours.'),
    ( 6, '1555318018', 'Keep Moving', 'Jungle', 'Loving In Stereo',
         'https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/1a/ef/f5/1aeff531-e697-3c07-7eaf-d209563888ad/5056167160984_1.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/05/6d/6f/056d6faf-4fde-d221-4437-ee2da3700995/mzaf_6480566883382337631.plus.aac.p.m4a',
         240880, 'Electronic', 'happy', '{funk,disco}',
         'Instant good mood. Try walking in time with it.'),
    ( 7, '1109715479', 'Videotape', 'Radiohead', 'In Rainbows',
         'https://is1-ssl.mzstatic.com/image/thumb/Music126/v4/dd/50/c7/dd50c790-99ac-d3d0-5ab8-e3891fb8fd52/634904032463.png/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/8f/04/26/8f0426ef-4d1b-c1e7-dcde-dfb868f2c52a/mzaf_6071995543397747319.plus.aac.p.m4a',
         279634, 'Alternative', 'sad', '{alternative}',
         'For when you want to sit with it for a bit instead of fixing it.'),
    ( 8, '313302072', 'Between the Bars', 'Elliott Smith', 'Either/Or',
         'https://is1-ssl.mzstatic.com/image/thumb/Music124/v4/ff/ea/28/ffea28a4-988a-f02e-7e1a-8566db3cab02/mzi.uoqucyoy.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/56/a7/df/56a7df52-3fb4-1c49-11e7-72438cb07330/mzaf_11460446142122429949.plus.aac.p.m4a',
         141200, 'Rock', 'sad', '{folk,indie}',
         'Barely above a whisper. Best heard alone, late, with the lights off.'),
    ( 9, '955572623', 'Fourth of July', 'Sufjan Stevens', 'Carrie & Lowell',
         'https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/ba/61/bc/ba61bcaf-2034-f13e-6f66-fdb578d618dc/656605609966.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/98/06/1c/98061cc2-a2cb-7d0a-e68f-8356950a73ae/mzaf_3793087860127341148.plus.aac.p.m4a',
         279316, 'Singer/Songwriter', 'sad', '{folk}',
         'Heavy, but gentle about it. Someone sent this to me when I needed it.'),
    (10, '1440758657', 'Pink Moon', 'Nick Drake', 'Pink Moon ((Remastered))',
         'https://is1-ssl.mzstatic.com/image/thumb/Music124/v4/2f/9a/a0/2f9aa0ed-18c9-5e42-4630-2637efbc876a/00042284292320.rgb.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview125/v4/a5/fd/2c/a5fd2cd9-eb7d-674a-4e09-4d936d7002ba/mzaf_2711277978859933745.plus.aac.p.m4a',
         123333, 'Singer/Songwriter', 'sad', '{folk}',
         'Two minutes, one guitar, one piano note that gets me every time.'),
    (11, '1507014130', 'andata', 'Ryuichi Sakamoto', 'async',
         'https://is1-ssl.mzstatic.com/image/thumb/Music123/v4/82/e0/7b/82e07b9a-1d98-bbf4-d1e2-fb94312bbea2/731383683060.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/3b/28/18/3b2818f5-1afd-4fcf-8251-874f1e689379/mzaf_10653528020245327894.plus.aac.p.m4a',
         278641, 'Classical Crossover', 'sad', '{classical,ambient}',
         'An organ hymn that slowly dissolves into noise. Let it finish.'),
    (12, '1689673597', 'Masterpiece', 'Big Thief', 'Masterpiece',
         'https://is1-ssl.mzstatic.com/image/thumb/Music116/v4/85/8d/4b/858d4ba6-b2bb-8d0d-da66-6b267891c954/191400064478.png/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/7c/f8/17/7cf81768-62c7-750a-3f77-34eb7ce1f49a/mzaf_977578577280424521.plus.aac.p.m4a',
         230586, 'Alternative', 'sad', '{indie,folk}',
         'About old friends and the people we used to be. Rough edges and all.'),
    (13, '724436237', '1/1', 'Brian Eno', 'Ambient 1: Music for Airports',
         'https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/ee/71/42/ee71425d-6bc9-3df8-c90b-8539f59144ab/00724386649553.rgb.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/bf/4d/76/bf4d76df-83ae-eafa-aa13-16ce3881a6b3/mzaf_6194359874510546862.plus.aac.p.m4a',
         1041520, 'Electronic', 'calm', '{ambient}',
         'Made to make waiting feel lighter. Works for most kinds of waiting.'),
    (14, '1668862649', 'Xtal', 'Aphex Twin', 'Selected Ambient Works 85-92',
         'https://is1-ssl.mzstatic.com/image/thumb/Music116/v4/5f/b3/e0/5fb3e08d-c2cd-3da4-6ad7-c5dc61803683/cover.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/a1/a9/59/a1a9593a-f705-ca02-495f-2d83f83e4e49/mzaf_282027520707936830.plus.aac.p.m4a',
         293752, 'Electronic', 'calm', '{electronic,ambient}',
         'Warm, hazy and somehow nostalgic for a place you''ve never been.'),
    (15, '1440942220', 'Waltz for Debby (Take 2)', 'Bill Evans Trio', 'Waltz for Debby (Original Jazz Classics Remasters) [with Paul Motian & Scott LaFaro]',
         'https://is1-ssl.mzstatic.com/image/thumb/Music126/v4/28/a1/59/28a1595d-5592-2490-0399-1c802c34e327/10CMGIM00782.rgb.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/4b/d6/62/4bd662a5-1435-85c2-3fd4-f95f4999f077/mzaf_4342118010685951964.plus.aac.p.m4a',
         420227, 'Jazz', 'calm', '{jazz}',
         'Recorded live in a club. You can hear glasses clinking. Lovely.'),
    (16, '1016524120', 'Goldberg Variations, BWV 988: Aria (1981 Recording)', 'Glenn Gould', 'Bach: The Goldberg Variations, BWV 988 (1981 Recording, Remastered)',
         'https://is1-ssl.mzstatic.com/image/thumb/Music112/v4/51/95/c2/5195c22f-e480-e53a-e04b-85cd603e685d/886445085471.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/19/5b/f0/195bf05e-b389-b141-d587-7675b3998e6a/mzaf_12812310913889567065.plus.aac.p.m4a',
         184845, 'Classical', 'calm', '{classical}',
         'Listen closely and you can hear him humming along. Perfect for reading.'),
    (17, '281116081', 'Roygbiv', 'Boards of Canada', 'Music Has the Right to Children',
         'https://is1-ssl.mzstatic.com/image/thumb/Features125/v4/b5/4c/c2/b54cc20d-03f5-f2c4-4a0d-9b51ad65af89/dj.txuslqgv.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/f3/e0/de/f3e0def5-52bf-f0e1-beea-523cbf8f6c1e/mzaf_15520357652152223442.plus.aac.p.m4a',
         151520, 'Electronic', 'calm', '{electronic}',
         'Sounds like an old home video of a summer you half remember.'),
    (18, '1078923526', 'Feather (feat. Cise Starr & Akin)', 'Nujabes', 'Kei Nishikori Meets Nujabes',
         'https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/ad/18/6b/ad186b39-cd19-fa4d-abe0-bf6860cd8d9d/POCS-30003_jkt.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/57/94/1f/57941f7d-796f-3643-ac12-0e96c214e3e9/mzaf_10549193640541459923.plus.aac.p.m4a',
         175693, 'Hip-Hop/Rap', 'calm', '{hiphop,jazz}',
         'My go-to for long study sessions. Piano, a soft beat, nothing to fight.'),
    (19, '500162140', 'Breathe', 'The Prodigy', 'The Fat of the Land',
         'https://is1-ssl.mzstatic.com/image/thumb/Music126/v4/96/8b/08/968b08e4-cb2b-54c2-32bb-dba77ad1c2ca/634904912161.png/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/9f/2e/9c/9f2e9c57-8666-53a8-6d62-e5647c702e95/mzaf_81861469305734864.plus.aac.p.m4a',
         336280, 'Electronic', 'energetic', '{electronic,edm}',
         'For the last set when your legs are saying no.'),
    (20, '714366406', 'Block Rockin'' Beats', 'The Chemical Brothers', 'Dig Your Own Hole',
         'https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/54/c6/e4/54c6e459-524b-790c-80d6-5dee6cde1633/00724384295059.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/33/59/7b/33597bcb-8c82-6882-de18-7e2c28188c67/mzaf_8665126214156159651.plus.aac.p.m4a',
         313667, 'Dance', 'energetic', '{electronic,edm}',
         'That bassline. That''s the whole message.'),
    (21, '1275819878', 'Master of Puppets', 'Metallica', 'Master of Puppets (Deluxe Box Set)',
         'https://is1-ssl.mzstatic.com/image/thumb/Music114/v4/b8/5a/82/b85a8259-60d9-bfaa-770a-2baac8380e87/858978005196.png/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/fc/41/b8/fc41b8d9-1946-643c-34b8-d48d99adb93a/mzaf_11376883454063654277.plus.aac.p.m4a',
         515387, 'Metal', 'energetic', '{metal}',
         'Eight and a half minutes. Run until it ends.'),
    (22, '1640463908', 'Delilah (pull me out of this)', 'Fred again.. & Delilah Montagu', 'Actual Life 3 (January 1 - September 9 2022)',
         'https://is1-ssl.mzstatic.com/image/thumb/Music122/v4/b0/9c/b7/b09cb72c-cca9-5d66-bc9d-a9b5e5f86b22/5054197236389.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/b7/52/49/b75249d5-f30c-15bd-b6b6-2b2592a8273f/mzaf_16220054371150220513.plus.aac.p.m4a',
         250702, 'Electronic', 'energetic', '{electronic,edm}',
         'Builds and builds, then lets go. Best with headphones and nowhere to be.'),
    (23, '1440838060', 'Let It Happen', 'Tame Impala', 'Currents',
         'https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/a8/2e/b4/a82eb490-f30a-a321-461a-0383c88fec95/15UMGIM23316.rgb.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/25/fe/60/25fe60d3-3e30-c6f9-fdcb-97058fed0c38/mzaf_1903612753893368017.plus.aac.p.m4a',
         466893, 'Alternative', 'energetic', '{indie,synthpop}',
         'Wait for the part where it seems to skip. It''s on purpose.'),
    (24, '251499239', 'Brianstorm', 'Arctic Monkeys', 'Favourite Worst Nightmare',
         'https://is1-ssl.mzstatic.com/image/thumb/Music124/v4/82/90/14/829014ad-a301-62ab-bee6-f4cca4457411/mzi.hozudery.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/b3/34/10/b3341094-fc94-a870-3714-6d28a2954efa/mzaf_3427319198779915187.plus.aac.p.m4a',
         172267, 'Alternative', 'energetic', '{rock,indie}',
         'Those drums. Instant motivation for whatever you''ve been putting off.'),
    (25, '1558003296', 'Since I Left You', 'The Avalanches', 'Since I Left You (20th Anniversary Deluxe Edition)',
         'https://is1-ssl.mzstatic.com/image/thumb/Music124/v4/dc/38/47/dc3847df-0457-0c03-467c-cf4d1ac57896/19UMGIM71485.rgb.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/52/3f/24/523f240e-57d3-1548-04d7-9557f68b1950/mzaf_9143011329257065031.plus.aac.p.m4a',
         262840, 'Alternative', 'nostalgic', '{electronic,disco}',
         'Built from hundreds of old records. Feels like a holiday postcard.'),
    (26, '594061856', 'Dreams', 'Fleetwood Mac', 'Rumours',
         'https://is1-ssl.mzstatic.com/image/thumb/Music124/v4/4d/13/ba/4d13bac3-d3d5-7581-2c74-034219eadf2b/081227970949.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/d4/37/e7/d437e72a-c41b-332c-f196-bee295a9d673/mzaf_11574904347171701919.plus.aac.p.m4a',
         257800, 'Rock', 'nostalgic', '{rock,soft_rock}',
         'My parents'' car, a long drive, this on the radio.'),
    (27, '380907765', 'Take On Me', 'a-ha', 'Hunting High and Low (Deluxe Edition)',
         'https://is1-ssl.mzstatic.com/image/thumb/Music/c6/e1/c8/mzi.ixgzfcmc.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/f2/03/4f/f2034f41-707f-7111-bc63-e5d3cf7f2240/mzaf_17215043934336702540.plus.aac.p.m4a',
         228520, 'Pop', 'nostalgic', '{synthpop,pop}',
         'Try hitting the high note. Everyone tries. Nobody makes it.'),
    (28, '258200075', 'Heaven or Las Vegas', 'Cocteau Twins', 'Heaven Or Las Vegas (Remastered)',
         'https://is1-ssl.mzstatic.com/image/thumb/Music116/v4/76/cd/61/76cd61e7-0714-dce5-c48e-0f05f8fcb84b/652637001280.png/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/2f/32/ee/2f32ee9d-10cd-459e-b451-abaf4a49b803/mzaf_16630779446858414543.plus.aac.p.m4a',
         298400, 'Rock', 'nostalgic', '{shoegaze,alternative}',
         'You won''t catch half the words. You won''t need to.'),
    (29, '1675375090', 'Running Up That Hill (A Deal With God)', 'Kate Bush', 'Hounds of Love',
         'https://is1-ssl.mzstatic.com/image/thumb/Music126/v4/2c/3e/c9/2c3ec991-0f6e-bec3-7eff-750f50c5b3a6/cover.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/9d/b4/87/9db487bc-9aa6-390d-911d-7e234bc227f0/mzaf_571823162673168934.plus.aac.p.m4a',
         298933, 'Soundtrack', 'nostalgic', '{synthpop,pop}',
         'Came out in 1985, felt new again in 2022. Some songs just wait for you.'),
    (30, '1492263131', 'A Case of You', 'Joni Mitchell', 'Blue',
         'https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/00/a2/43/00a24363-cf69-bfd2-a26a-a042d57ab141/075992719926.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/bc/8e/e6/bc8ee6e2-9fb8-75a1-bdf9-37f97a009fef/mzaf_1734596607807807485.plus.aac.p.m4a',
         262827, 'Pop', 'nostalgic', '{folk}',
         'An old love song that still feels personal. Play it on a slow evening.'),
    (31, '1544474461', 'Narrator', 'Squid & Martha Skye Murphy', 'Bright Green Field',
         'https://is1-ssl.mzstatic.com/image/thumb/Music211/v4/f7/1f/cd/f71fcd9d-0705-687e-d31e-5580355c8ba8/0801061031435.png/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/4c/e2/02/4ce202bd-c120-5d94-68e3-389760fa5877/mzaf_6245098959850246306.plus.aac.p.m4a',
         508733, 'Indie Rock', 'anxious', '{rock,indie}',
         'Nervous energy, bottled. Ride it out to the end.'),
    (32, '1440634031', 'Machine Gun', 'Portishead', 'Third',
         'https://is1-ssl.mzstatic.com/image/thumb/Music112/v4/a3/78/f5/a378f5c8-f3ee-d26d-3181-6cfe160c921b/08UMGIM06817.rgb.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/7b/21/79/7b2179c5-98da-8684-b0e2-77d0cfa2678d/mzaf_15373660542934643537.plus.aac.p.m4a',
         283440, 'Electronic', 'anxious', '{electronic,alternative}',
         'Cold and tense, but strangely calming once you lean into it.'),
    (33, '300948073', 'Once In a Lifetime', 'Talking Heads', 'Remain In Light',
         'https://is1-ssl.mzstatic.com/image/thumb/Music/87/5f/5b/mzi.zzquknhm.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/e0/31/66/e0316603-d04b-6d7f-06a4-322b16e2cb3d/mzaf_8848157553075282502.plus.aac.p.m4a',
         258600, 'Alternative', 'anxious', '{rock,funk}',
         'How did I get here? Same question, better groove.'),
    (34, '1586070262', 'Concorde', 'Black Country, New Road', 'Ants From Up There',
         'https://is1-ssl.mzstatic.com/image/thumb/Music221/v4/8f/6a/c6/8f6ac6c6-5bcd-fa25-94f2-fd9922725215/5054429151442.png/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/fc/ec/f6/fcecf6ee-043e-f168-6f99-2b34c21dbf45/mzaf_9646130708964145947.plus.aac.p.m4a',
         363773, 'Alternative', 'anxious', '{indie,rock}',
         'For when your head won''t stop talking. This one talks back, kindly.'),
    (35, '724466660', 'Angel', 'Massive Attack', 'Mezzanine',
         'https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/0a/98/55/0a98555b-8d9d-3b46-660a-b91261557d17/00724384559953.rgb.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/db/af/6e/dbaf6e32-911f-be9d-e2b0-0e48189d14fe/mzaf_14963447783895938076.plus.aac.p.m4a',
         379533, 'Electronic', 'anxious', '{electronic}',
         'Starts slow and creeping, then the guitar arrives. Brace yourself.'),
    (36, '266603053', 'Disorder', 'Joy Division', 'Unknown Pleasures (Collector''s Edition)',
         'https://is1-ssl.mzstatic.com/image/thumb/Features114/v4/80/14/7f/80147fb9-8c62-8773-60fe-51d9bdcfd415/dj.blmthqrv.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/4d/cc/89/4dcc8922-012f-c11f-b061-caef7bafe7e2/mzaf_11167643022210339253.plus.aac.p.m4a',
         209040, 'Alternative', 'anxious', '{rock,alternative}',
         'That opening bass line is the sound of overthinking.'),
    (37, '1440766128', 'Thinkin Bout You', 'Frank Ocean', 'channel ORANGE',
         'https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/04/f8/63/04f863fc-2852-604f-c910-a97ac069506b/12UMGIM40339.rgb.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/7e/a3/11/7ea311f5-790a-84ec-3cae-940f89599e62/mzaf_14067239133136508975.plus.aac.p.m4a',
         200747, 'Pop', 'romantic', '{rnb}',
         'That falsetto. Send this to whoever you''re thinking of.'),
    (38, '509665325', 'Myth', 'Beach House', 'Bloom',
         'https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/8b/b7/08/8bb7086b-cdb5-1ffe-926b-e7637bda6a0d/BeachHouse_Bloom_iTunes.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/a3/d1/3c/a3d13c15-969d-c248-5993-27dad812df09/mzaf_11045108390724848951.plus.aac.p.m4a',
         258653, 'Alternative', 'romantic', '{indie,shoegaze}',
         'Soft-focus and glowing. Good for a late walk with someone.'),
    (39, '950764325', 'Really Love', 'D’Angelo and The Vanguard', 'Black Messiah',
         'https://is1-ssl.mzstatic.com/image/thumb/Music114/v4/01/38/24/01382463-de8c-2a33-f8fe-fb3a008dfc87/886444977449.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/88/49/d5/8849d568-83c3-ef19-5ab0-023a0e76c73a/mzaf_11706620226372474356.plus.aac.p.m4a',
         344347, 'R&B/Soul', 'romantic', '{soul,rnb}',
         'Starts with Spanish guitar and strings, then the groove sneaks in.'),
    (40, '1440865800', 'My Funny Valentine', 'Chet Baker', 'Chet Baker Sings',
         'https://is1-ssl.mzstatic.com/image/thumb/Music221/v4/2f/99/e5/2f99e569-3920-07ab-b29e-c2da9b50468d/16UMGIM49055.rgb.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/5d/1d/eb/5d1debb8-aba6-960f-d05d-71ec5352ce82/mzaf_16224671045077102639.plus.aac.p.m4a',
         139065, 'Jazz', 'romantic', '{jazz}',
         'Older than all of us and still the sweetest thing on this list.'),
    (41, '211429895', 'Smooth Operator', 'Sade', 'Diamond Life',
         'https://is1-ssl.mzstatic.com/image/thumb/Music/51/d0/6a/mzi.zxcjkymx.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview125/v4/65/a7/dd/65a7ddeb-7c77-4238-59f1-a99c08406090/mzaf_12043866415592815824.plus.aac.p.m4a',
         298807, 'R&B/Soul', 'romantic', '{soul,jazz}',
         'Dim the lights. Saxophone does the rest.'),
    (42, '1440864172', 'City of Stars', 'Ryan Gosling & Emma Stone', 'La La Land (Original Motion Picture Soundtrack)',
         'https://is1-ssl.mzstatic.com/image/thumb/Music114/v4/bb/47/a3/bb47a36e-57b8-9260-f9a4-d09851145c45/00602557100556.rgb.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/07/1b/17/071b1716-5a3f-9883-ad1c-426e531a3df3/mzaf_3825775767124502166.plus.aac.p.m4a',
         149720, 'Musicals', 'romantic', '{musical,jazz}',
         'Cheesy? A bit. Beautiful anyway.'),
    (43, '1099843209', 'My Own Summer (Shove It)', 'Deftones', 'Around the Fur',
         'https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/27/cb/b4/27cbb40e-f8c6-dede-9c69-62089a873fa9/093624919803.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/7b/82/e2/7b82e273-7849-4387-2209-f086eef927cd/mzaf_7584156168517905468.plus.aac.p.m4a',
         215480, 'Hard Rock', 'angry', '{metal,alternative}',
         'For when you need everyone to just back off for a minute.'),
    (44, '1746567085', 'Protect Ya Neck (feat. RZA, Method Man, Inspectah Deck, Raekwon, U-God, Ol'' Dirty Bastard, Ghostface Killah & GZA)', 'Wu-Tang Clan', 'Enter the Wu-Tang (36 Chambers)',
         'https://is1-ssl.mzstatic.com/image/thumb/Music221/v4/8c/20/1f/8c201f03-7617-2d8b-3d8d-e0ba2d55041b/196872123784.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/10/9e/49/109e49fc-bde7-ee89-caa6-d61ca194c0eb/mzaf_17322852598698011391.plus.aac.p.m4a',
         277067, 'Hip-Hop/Rap', 'angry', '{hiphop,rap}',
         'Nine voices, one grimy beat. Pure attitude.'),
    (45, '1733769810', 'Raining Blood', 'Slayer', 'Reign In Blood',
         'https://is1-ssl.mzstatic.com/image/thumb/Music112/v4/6b/83/2c/6b832cb7-7234-0fb7-3930-38d9c385dc9a/23UM1IM55794.rgb.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/53/5c/0b/535c0b0f-7964-a7ea-2418-23a1da5368eb/mzaf_122777616155196911.plus.aac.p.m4a',
         254474, 'Rock', 'angry', '{metal}',
         'For days when only the heaviest riff will do.'),
    (46, '1440783638', 'Breed', 'Nirvana', 'Nevermind',
         'https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/95/fd/b9/95fdb9b2-6d2b-92a6-97f2-51c1a6d77f1a/00602527874609.rgb.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/56/50/66/56506651-6799-6452-fe7c-5461d388b71c/mzaf_640409494128433896.plus.aac.p.m4a',
         184040, 'Rock', 'angry', '{rock,alternative}',
         'Three minutes of pure frustration, turned into something fun.'),
    (47, '1440871882', 'King Kunta', 'Kendrick Lamar', 'To Pimp a Butterfly',
         'https://is1-ssl.mzstatic.com/image/thumb/Music112/v4/b5/a6/91/b5a69171-5232-3d5b-9c15-8963802f83dd/15UMGIM15814.rgb.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/28/14/64/281464e2-0033-0ba3-303f-f74215f21fb0/mzaf_14773791895724654194.plus.aac.p.m4a',
         234690, 'Hip-Hop/Rap', 'angry', '{hiphop,rap}',
         'Walk somewhere fast with this on. You''ll feel ten feet tall.'),
    (48, '49249866', 'Merchandise', 'Fugazi', 'Repeater & 3 Songs',
         'https://is1-ssl.mzstatic.com/image/thumb/Music/y2005/m03/d06/h06/s05.sbssbjxy.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/f1/ae/18/f1ae186b-b3a6-53b8-60db-68519e866244/mzaf_840852498239281268.plus.aac.p.m4a',
         178960, 'Alternative', 'angry', '{rock}',
         'Angry, but at the right things. Short and sharp.'),
    (49, '1249418625', 'Wake Up', 'Arcade Fire', 'Funeral',
         'https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/2b/09/6e/2b096e8c-ae65-fc42-a4b1-19abb4100433/886446576442.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/1e/7b/73/1e7b73ee-bc1d-2e1b-4ff9-e6aa43772774/mzaf_1501049522312124735.plus.aac.p.m4a',
         335333, 'Alternative', 'hopeful', '{indie,rock}',
         'Best sung loudly with other people. Shout along anyway.'),
    (50, '1666662417', 'My Sweet Lord (2020 Mix)', 'George Harrison', 'All Things Must Pass (50th Anniversary)',
         'https://is1-ssl.mzstatic.com/image/thumb/Music123/v4/c9/24/7c/c9247c5b-f111-b90b-f92b-0464a41d2fc3/4050538893908.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/a3/e7/a3/a3e7a34a-ddc0-264e-ad09-253a05ad8bf7/mzaf_187954892160103361.plus.aac.p.m4a',
         281227, 'Rock', 'hopeful', '{rock,folk}',
         'Warm all the way through. Good for a slow Sunday.'),
    (51, '302077858', 'Movin'' On Up', 'Primal Scream', 'Screamadelica',
         'https://is1-ssl.mzstatic.com/image/thumb/Music114/v4/42/b2/ac/42b2ac20-430c-9099-0704-5d50cfd6bb00/mzi.yjfmvrdv.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/3a/b3/9c/3ab39c36-e32d-432f-dfb7-6dd31f64dfb3/mzaf_12486890356188353326.plus.aac.p.m4a',
         230533, 'Alternative', 'hopeful', '{rock,alternative}',
         'Exactly what it says. You''re moving on up.'),
    (52, '1447486980', 'Hoppípolla', 'Sigur Rós', 'Takk...',
         'https://is1-ssl.mzstatic.com/image/thumb/Music114/v4/ce/3e/b8/ce3eb805-fb13-0200-5856-081ed0014e13/190296926952.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/ad/da/59/adda5942-513b-8cc7-c0b5-d580e19c37bc/mzaf_18208108812196817078.plus.aac.p.m4a',
         268707, 'Alternative', 'hopeful', '{ambient,alternative}',
         'The title means ''hopping into puddles''. Act accordingly.'),
    (53, '1263764683', 'Truth', 'Kamasi Washington', 'Harmony of Difference - EP',
         'https://is1-ssl.mzstatic.com/image/thumb/Music113/v4/ae/dd/05/aedd058e-cc07-3c40-6d22-74f2fbec7073/889030017161.png/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/2f/e5/1b/2fe51ba5-2197-9873-eabd-e82b19019077/mzaf_9106204351589491546.plus.aac.p.m4a',
         810322, 'Jazz', 'hopeful', '{jazz}',
         'Starts small and ends as a whole choir. Feels like things getting better.'),
    (54, '1388988078', 'Hyperballad', 'Björk', 'Strange Glue: 90s Buzz Guitars and Bubblegum',
         'https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/41/ab/ae/41abae58-00c4-18a3-aa52-a6dcbf237202/842474159538.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview115/v4/9b/53/6f/9b536f35-90b8-b72e-5f8b-20da968c19f3/mzaf_1787126311446272634.plus.aac.p.m4a',
         322133, 'Pop', 'hopeful', '{electronic,avant_garde}',
         'About throwing small things off a cliff so you can feel lighter. Try it (metaphorically).'),
    (55, '893175788', 'Archangel', 'Burial', 'Untrue',
         'https://is1-ssl.mzstatic.com/image/thumb/Music116/v4/9d/0f/1c/9d0f1c2b-2fae-d8ac-3920-ce9ec5bc85b5/7982.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/fd/80/ea/fd80eabb-293c-3f0f-f8eb-aa2fe01092e7/mzaf_11630702710896917232.plus.aac.p.m4a',
         238427, 'Electronic', 'lonely', '{electronic}',
         'Night bus, rain on the window, nobody to text. This is that feeling.'),
    (56, '1429663167', 'Liability', 'Lorde', 'Melodrama',
         'https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/8d/0d/15/8d0d1532-493b-52ec-6a29-a239ced6931b/17UMGIM81023.rgb.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/93/f7/8a/93f78a0a-11fc-d2b9-44b3-32ccf30ae016/mzaf_3308956915771056638.plus.aac.p.m4a',
         171728, 'Alternative', 'lonely', '{pop}',
         'For when you feel like too much for everyone. You are not.'),
    (57, '1526437442', 'anything', 'Adrianne Lenker', 'songs',
         'https://is1-ssl.mzstatic.com/image/thumb/Music125/v4/70/f0/a6/70f0a6a5-cd71-9da2-174b-41927d331cdd/cover.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/da/4c/cd/da4ccd11-f2ec-8369-118a-8f9b28bbaf55/mzaf_5367988796767748643.plus.aac.p.m4a',
         202047, 'Alternative', 'lonely', '{folk,indie}',
         'Recorded in a cabin in the woods. You can hear the rain.'),
    (58, '292885262', 'When the Sun Hits', 'Slowdive', 'Souvlaki',
         'https://is1-ssl.mzstatic.com/image/thumb/Music124/v4/1b/50/ad/1b50adc8-139b-1ad9-8500-cc2eb93faf17/888880730831.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/8c/d6/29/8cd62943-9c69-3f1b-79b9-fad0883a0e9a/mzaf_9349941370226318512.plus.aac.p.m4a',
         285907, 'Alternative', 'lonely', '{shoegaze}',
         'Wait for the chorus. It''s like a wave.'),
    (59, '1445055008', 'First Love', 'Hikaru Utada', 'First Love - EP',
         'https://is1-ssl.mzstatic.com/image/thumb/Music128/v4/d1/a6/81/d1a681b9-aa68-6148-5a50-69fd4c95cc37/00602567241966.rgb.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview221/v4/8a/ef/99/8aef99aa-02e7-a0ed-62c2-52b8b7e0c438/mzaf_18106053477060712364.plus.aac.p.m4a',
         258627, 'J-Pop', 'lonely', '{pop,rnb}',
         'Every word aches, even if you don''t speak Japanese.'),
    (60, '1440814573', 'Pale Blue Eyes', 'The Velvet Underground', 'The Velvet Underground',
         'https://is1-ssl.mzstatic.com/image/thumb/Music124/v4/33/98/39/3398391b-3586-0903-a094-423c93d8fe53/00602547075031.rgb.jpg/100x100bb.jpg',
         'https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/bf/5d/48/bf5d4884-a488-2287-b332-6f02e6c2537b/mzaf_2986102431447014921.plus.aac.p.m4a',
         339546, 'Rock', 'lonely', '{rock}',
         'The simplest song about missing someone. Still gets me.');

-- A teammate may already have sent one of these songs from the app; their
-- tracks row is reused rather than duplicated.
insert into public.tracks (provider, provider_track_id, title, artist, album,
                           artwork_url, preview_url, duration_ms, genres)
select 'itunes', s.tid, s.title, s.artist, s.album,
       s.artwork, s.preview, s.ms,
       case when s.itunes is null then '{}' else array[s.itunes] end
from _seed s
on conflict (provider, provider_track_id) do nothing;

-- Record n goes to sender 1 + (n-1) % 10, so each mood's six records come from
-- six different people. Sent-at times are spread over the past 20 days.
insert into public.submissions (sender_id, track_id, message, mood, genres, lat, lng, created_at)
select p.id, t.id, s.msg, s.mood, s.genres, p.lat, p.lng,
       now() - make_interval(hours => (s.n * 173) % 480, mins => (s.n * 37) % 60)
from _seed s
join public.tracks t
  on t.provider = 'itunes' and t.provider_track_id = s.tid
join auth.users u
  on u.email = format('vinyl-demo-%s@example.invalid', 1 + (s.n - 1) % 10)
join public.profiles p
  on p.id = u.id
where not exists (
  select 1 from public.submissions x
  where x.sender_id = p.id and x.track_id = t.id
);

-- Favourite genres = what each sender sends most, so their profiles look lived-in.
update public.profiles p
   set favorite_genres = (
     select array_agg(g order by c desc, g)
     from (
       select g, count(*) as c
       from public.submissions s, unnest(s.genres) as g
       where s.sender_id = p.id
       group by g
       order by count(*) desc, g
       limit 3
     ) top
   )
 where p.id in (select id from auth.users where email like 'vinyl-demo-%@example.invalid');

commit;

-- What landed (read-only).
select
  (select count(*) from auth.users where email like 'vinyl-demo-%@example.invalid') as demo_accounts,
  (select count(*) from public.submissions s
     join auth.users u on u.id = s.sender_id
    where u.email like 'vinyl-demo-%@example.invalid')                              as demo_records,
  (select count(*) from public.submissions s
     join public.tracks t on t.id = s.track_id
    where t.provider = 'manual' and t.provider_track_id like 'seed-%')              as old_seed_left;

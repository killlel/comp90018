-- =============================================================================
-- Vinyl — 0005: genre becomes a lookup table
-- Owner: Ivan (guangyu11)  |  Sprint 2
--
-- Why a lookup table rather than an enum:
--
--   * seed.sql already uses nine genres the app's hardcoded list doesn't have
--     (rock, funk, indie, metal...). An enum conversion would fail the cast on
--     those rows; a lookup table just gets the extra rows inserted.
--   * the app can fetch the vocabulary from the database instead of hardcoding
--     GenreOptions.all, so adding a genre is one INSERT rather than a migration
--     plus an app release. (GenreOptions currently has no "Rock".)
--   * is_active lets a genre be retired without orphaning old submissions.
--
-- Scope: this governs submissions.genres — the genres a USER picks. It does not
-- touch tracks.genres, which is raw iTunes metadata and must stay unconstrained.
--
-- Idempotent: safe to re-run.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. The vocabulary
--
-- slug  = stored and matched on (lowercase snake_case, like mood_tag)
-- label = shown to the user
-- The same split MoodOptions already makes between `tag` and `title`.
-- -----------------------------------------------------------------------------

create table if not exists public.genres (
  slug       text primary key check (slug ~ '^[a-z0-9_]+$'),
  label      text not null,
  sort_order integer not null default 100,
  is_active  boolean not null default true
);

comment on table public.genres is
  'Controlled vocabulary for submissions.genres. The app should read this rather than hardcode a list.';

insert into public.genres (slug, label, sort_order) values
  ('pop',         'Pop',          10),
  ('rock',        'Rock',         20),
  ('indie',       'Indie',        30),
  ('alternative', 'Alternative',  40),
  ('electronic',  'Electronic',   50),
  ('edm',         'EDM',          60),
  ('synthpop',    'Synth-pop',    70),
  ('hiphop',      'Hip-Hop',      80),
  ('rap',         'Rap',          90),
  ('rnb',         'R&B',         100),
  ('soul',        'Soul',        110),
  ('funk',        'Funk',        120),
  ('disco',       'Disco',       130),
  ('jazz',        'Jazz',        140),
  ('classical',   'Classical',   150),
  ('folk',        'Folk',        160),
  ('soft_rock',   'Soft Rock',   170),
  ('metal',       'Metal',       180),
  ('ambient',     'Ambient',     190),
  ('shoegaze',    'Shoegaze',    200),
  ('k_pop',       'K-pop',       210),
  ('musical',     'Musical',     220),
  ('avant_garde', 'Avant-garde', 230)
on conflict (slug) do update
  set label = excluded.label, sort_order = excluded.sort_order;


-- -----------------------------------------------------------------------------
-- 2. Normalise the genres already stored
--
-- Existing rows use display-ish values such as 'soft rock'. Bring them in line
-- with the slug format BEFORE the validation trigger starts rejecting writes.
-- -----------------------------------------------------------------------------

update public.submissions
   set genres = (
     select coalesce(array_agg(replace(lower(btrim(g)), ' ', '_')), '{}')
     from unnest(genres) as g
   )
 where coalesce(array_length(genres, 1), 0) > 0;

-- Report anything still unrecognised rather than silently leaving bad data.
do $$
declare
  v_unknown text[];
begin
  select coalesce(array_agg(distinct g), '{}')
    into v_unknown
  from public.submissions s, unnest(s.genres) as g
  where not exists (select 1 from public.genres gg where gg.slug = g);

  if coalesce(array_length(v_unknown, 1), 0) > 0 then
    raise notice
      'These genres are stored on submissions but are not in public.genres: %. Add them with an INSERT, or clear them from the affected rows.',
      v_unknown;
  else
    raise notice 'All stored submission genres resolve against public.genres.';
  end if;
end $$;


-- -----------------------------------------------------------------------------
-- 3. Validation
--
-- A text[] cannot carry a per-element foreign key, so integrity is a trigger.
-- Keeping the array (rather than a junction table) preserves the GIN index and
-- the && overlap operator the matchmaker uses.
-- -----------------------------------------------------------------------------

create or replace function public.validate_submission_genres()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
  v_bad text;
begin
  if coalesce(array_length(new.genres, 1), 0) = 0 then
    return new;
  end if;

  select g into v_bad
  from unnest(new.genres) as g
  where not exists (
    select 1 from public.genres gg
    where gg.slug = g and gg.is_active
  )
  limit 1;

  if v_bad is not null then
    raise exception 'unknown or inactive genre: %', v_bad using errcode = '23514';
  end if;

  return new;
end;
$$;

drop trigger if exists submissions_validate_genres on public.submissions;
create trigger submissions_validate_genres
  before insert or update of genres on public.submissions
  for each row execute function public.validate_submission_genres();


-- -----------------------------------------------------------------------------
-- 4. Access
--
-- Public vocabulary: any signed-in user may read it, nobody may write it from
-- the app. Adding a genre is an INSERT from the SQL editor or a migration.
-- -----------------------------------------------------------------------------

alter table public.genres enable row level security;

drop policy if exists genres_select_authenticated on public.genres;
create policy genres_select_authenticated on public.genres
  for select to authenticated
  using (true);

grant select on public.genres to authenticated;
revoke all on public.genres from anon;


-- -----------------------------------------------------------------------------
-- 5. tracks.genres is deliberately untouched
--
-- That column holds iTunes' primaryGenreName — roughly a hundred values that
-- Apple changes without notice. Constraining it would mean a user's submission
-- fails because Apple invented a subgenre. Match on the user-chosen column.
-- -----------------------------------------------------------------------------

comment on column public.tracks.genres is
  'Raw provider genre strings (iTunes primaryGenreName). Intentionally unvalidated — do NOT constrain to public.genres.';

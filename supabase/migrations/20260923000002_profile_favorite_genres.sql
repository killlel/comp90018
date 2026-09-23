-- =============================================================================
-- Vinyl — 0008: favorite genres on the profile
-- Owner: Ivan (guangyu11)  |  Sprint 2
--
-- Onboarding asks the user which genres they like, or lets them say "I listen
-- to everything". This stores that answer so it can be displayed on the profile
-- and used as the default genre signal when matching.
--
-- THREE STATES IN ONE COLUMN
--
--   null          — has not answered yet
--   '{}'          — answered "I listen to everything"
--   '{jazz,soul}' — answered with specific genres
--
-- Deliberately one nullable column rather than an array plus a
-- `listens_to_everything` boolean: two fields can contradict each other, and
-- there is no sensible meaning for `true` alongside '{jazz}'. A sentinel slug
-- such as 'everything' in public.genres was also rejected — it would appear as
-- a selectable chip and pollute the vocabulary that submissions match against.
--
-- WHY text[] AND NOT A FOREIGN KEY
--
-- A single-genre answer could be `text references public.genres(slug)` and let
-- Postgres enforce it outright. Users may pick several, and an array cannot
-- carry a per-element foreign key — Postgres has no such constraint. The
-- alternatives were a junction table (real FKs, but every match query grows a
-- join and an aggregate) or a validation trigger. The trigger wins here because
-- it keeps the column the same shape as submissions.genres, so matching stays a
-- single && overlap between two arrays.
--
-- Idempotent: safe to re-run.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. The column
-- -----------------------------------------------------------------------------

alter table public.profiles
  add column if not exists favorite_genres text[];

comment on column public.profiles.favorite_genres is
  'Slugs from public.genres. null = not answered; {} = "I listen to everything"; otherwise the chosen genres.';


-- -----------------------------------------------------------------------------
-- 2. Validation
--
-- Same guarantee submissions.genres gets: a display label such as 'K-pop' is
-- rejected rather than stored, so the column can never drift from the
-- vocabulary the matchmaker reads.
--
-- Both null and '{}' pass — they are the two legitimate "no specific genres"
-- answers, and neither can contain a bad slug.
-- -----------------------------------------------------------------------------

create or replace function public.validate_profile_genres()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
  v_bad text;
begin
  if new.favorite_genres is null
     or coalesce(array_length(new.favorite_genres, 1), 0) = 0 then
    return new;
  end if;

  select g into v_bad
  from unnest(new.favorite_genres) as g
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

drop trigger if exists profiles_validate_genres on public.profiles;
create trigger profiles_validate_genres
  before insert or update of favorite_genres on public.profiles
  for each row execute function public.validate_profile_genres();


-- -----------------------------------------------------------------------------
-- 3. Index
--
-- Mirrors submissions_genres_idx so overlap (&&) against a user's taste stays
-- cheap once the matchmaker reads this column.
-- -----------------------------------------------------------------------------

create index if not exists profiles_favorite_genres_idx
  on public.profiles using gin (favorite_genres);


-- =============================================================================
-- CLIENT NOTE
--
-- No RPC. Scott's ProfileRepository already upserts public.profiles directly and
-- RLS confines that to the caller's own row; write this column the same way
-- rather than introducing a second style.
--
-- Send slugs, not labels: 'k_pop', not 'K-pop'. Read the vocabulary from
-- public.genres (slug + label) — the trigger rejects anything else.
--
-- To record "I listen to everything", send an empty array. To leave the question
-- unanswered, do not send the column at all.
-- =============================================================================

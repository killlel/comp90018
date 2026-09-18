-- =============================================================================
-- Vinyl — 0006: drop profiles.city
-- Owner: Ivan (guangyu11)  |  Sprint 2
--
-- 0004 stored a display label alongside the centroid so the UI would not need a
-- working Geocoder to render a place name. Team decision is to drop it: the
-- coordinates are the source of truth and the label is presentation.
--
-- Consequence to be aware of: anything that wants to show "Melbourne" rather
-- than a pair of numbers now has to reverse-geocode on the client, which needs
-- network. Offline, the label is unavailable — show the distance instead, or
-- nothing.
--
-- Nothing else depended on the column. submit_song() never read it, and
-- room_card never carried it, so no recipient-facing behaviour changes.
--
-- Idempotent: safe to re-run.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. Replace update_my_location() with the two-argument form
--
-- Dropped explicitly rather than `create or replace`d: removing a parameter
-- produces a second overload instead of replacing the original, and PostgREST
-- would then have two candidates to choose between.
-- -----------------------------------------------------------------------------

drop function if exists public.update_my_location(
  double precision, double precision, text
);

create or replace function public.update_my_location(
  p_lat double precision default null,
  p_lng double precision default null
)
returns void
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

  if (p_lat is null) <> (p_lng is null) then
    raise exception 'latitude and longitude must be supplied together'
      using errcode = '22023';
  end if;

  update public.profiles
     set lat  = p_lat,
         lng  = p_lng,
         location_updated_at = case when p_lat is null then null else now() end
   where id = v_uid;
end;
$$;


-- -----------------------------------------------------------------------------
-- 2. Drop the column
-- -----------------------------------------------------------------------------

alter table public.profiles
  drop column if exists city;


-- -----------------------------------------------------------------------------
-- 3. Grants
--
-- A dropped-and-recreated function loses its grants, so they are reapplied.
-- -----------------------------------------------------------------------------

do $$
declare
  f record;
begin
  for f in
    select format('public.%I(%s)', p.proname,
                  pg_get_function_identity_arguments(p.oid)) as sig
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname = 'update_my_location'
  loop
    execute format('revoke execute on function %s from public, anon', f.sig);
    execute format('grant execute on function %s to authenticated', f.sig);
  end loop;
end $$;


-- =============================================================================
-- CLIENT NOTE — update_my_location() now takes two arguments, not three.
--
--   update_my_location(p_lat => -37.81, p_lng => 144.96)
--
-- No client code calls it yet, so nothing is currently broken. Worth telling
-- Scott before the settings/onboarding screen is written against the old shape.
-- =============================================================================

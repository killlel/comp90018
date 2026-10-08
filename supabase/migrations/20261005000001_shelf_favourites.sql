-- =============================================================================
-- Vinyl — 0013: favourites on the record shelf
-- Owner: Scott  |  Sprint 3  |  Schema review: Ivan
--
-- The Collection screen has a Favourites filter. A favourite is a kept record
-- the user has starred, so it belongs on the shelf row they already own.
--
--
-- WHY A COLUMN ON shelf_items
--
-- Only kept records can be favourites, and a shelf row is exactly "this user
-- kept this record". A separate favourites table would need its own RLS, its
-- own insert check against recommendations, and would let a favourite outlive
-- the shelf row it depends on. As a column, removing a record from the shelf
-- removes the favourite with it, and the existing owner-only policies already
-- cover reading and updating it — no new policy is needed.
--
-- Sent records cannot be favourites: you never shelve your own submission
-- (the insert policy requires a recommendation addressed to you).
--
--
-- WHY room_card IS NOT TOUCHED
--
-- Same reasoning as 0012. Adding is_favourite to room_card means dropping and
-- recreating every function that returns it, and older builds would decode a
-- key they don't know. The app reads the flag straight from shelf_items, which
-- the owner can already select, and merges it with get_shelf() by submission_id.
--
-- Idempotent: safe to re-run.
-- =============================================================================

alter table public.shelf_items
  add column if not exists is_favourite boolean not null default false;

comment on column public.shelf_items.is_favourite is
  'Starred by the owner in their Collection. Lives and dies with the shelf row.';

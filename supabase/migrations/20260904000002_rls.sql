-- =============================================================================
-- Vinyl — 0002: row level security
-- Owner: Ivan (guangyu11)  |  Sprint 1
--
-- The Android app talks to Postgres directly through PostgREST using the public
-- anon key. There is no server of ours in between, so RLS *is* the backend.
--
-- Rule of the schema: a user may read their own rows and nothing else.
-- Every cross-user read (someone else's song, someone else's reaction) goes
-- through a SECURITY DEFINER function in 0003_functions.sql that returns an
-- anonymised column list. If you ever find yourself wanting to add a policy
-- that lets user A read user B's row, stop and write an RPC instead.
--
-- Policies are scoped `to authenticated`, so the `anon` role (a client that has
-- not signed in) can read nothing at all.
--
-- Idempotent: safe to re-run.
-- =============================================================================

alter table public.profiles        enable row level security;
alter table public.tracks          enable row level security;
alter table public.submissions     enable row level security;
alter table public.recommendations enable row level security;
alter table public.shelf_items     enable row level security;
alter table public.reactions       enable row level security;

-- Note: `(select auth.uid())` rather than a bare `auth.uid()` is deliberate.
-- Postgres caches it as an InitPlan instead of re-evaluating per row, which is
-- a large difference once the submissions table has real data in it.


-- -----------------------------------------------------------------------------
-- profiles — owner only, all operations
-- -----------------------------------------------------------------------------

drop policy if exists profiles_select_own on public.profiles;
create policy profiles_select_own on public.profiles
  for select to authenticated
  using ((select auth.uid()) = id);

drop policy if exists profiles_insert_own on public.profiles;
create policy profiles_insert_own on public.profiles
  for insert to authenticated
  with check ((select auth.uid()) = id);

drop policy if exists profiles_update_own on public.profiles;
create policy profiles_update_own on public.profiles
  for update to authenticated
  using ((select auth.uid()) = id)
  with check ((select auth.uid()) = id);

-- No delete policy: accounts are removed through auth, which cascades.


-- -----------------------------------------------------------------------------
-- tracks — readable by any signed-in user, writable only via submit_song()
--
-- Track metadata is public information from the music API and carries no link
-- to a user, so it is safe to read. Writes go through the RPC so that the
-- (provider, provider_track_id) upsert cannot race.
-- -----------------------------------------------------------------------------

drop policy if exists tracks_select_authenticated on public.tracks;
create policy tracks_select_authenticated on public.tracks
  for select to authenticated
  using (true);


-- -----------------------------------------------------------------------------
-- submissions — sender only
--
-- A recipient gets NO direct access here. If they could select the row they
-- would get sender_id with it, which is exactly the thing the app promises not
-- to reveal. Recipients use get_room() / get_shelf() instead.
-- -----------------------------------------------------------------------------

drop policy if exists submissions_select_own on public.submissions;
create policy submissions_select_own on public.submissions
  for select to authenticated
  using (sender_id = (select auth.uid()));

drop policy if exists submissions_insert_own on public.submissions;
create policy submissions_insert_own on public.submissions
  for insert to authenticated
  with check (sender_id = (select auth.uid()));

-- Lets a sender retract a song (is_active = false) or fix their message.
drop policy if exists submissions_update_own on public.submissions;
create policy submissions_update_own on public.submissions
  for update to authenticated
  using (sender_id = (select auth.uid()))
  with check (sender_id = (select auth.uid()));


-- -----------------------------------------------------------------------------
-- recommendations — recipient may read their own; only the matchmaker writes
-- -----------------------------------------------------------------------------

drop policy if exists recommendations_select_own on public.recommendations;
create policy recommendations_select_own on public.recommendations
  for select to authenticated
  using (recipient_id = (select auth.uid()));

-- No insert/update/delete policy on purpose: rows are created exclusively by
-- public.request_recommendations(), so a client cannot hand itself a match.


-- -----------------------------------------------------------------------------
-- shelf_items — owner only, and you can only shelve a record you were given
-- -----------------------------------------------------------------------------

drop policy if exists shelf_items_select_own on public.shelf_items;
create policy shelf_items_select_own on public.shelf_items
  for select to authenticated
  using (owner_id = (select auth.uid()));

drop policy if exists shelf_items_insert_own on public.shelf_items;
create policy shelf_items_insert_own on public.shelf_items
  for insert to authenticated
  with check (
    owner_id = (select auth.uid())
    and exists (
      select 1 from public.recommendations r
      where r.recipient_id = (select auth.uid())
        and r.submission_id = shelf_items.submission_id
    )
  );

drop policy if exists shelf_items_update_own on public.shelf_items;
create policy shelf_items_update_own on public.shelf_items
  for update to authenticated
  using (owner_id = (select auth.uid()))
  with check (owner_id = (select auth.uid()));

drop policy if exists shelf_items_delete_own on public.shelf_items;
create policy shelf_items_delete_own on public.shelf_items
  for delete to authenticated
  using (owner_id = (select auth.uid()));


-- -----------------------------------------------------------------------------
-- reactions — the anonymity-critical table
--
-- SELECT is granted to the REACTOR only, so a user can see and change what they
-- sent. The SUBMITTER is deliberately locked out of this table entirely: if
-- they could read even one row they would see reactor_id. They read counts via
-- public.get_reactions() instead, which never returns an identity.
--
-- This is the Sprint 3 "reactions stay anonymous" requirement, enforced at the
-- database layer in Sprint 1 so no later screen can accidentally leak it.
-- -----------------------------------------------------------------------------

drop policy if exists reactions_select_own on public.reactions;
create policy reactions_select_own on public.reactions
  for select to authenticated
  using (reactor_id = (select auth.uid()));

drop policy if exists reactions_insert_own on public.reactions;
create policy reactions_insert_own on public.reactions
  for insert to authenticated
  with check (
    reactor_id = (select auth.uid())
    and exists (
      select 1 from public.recommendations r
      where r.recipient_id = (select auth.uid())
        and r.submission_id = reactions.submission_id
    )
  );

drop policy if exists reactions_update_own on public.reactions;
create policy reactions_update_own on public.reactions
  for update to authenticated
  using (reactor_id = (select auth.uid()))
  with check (reactor_id = (select auth.uid()));

drop policy if exists reactions_delete_own on public.reactions;
create policy reactions_delete_own on public.reactions
  for delete to authenticated
  using (reactor_id = (select auth.uid()));


-- -----------------------------------------------------------------------------
-- Table privileges
--
-- RLS filters rows, but a role still needs the underlying GRANT to reach the
-- table at all. Supabase's default privileges usually cover this; granting
-- explicitly means the schema behaves the same on a local `supabase start`, on
-- a teammate's own project, and after a restore.
--
-- `anon` (signed out) is revoked outright — belt and braces, since no policy
-- targets that role anyway.
-- -----------------------------------------------------------------------------

grant usage on schema public to authenticated;

grant select, insert, update, delete on
  public.profiles,
  public.submissions,
  public.recommendations,
  public.shelf_items,
  public.reactions
to authenticated;

grant select on public.tracks to authenticated;

revoke all on
  public.profiles,
  public.tracks,
  public.submissions,
  public.recommendations,
  public.shelf_items,
  public.reactions
from anon;

# Vinyl — design decisions

**Current state, not history.** When a decision changes, edit it in place. No
changelog, no "previously we did X". Git already remembers.

Read this before building a screen or changing the schema, so four people
building in parallel end up with one app.

---

## 0. How we use this file

Everyone edits it. It is not owned by one person.

**Rules**

- **Changing a decision = editing the line.** Never append a newer version
  below an older one. Two contradictory rows are worse than no doc.
- **Don't silently delete someone else's decision.** Raise it first — in the
  group chat or at standup. Then edit.
- **Agreed something in a meeting? Write it here the same day.** A decision
  that only exists in a chat thread will be re-litigated within a week.
- **Once an open decision is settled**, move it out of §10 into the section it
  belongs to. §10 should only ever hold live questions.
- **Fixed something in §12?** Delete that line. That section rots fastest.

**Avoiding merge conflicts** — four branches, one file:

- `git pull` immediately before editing, not after.
- Commit this file **on its own**, and push straight away. A doc edit sitting
  uncommitted for three days is what causes the painful conflicts.
- Keep lines short and one decision per row. If you both edit, git can merge
  different rows of the same table — but not different halves of one long
  paragraph.
- If you do hit a conflict here, **keep both sides and reconcile by asking**.
  Never resolve a design conflict by picking whichever looks tidier.

**When in doubt**, the schema is the source of truth for data shapes and this
file is the source of truth for intent. If they disagree, that is a bug worth
raising.

---

## 1. What Vinyl is

You answer how you feel today. Three anonymous records arrive, sent by
strangers. You pick one, listen, and may react. You never learn who sent it;
they never learn who you are.

One question, three records, once a day. That rhythm is the product — protect
it when adding features.

---

## 2. Non-negotiables

These are settled. Do not work around them; ask first if something seems to
require it.

| Rule                                          | Meaning                                                                                                                                   |
| --------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| **A recipient never learns the sender**       | `sender_id` is never returned by any API                                                                                                  |
| **A sender never learns who reacted**         | The submitter cannot read the `reactions` table at all                                                                                    |
| **The database enforces this, not the UI**    | RLS + fixed column lists. A UI bug must not be able to leak identity                                                                      |
| **Location is coarse and opt-in**             | Snapped to the nearest city centre on the device, then rounded to 2dp on write as a backstop. Set by the user, never tracked continuously |
| **Real names never leave the owner's device** | The Google name/avatar are private to their own settings screen                                                                           |

The anonymity rules are proven by `supabase/tests/smoke_test.sql` (checks 4, 5,
8, 9, 10, 20, 26–28). If a change breaks one of those checks, the change is wrong.

---

## 3. Identity

Users **do not** appear under their Google name.

- At onboarding the database **generates a username** for them — an
  "Adjective Animal" like `Happy Giraffe` or `Sleepy Tiger` — and they pick a
  **profile picture from a set we provide** (`public.avatars`).
- Nobody types a username. Free text would let someone identify themselves and
  defeat the anonymity model.
- Usernames are **unique**, case-insensitively. Two users never hold the same one.
- A user can tap **"Try another name"** (`reroll_username()`) while onboarding
  is unfinished. Once `onboarding_completed` is true the name is **fixed**.
- The username is stored **exactly as displayed**, as plain text in
  `profiles.username`. There is no slug/label split: nothing looks it up by key.
- **Only the database writes it.** The client cannot choose or edit a username;
  it reads `profiles.username` and shows it.
- Avatar images are **remote**; `avatars.url` points at Supabase Storage. The
  avatar choice is stored as a **slug**, never the label or the URL.
- The Google name and avatar live in `display_name` / `avatar_url`, are private
  to their owner, and are **separate columns** — never reused to hold the
  chosen username or avatar. The app must never show `display_name` as the
  user's name.

Profile pictures and usernames are **display-only**. They are shown on the
user's own profile and settings. They are _not_ attached to a record a
recipient sees.

---

## 4. Onboarding

Runs once, after first sign-in. Gated by `profiles.onboarding_completed`, which
the app can only ever move from `false` to `true`.

| Step                                             | Stores                                                     |
| ------------------------------------------------ | ---------------------------------------------------------- |
| 1. Username (generated) + profile picture        | `profiles.username` (database-written) / `avatar_slug`     |
| 2. Favourite genres, or "I listen to everything" | `profiles.favorite_genres`                                 |
| 3. Location permission                           | `profiles.lat` / `lng`                                     |
| 4. Notification permission                       | Device only — `ReminderPrefs` (see §5)                     |

All four are **skippable**. A user who declines everything still gets a working
app with weaker matching. Nothing here may block reaching the main screen.

---

## 5. Settings

| Setting                        | Backed by                              |
| ------------------------------ | --------------------------------------- |
| Change profile picture         | `profiles.avatar_slug`                 |
| Change favourite genres        | `profiles.favorite_genres`             |
| Daily reminder on / off        | Device only — `ReminderPrefs`          |
| Update or clear location       | `update_my_location()`                 |
| Log out                        | Auth only, no data change              |
| Delete account                 | _(button removed — see §10)_           |
| Q&A / help                     | Static content, no backend             |

---

## 6. The daily loop

1. **Mood question** — asked fresh every day. Required to match. Never stored
   as a preference; asking _is_ the ritual.
2. **Genres** — optional chips, read from `public.genres`. They start on the
   user's `favorite_genres`; whatever is selected replaces the favourites for
   that pull. None selected falls back to the favourites.
3. **Context** — not used. A context match needs both the sender's record and
   the receiver's request to carry one, and neither side ever sets it. The
   column and `p_context` stay, since both are optional and harmless.
4. **Three records arrive.** Pick one, listen, react, optionally shelve it.

Matching never returns your own songs, and never repeats a record you have
already been shown. How the three are chosen is explained in `matching.md`.

---

## 7. Data decisions already made

| Decision                                                                    | Why                                                                                                                                                                             |
| --------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Genres are a **lookup table**, not an enum                                  | Adding a genre is one `INSERT`, not a migration plus an app release                                                                                                             |
| Genres are stored as **slugs** (`k_pop`), displayed as **labels** (`K-pop`) | The app must read `public.genres`, never hardcode a list                                                                                                                        |
| Location lives on the **profile**, copied onto a submission at send time    | No continuous tracking; old records don't move when you relocate                                                                                                                |
| Location is snapped to a **city from a bundled list**, not reverse-geocoded | Android's Geocoder returns suburbs in Australia ("Collingwood"). The list is GeoNames `cities15000` minus suburbs (`PPLX`), in `assets/cities.tsv` — five columns (`name`, `lat`, `lng`, `population`, `countryCode`); GeoNames doesn't supply the country code, so it's backfilled offline. Needed so the Compass screen can show "Chicago, US", not just "Chicago". CC BY 4.0 — credit required |
| **No place-name column**                                                    | Coordinates are the truth; the label comes from the bundled city list, so it works offline                                                                                      |
| Distances are shown **only as bands**                                       | `< 20`, `< 50`, `< 100`, `100+`, `200+`, `1000+`, `2000+`, `3000+ km`. An exact figure would claim precision the data doesn't have                                              |
| A missing location shows **N/A with a reason**                              | If both sides are missing, the reader's own reason wins — it's the one they can fix                                                                                             |
| **No** `default_mood` / `default_context`                                   | Mood is daily; context is not used (§6)                                                                                                                                         |
| Favourite genres are one **nullable `text[]`**                              | Three states in one column, nothing to keep in sync                                                                                                                             |
| Avatars are a **lookup table** with a real foreign key                      | One value, so Postgres enforces it outright. Not an enum — an icon added or retired would otherwise mean a migration plus an app release                                        |
| Usernames are **generated by the database**, unique, stored as plain text   | A fixed list would repeat names and cap the user count. Uniqueness needs the database: RLS hides other users' rows, so a client can't check what is taken                        |
| Username = random adjective + animal, **60 × 60 = 3600** combinations       | After 25 failed picks a 4-digit number is appended (`Happy Giraffe 4821`) so signup never fails on a crowded pool. That starts around 3000 users — widen the lists before then |
| **No `settings` jsonb.** Every preference is its own typed column           | A jsonb bag has no type, no default, no `NOT NULL`, and a key anyone can misspell. Migrations are cheap; add a column                                                            |
| **OS permissions are never stored.** Ask Android at runtime                 | The user can revoke a permission in system settings without the app knowing. A copy in the database is a mirror that silently goes stale. For GPS the state already *is* whether `lat` is null |
| The daily reminder is a **local notification**, its switch stored **on the device** | Cards are pulled, so there is no server event to push; a per-phone reminder has no business in `profiles` |
| Matching uses **small-scale rules, not a learned model**                    | IDF genre weighting, Hacker News freshness, a circulation penalty, one-per-artist, one random slot. A model needs interaction data we don't have. See `matching.md`              |
| **Genre can never outweigh mood**                                           | Genre rarity is scaled to 0–1, so the genre term is at most 2.0 against mood's 3.0. Mood is the question the user answered; one rare genre chip must not override it            |
| **Filtering is always server-side; ranking may move to the device**         | Eligibility is a privacy rule. Ranking is arithmetic over rows that are already anonymous                                                                                       |
| The app **ignores unknown JSON keys**                                       | supabase-kt rejects them by default, so one new server column would crash every older build. Set in `SupabaseClient.kt`                                                         |

---

## 8. Contracts

Exact shapes. Get these wrong and the call fails at runtime.

### Genre slugs

Send `k_pop`, not `K-pop`. A display label is **rejected** by a trigger on both
`submissions.genres` and `profiles.favorite_genres`, with error `23514`.

Read the vocabulary from the table — 23 active rows:

```kotlin
supabase.postgrest.from("genres")
  .select(Columns.list("slug", "label")) { order("sort_order", Order.ASCENDING) }
```

### Usernames

The database owns them. **Read** `profiles.username` from the user's own row;
**never send one**. A client `UPDATE` of `username` is rejected with `42501`, and
a `username` supplied on `INSERT` is overwritten.

"Try another name" is one call and returns the new name:

```kotlin
supabase.postgrest.rpc("reroll_username").decodeAs<String>()
```

It raises `42501` once `onboarding_completed` is true.

### Avatars

Read the set, show the image, store the **slug**:

```kotlin
supabase.postgrest.from("avatars")
  .select(Columns.list("slug", "url")) { order("sort_order", Order.ASCENDING) }
```

Filter on `is_active` when building the picker. A retired entry stays valid on
profiles that already chose it — the foreign key checks that the slug exists,
not that it is still offered.

`profiles.avatar_slug` is a real foreign key, so an unknown slug is rejected by
Postgres with `23503`.

### `profiles.favorite_genres` — three states

| Value             | Means                    | Matching           |
| ----------------- | ------------------------ | ------------------ |
| `null`            | Hasn't answered          | no genre filter    |
| `[]`              | "I listen to everything" | no genre filter    |
| `["jazz","soul"]` | Those genres             | genre term applies |

### RPCs

| Function                                                            | Returns            |
| ------------------------------------------------------------------- | ------------------ |
| `request_recommendations(p_mood, p_context?, p_limit?, p_genres?)`  | `room_card[]`      |
| `get_room(p_limit?)`                                                | `room_card[]`      |
| `get_shelf(p_limit?)`                                               | `room_card[]`      |
| `get_candidates(p_limit?)`                                          | `candidate_card[]` |
| `get_genre_weights()`                                               | `{slug, weight}[]` |
| `commit_recommendations(p_submission_ids, p_mood, p_context?, p_scores?)` | `room_card[]` |
| `submit_song(...)`                                      | `uuid`            |
| `update_my_location(p_lat?, p_lng?)`                    | `void`            |
| `reroll_username()`                                     | `text`            |
| `add_reaction(p_submission_id, p_kind)`                 | `integer`         |
| `get_reactions(p_submission_id)`                        | `{kind, total}[]` |
| `get_my_submissions(p_limit?)`                          | rows              |

`submit_song` requires `p_provider`, `p_provider_track_id`, `p_title`,
`p_artist`, `p_message`, `p_mood`. It takes **`p_attach_location` (boolean)**,
not coordinates.

`p_genres` takes **slugs**. Leave it out and the server uses `favorite_genres`.

There are two ways to fill the room; the app uses the first. The split path
(`get_candidates` → rank on device → `commit_recommendations`) is built but
nothing calls it yet. See `matching.md` §6.

**Do not add fields to `room_card`** until every teammate's build has
`ignoreUnknownKeys = true`. An older build throws `JsonDecodingException` on
every room, shelf and match call. New fields go on `candidate_card` or a new
type.

Shelving is a plain insert/delete on `shelf_items` — RLS covers it. Starring a
kept record is an update of `shelf_items.is_favourite` on the same row; read it
straight from `shelf_items` (it is not on `room_card`).

Writing to `profiles` is a direct upsert or update — RLS confines it to your own
row. Do not add an RPC for it. The exceptions are `update_my_location()`, which
exists for the rounding semantics, and `reroll_username()`, because the client
is not allowed to write `username` at all.

Onboarding finishes with a plain update setting `onboarding_completed = true`.
It only goes `false` → `true`: a client that tries to set it back gets `42501`.

### Errors

| Code       | Meaning                                                           |
| ---------- | ----------------------------------------------------------------- |
| `28000`    | Not signed in                                                     |
| `42501`    | Signed in, but not allowed — usually correct behaviour, not a bug. Also what you get for editing `username`, rerolling after onboarding, or reopening onboarding |
| `23514`    | Bad genre slug                                                    |
| `23503`    | Unknown avatar slug                                               |
| `22023`    | Missing message, title or artist; or more than 10 ids in one commit |
| `PGRST202` | Wrong argument names — the client is calling an old signature     |

---

## 9. Vocabularies

**Mood** (required, one) — `happy`, `sad`, `calm`, `energetic`, `nostalgic`,
`anxious`, `romantic`, `angry`, `hopeful`, `lonely`

**Context** (optional, one; **not used**, see §6) — `commuting`, `studying`,
`working_out`, `relaxing`, `sleeping`, `partying`, `heartbroken`,
`celebrating`, `late_night`

**Reaction** (one per record, changeable) — `heart`, `tears`, `fire`, `hug`,
`goosebumps`, `smile`

Enum values cannot be removed or reordered. Agree additions with the team.

---

## 10. Open decisions

Unresolved. Do not build past these without agreeing them first.

| Question                                   | Why it matters                                                                                                                                                                                                                                |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Delete account** — how?                  | `auth.users` cascades to everything, but the app cannot delete its own auth user with the publishable key. Needs an edge function with `service_role`, which bypasses all RLS. The Settings button was removed until this is decided. Also decide whether a deleted user's sent cards vanish from other people's shelves (the cascade) or stay as anonymous. Google Play requires in-app deletion before release. |
| **Distance vs coordinates** in `room_card` | Returning `distance_km` instead of `lat`/`lng` would mean a recipient never holds a sender's position. Changing `room_card` requires dropping and recreating three functions. The client already bands distances, so the UI works either way. |
| **Where does the genre picker live?**      | The chip component is in the daily questionnaire; onboarding needs the same thing. Shared component, or two copies?                                                                                                                           |
| **Who writes the avatar images?**          | `public.avatars` is seeded empty on purpose — placeholder URLs would make the feature look done while every icon 404s. Someone uploads the files to Supabase Storage, then one INSERT per icon. Until then the picker has nothing to show. |
| **Is the Google name/picture still worth storing?** | `display_name` and `avatar_url` hold the Google identity and nothing reads them. Now that users get a generated username and pick an icon, they may have no purpose left.                                                              |
| **Move the receive flow to the split matcher?** | It would stop over-fetched candidates being marked as seen and let ranking change without a migration. Costs three calls instead of one. The all-SQL path works today.                                                                    |
| **Mood/genre card on Home** — summary or live dropdowns? | It currently shows a summary. Live dropdowns would let a user change today's answer without reopening the questionnaire.                                                                                                     |

---

## 11. Ownership

| Area                                     | Owner   |
| ---------------------------------------- | ------- |
| Database, matchmaking, reactions, Home   | Ivan    |
| Auth, record room, GPS, onboarding flow  | Scott   |
| Submission, questionnaire, accelerometer | Natalie |
| UI / UX                                  | Raina   |

Schema changes go through Ivan. Anything touching `sender_id`, `reactor_id` or
location needs a second pair of eyes.

---

## 12. Known gaps

Not decisions — just things that are true right now and will surprise you.

- **The daily reminder is local and fixed at 6 pm.** One notification a day
  ("Today's record is waiting") nudges the user to open the app; tapping it
  opens the mood question. It is skipped once they've asked for today's music.
  It is local (WorkManager), not push: cards are pulled when the user asks, so
  the server has no "a card arrived" event to send. The on/off choice lives on
  the device (`ReminderPrefs`), not in `profiles` — a reminder belongs to the
  phone it rings on, like the OS permission it depends on (§7). There is no
  time picker yet.
- **Avatars don't work yet.** Onboarding reads `public.avatars`, which is
  **empty**, so the picker shows no icons. (Next is let through when there are
  none, so testers aren't stuck.) Even once rows exist, `AvatarIcon` is still a
  placeholder circle and does not load `avatar.url` — it needs an image loader.
- **Clearing every daily genre chip doesn't switch genre off.** An empty
  selection falls back to `favorite_genres`, so there's no "no genre, just
  today" option.
- **Reactions are promised in the UI** ("Reactions stay anonymous") but no code
  calls `add_reaction` or `get_reactions`.
- **The Home turntable can't be tapped.** It looks like a real object but does
  nothing when pressed; it should open the receive flow.
- **The split matcher is unused.** `get_candidates`, `get_genre_weights`,
  `commit_recommendations` and `Matchmaker.kt` are built and tested, but the
  app still calls `request_recommendations`. See §10.
- **`SubmissionViewModel` is unused.** Nothing references it.
- **GeoNames isn't credited in the app yet.** CC BY 4.0 requires it; the credit
  is only in `assets/cities.tsv`.
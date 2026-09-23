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

| Rule | Meaning |
| --- | --- |
| **A recipient never learns the sender** | `sender_id` is never returned by any API |
| **A sender never learns who reacted** | The submitter cannot read the `reactions` table at all |
| **The database enforces this, not the UI** | RLS + fixed column lists. A UI bug must not be able to leak identity |
| **Location is coarse and opt-in** | Rounded to 2dp (~1.1 km) on write, set by the user, never tracked continuously |
| **Real names never leave the owner's device** | The Google name/avatar are private to their own settings screen |

The anonymity rules are proven by `supabase/tests/smoke_test.sql` (checks 4, 5,
8, 9, 10, 20). If a change breaks one of those checks, the change is wrong.

---

## 3. Identity

Users **do not** appear under their Google name.

- At onboarding they pick a **username from a list we provide** and a
  **profile picture from a set we provide**.
- Free-text usernames are not offered — they would let someone identify
  themselves and defeat the anonymity model.
- The Google name and avatar are still stored, but only the owner ever sees
  them.

Profile pictures and usernames are **display-only**. They are shown on the
user's own profile and settings. They are *not* attached to a record a
recipient sees.

---

## 4. Onboarding

Runs once, after first sign-in. Gated by `profiles.onboarding_completed`.

| Step | Stores |
| --- | --- |
| 1. Pick username + profile picture | `profiles` |
| 2. Favourite genres, or "I listen to everything" | `profiles.favorite_genres` |
| 3. Location permission | `profiles.lat` / `lng` |
| 4. Notification permission | *(not yet in the schema)* |

All four are **skippable**. A user who declines everything still gets a working
app with weaker matching. Nothing here may block reaching the main screen.

---

## 5. Settings

| Setting | Backed by |
| --- | --- |
| Change profile picture | `profiles.avatar_url` |
| Change favourite genres | `profiles.favorite_genres` |
| Enable / disable notifications | *(not yet in the schema)* |
| Update or clear location | `update_my_location()` |
| Log out | Auth only, no data change |
| Delete account | *(not yet built — see open decisions)* |
| Q&A / help | Static content, no backend |

---

## 6. The daily loop

1. **Mood question** — asked fresh every day. Required to match. Never stored
   as a preference; asking *is* the ritual.
2. **Genres** — optional override. Pre-filled from `favorite_genres`.
3. **Context** — not asked. Intended to be inferred from the accelerometer
   (walking → `commuting`, still → `studying`).
4. **Three records arrive.** Pick one, listen, react, optionally shelve it.

Matching never returns your own songs, and never repeats a record you have
already been shown.

---

## 7. Data decisions already made

| Decision | Why |
| --- | --- |
| Genres are a **lookup table**, not an enum | Adding a genre is one `INSERT`, not a migration plus an app release |
| Genres are stored as **slugs** (`k_pop`), displayed as **labels** (`K-pop`) | The app must read `public.genres`, never hardcode a list |
| Location lives on the **profile**, copied onto a submission at send time | No continuous tracking; old records don't move when you relocate |
| **No place-name column** | Coordinates are the truth; reverse-geocode on the client when a label is needed |
| **No** `default_mood` / `default_context` | Mood is daily; context comes from the sensor |
| Favourite genres are one **nullable `text[]`** | Three states in one column, nothing to keep in sync |

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

### `profiles.favorite_genres` — three states

| Value | Means | Matching |
| --- | --- | --- |
| `null` | Hasn't answered | no genre filter |
| `[]` | "I listen to everything" | no genre filter |
| `["jazz","soul"]` | Those genres | genre term applies |

### RPCs

| Function | Returns |
| --- | --- |
| `request_recommendations(p_mood, p_context?, p_limit?)` | `room_card[]` |
| `get_room(p_limit?)` | `room_card[]` |
| `get_shelf(p_limit?)` | `room_card[]` |
| `submit_song(...)` | `uuid` |
| `update_my_location(p_lat?, p_lng?)` | `void` |
| `add_reaction(p_submission_id, p_kind)` | `integer` |
| `get_reactions(p_submission_id)` | `{kind, total}[]` |
| `get_my_submissions(p_limit?)` | rows |

`submit_song` requires `p_provider`, `p_provider_track_id`, `p_title`,
`p_artist`, `p_message`, `p_mood`. It takes **`p_attach_location` (boolean)**,
not coordinates.

Shelving is a plain insert/delete on `shelf_items` — RLS covers it.

Writing to `profiles` is a direct upsert — RLS confines it to your own row. Do
not add an RPC for it; `update_my_location()` is the one exception and exists
for the rounding semantics.

### Errors

| Code | Meaning |
| --- | --- |
| `28000` | Not signed in |
| `42501` | Signed in, but not allowed — usually correct behaviour, not a bug |
| `23514` | Bad genre slug |
| `22023` | Missing message, title or artist |
| `PGRST202` | Wrong argument names — the client is calling an old signature |

---

## 9. Vocabularies

**Mood** (required, one) — `happy`, `sad`, `calm`, `energetic`, `nostalgic`,
`anxious`, `romantic`, `angry`, `hopeful`, `lonely`

**Context** (optional, one) — `commuting`, `studying`, `working_out`,
`relaxing`, `sleeping`, `partying`, `heartbroken`, `celebrating`, `late_night`

**Reaction** (one per record, changeable) — `heart`, `tears`, `fire`, `hug`,
`goosebumps`, `smile`

Enum values cannot be removed or reordered. Agree additions with the team.

---

## 10. Open decisions

Unresolved. Do not build past these without agreeing them first.

| Question | Why it matters |
| --- | --- |
| **Notifications** — local or push? | Push needs a device-token column and a server to send from. Local needs neither. Decide before building the toggle. |
| **Delete account** — how? | `auth.users` cascades to everything, but the app cannot delete its own auth user with the publishable key. Needs an edge function with `service_role`, which bypasses all RLS. |
| **Distance vs coordinates** in `room_card` | Returning `distance_km` instead of `lat`/`lng` would mean a recipient never holds a sender's position. Changing `room_card` requires dropping and recreating three functions. |
| **Where does the genre picker live?** | The chip component is in the daily questionnaire; onboarding needs the same thing. Shared component, or two copies? |
| **Username and picture sets** | Who writes the lists, and are they stored in the app or the database? |

---

## 11. Ownership

| Area | Owner |
| --- | --- |
| Database, matchmaking, reactions | Ivan |
| Auth, record room, GPS, onboarding flow | Scott |
| Submission, questionnaire, accelerometer | Natalie |
| UI / UX | Raina |

Schema changes go through Ivan. Anything touching `sender_id`, `reactor_id` or
location needs a second pair of eyes.

---

## 12. Known gaps

Not decisions — just things that are true right now and will surprise you.

- **`SubmissionRepository` sends `p_lat` / `p_lng`**, which no longer exist, and
  display labels instead of genre slugs. It will fail on both counts the moment
  it is switched off the fake repository.
- **Nothing reads `favorite_genres`.** `request_recommendations` has no genre
  term yet, so both this column and the daily genre chips are write-only.
- **Nothing calls `get_room` or `request_recommendations`.** The receive flow is
  unbuilt, not blocked — the server side has been ready since Sprint 1.
- **Reactions are promised in the UI** ("Reactions stay anonymous") but no code
  calls `add_reaction` or `get_reactions`.
- **`GenreOptions.all` is hardcoded** and missing Rock, Indie, Metal and
  Hip-Hop. Replace it with a read from `public.genres`.

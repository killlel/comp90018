# Vinyl — the matching algorithm

**Owner:** Ivan (`guangyu11`) · Sprint 3

How Vinyl decides which three records you get today, and why it works this
way. The exact contracts (argument names, return types) are in
`database.md` §4. This file explains the reasoning behind them.

| Where it lives | What it is |
| --- | --- |
| `supabase/migrations/20261004000001_matchmaking_v2.sql` | `request_recommendations()`: the whole algorithm in SQL. **This is the one the app calls.** |
| `supabase/migrations/20261004000002_split_matchmaker.sql` | `get_candidates()`, `get_genre_weights()`, `commit_recommendations()`: the split path |
| `supabase/migrations/20261008000001_cap_genre_weight.sql` | Caps the genre term so it can never outweigh mood (current version of both functions above) |
| `supabase/migrations/20261010000002_daily_pull_limit.sql` | One pull a day. Moves both dealers to `app_private` unchanged (`deal_recommendations`, `commit_recommendations_unchecked`); the public names are now wrappers that check the limit first. **Scoring changes go in `app_private.deal_recommendations`.** |
| `Vinyl/app/.../matchmaking/Matchmaker.kt` | The same scoring in Kotlin, for the split path |
| `Vinyl/app/src/test/.../matchmaking/MatchmakerTest.kt` | 15 unit tests, one or two per term |
| `supabase/tests/smoke_test.sql` checks 26–30 | The split path's security and genre weights, tested against a real database |

---

## 1. The short version

Matching runs in two stages:

1. **Filter.** Which records are you *allowed* to get? This is a privacy rule,
   so the server always does it.
2. **Rank.** Of those, which three do you get *first*? This is arithmetic. Each
   record gets a score, then two selection rules pick the final three.

```
score = mood match      (+3)
      + genre overlap   (up to +2; rare genres count more)
      + freshness       (newer counts more, smoothly)
      − circulation     (already shown to many people counts less)

then: one record per artist
then: top 2 by score + 1 picked at random
```

A mood match always outweighs any genre match.

---

## 2. Stage 1: the filter

A record is eligible only if **all** of these hold:

| Rule | Why |
| --- | --- |
| It is active (`is_active`) | A sender can take a record back |
| You did not send it | Getting your own song back is pointless |
| You have never been shown it | Each record reaches you at most once, ever |

This stage **never** moves to the phone. Filtering on the phone would mean
sending the phone the whole pool, records you are not allowed to see included.
Blocking exactly that is what RLS is for (see `database.md` §1).

What survives is turned into an anonymous card **with no `sender_id` in it**
before it leaves the database. Everything after this point works on anonymous
data, so it can run anywhere.

---

## 3. Stage 2: the score

Four terms are added together. The weights are fixed numbers, listed here in
order of importance:

| Term | Weight | In plain words |
| --- | --- | --- |
| Mood | **3.0** | Same mood as the one you picked today? |
| Genre | up to **2.0** | Shares a genre you picked? Rare genres count more |
| Freshness | **1.5**, decaying | How recently it was sent |
| Circulation | **−0.8**, growing | How many people have already been shown it |

**Nobody tuned these weights against data, because there isn't enough data to
tune against.** They state an opinion about what matters, in this order: how
you feel, then what you like, then what is new, then what is fair. If the room
feels wrong, change the weights first.

### Mood (3.0)

If the record's mood equals today's answer, add 3. Otherwise add nothing.

Mood is the question the user actually answered, so it gets the biggest
weight, and the genre term is capped below it (see below).

On the device, a null mood ("let the crate decide") skips this term, and the
other three decide the order. The SQL path always needs a mood.

### Context: not used

The code still has a context term (+1.0 for the same listening moment, like
`commuting`), but **it always adds zero, and we've dropped it from the plan.**
A match needs both the sender's record and the receiver's request to carry a
context, and neither ever does: the write screen doesn't ask, and nothing
infers it. The column and the `p_context` argument stay, because both are
optional and removing them would mean recreating three functions for no gain.

### Genre overlap (up to 2.0), using IDF

**The idea:** matching on a genre that half the pool has tells you very
little. Matching on a genre only three records have tells you a lot.

It is the same reason a search engine ignores the word "the". The technique
is called **inverse document frequency (IDF)**, and it comes from BM25, the
ranking formula behind most text search:

```
rarity(genre) = ln(1 + N / df) / ln(1 + N)

N  = active records in the whole pool
df = how many of them carry this genre
```

The top half is plain IDF. The bottom half is the IDF of a genre only **one**
record carries, which is the largest IDF possible. Dividing by it puts every
rarity between 0 and 1.

With today's 24 records:

| Records with this genre | Rarity | × weight 2.0 |
| --- | --- | --- |
| 1 | 1.00 | 2.00 |
| 3 | 0.68 | 1.37 |
| 6 | 0.50 | 1.00 |
| 12 | 0.34 | 0.68 |
| 24 (every record) | 0.22 | 0.43 |

The `1 +` keeps the value above zero even when every record has the genre.

**Why divide by the maximum:** without it, a single rare genre could score
up to 6.4, more than double a mood match. Once the daily chips were connected,
people picking one genre would have seen genre quietly outrank the mood they
chose. With the cap, the genre term can never exceed 2.0, so mood (3.0) always
wins, and rarer genres still count more than common ones.

**The total is divided by how many genres you picked.** If it weren't, someone
who ticks eight genres would beat someone who ticks one just by ticking more.

```
genre term = 2.0 × (sum of rarity of the matching genres) / (genres you picked)
```

**Which genres are "yours":** the chips selected on the daily questionnaire.
They start on your onboarding favourites, so leaving them alone means your
favourites are used. Whatever is selected **replaces** the favourites for that
pull rather than adding to them. If nothing is selected, the server falls back
to the favourites; if those are empty too (`null`, or "I listen to
everything"), the term is skipped and genre has no effect.

**Rarity is measured over the whole pool**, not over the records you can still
receive. Otherwise your own history would change how rare a genre looks to you.
This is also why the device cannot calculate it. It only sees the 20 candidates
it was sent, a biased sample, so the server sends the numbers ready-made
(`get_genre_weights()`).

### Freshness (1.5, decaying), the Hacker News formula

**The problem with the old rule:** "posted in the last 30 days, +0.5". A record
29 days old got full marks and one 31 days old got nothing. That is a cliff.

**The replacement** is the formula Hacker News uses to rank its front page. It
falls smoothly and never reaches zero:

```
freshness = 1.5 / (age in days + 2) ^ 1.2
```

| Age | Freshness |
| --- | --- |
| Just sent | 0.65 |
| 1 day | 0.40 |
| 3 days | 0.22 |
| 1 week | 0.11 |
| 2 weeks | 0.05 |
| 1 month | 0.02 |

- **`+ 2`** stops a brand-new record from scoring infinitely high.
- **`^ 1.2`** ("gravity") sets how fast it falls. Hacker News uses 1.8, but
  measures in hours. We measure in days and have far fewer posts, so a gentler
  slope keeps old records reachable.

### Circulation (−0.8, growing), fairness

**The problem:** without this, whatever scores best on day one wins every
round. With four users, one person's songs would fill everyone's room, and a
new record would never be seen at all. Recommender research calls this the
rich-get-richer problem.

**The fix:** a small penalty for every person a record has already been shown
to:

```
circulation = −0.8 × ln(1 + times shown)
```

| Already shown to | Penalty |
| --- | --- |
| nobody | 0 |
| 1 person | −0.55 |
| 3 people | −1.11 |
| 10 people | −1.92 |

The logarithm makes it grow more slowly over time. A popular record gets
nudged down, but it never disappears.

"Times shown" is a count across all users, which the device can't see. The
server sends it on each candidate as `times_recommended`: just a number, never
who.

---

## 4. Stage 3: picking the three

The highest three scores are not simply taken. Two rules apply first.

### One record per artist (diversity)

If two records share an artist, only the higher-scoring one is kept.

In a pool of 24, getting three cards by one artist is quite likely, and it
looks broken even when the ranking is technically correct. The full technique,
**MMR** (maximal marginal relevance), penalises *any* similarity between
picks. This rule is its cheap half. It catches the obvious case and costs
almost nothing.

### One random slot (exploration)

The first two slots go to the top two scores. **The third is picked at random**
from the rest.

This is **epsilon-greedy**, a standard method from multi-armed bandit problems:
usually take the best known option, and sometimes try something else. Here,
"sometimes" is fixed at one slot in three. Without it:

- you would only ever see your own mood reflected back, and
- a new record would have to beat established ones before anyone saw it even
  once.

If only one record is asked for, there is no random slot.

---

## 5. A worked example

The pool has 24 records. 12 carry `pop` and 3 carry `jazz`. Today you picked
mood **happy** and genres **{pop, jazz}**.

| | Card A | Card B | Card C |
| --- | --- | --- | --- |
| Mood | happy | sad | happy |
| Genres | pop | jazz | jazz |
| Age | 1 day | just sent | 10 days |
| Already shown to | 0 | 2 | 5 |
| **Mood** | +3.00 | 0 | +3.00 |
| **Genre** 2.0 × rarity ÷ 2 picked | +0.34 | +0.68 | +0.68 |
| **Freshness** | +0.40 | +0.65 | +0.08 |
| **Circulation** | 0 | −0.88 | −1.43 |
| **Score** | **3.74** | **0.46** | **2.33** |

Order: **A, C, B.** (B's parts add to 0.45; the 0.46 is rounding.)

- A wins on mood alone, even though its genre is the common one.
- C has the rare genre *and* the mood, but has already been shown widely.
  Circulation costs it the lead.
- B is the newest record and has the rare genre, but the wrong mood. However
  rare its genre, it can't make up the 3 points of a mood match.

With three slots: A and C take the top two, and the third is a random pick
from everything else, which may or may not be B.

---

## 6. Two ways to run it

The same algorithm exists twice. **Only the first is in use.**

### All in SQL (in use)

```
request_recommendations(p_mood, p_context?, p_limit?, p_genres?)
```

One call does everything: filter, score, pick, save as delivered, return the
cards.

**Its limit:** it saves everything it returns. If the app wanted 20 to rank on
the device and asked for 20, all 20 would be marked as seen, and the 17 not
shown could never be offered again.

### Split (built, nothing calls it yet)

```
get_genre_weights()               rarity (0–1) of every genre; cacheable for the session
get_candidates(p_limit => 20)     read-only: filtered, anonymous, saves nothing
   … Matchmaker.kt ranks and picks on the device …
commit_recommendations(ids, mood) saves only the three actually shown
```

**Why it exists:** to rank on the device without wasting records, and so the
ranking can be changed without a migration.

**The safety catch:** `commit_recommendations` takes submission ids *from the
client*, so it checks every rule from §2 again instead of trusting them.
Otherwise, naming a submission id would be enough to get a match for any
record in the database. Ids that fail the check are dropped silently: a record
that was deactivated between fetch and commit is a timing issue, not an attack.

**Other limits:**

| Limit | Why |
| --- | --- |
| `get_candidates` returns at most 50 | So the pool can't be copied out one call at a time |
| Candidates come back in random order | A server-side order would quietly re-impose a ranking the device can't undo |
| `commit_recommendations` takes at most 10 ids | Same reason as the 50 |
| Candidates use their own type, `candidate_card` | Adding a field to `room_card` would break every older build (see `database.md` §6) |

### Where the line is

| Server, always (security) | Either side (arithmetic) | Server provides the numbers |
| --- | --- | --- |
| Not your own record | Mood | Genre rarity (IDF) |
| Never one you've seen | Genre overlap | Times shown (circulation) |
| Active only | Freshness | |
| No `sender_id` in the output | One per artist, random slot | |

The right-hand column is arithmetic that needs a view of *all* users. The
device can't have that view, so the server sends the result as plain numbers.

---

## 7. Known limitations

Things that are true about the current numbers. They aren't bugs, but anyone
tuning the weights should know them.

- **Clearing every chip doesn't mean "no genre".** An empty selection makes
  the server fall back to the onboarding favourites, so a user can't switch
  genre off for one pull. Today the only way is answering "I listen to
  everything" at onboarding.
- **The two paths compare artists slightly differently.** Kotlin ignores case
  and surrounding spaces ("Adele" = " adele"). SQL compares exactly. This only
  matters for records whose artist names differ only in case or spacing.
- **The random slot is truly random.** Two calls with the same inputs can
  return different third cards. Tests pin it by injecting a seeded `Random`.
- **Weights are opinions, not measurements.** See §3.

---

## 8. Deliberately left out

| Technique | Why not |
| --- | --- |
| Collaborative filtering ("people like you liked…") | Needs thousands of interactions. With 4 accounts it would fit noise |
| Matrix factorisation | Same problem, and harder to explain |
| Learning-to-rank | Needs labelled outcomes we don't have, and would leave nothing honest to say in the report |
| Geographic proximity | Every test account is in Melbourne, so it would change nothing |

Every technique used was chosen because it **works at small scale**, which is
where this app actually is. Each one is a standard, citable idea:

| Term | Source |
| --- | --- |
| Genre rarity | IDF, from BM25 (Robertson & Zaragoza, 2009) |
| Freshness | The Hacker News ranking formula |
| Circulation | Exposure fairness / popularity-bias correction |
| One per artist | The cheap half of MMR (Carbonell & Goldstein, 1998) |
| Random slot | Epsilon-greedy, from multi-armed bandits (Sutton & Barto) |

---

## 9. How it is tested

**On the device, `MatchmakerTest.kt` (15 tests).** `Matchmaker` is plain
Kotlin, with no Android, no network and no clock of its own. The current time
and the random generator are passed in, so every test is repeatable. Each term
is tested on its own:

| Test | Proves |
| --- | --- |
| mood match outranks a mood mismatch | Mood term |
| a rare genre beats a common one | IDF |
| a genre match never outweighs a mood match | The genre cap |
| raw idf from an older server is clamped rather than trusted | The cap holds even before the migration |
| picking many genres does not simply outscore picking one | Dividing by genres picked |
| freshness decays smoothly rather than falling off a cliff | Hacker News curve |
| a heavily circulated record is pushed down | Circulation |
| the same artist is never returned twice | Diversity |
| artist matching ignores case and padding | Diversity |
| the last slot is exploration, not the next best card | Epsilon-greedy |
| a null mood still ranks on the other terms | Null mood |
| no genre preference means no genre filter | `null` / `{}` favourites |
| an unreadable timestamp is treated as new, not ancient | Bad input degrades one card |
| an empty pool returns nothing rather than throwing | Edge case |
| fewer candidates than slots returns what there is | Edge case |

**In the database, `smoke_test.sql`:**

| Check | Proves |
| --- | --- |
| 26 | `get_candidates()` saves nothing |
| 27 | `commit_recommendations()` refuses your own submission |
| 28 | `commit_recommendations()` refuses a record you've already seen |
| 29 | Rarer genres get a higher weight |
| 30 | Every genre weight is between 0 and 1, so genre can't outweigh mood |
| 31 | `get_pull_status()` counts the cards dealt today |
| 32 | A second pull the same day is refused |
| 33 | The day turns over at 06:00 local time, not midnight |

Checks 26–28 are security checks. **If a change breaks one, the change is
wrong.**

---

## 10. Changing it

| You want to… | Change |
| --- | --- |
| Re-weight a term | The `w_*` constants in `app_private.deal_recommendations` (new migration), and `Matchmaker.Weights` to match. Keep `w_genre` below `w_mood` or genre can outrank mood again |
| Make old records fade faster | Raise `gravity` |
| Make popular records fade faster | Raise `w_crowded` / `circulation` |
| Add a scoring term | Both paths, plus a unit test that isolates it |
| Change who is eligible | **Both** `app_private.deal_recommendations` and `app_private.candidate_cards`, **and** the re-check in `app_private.commit_recommendations_unchecked`. All three must agree, or one of them leaks |
| Change the daily limit or the 06:00 turnover | `app_private.pull_status` / `pull_window` (new migration), and `PULL_DAY_STARTS_AT_HOUR` in `data/PullDay.kt` to match |

Never edit an applied migration. Add a new file that re-creates the function.

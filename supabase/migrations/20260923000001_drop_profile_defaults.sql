-- =============================================================================
-- Vinyl — 0007: drop profiles.default_mood and profiles.default_context
-- Owner: Ivan (guangyu11)  |  Sprint 2
--
-- Both were added speculatively in 0001, on the assumption the questionnaire
-- would pre-fill from a remembered preference. Nothing ever read or wrote them,
-- and the design has moved past both:
--
--   * mood is inherently a daily question — a stored default is the wrong shape,
--     and asking is the point of the screen;
--   * context is heading for the accelerometer, where an inferred "walking right
--     now" beats a remembered "usually commuting".
--
-- The preference the profile genuinely should remember — the user's lasting
-- genre taste — belongs in `settings` (jsonb), not a dedicated column.
--
-- Unused columns invite someone to wire them up on the assumption they were
-- designed for a purpose. Removing them keeps the table honest.
--
-- The mood_tag and context_tag ENUMS stay: submissions and recommendations
-- both use them, and they remain the shared vocabulary for the questionnaire.
-- Only these two columns go.
--
-- Idempotent: safe to re-run.
-- =============================================================================

alter table public.profiles
  drop column if exists default_mood,
  drop column if exists default_context;


-- =============================================================================
-- No client change required. Neither column was referenced anywhere in the
-- Android app, and no RPC selected them.
-- =============================================================================

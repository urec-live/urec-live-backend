# Exercise details and private notes

Companion client: app branch `feature/member-experience-2026-11-13`.
Review/apply `migrations/20261109_exercise_notes.sql` before deployment.
No shared database or deployment was changed during implementation.

## Additive API and schema

GET/PUT `/api/exercises/{id}/notes` returns `{ "note": "..." }`.
PUT requires a non-null string of at most 2000 characters; whitespace is trimmed
and the empty string clears the note. Notes are unique per authenticated member
and exercise. Exercise visibility is enforced through ExerciseCatalogService,
so notes cannot be read or written against another member's private exercise.
GET for an exercise with no note returns the empty string.

Exercise catalog/detail responses now include `secondaryMuscles: string[]`.
The nullable exercise.secondary_muscles field stores comma-separated metadata.
Catalog initialization adds metadata only when missing and only to shared exercises,
preserving existing instructions and staff-provided data. The TSV has an optional
eighth column; existing seven-column rows remain valid.

Initial secondary metadata covers two entries:
- Bench Press: Triceps, Shoulders. The broad secondary body-part classification
  follows ACE's barbell chest-press listing in its triceps exercise library:
  https://www.acefitness.org/resources/everyone/exercise-library/body-part/arms/triceps/
- Seated Cable Row: Biceps. Based on Toi Mai's NZ Certificate in Exercise Level 4,
  which lists biceps among seated-row target muscles:
  https://toimai.nz/wp-content/uploads/2023/05/NZC-in-Exercise-Level-4.pdf

These are deliberately limited entries, not claims of complete secondary-muscle
coverage. Existing primary muscle groups remain unchanged. No third-party media
was downloaded or copied.


## Validation and handoff

Java 21 `mvn clean verify`: 329 tests, no failures/errors, 3 skipped.
ExerciseNotesIntegrationTest covers note creation/read/update/clear, isolation
between members, length/null validation, missing exercises, unauthenticated access
and secondary metadata. Existing plan completion tests pass after the date-boundary fix.
Live isolated-H2 HTTP checks passed note PUT/GET and metadata; the app documentation
EXERCISE_DETAILS_TESTING.md records web interaction and preview credentials.

Rollback: revert the exercise-details client before removing the notes API. The
migration includes the optional rollback for the notes table, which discards notes.
The nullable secondary_muscles column may remain in place during an application rollback.

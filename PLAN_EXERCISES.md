# Exercise-based plans

Pair with app branch `feature/member-experience-2026-11-13`.
Apply/review `migrations/20261109_plan_exercises.sql` on the intended database
before deploying this backend, then release the app. No shared database was changed.

Plan item requests may include `exerciseId` alongside the existing muscleGroup,
targetCount and sortOrder fields. The server resolves exercise metadata through
the member-visible catalog, ignoring client-supplied names/equipment. It snapshots
exerciseId, exerciseName and equipmentType on the item. Null exercise fields preserve
legacy muscle goals. These fields are also returned by plan read, update, copy and
today's-plan APIs. Today counts completed sessions by exact exercise ID where present,
or by muscle for legacy items; an unfinished session does not satisfy a goal.

Today's plan completion uses the same server-local timezone for the weekday and session boundaries; unfinished sessions do not count.

The migration adds nullable columns, with no backfill or required dependency changes.
Rollback requires reverting exercise-aware clients/backend before dropping columns;
doing so discards exercise selections while preserving the legacy muscle/count fields.

Validation: Java 21 `mvn clean verify`: 326 tests, 0 failures/errors, 3 skipped.
`PlanExerciseIntegrationTest` covers save/read/update/order/copy, legacy goals, exact
exercise completion and refusal of another member's private exercise. Live isolated-H2
HTTP checks passed auth health, combined catalog filters, and plan create/update/read.
The companion app's WORKOUT_PLAN_TESTING.md gives local preview/manual steps.

# Standalone workouts and optional gym connection

The member app defaults to independent training after sign-in. Profile → Settings → Connect to a gym selects one active facility. The directory connection is a preference, not membership verification. UREC is seeded first; no existing user is automatically connected.

## Contract

- `GET /api/gyms`: active directory (`id`, `name`).
- `GET /api/users/me/gym`: selected active gym, or an empty 200 response when disconnected. `PUT` accepts `{ "gymId": 1 }`; `DELETE` disconnects. Changes require connectivity; open staff help blocks changes server-side. The app additionally blocks changes during a locally active exercise.
- `GET /api/exercises`: paginated `search`, `muscleGroup`, `equipmentType`, `trainingType`, `atMyGym`, `page`, `size` (1–200). Catalog returns global and current-user custom exercises. `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` support custom exercises; delete archives. Logging mode cannot be changed on an existing custom exercise.
- `POST /api/sessions`: additive `exerciseId`, `clientSessionId`, `ownerUsername`; `machineCode` remains optional and legacy name-based requests remain accepted. Each set supports weight/reps, reps-only, seconds, or meters/seconds as prescribed by the exercise. Stable exercise IDs are returned in history. Weight PRs and volume exclude non-weight modes.
- New session IDs are unique per user. A user row lock serializes duplicate requests; a database unique constraint also protects retry IDs. Queue ownership is checked against the authenticated principal.
- Completed machine sessions can sync after disconnecting if the user previously selected that gym. This historical association does not grant current machine actions or facility browsing. Machine/exercise mappings are validated.
- Member machine operations, maps, report creation/status summaries and staff-help creation enforce the selected gym. Own report/help history remains accessible after disconnecting. Gym topics use `/topic/gyms/{id}/machines`; legacy `/topic/machines` is reserved for UREC administrators after activation. Messaging topics stay independent.
- UREC equipment/floor editing and staff queues are scoped to UREC. This release does not introduce staff accounts for additional facilities. Platform user administration and platform analytics retain their existing role model.

## Catalog

`src/main/resources/catalog/exercises.tsv` contains 212 authored exercise records, including logging modes and movement instructions. Legacy aliases preserve existing IDs and equipment mappings instead of creating duplicate variants. The seed is repeatable and fills missing catalog metadata without overwriting authored instructions. Original media URLs are retained for compatibility; the new catalog works without loading external media. No Hevy content or third-party exercise feed was copied. The catalog is an expandable starter library, not a claim to contain every possible exercise or to have undergone clinical review.

## Migration and rollout

1. Back up the target database, review `migrations/20261031_optional_gym.sql`, and apply it using the operator's PostgreSQL connection. It is repeatable and preserves IDs, workout references and disconnected accounts. No shared database migration has been applied by this task.
2. Deploy the backend first with `APP_GYM_SCOPE_ENABLED=false` only during the legacy-client transition. This temporary setting preserves broad legacy facility access and is not the final isolated configuration.
3. Release the updated app, then set `APP_GYM_SCOPE_ENABLED=true` (the default) and restart the backend. Old clients must update to connect from Settings. Native/admin global machine subscriptions require an admin principal after activation.
4. Keep the new columns/tables/catalog on application rollback. An older client requires the temporary compatibility setting; do not drop workout data or private exercise references. Older backend versions do not enforce the new isolation and should not be used as an isolation-preserving rollback.

For the isolated local database, `scripts/run-local-standalone.sh` selects JDK 21 on Homebrew when available, builds the test classpath, and starts localhost:8080 without sourcing `.env`. All its data is disposable and disappears when the server stops. It never connects to the configured shared PostgreSQL database.

## Manual test

1. Start the isolated backend with `./scripts/run-local-standalone.sh`; start the member app with `npm run web` in the app repository. Register a local test account or log in to an existing account in that disposable database.
2. Workout → search Plank → Start exercise → enter 45 seconds → Finish. History must show the exercise and 45 sec; Equipment must be absent before connection.
3. Profile → Settings → Connect to a gym → UREC → Confirm. Equipment must appear. Verify maps and machine check-in/out; out-of-order equipment must still refuse check-in.
4. Start an independent exercise while connected. Settings must block disconnect. Finish it, then disconnect; history and the full catalog must remain available and Equipment must disappear.
5. Create a custom exercise, log it, edit its name/instructions and archive it. Its previous session must remain in history; another account must not see the custom exercise.
6. Disconnect the network after opening an exercise, finish a set, reconnect, and open History or wait 30 seconds. The completed session must sync once. Sign-out and a different account must never replay that queued session.

## Validation record

- Full backend suite: `JAVA_HOME=<JDK21> mvn clean verify -Dspring.profiles.active=test`. The test profile is required for the existing context test; no real database credentials are needed.
- PostgreSQL 18 migration applied twice to a disposable fixture: preserved user/equipment/exercise IDs, left users disconnected, and allowed identical custom names for different owners.
- Member app: TypeScript and Jest checks; lint has existing errors in unrelated chat screens, recorded in the app handoff.
- Browser: independent timed logging and saved history, Settings connection and Equipment visibility verified against the isolated backend. Final restarted app displays weight/reps, reps-only, seconds and meters/seconds history.
- Live protocol smoke: authenticated `/topic/gyms/{id}/machines` subscription received a real check-in update; the test restored the machine and disconnected the demo account.
- Counts: backend 216 passing tests; member app 226 passing tests; dashboard 143 passing tests and production build passing (existing bundle/style/CommonJS warnings).
- `python3 scripts/smoke-standalone.py` verifies the isolated local backend and creates `standalone_demo` / `LocalWorkout2026!` for manual use. The current local service has this disposable account.
- iOS: attempted `npx expo run:ios --device <installed-iPhone-17-Pro-ID> --no-bundler`; blocked because Xcode's Simulator.app is missing. Generated-only tracked CocoaPods changes were restored. Native runtime is not validated.
- Android: pending platform-owner validation. No remote deployment or shared-database migration performed. Delivery is tracked in [ClickUp task 86bcw3kbr](https://app.clickup.com/t/86bcw3kbr).

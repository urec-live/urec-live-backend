## Shared team workflow

Read `TEAM_WORKFLOW.md` before making changes. It defines the shared setup, task ownership, verification, and handoff rules for Codex and Claude Code. Verify architecture notes against source and stay within the current task. Work priorities come from current task instructions.

# CLAUDE.md — UREC Live Backend

## Project Overview

UREC Live is a cross-platform gym management and fitness tracking platform targeting university recreation centers, built by a solo founder with the goal of scaling to commercial gyms as a B2B SaaS product.

**This repo** is the Spring Boot backend API that powers the mobile app and admin dashboard.

---

## Business Context

- **Solo founder** (CS student) building this into a real company
- **Revenue model**: B2B SaaS — gyms pay monthly, users use for free
- **First customer target**: University rec center (free pilot to prove value)
- **Competitive moat**: Real-time equipment availability via QR scanning
- **Current source scope**: Core member/admin APIs plus help requests, equipment issues, plans, progress, floor maps, social profiles, badges, leagues, and messaging. Source presence is not proof of deployed or end-to-end client support.

---

## Architecture

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.3.3, Java 21 |
| Security | Spring Security + JWT (HS512), BCrypt passwords |
| Database | PostgreSQL (Neon cloud) |
| Real-time | STOMP over SockJS (WebSocket) |
| Build | Maven |
| ORM | Spring Data JPA / Hibernate |

### Database Schema

Core tables include `users`, `roles`, `user_roles`, `equipment`, `exercise`, `equipment_exercise`, `workout_sessions`, `workout_sets`, `workout_plans`, `day_plans`, `day_plan_items`, `floor_plans`, `activity_log`, `equipment_issue_reports`, `help_requests`, `user_badges`, `user_league_scores`, `community_messages`, and `direct_messages`.

- Schema auto-managed via `spring.jpa.hibernate.ddl-auto=update`
- `DataInitializer` seeds 40+ exercises across all muscle groups on first startup
- `Equipment` has a `deleted` flag for soft deletes

### Entities

- **User** — username, email, password (BCrypt), roles (M2M), bio, and profile visibility
- **Equipment** — name, code (QR), status (Available/In Use/Reserved/Out of Order — constants in `EquipmentStatuses`, compare case-insensitively), exercises (M2M), deleted flag. Only admins set Out of Order; it blocks check-ins.
- **Exercise** — name, muscleGroup, gifUrl, equipment (M2M)
- **Role** — ROLE_ADMIN, ROLE_USER
- **WorkoutSession** — user, machine, exercise, muscleGroup, startedAt, endedAt, durationSeconds, notes
- **WorkoutSet** — setNumber, reps, weightLbs (per-set tracking within a session)
- **ActivityLog** — eventType (CHECK_IN/CHECK_OUT, … ISSUE_REPORTED/ISSUE_STATUS_CHANGED/ISSUE_WITHDRAWN, HELP_REQUESTED/HELP_STATUS_CHANGED/HELP_CLOSED), username, description, equipmentName, timestamp
- **EquipmentIssueReport** — equipment, reporter (User), severity (`IssueSeverity`: OUT_OF_ORDER/DAMAGED), description (10–1000 chars), status (`IssueStatus`: REPORTED → ACKNOWLEDGED → IN_PROGRESS → RESOLVED), reportedAt, updatedAt, resolvedAt, withdrawnAt (set when the member withdrew it as a mistake; it's then RESOLVED for good). One open report per member per machine (409 otherwise). Reports don't change `Equipment.status`, except that resolving the last open report on an Out of Order machine puts it back to Available.
- **HelpRequest** — a member's "Call staff" at a machine: member, equipment, optional exercise, status (`HelpRequestStatus`: open REQUEST_RECEIVED/ON_THE_WAY/TOO_BUSY, closed RESOLVED/CANCELLED/EXPIRED), staff (who last responded), createdAt, updatedAt, firstResponseAt, closedAt, closedBy (`HelpRequestClosedBy`: STAFF/MEMBER/SYSTEM), `@Version`. One open request per member (409 otherwise). `HelpRequestExpiryJob` expires requests idle for 30 min.
- **WorkoutPlan / DayPlan / DayPlanItem** — reusable weekly plans and ordered muscle-group targets
- **FloorPlan** — floor metadata and equipment positions
- **UserBadge / UserLeagueScore** — achievement and leaderboard state
- **CommunityMessage / DirectMessage** — persisted community and one-to-one chat messages

---

## API Endpoints

### Public / User Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Login → JWT tokens |
| POST | `/api/auth/refresh` | Refresh access token |
| GET | `/api/auth/test` | Health check |
| GET | `/api/machines` | List all equipment |
| GET | `/api/machines/{id}` | Single machine |
| GET | `/api/machines/code/{code}` | Machine by QR code |
| GET | `/api/machines/{id}/exercises` | Exercises for a machine |
| GET | `/api/machines/code/{code}/exercises` | Exercises by machine code |
| PUT | `/api/machines/{id}/status` | Update machine status (broadcasts WS). Out of Order rules: requesting it → 403; checking in to one → 409; other changes return 200 but it stays Out of Order |
| PUT | `/api/machines/code/{code}/status` | Update status by QR code (broadcasts WS); same Out of Order rules |
| GET | `/api/machines/muscle-groups` | All unique muscle groups |
| GET | `/api/machines/exercises/muscle/{group}` | Exercises by muscle group |
| GET | `/api/machines/exercise/{name}` | Machines for an exercise |

### Session Endpoints (Authenticated)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/sessions` | Save a completed workout session |
| GET | `/api/sessions/me` | User's session history (paginated, default 20/page) |
| GET | `/api/sessions/me/stats` | Aggregated stats (total sessions, duration, top exercises, sessions/week) |
| GET | `/api/sessions/me/prs` | Personal records |
| GET | `/api/sessions/me/prs/{exercise}/history` | Weight progression for one exercise |
| GET | `/api/sessions/me/calendar` | Calendar activity |

### Workout Plan Endpoints (Authenticated)

`/api/plans` supports create, current-user listing, update, delete, today's plan, public-plan discovery, copying, and visibility changes. See `WorkoutPlanController` for the exact request contract.

### Equipment Issue Endpoints (Authenticated)

Deliberately outside `/api/machines/**`, which is `permitAll`.

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/equipment-issues` | Report a problem `{equipmentId, severity, description}` → 201; 409 if already open; 404 if machine missing/removed |
| GET | `/api/equipment-issues/me` | Caller's reports, newest first |
| POST | `/api/equipment-issues/{id}/withdraw` | Take back your own open report (filed by mistake) → closed as RESOLVED with `withdrawnAt` set; 404 if not yours; 409 if already closed. The machine's status is left alone |
| GET | `/api/equipment-issues/equipment/{id}` | Open-issue summary for the machine page (counts/status only — no reporter details) |

### Help Request Endpoints (Authenticated)

Members only ever see their own requests (someone else's id → 404), and the member view never names the staff member.

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/help-requests` | Call staff `{equipmentId \| equipmentCode, exerciseName?}` → 201 with `demos`; 409 if one is already open; 404 if the machine is missing/removed |
| GET | `/api/help-requests/me/active` | The open request, or 204 |
| GET | `/api/help-requests/{id}` | One of the caller's requests, open or closed (the app polls this) |
| POST | `/api/help-requests/{id}/received` | "Received help": closes it as RESOLVED by the member; 409 if already closed |
| POST | `/api/help-requests/{id}/cancel` | Cancels it; 409 if already closed |

`demos` (`HelpDemoLinks`): one entry per exercise (the member's, else up to 3 of the machine's, else the machine itself), each with `videoUrl`/`gifUrl` and `videoPlaceholder`/`gifPlaceholder` flags. Until real how-to media exist, every demo gets the placeholder video, and the placeholder GIF unless an admin set a real one (see Configuration).

### Social, Achievement, and Messaging Endpoints

- `/api/social/*` — public profiles, user search, and authenticated profile updates. The current `GET /api/social/users/*` security matcher also permits `/users/search`; narrow it if search should require authentication.
- `/api/badges/me` — the authenticated user's badges.
- `/api/league/me` and `/api/league/leaderboard` — league and leaderboard views.
- `/api/messaging/*` — community history, direct-message history/inbox, and read state.
- STOMP sends use `/app/chat.community.send` and `/app/chat.dm.send`; delivery uses `/topic/chat.community` and user queues.

The checked-out mobile app does not yet consume these social/achievement/messaging APIs. It also calls `POST /api/chat` for an AI chatbot, but this backend has no matching endpoint.

### Admin Endpoints (ROLE_ADMIN only, `@PreAuthorize`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/equipment` | Paginated list with optional `name` filter |
| POST | `/api/admin/equipment` | Create equipment |
| PUT | `/api/admin/equipment/{id}` | Update equipment |
| DELETE | `/api/admin/equipment/{id}` | Soft delete equipment |
| POST | `/api/admin/equipment/{id}/qr` | Generate/assign QR code |
| GET | `/api/admin/exercises` | List all exercises |
| POST | `/api/admin/exercises` | Create exercise |
| PUT | `/api/admin/exercises/{id}` | Update exercise |
| DELETE | `/api/admin/exercises/{id}` | Hard delete exercise |
| POST | `/api/admin/exercises/{id}/equipment` | Link equipment IDs to exercise |
| DELETE | `/api/admin/exercises/{id}/equipment/{equipmentId}` | Unlink equipment from exercise |
| GET | `/api/admin/analytics/live` | Current gym snapshot (counts by status) |
| GET | `/api/admin/analytics/usage?period=week\|month` | Top 10 most/least used, session counts, avg duration |
| GET | `/api/admin/analytics/peak-hours?period=week\|month` | Sessions by hour (0–23) |
| GET | `/api/admin/analytics/users?period=week\|month` | User activity metrics |
| GET | `/api/admin/activity-log?page=0&size=20` | Recent activity feed |
| GET | `/api/admin/users` | List all users (paginated) |
| POST | `/api/admin/users` | Create user |
| PUT | `/api/admin/users/{id}/roles` | Change user roles |
| DELETE | `/api/admin/users/{id}` | Delete user |
| GET | `/api/admin/equipment-issues?includeResolved=false` | Reports grouped by machine (not-working first) |
| GET | `/api/admin/equipment-issues/summary` | Counts: reported, acknowledged, inProgress, affectedMachines |
| PUT | `/api/admin/equipment-issues/{id}/status` | Set one report's status; 409 if the member withdrew it |
| PUT | `/api/admin/equipment-issues/equipment/{id}/status` | Set status on all of a machine's open reports |
| PUT | `/api/admin/equipment-issues/equipment/{id}/out-of-order` | `{outOfOrder}` — take a machine out of service or put it back (never turns In Use into Available) |
| GET | `/api/admin/help-requests` | Open help requests, longest waiting first |
| GET | `/api/admin/help-requests/history?limit=50` | Recently closed requests, newest first |
| PUT | `/api/admin/help-requests/{id}/status` | `{status: ON_THE_WAY \| TOO_BUSY}`; other statuses → 400; closed → 409 |
| POST | `/api/admin/help-requests/{id}/done` | "Done helping": closes it as RESOLVED by staff |

`/api/admin/floor-plans` supports floor-plan CRUD, unassigned-equipment listing, bulk position updates, and removing equipment from a floor. `PUT /api/admin/equipment/{id}/position` updates one equipment position.

Admin-made status changes (the out-of-order endpoint, `PUT /api/admin/equipment/{id}`, and the automatic return to service) go through `MachineStatusService`, which broadcasts to `/topic/machines` (after the same `CvAvailabilityService` pass as the machine endpoints) and logs `EQUIPMENT_OUT_OF_ORDER` / `EQUIPMENT_BACK_IN_SERVICE`.

Role names are stored verbatim (the admin UI assigns `ADMIN`), so `hasRole('ADMIN')` only matches `ROLE_ADMIN`. `AdminUserController`, `AdminEquipmentIssueController` and `AdminHelpRequestController` accept both via `hasAuthority('ADMIN') or hasAuthority('ROLE_ADMIN')`. Clients poll the help request endpoints (every 5 s) rather than use the WebSocket.

### WebSocket

- STOMP broker over SockJS at `/ws`; `/topic` and `/queue` are broker prefixes.
- Machine changes publish to `/topic/machines`; community messages publish to `/topic/chat.community`; direct messages use user queues.
- The HTTP handshake is public. A Bearer token on the STOMP `CONNECT` frame is validated by `StompAuthChannelInterceptor` and establishes the messaging principal.

---

## Project Structure

```
src/main/java/com/ureclive/urec_live_backend/
├── controller/
│   ├── AuthController.java
│   ├── MachineController.java
│   ├── WorkoutSessionController.java
│   ├── WorkoutPlanController.java
│   ├── AdminEquipmentController.java
│   ├── AdminExerciseController.java
│   ├── AdminAnalyticsController.java
│   ├── AdminUserController.java
│   ├── EquipmentIssueController.java       # member issue reporting
│   ├── AdminEquipmentIssueController.java  # admin Equipment Issues view
│   ├── HelpRequestController.java          # member "Call staff"
│   ├── AdminHelpRequestController.java     # staff queue
│   ├── AdminFloorPlanController.java
│   ├── SocialController.java, BadgeController.java, LeagueController.java
│   └── ChatMessagingController.java
├── service/
│   ├── AuthService.java
│   ├── AdminEquipmentService.java
│   ├── AdminExerciseService.java
│   ├── AdminAnalyticsService.java
│   ├── WorkoutSessionService.java
│   ├── ActivityLogService.java
│   ├── EquipmentIssueService.java
│   ├── AdminEquipmentIssueService.java
│   ├── MachineStatusService.java   # admin status changes: save + broadcast + log
│   ├── HelpRequestService.java, AdminHelpRequestService.java
│   ├── HelpDemoLinks.java          # demo media (placeholders for now)
│   ├── HelpRequestExpiryJob.java   # @Scheduled: expires idle requests
│   ├── WorkoutPlanService.java, BadgeService.java, LeagueService.java
│   └── CvAvailabilityService.java
├── entity/
│   ├── User.java, Equipment.java, Exercise.java, Role.java
│   ├── WorkoutSession.java, WorkoutSet.java, ActivityLog.java
│   ├── EquipmentIssueReport.java, IssueSeverity.java, IssueStatus.java
│   ├── EquipmentStatuses.java      # Equipment.status values, isOutOfOrder()
│   ├── HelpRequest.java, HelpRequestStatus.java, HelpRequestClosedBy.java
│   ├── WorkoutPlan.java, DayPlan.java, DayPlanItem.java, FloorPlan.java
│   ├── UserBadge.java, UserLeagueScore.java
│   └── CommunityMessage.java, DirectMessage.java
├── repository/
│   ├── UserRepository.java, EquipmentRepository.java
│   ├── ExerciseRepository.java, RoleRepository.java
│   ├── WorkoutSessionRepository.java, WorkoutSetRepository.java
│   ├── ActivityLogRepository.java
│   ├── EquipmentIssueReportRepository.java
│   ├── HelpRequestRepository.java
│   └── plan, floor-plan, badge, league, and messaging repositories
├── dto/              # Request + response objects for every endpoint
├── security/
│   ├── JwtUtil.java, JwtAuthenticationFilter.java
│   └── CustomUserDetailsService.java
├── config/
│   ├── WebSocketConfig.java, SecurityConfig.java, CorsConfig.java
│   ├── StompAuthChannelInterceptor.java
│   └── SchedulingConfig.java   # @EnableScheduling
└── DataInitializer.java
```

---

## Coding Conventions

- Follow existing controller → service → repository layering
- Use DTOs for all API request/response — never expose entities directly
- All new endpoints need proper error handling with meaningful HTTP status codes
- Use `@Valid` and Bean Validation annotations on request DTOs
- Keep controllers thin — business logic belongs in service classes
- All `/api/admin/**` endpoints must use `@PreAuthorize` with an admin check (see the role-name note under Admin Endpoints)

## Testing

- Unit tests: Mockito, no Spring context (e.g. `service/EquipmentIssueServiceTest`, `service/HelpRequestServiceTest`)
- Integration tests run the whole app on in-memory H2: the `test` profile (`src/test/resources/application-test.properties`), so no database or `.env` is needed. Two base classes, both `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")` (one shared, cached context), mint real JWTs (`tokenFor(username, role)`) and have request helpers:
  - `controller/ApiIntegrationTestSupport` (equipment issues, out of order): `machine(...)` resets a machine to Available
  - `controller/HelpRequestApiTestSupport` (help requests): `machine(...)` finds or creates a test machine
  - Test data outlives each test and is shared across classes, so give a test that changes shared data its own machine/exercise.
- Suites: `mvn test -Dtest='*EquipmentIssue*,*OutOfOrder*,MachineStatusServiceTest'` (equipment issues / out of order) and `mvn test -Dtest='HelpRequest*,HelpDemoLinksTest,AdminHelpRequestServiceTest'` (help requests). `UrecLiveBackendApplicationTests.contextLoads` still needs the real DB env vars.
- API smoke tests against a running server: `scripts/smoke-test-equipment-issues.sh` (see `EQUIPMENT_ISSUES_TESTING.md`) and `scripts/smoke-test-help-requests.sh` (see `HELP_REQUESTS_TESTING.md`)

---

## How to Run Locally

```bash
# Requires: Java 21, Maven
mvn spring-boot:run
# Or with a specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Connects through the configured PostgreSQL environment values. `DataInitializer` seeds exercise data on first run. Do not point local startup at production.

---

## Configuration

`src/main/resources/application.properties` expects database and JWT values from the environment, binds to `0.0.0.0:8080` by default, and configures 24-hour access tokens plus seven-day refresh tokens. Check `.env.example` for variable names; never document or print values.

The CV adapter is controlled by `APP_CV_SECONDARY_ENABLED`, `APP_CV_SECONDARY_BASE_URL`, `APP_CV_SECONDARY_CONFIDENCE_THRESHOLD`, and `APP_CV_SECONDARY_CACHE_TTL_MS`.

Help requests (defaults live in the `@Value` annotations, so nothing is needed in `application.properties`):
- `app.help-requests.expire-after-minutes` (30) and `app.help-requests.expiry-check-ms` (60000)
- `app.help-requests.placeholder-video-url` and `app.help-requests.placeholder-gif-url`: the placeholder demo media (an MDN sample MP4 and a "Coming soon" GIF). A blank value switches that placeholder off. As environment variables: `APP_HELPREQUESTS_PLACEHOLDERVIDEOURL`, `APP_HELPREQUESTS_PLACEHOLDERGIFURL`.

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
- **Current stage**: MVP feature-complete. Admin dashboard API, session persistence, and analytics are all built.

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

Tables: `users`, `roles`, `user_roles`, `equipment`, `exercise`, `equipment_exercise`, `workout_sessions`, `workout_sets`, `activity_log`, `help_requests`

- Schema auto-managed via `spring.jpa.hibernate.ddl-auto=update`
- `DataInitializer` seeds 40+ exercises across all muscle groups on first startup
- `Equipment` has a `deleted` flag for soft deletes

### Entities

- **User** — username, email, password (BCrypt), roles (M2M)
- **Equipment** — name, code (QR), status (Available/In Use/Reserved), exercises (M2M), deleted flag
- **Exercise** — name, muscleGroup, gifUrl, equipment (M2M)
- **Role** — ROLE_ADMIN, ROLE_USER
- **WorkoutSession** — user, machine, exercise, muscleGroup, startedAt, endedAt, durationSeconds, notes
- **WorkoutSet** — setNumber, reps, weightLbs (per-set tracking within a session)
- **ActivityLog** — eventType (CHECK_IN/CHECK_OUT, … HELP_REQUESTED/HELP_STATUS_CHANGED/HELP_CLOSED), username, description, equipmentName, timestamp
- **HelpRequest** — a member's "Call staff" at a machine: member, equipment, optional exercise, status (`HelpRequestStatus`: open REQUEST_RECEIVED/ON_THE_WAY/TOO_BUSY, closed RESOLVED/CANCELLED/EXPIRED), staff (who last responded), createdAt, updatedAt, firstResponseAt, closedAt, closedBy (`HelpRequestClosedBy`: STAFF/MEMBER/SYSTEM), `@Version`. One open request per member (409 otherwise). `HelpRequestExpiryJob` expires requests idle for 30 min.

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
| PUT | `/api/machines/{id}/status` | Update machine status (broadcasts WS) |
| PUT | `/api/machines/code/{code}/status` | Update status by QR code (broadcasts WS) |
| GET | `/api/machines/muscle-groups` | All unique muscle groups |
| GET | `/api/machines/exercises/muscle/{group}` | Exercises by muscle group |
| GET | `/api/machines/exercise/{name}` | Machines for an exercise |

### Session Endpoints (Authenticated)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/sessions` | Save a completed workout session |
| GET | `/api/sessions/me` | User's session history (paginated, default 20/page) |
| GET | `/api/sessions/me/stats` | Aggregated stats (total sessions, duration, top exercises, sessions/week) |

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
| GET | `/api/admin/users/{id}` | User detail |
| PUT | `/api/admin/users/{id}/role` | Change user role |
| GET | `/api/admin/help-requests` | Open help requests, longest waiting first |
| GET | `/api/admin/help-requests/history?limit=50` | Recently closed requests, newest first |
| PUT | `/api/admin/help-requests/{id}/status` | `{status: ON_THE_WAY \| TOO_BUSY}`; other statuses → 400; closed → 409 |
| POST | `/api/admin/help-requests/{id}/done` | "Done helping": closes it as RESOLVED by staff |

Role names are stored verbatim (the admin UI assigns `ADMIN`), so `hasRole('ADMIN')` only matches `ROLE_ADMIN`. `AdminHelpRequestController` accepts both via `hasAuthority('ADMIN') or hasAuthority('ROLE_ADMIN')`. Clients poll the help request endpoints (every 5 s) rather than use the WebSocket.

### WebSocket

- STOMP broker over SockJS at `/ws`
- Broadcasts updated machine list to `/topic/machines` on every status change
- All connected clients receive real-time equipment updates

---

## Project Structure

```
src/main/java/com/ureclive/urec_live_backend/
├── controller/
│   ├── AuthController.java
│   ├── MachineController.java
│   ├── WorkoutSessionController.java
│   ├── AdminEquipmentController.java
│   ├── AdminExerciseController.java
│   ├── AdminAnalyticsController.java
│   ├── AdminUserController.java
│   ├── HelpRequestController.java        # member "Call staff"
│   └── AdminHelpRequestController.java   # staff queue
├── service/
│   ├── AuthService.java
│   ├── AdminEquipmentService.java
│   ├── AdminExerciseService.java
│   ├── AdminAnalyticsService.java
│   ├── WorkoutSessionService.java
│   ├── ActivityLogService.java
│   ├── HelpRequestService.java, AdminHelpRequestService.java
│   ├── HelpDemoLinks.java                # demo media (placeholders for now)
│   └── HelpRequestExpiryJob.java         # @Scheduled: expires idle requests
├── entity/
│   ├── User.java, Equipment.java, Exercise.java, Role.java
│   ├── WorkoutSession.java, WorkoutSet.java, ActivityLog.java
│   ├── HelpRequest.java, HelpRequestStatus.java, HelpRequestClosedBy.java
├── repository/
│   ├── UserRepository.java, EquipmentRepository.java
│   ├── ExerciseRepository.java, RoleRepository.java
│   ├── WorkoutSessionRepository.java, WorkoutSetRepository.java
│   ├── ActivityLogRepository.java
│   └── HelpRequestRepository.java
├── dto/              # Request + response objects for every endpoint
├── security/
│   ├── JwtUtil.java, JwtAuthenticationFilter.java
│   └── CustomUserDetailsService.java
├── config/
│   ├── WebSocketConfig.java, SecurityConfig.java, CorsConfig.java
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
- All `/api/admin/**` endpoints must use `@PreAuthorize("hasRole('ADMIN')")`

---

## How to Run Locally

```bash
# Requires: Java 21, Maven
mvn spring-boot:run
# Or with a specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Connects to Neon PostgreSQL (see `application.properties`). DataInitializer seeds exercise data on first run.

---

## Configuration

`src/main/resources/application.properties`:
- DB: `jdbc:postgresql://ep-twilight-surf-adiwldgq-pooler.c-2.us-east-1.aws.neon.tech/...` (Neon)
- `server.address=0.0.0.0`, `server.port=8080`
- JWT: 24h access token expiry, 7d refresh token expiry

Help requests (defaults live in the `@Value` annotations, so nothing is needed in `application.properties`):
- `app.help-requests.expire-after-minutes` (30) and `app.help-requests.expiry-check-ms` (60000)
- `app.help-requests.placeholder-video-url` and `app.help-requests.placeholder-gif-url`: the placeholder demo media (an MDN sample MP4 and a "Coming soon" GIF). A blank value switches that placeholder off. As environment variables: `APP_HELPREQUESTS_PLACEHOLDERVIDEOURL`, `APP_HELPREQUESTS_PLACEHOLDERGIFURL`.

## Testing

- Unit tests: Mockito, no Spring context (e.g. `service/HelpRequestServiceTest`)
- Integration tests extend `controller/HelpRequestApiTestSupport` (`@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")`). The `test` profile (`src/test/resources/application-test.properties`) uses in-memory H2, so no database or `.env` is needed. The base class mints real JWTs (`tokenFor(username, role)`), finds or creates test machines (`machine(...)`), and has request helpers. Test data outlives each test, so give a test that changes shared data its own machine/exercise.
- `mvn test -Dtest='HelpRequest*,HelpDemoLinksTest,AdminHelpRequestServiceTest'` runs the help request suite; `UrecLiveBackendApplicationTests.contextLoads` still needs the real DB env vars.
- API smoke test against a running server: `scripts/smoke-test-help-requests.sh` (see `HELP_REQUESTS_TESTING.md`)

---

## What Needs to Be Built Next

### Priority 1: Multi-Tenancy Foundation (Phase 3 prep)
Not urgent, but for new tables consider adding a `gym_id` column now to avoid a painful migration later. Eventually equipment, exercises, and users will be scoped to a gym tenant.

### Priority 2: Export Endpoints
- `GET /api/admin/equipment/export` — CSV export of equipment list (planned, not yet built)

### Priority 3: Push Notification Infrastructure (Phase 2)
- Device token registration endpoint for push notifications

---

## Project Roadmap

- **Phase 1 (NOW — mostly complete)**: MVP backend is feature-complete
- **Phase 2**: AI workout plans (Claude API), smartwatch sync, push notifications
- **Phase 3**: Multi-tenant SaaS, billing (Stripe), chatbot
- **Phase 4**: CCTV computer vision, trainer marketplace, API platform

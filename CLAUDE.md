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

Tables: `users`, `roles`, `user_roles`, `equipment`, `exercise`, `equipment_exercise`, `workout_sessions`, `workout_sets`, `activity_log`, `equipment_issue_reports`

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
- **ActivityLog** — eventType (CHECK_IN/CHECK_OUT, … ISSUE_REPORTED/ISSUE_STATUS_CHANGED), username, description, equipmentName, timestamp
- **EquipmentIssueReport** — equipment, reporter (User), severity (`IssueSeverity`: OUT_OF_ORDER/DAMAGED), description (10–1000 chars), status (`IssueStatus`: REPORTED → ACKNOWLEDGED → IN_PROGRESS → RESOLVED), reportedAt, updatedAt, resolvedAt. One open report per member per machine (409 otherwise). Never changes `Equipment.status`.

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

### Equipment Issue Endpoints (Authenticated)

Deliberately outside `/api/machines/**`, which is `permitAll`.

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/equipment-issues` | Report a problem `{equipmentId, severity, description}` → 201; 409 if already open; 404 if machine missing/removed |
| GET | `/api/equipment-issues/me` | Caller's reports, newest first |
| GET | `/api/equipment-issues/equipment/{id}` | Open-issue summary for the machine page (counts/status only — no reporter details) |

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
| GET | `/api/admin/equipment-issues?includeResolved=false` | Reports grouped by machine (not-working first) |
| GET | `/api/admin/equipment-issues/summary` | Counts: reported, acknowledged, inProgress, affectedMachines |
| PUT | `/api/admin/equipment-issues/{id}/status` | Set one report's status |
| PUT | `/api/admin/equipment-issues/equipment/{id}/status` | Set status on all of a machine's open reports |

Note: role names are stored verbatim (the admin UI assigns `ADMIN`), so `hasRole('ADMIN')` only matches `ROLE_ADMIN`. `AdminUserController` and `AdminEquipmentIssueController` accept both via `hasAuthority('ADMIN') or hasAuthority('ROLE_ADMIN')`.

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
│   ├── EquipmentIssueController.java       # member issue reporting
│   └── AdminEquipmentIssueController.java  # admin Equipment Issues view
├── service/
│   ├── AuthService.java
│   ├── AdminEquipmentService.java
│   ├── AdminExerciseService.java
│   ├── AdminAnalyticsService.java
│   ├── WorkoutSessionService.java
│   ├── ActivityLogService.java
│   ├── EquipmentIssueService.java
│   └── AdminEquipmentIssueService.java
├── entity/
│   ├── User.java, Equipment.java, Exercise.java, Role.java
│   ├── WorkoutSession.java, WorkoutSet.java, ActivityLog.java
│   ├── EquipmentIssueReport.java, IssueSeverity.java, IssueStatus.java
├── repository/
│   ├── UserRepository.java, EquipmentRepository.java
│   ├── ExerciseRepository.java, RoleRepository.java
│   ├── WorkoutSessionRepository.java, WorkoutSetRepository.java
│   ├── ActivityLogRepository.java
│   └── EquipmentIssueReportRepository.java
├── dto/              # Request + response objects for every endpoint
├── security/
│   ├── JwtUtil.java, JwtAuthenticationFilter.java
│   └── CustomUserDetailsService.java
├── config/
│   ├── WebSocketConfig.java, SecurityConfig.java, CorsConfig.java
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

- Unit tests: Mockito, no Spring context (e.g. `service/EquipmentIssueServiceTest`)
- Integration tests: `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")` — the `test` profile (`src/test/resources/application-test.properties`) uses in-memory H2, so no database or `.env` is needed. Mint tokens with `JwtUtil.generateToken(userDetailsService.loadUserByUsername(...))`.
- `mvn test -Dtest='*EquipmentIssue*'` runs the equipment issue suite; `UrecLiveBackendApplicationTests.contextLoads` still needs the real DB env vars.
- API smoke test against a running server: `scripts/smoke-test-equipment-issues.sh` (see `EQUIPMENT_ISSUES_TESTING.md`)

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

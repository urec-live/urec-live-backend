# CLAUDE.md — UREC Live Backend

## Project Overview

UREC Live is a cross-platform gym management and fitness tracking platform targeting university recreation centers, built by a solo founder with the goal of scaling to commercial gyms as a B2B SaaS product.

**This repo** is the Spring Boot backend API that powers the mobile app and (soon) the admin dashboard.

---

## Business Context

- **Solo founder** (CS student) building this into a real company
- **Revenue model**: B2B SaaS — gyms pay monthly, users use for free
- **First customer target**: University rec center (free pilot to prove value)
- **Competitive moat**: Real-time equipment availability via QR scanning — no other gym app does this
- **Current stage**: MVP ~80% complete. The admin dashboard is the final piece needed before beta launch.

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

Tables: `users`, `roles`, `user_roles`, `equipment`, `exercise`, `equipment_exercise`

- Schema auto-managed via `spring.jpa.hibernate.ddl-auto=update`
- `DataInitializer` seeds 40+ exercises across all muscle groups on first startup
- Role-based auth structure is in place (ROLE_USER, ROLE_ADMIN) but admin endpoints don't exist yet

### Existing API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Login → JWT tokens |
| POST | `/api/auth/refresh` | Refresh access token |
| GET | `/api/machines` | List all equipment |
| GET | `/api/machines/{id}` | Single machine |
| GET | `/api/machines/code/{code}` | Machine by QR code |
| PUT | `/api/machines/{id}/status` | Update machine status |
| PUT | `/api/machines/code/{code}/status` | Update status by QR code |
| GET | `/api/machines/muscle-groups` | All muscle groups |
| GET | `/api/machines/exercises/muscle/{group}` | Exercises by muscle group |
| GET | `/api/machines/exercise/{name}` | Machines for an exercise |

### WebSocket

- STOMP broker broadcasting to `/topic/machines` on every status change
- All connected clients receive real-time equipment status updates

---

## What's Complete

1. **Authentication** — Full register/login/refresh flow with JWT (24h access, 7d refresh)
2. **Equipment CRUD** — Full machine model with QR code mapping
3. **Real-time availability** — WebSocket/STOMP broadcasting on status changes
4. **Exercise library** — 40+ exercises seeded, linked to equipment, organized by muscle group
5. **Session tracking** — Check-in/check-out updates machine status and tracks session data

---

## What Needs to Be Built Next (Priority Order)

### Priority 1: Admin Dashboard API Endpoints (CRITICAL — blocks beta launch)

The admin dashboard is a separate Angular 17+ web app, but it needs backend endpoints. Build these:

**Equipment Management (Admin only)**
- `POST /api/admin/equipment` — Create new equipment record
- `PUT /api/admin/equipment/{id}` — Update equipment details
- `DELETE /api/admin/equipment/{id}` — Remove equipment
- `POST /api/admin/equipment/{id}/qr` — Generate/assign QR code to equipment
- `GET /api/admin/equipment/export` — Export equipment list (CSV)

**Exercise Management (Admin only)**
- `POST /api/admin/exercises` — Create exercise
- `PUT /api/admin/exercises/{id}` — Update exercise
- `DELETE /api/admin/exercises/{id}` — Remove exercise
- `POST /api/admin/exercises/{id}/equipment` — Link exercise to equipment

**Analytics & Monitoring (Admin only)**
- `GET /api/admin/analytics/usage` — Equipment usage stats (most used, peak hours)
- `GET /api/admin/analytics/sessions` — Session analytics (daily/weekly active users)
- `GET /api/admin/analytics/live` — Current gym snapshot (active users, occupied machines)
- `GET /api/admin/activity-log` — Recent activity feed

**User Management (Admin only)**
- `GET /api/admin/users` — List all users (paginated)
- `GET /api/admin/users/{id}` — User detail
- `PUT /api/admin/users/{id}/role` — Change user role

All `/api/admin/**` endpoints must be secured with `@PreAuthorize("hasRole('ADMIN')")`.

### Priority 2: Session History API

Currently session data lives only in the mobile app's context state. It needs to be persisted server-side:

- `POST /api/sessions` — Save a completed workout session
- `GET /api/sessions/me` — Get current user's session history (paginated)
- `GET /api/sessions/me/stats` — Aggregated stats (total sessions, total duration, favorite exercises)

New table needed: `workout_sessions` (id, user_id, machine_id, exercise_id, started_at, ended_at, duration_seconds, notes)

### Priority 3: Multi-Tenancy Foundation (Phase 3 prep)

Not urgent, but when designing new tables and endpoints, keep in mind:
- Eventually each "gym" will be a tenant
- Equipment, exercises, and users will be scoped to a gym
- Consider adding a `gym_id` column to new tables now to avoid a painful migration later

---

## Coding Conventions

- Follow existing patterns in the codebase (controller → service → repository)
- Use DTOs for API request/response (don't expose entities directly)
- All new endpoints need proper error handling with meaningful HTTP status codes
- Use `@Valid` and Bean Validation annotations on request DTOs
- Write Javadoc on service methods
- Keep controllers thin — business logic belongs in service classes

---

## How to Run Locally

```bash
# Requires: Java 21, Maven, PostgreSQL
mvn spring-boot:run
# Or with a specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The app connects to PostgreSQL (check `application.properties` for connection string).
DataInitializer seeds exercise data on first run.

---

## Project Roadmap Context

This backend supports a 4-phase product roadmap:
- **Phase 1 (NOW)**: MVP — Admin dashboard API is the last missing piece
- **Phase 2**: AI workout plans (Claude API), smartwatch sync, push notifications
- **Phase 3**: Multi-tenant SaaS, billing (Stripe), chatbot
- **Phase 4**: CCTV computer vision, trainer marketplace, API platform
# Backend Authentication Setup

The backend uses Spring Security, BCrypt, stateless HS512 JWT access/refresh tokens, and PostgreSQL.

## Local configuration

Use JDK 21. Copy `.env.example` to an ignored local `.env`, populate the required database/JWT variables, and load them into the process before startup. Never place credentials or JWT secrets in committed properties or documentation.

On macOS/Linux with a shell-compatible file:

```bash
set -a
source .env
set +a
mvn spring-boot:run
```

Spring Boot does not automatically source `.env`. Use a development database/Neon branch; startup can seed data and update the schema.

The default origin is http://localhost:8080.

## Auth endpoints

| Method | Endpoint | Purpose |
| --- | --- | --- |
| POST | `/api/auth/register` | Create a user and return access/refresh tokens |
| POST | `/api/auth/login` | Authenticate and return access/refresh tokens |
| POST | `/api/auth/refresh` | Exchange a valid refresh token for new tokens |
| GET | `/api/auth/test` | Public auth-route health check |

Access tokens currently expire after 24 hours and refresh tokens after seven days.

## Implementation locations

- `controller/AuthController.java`
- `service/AuthService.java`
- `security/CustomUserDetailsService.java`
- `security/JwtAuthenticationFilter.java`
- `security/JwtUtil.java`
- `config/SecurityConfig.java`
- `config/StompAuthChannelInterceptor.java`

The REST filter authenticates Bearer tokens on protected HTTP requests. The STOMP interceptor separately reads a Bearer token from the `CONNECT` frame.

## Validation

Use development credentials and do not paste live tokens into documentation.

```bash
curl http://localhost:8080/api/auth/test
```

Then verify register/login/refresh with an API client, a protected request, an expired-access-token refresh, and an invalid-refresh-token failure. A local result does not prove production CORS, secrets, database connectivity, or hosted WebSockets.

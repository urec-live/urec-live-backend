# UREC Live Backend - Authentication Setup Guide

This document describes the authentication system implementation using Spring Boot, JWT, and PostgreSQL.

## Prerequisites

- Java 21+
- Maven 3.8+
- PostgreSQL 12+
- Node.js 18+ (for frontend)

## Backend Setup

### 1. Database Setup

Create a PostgreSQL database:

```sql
CREATE DATABASE urec_live_db;
```

Update the database credentials in `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/urec_live_db
spring.datasource.username=postgres
spring.datasource.password=your_password_here
```

### 2. JWT Secret Configuration

Update the JWT secret in `src/main/resources/application.properties`. For production, use a strong, random key:

```properties
jwt.secret=your_super_secret_jwt_key_that_should_be_at_least_256_bits_long_for_security_purposes_in_production
jwt.expiration=86400000
jwt.refreshExpiration=604800000
```

### 3. Build and Run

```bash
# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

The backend will start at `http://localhost:8080`

## API Endpoints

### Authentication Endpoints

#### Register a New User
- **Endpoint**: `POST /api/auth/register`
- **Request Body**:
```json
{
  "username": "newuser",
  "email": "user@example.com",
  "password": "securePassword123"
}
```
- **Response**:
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
  "username": "newuser",
  "email": "user@example.com"
}
```

#### Login
- **Endpoint**: `POST /api/auth/login`
- **Request Body**:
```json
{
  "username": "newuser",
  "password": "securePassword123"
}
```
- **Response**:
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
  "username": "newuser",
  "email": "user@example.com"
}
```

#### Refresh Access Token
- **Endpoint**: `POST /api/auth/refresh`
- **Request Body**:
```json
{
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9..."
}
```
- **Response**:
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
  "username": "newuser",
  "email": "user@example.com"
}
```

#### Test Endpoint
- **Endpoint**: `GET /api/auth/test`
- **Response**: `Auth endpoint is working!`

## Project Structure

```
src/main/java/com/ureclive/urec_live_backend/
├── controller/
│   └── AuthController.java          # REST endpoints
├── service/
│   └── AuthService.java              # Business logic
├── entity/
│   ├── User.java                     # User entity
│   └── Role.java                     # Role entity
├── repository/
│   ├── UserRepository.java           # User data access
│   └── RoleRepository.java           # Role data access
├── security/
│   ├── CustomUserDetailsService.java # Load user details
│   ├── JwtAuthenticationFilter.java  # JWT validation filter
│   └── JwtUtil.java                  # JWT token utilities
├── config/
│   └── SecurityConfig.java           # Spring Security configuration
└── dto/
    ├── RegisterRequest.java
    ├── LoginRequest.java
    ├── AuthResponse.java
    └── RefreshTokenRequest.java
```

## Security Features

- **Password Encoding**: BCrypt with salt
- **JWT Tokens**: HS512 algorithm
- **Token Expiration**: 24 hours for access token, 7 days for refresh token
- **CORS**: Enabled for all origins (configure for production)
- **STATELESS**: No server-side session storage

## Database Schema

### Users Table
```sql
CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(255) NOT NULL UNIQUE,
  email VARCHAR(255) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT true
);
```

### Roles Table
```sql
CREATE TABLE roles (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL UNIQUE
);
```

### User Roles Junction Table
```sql
CREATE TABLE user_roles (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);
```

## Testing with cURL

```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","email":"test@example.com","password":"password123"}'

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"password123"}'

# Refresh Token
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"your_refresh_token_here"}'
```

## Troubleshooting

### Database Connection Issues
- Ensure PostgreSQL is running
- Check database URL and credentials in `application.properties`
- Verify the database exists

### JWT Errors
- Check that JWT secret is properly configured
- Ensure token is included in Authorization header as: `Bearer <token>`
- Verify token hasn't expired

### CORS Issues
- For development, CORS is enabled for all origins
- For production, update SecurityConfig to restrict origins

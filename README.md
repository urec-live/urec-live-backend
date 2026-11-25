# UREC Live Backend

## Local development with Docker

From the backend repository root, create a file named `.env.dev` with your own credentials. Generate each value separately with `openssl rand -hex 64` or an equivalent random generator.

```dotenv
UREC_DEV_DB_PASSWORD=<random value>
UREC_DEV_JWT_SECRET=<different random value>
```

Keep the file for subsequent runs. Add `UREC_DEV_GOOGLE_CLIENT_ID` if you need Google sign-in.

For the full stack, clone the backend, member app, and admin repos as sibling directories named `urec-live-backend`, `urec-live-app`, and `urec-live-admin`. Run this from the backend repo.

```sh
docker compose --env-file .env.dev -f compose.dev.yaml up -d --build
```

The API is available at `http://localhost:11080`. Check startup at `http://localhost:11080/api/auth/test`. The `urec-live-dev` project initializes its own Postgres database and keeps it in the `urec-live-dev_postgres-data` volume. For native Expo testing, set `EXPO_PUBLIC_BACKEND_ORIGIN` to `http://localhost:11080` on the iOS simulator or `http://10.0.2.2:11080` on the Android emulator.

The member app is on `http://localhost:11081` and the admin dashboard is on `http://localhost:11082`. Both route to the same development API and Postgres volume.

With only the backend repo cloned, start the API and Postgres by targeting the `backend` service.

```sh
docker compose --env-file .env.dev -f compose.dev.yaml up -d --build backend
```

FROM node:22-bookworm-slim AS app-build
WORKDIR /src
COPY urec-live-app/package.json urec-live-app/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY urec-live-app/ ./
ARG EXPO_PUBLIC_GOOGLE_CLIENT_ID
ENV EXPO_NO_TELEMETRY=1
ENV CI=1
COPY urec-live-backend/deploy-dev/networkConfig.web.ts ./services/
RUN npx --no-install expo export --platform web

FROM nginx:stable-alpine AS app
COPY urec-live-backend/deploy-dev/expo.conf /etc/nginx/conf.d/default.conf
COPY urec-live-backend/deploy-dev/backend*.conf /etc/nginx/urec/
COPY --from=app-build /src/dist/ /usr/share/nginx/html/

FROM node:20-bookworm-slim AS admin-build
WORKDIR /src
COPY urec-live-admin/package.json urec-live-admin/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY urec-live-admin/ ./
ENV NG_CLI_ANALYTICS=false
COPY urec-live-backend/deploy-dev/environment.prod.ts ./src/environments/
RUN npm run build

FROM nginx:stable-alpine AS admin
COPY urec-live-backend/deploy-dev/angular.conf /etc/nginx/conf.d/default.conf
COPY urec-live-backend/deploy-dev/backend*.conf /etc/nginx/urec/
COPY --from=admin-build /src/dist/urec-live-admin/browser/ /usr/share/nginx/html/

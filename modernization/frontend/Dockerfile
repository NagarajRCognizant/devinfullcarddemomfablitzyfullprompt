# Static hosting for the converted React screens, built from a clean clone.
#
# The bundle is produced in the first stage, so the checkout is the only input. The realm the browser
# is sent to is baked in at build time because it is public configuration of a public client; no client
# secret is passed here, and passing one would put it in the bundle.
FROM node:22-alpine AS build

WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY tsconfig.json vite.config.ts index.html ./
COPY src ./src

ARG VITE_KEYCLOAK_URL=http://127.0.0.1:8088
ARG VITE_KEYCLOAK_REALM=carddemo
ARG VITE_KEYCLOAK_CLIENT_ID=carddemo-ui
ARG VITE_KEYCLOAK_REDIRECT_URI
ARG VITE_KEYCLOAK_POST_LOGOUT_REDIRECT_URI
RUN npm run build

FROM nginx:1.27-alpine

COPY --from=build /src/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --retries=10 \
  CMD wget -q -O - http://127.0.0.1/ >/dev/null || exit 1

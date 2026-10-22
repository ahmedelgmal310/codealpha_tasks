# ---- dependencies (production only) ----
FROM node:24-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force

# ---- runtime ----
FROM node:24-alpine

# Filled in by the pipeline: --build-arg BUILD_ID=$(Build.BuildId) --build-arg GIT_COMMIT=$(Build.SourceVersion)
ARG BUILD_ID=local
ARG GIT_COMMIT=unknown

ENV NODE_ENV=production \
    PORT=3000 \
    BUILD_ID=${BUILD_ID} \
    GIT_COMMIT=${GIT_COMMIT}

WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY package.json ./
COPY src ./src
COPY public ./public

# Run as the unprivileged "node" user (UID 1000) that ships with the official image.
# A numeric UID lets platforms like Kubernetes verify runAsNonRoot.
USER 1000

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD ["wget", "-q", "-O", "/dev/null", "http://127.0.0.1:3000/health"]

CMD ["node", "src/server.js"]

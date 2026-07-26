# Single-origin production image: the React bundle is built and then baked into the Spring
# Boot jar as static resources, so the deployed app serves the dashboard and the API from one
# host. That removes cross-origin configuration from the deployment entirely — no CORS origin
# to keep in sync, no VITE_API_BASE_URL to rebuild the frontend for.
#
# backend/Dockerfile still builds the API on its own, which is what docker-compose uses for
# local development (Vite serves the frontend there with its own dev server).

# --- stage 1: the dashboard ---
FROM node:20-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
# No VITE_API_BASE_URL: an empty base keeps every request same-origin.
RUN npm run build

# --- stage 2: the API, with the dashboard on its classpath ---
FROM maven:3.9-eclipse-temurin-21 AS api
WORKDIR /app
COPY backend/pom.xml .
RUN mvn -q dependency:go-offline
COPY backend/src ./src
COPY --from=web /web/dist ./src/main/resources/static
RUN mvn -q clean package -DskipTests

# --- stage 3: runtime ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=api /app/target/tradejournal-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

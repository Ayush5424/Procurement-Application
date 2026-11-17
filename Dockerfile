# Stage 1: Build React frontend
FROM node:20-alpine AS frontend-builder
WORKDIR /app/frontend

COPY frontend/package*.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# Stage 2: Build Spring Boot JAR with frontend static resources
FROM maven:3.9.9-eclipse-temurin-21-alpine AS backend-builder
WORKDIR /app

# Copy backend pom and source
COPY backend/pom.xml ./backend/
COPY backend/src ./backend/src

# Copy built frontend assets into Spring Boot static resources
COPY --from=frontend-builder /app/frontend/dist/ ./backend/src/main/resources/static/

WORKDIR /app/backend
RUN mvn clean package -DskipTests

# Stage 3: Lightweight Production JRE runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Include submission directory for batch processing outputs
COPY submission ./submission

COPY --from=backend-builder /app/backend/target/procurement-engine-*.jar app.jar

ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

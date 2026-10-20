FROM node:22-alpine AS frontend-build
WORKDIR /build/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS backend-build
WORKDIR /build/backend
COPY backend/pom.xml ./pom.xml
COPY backend/src ./src
COPY --from=frontend-build /build/frontend/dist ./src/main/resources/static
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=backend-build /build/backend/target/daily-farm-log-0.1.0.jar ./app.jar
ENV UPLOAD_DIR=/var/data/uploads
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

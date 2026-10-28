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

FROM mysql:8.4.13 AS mysql-tools
RUN set -eux; mkdir -p /mysql-tools/bin /mysql-tools/lib; \
    cp /usr/bin/mysqldump /mysql-tools/bin/; \
    ldd /usr/bin/mysqldump > /tmp/mysql-libs; \
    for lib in $(cat /tmp/mysql-libs | tr '[:space:]' '\n' | sed -n '/^\//p'); do cp -L "$lib" /mysql-tools/lib/; done

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN apk add --no-cache postgresql-client
COPY --from=mysql-tools /mysql-tools /opt/mysql
RUN /opt/mysql/lib/ld-linux-x86-64.so.2 --library-path /opt/mysql/lib /opt/mysql/bin/mysqldump --version
COPY --from=backend-build /build/backend/target/daily-farm-log-0.1.0.jar ./app.jar
ENV UPLOAD_DIR=/var/data/uploads
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# Swarnabhoomi — Daily Farm Log

A Marathi-first web application with a React frontend and Java Spring Boot API. Diary entries and uploaded photo bytes are stored in MySQL. Photos uploaded before database photo storage was added remain readable from `backend/uploads/` when that folder is available.

## Run locally

Requirements: Java 17+, Maven, Node.js 20+, and Docker Desktop (for the local MySQL service).

1. From the project root, start MySQL: `docker compose up -d mysql`.
2. In one terminal: `cd backend` then `mvn spring-boot:run`.
3. In another terminal: `cd frontend` then `npm install` and `npm run dev`.
4. Open the Vite URL in your browser. The API runs on `http://localhost:8080`.

The backend reads `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, and `MYSQL_SSL_MODE`. The local Compose database uses development-only credentials. For a different MySQL server, set these variables before starting Spring Boot. Do not use the local defaults in a deployed environment.

The weather lookup reads active district alerts from the India Meteorological Department (IMD) public nowcast RSS feed. PIN codes are mapped to a district through `api.postalpincode.in` (a non-government postal lookup service); set `POSTAL_API_URL` to use another compatible PIN lookup endpoint. The IMD feed provides district-level alerts, not exact farm conditions, so users can select the observed weather manually and save the entry. The app handles unavailable or non-JSON API responses with a Marathi error message.

The app uses MySQL for both diary entries and photo bytes in local and hosted environments. New photos are stored in the `farm_photo` table as `LONGBLOB` data (up to 10 MB) and served by the `/api/photos/{id}` endpoint. Existing filesystem photos remain readable as a legacy fallback when their files are available.

## Client demo deployment

The root `Dockerfile` packages the React app into the Spring Boot server. `render.yaml` configures only the Singapore web service; it does not provision a MySQL server. Point `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, and `MYSQL_SSL_MODE` in the Render Dashboard at the existing MySQL server that should hold the app's data. Keep credentials out of Git. The database host must be reachable from Render; `localhost` on Render refers to the app container. New photo uploads are stored in MySQL and do not depend on a Render disk.

The previous local H2 file at `backend/data/swarnabhoomi.mv.db` is not deleted, but its records are not automatically copied into MySQL. Back up/export and import those records after choosing the target MySQL server.

Render persistent disks require a paid web service; storage is billed separately. Review [Render's current pricing](https://render.com/pricing) before creating the service. To deploy, push this project to a Git provider and create a Render Blueprint from its repository, or use the connected Render integration. The generated `*.onrender.com` URL is then shareable.

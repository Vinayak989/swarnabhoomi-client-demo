# Swarnabhoomi — Daily Farm Log

A Marathi-first web application with a React frontend and Java Spring Boot API. Diary entries and uploaded photo bytes are stored in MySQL. Photos uploaded before database photo storage was added remain readable from `backend/uploads/` when that folder is available.

## Run locally

Requirements: Java 17+, Maven, Node.js 20+, and a running MySQL server. For your PC-installed MySQL service, use `127.0.0.1:3306`; Docker Desktop is optional.

1. Start the MySQL Windows service and create the database: `CREATE DATABASE swarnabhoomi CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`.
2. In a PowerShell terminal, set `MYSQL_HOST=127.0.0.1`, `MYSQL_PORT=3306`, `MYSQL_DATABASE=swarnabhoomi`, `MYSQL_USER` to your MySQL username, and `MYSQL_PASSWORD` to the password for that account. Keep these values local; do not commit them.
3. In one terminal: `cd backend` then `mvn spring-boot:run`.
4. In another terminal: `cd frontend` then `npm install` and `npm run dev`.
5. Open the Vite URL in your browser. The API runs on `http://localhost:8080`.

The optional Compose MySQL container is mapped to `127.0.0.1:3307` so it can run beside a native MySQL service. If you choose it, point the backend at port `3307` and use the development credentials in `compose.yaml`.

The backend reads `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, and `MYSQL_SSL_MODE`. The local Compose database uses development-only credentials. For a different MySQL server, set these variables before starting Spring Boot. Do not use the local defaults in a deployed environment.

The weather lookup reads active district alerts from the India Meteorological Department (IMD) public nowcast RSS feed. PIN codes are mapped to a district through `api.postalpincode.in` (a non-government postal lookup service); set `POSTAL_API_URL` to use another compatible PIN lookup endpoint. The IMD feed provides district-level alerts, not exact farm conditions, so users can select the observed weather manually and save the entry. The app handles unavailable or non-JSON API responses with a Marathi error message.

The app uses MySQL for both diary entries and photo bytes in local and hosted environments. New photos are stored in the `farm_photo` table as `LONGBLOB` data (up to 10 MB) and served by the `/api/photos/{id}` endpoint. Existing filesystem photos remain readable as a legacy fallback when their files are available.

## Temporary client demo from your PC

Keep MySQL bound to your PC's loopback interface (`127.0.0.1:3306`). Do not expose or forward port `3306`. You can share only the React web port (`5173`) through an HTTPS tunnel such as ngrok. Start the tunnel first and copy its exact hostname (for example, `example.ngrok-free.app`).

In the frontend PowerShell terminal, set `VITE_ALLOWED_HOSTS` to that hostname, then run `npm run dev`. In the backend terminal, set `APP_CORS_ALLOWED_ORIGINS` to `http://localhost:5173,https://<your-tunnel-hostname>`, set `APP_ACCESS_ENABLED=true`, choose `APP_ACCESS_USERNAME` and a temporary `APP_ACCESS_PASSWORD`, and start Spring Boot. The app's Marathi login screen will ask your client for those credentials. Use the same exact HTTPS origin in the CORS setting; the code now permits only the configured origins and Vite hosts. API preflight requests are allowed through so browser login works. Stop the tunnel after the demo.

PowerShell can prompt for passwords without echoing them: `$secure = Read-Host 'Password' -AsSecureString; $env:MYSQL_PASSWORD = [System.Net.NetworkCredential]::new('', $secure).Password`. Environment variables set this way apply only to that terminal and its child process. Repeat for `APP_ACCESS_PASSWORD` in the backend terminal.

## Client demo deployment

The root `Dockerfile` packages the React app into the Spring Boot server. `render.yaml` configures only the Singapore web service; it does not provision a MySQL server. Point `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, and `MYSQL_SSL_MODE` in the Render Dashboard at the existing MySQL server that should hold the app's data. Keep credentials out of Git. The database host must be reachable from Render; `localhost` on Render refers to the app container. New photo uploads are stored in MySQL and do not depend on a Render disk.

The previous local H2 file at `backend/data/swarnabhoomi.mv.db` is not deleted, but its records are not automatically copied into MySQL. Back up/export and import those records after choosing the target MySQL server.

Render persistent disks require a paid web service; storage is billed separately. Review [Render's current pricing](https://render.com/pricing) before creating the service. To deploy, push this project to a Git provider and create a Render Blueprint from its repository, or use the connected Render integration. The generated `*.onrender.com` URL is then shareable.

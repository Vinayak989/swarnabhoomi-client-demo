# Swarnabhoomi — Daily Farm Log

A Marathi-first web application with a React frontend and Java Spring Boot API. Diary entries and uploaded photo bytes are stored in PostgreSQL. Photos uploaded before database photo storage was added remain readable from `backend/uploads/` when that folder is available.

## Run locally

Requirements: Java 17+, Maven, Node.js 20+, and PostgreSQL. Docker Desktop is optional.

1. Start PostgreSQL or run `docker compose up -d postgres` from the project root. The Compose service creates the local `swarnabhoomi` database.
2. For a different PostgreSQL server, set `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DATABASE`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `POSTGRES_SSL_MODE` in the backend terminal. Keep passwords local; do not commit them.
3. In one terminal: `cd backend` then `mvn spring-boot:run`.
4. In another terminal: `cd frontend` then `npm install` and `npm run dev`.
5. Open the Vite URL in your browser. The API runs on `http://localhost:8080`.

The Compose PostgreSQL service is mapped to `127.0.0.1:5432`. Its local-only development credentials are defined in `compose.yaml` and match the backend defaults.

The backend reads `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DATABASE`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `POSTGRES_SSL_MODE`. The local Compose database uses development-only credentials. For Render or another hosted PostgreSQL server, set these variables to the provider's connection details before starting Spring Boot. Render Postgres internal connections use the database's internal hostname; external connections require TLS, so set `POSTGRES_SSL_MODE=require` for an external endpoint when required by the provider.

The weather lookup reads active district alerts from the India Meteorological Department (IMD) public nowcast RSS feed. PIN codes are mapped to a district through `api.postalpincode.in` (a non-government postal lookup service); set `POSTAL_API_URL` to use another compatible PIN lookup endpoint. The IMD feed provides district-level alerts, not exact farm conditions, so users can select the observed weather manually and save the entry. The app handles unavailable or non-JSON API responses with a Marathi error message.

The app uses PostgreSQL for both diary entries and photo bytes in local and hosted environments. New photos are stored in the `farm_photo` table as `bytea` data (up to 10 MB) and served by the `/api/photos/{id}` endpoint. Existing filesystem photos remain readable as a legacy fallback when their files are available.

## Temporary client demo from your PC

Keep PostgreSQL bound to your PC's loopback interface (`127.0.0.1:5432`). Do not expose or forward port `5432`. You can share only the React web port (`5173`) through an HTTPS tunnel such as ngrok. Start the tunnel first and copy its exact hostname (for example, `example.ngrok-free.app`).

In the frontend PowerShell terminal, set `VITE_ALLOWED_HOSTS` to that hostname, then run `npm run dev`. In the backend terminal, set `APP_CORS_ALLOWED_ORIGINS` to `http://localhost:5173,https://<your-tunnel-hostname>`, set `APP_ACCESS_ENABLED=true`, choose `APP_ACCESS_USERNAME` and a temporary `APP_ACCESS_PASSWORD`, and start Spring Boot. The app's Marathi login screen will ask your client for those credentials. Use the same exact HTTPS origin in the CORS setting; the code now permits only the configured origins and Vite hosts. API preflight requests are allowed through so browser login works. Stop the tunnel after the demo.

PowerShell can prompt for passwords without echoing them: `$secure = Read-Host 'Password' -AsSecureString; $env:POSTGRES_PASSWORD = [System.Net.NetworkCredential]::new('', $secure).Password`. Environment variables set this way apply only to that terminal and its child process. Repeat for `APP_ACCESS_PASSWORD` in the backend terminal.

## Client demo deployment

The root `Dockerfile` packages the React app into the Spring Boot server. `render.yaml` configures the Singapore web service but does not provision a PostgreSQL database. Set `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DATABASE`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `POSTGRES_SSL_MODE` in the Render Dashboard to the PostgreSQL provider's connection details. For Render Postgres, use the internal hostname when the database and web service are in the same region. Keep credentials out of Git. New photo uploads are stored in PostgreSQL and do not depend on a Render disk. Render's Free Postgres has a 30-day lifetime and no backups; use a paid database or an external PostgreSQL provider for ongoing client data.

Existing records in MySQL or the previous local H2 file at `backend/data/swarnabhoomi.mv.db` are not automatically copied into PostgreSQL. Export and migrate them separately before removing or shutting down the old database.

Render persistent disks require a paid web service; storage is billed separately. Review [Render's current pricing](https://render.com/pricing) before creating the service. To deploy, push this project to a Git provider and create a Render Blueprint from its repository, or use the connected Render integration. The generated `*.onrender.com` URL is then shareable.

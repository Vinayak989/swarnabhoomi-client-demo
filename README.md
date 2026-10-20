# Swarnabhoomi — Daily Farm Log

A Marathi-first web application with a React frontend and Java Spring Boot API. Diary entries and uploaded photo bytes are stored in a file-based H2 database. Photos uploaded before database photo storage was added remain readable from `backend/uploads/` when that folder is available.

## Run locally

Requirements: Java 17+, Maven, Node.js 20+. No database server is required. Docker Desktop is optional.

1. H2 starts automatically when Spring Boot launches. By default, its database file is `backend/data/swarnabhoomi.mv.db`.
2. To store the H2 database elsewhere, set `H2_FILE_PATH` to a file path without the `.mv.db` suffix. Optional credentials are `H2_USER` and `H2_PASSWORD`.
3. In one terminal: `cd backend` then `mvn spring-boot:run`.
4. In another terminal: `cd frontend` then `npm install` and `npm run dev`.
5. Open the Vite URL in your browser. The API runs on `http://localhost:8080`.

To run the packaged web app with Docker, use `docker compose up --build` from the project root and open `http://localhost:8080`. Compose stores the H2 database and photo uploads in a named local volume so they survive container recreation. To enable WhatsApp reminders, create a `.env` file in the project root with the WhatsApp settings listed below before starting Compose; the file is ignored by Git.

The backend reads `H2_FILE_PATH` for the database file location, plus optional `H2_USER` and `H2_PASSWORD` values. The JDBC URL sets `DB_CLOSE_DELAY=-1`, so H2 stays open until the Java process exits even if all database connections close. Do not place the H2 file inside a temporary directory if its data must survive restarts.

The weather lookup reads active district alerts from the India Meteorological Department (IMD) public nowcast RSS feed. PIN codes are mapped to a district through `api.postalpincode.in` (a non-government postal lookup service); set `POSTAL_API_URL` to use another compatible PIN lookup endpoint. The IMD feed provides district-level alerts, not exact farm conditions, so users can select the observed weather manually and save the entry. The app handles unavailable or non-JSON API responses with a Marathi error message.

The app uses H2 for both diary entries and photo bytes. New photos are stored in the `farm_photo` table as binary large objects (up to 10 MB) and served by the `/api/photos/{id}` endpoint. Existing filesystem photos remain readable as a legacy fallback when their files are available.

## Temporary client demo from your PC

The local H2 file stays on your PC. You can share the web app through an HTTPS tunnel such as ngrok. Start the tunnel first and copy its exact hostname (for example, `example.ngrok-free.app`).

In the frontend PowerShell terminal, set `VITE_ALLOWED_HOSTS` to that hostname, then run `npm run dev`. In the backend terminal, set `APP_CORS_ALLOWED_ORIGINS` to `http://localhost:5173,https://<your-tunnel-hostname>`, set `APP_ACCESS_ENABLED=true`, choose `APP_ACCESS_USERNAME` and a temporary `APP_ACCESS_PASSWORD`, and start Spring Boot. The app's Marathi login screen will ask your client for those credentials. Use the same exact HTTPS origin in the CORS setting; the code now permits only the configured origins and Vite hosts. API preflight requests are allowed through so browser login works. Stop the tunnel after the demo.

Environment variables set in PowerShell apply only to that terminal and its child process. Set `APP_ACCESS_PASSWORD` in the backend terminal for the demo login.

## Client demo deployment

The root `Dockerfile` packages the React app into the Spring Boot server. `render.yaml` configures the Singapore web service to write H2 data and uploads under `/var/data` on a 1 GB persistent disk. This requires a paid web service; Render currently lists its smallest paid web compute plan at $7/month and persistent disks at $0.25/GB per month. A disk-backed service runs as a single instance and has brief downtime during deploys. Review [Render's current pricing](https://render.com/pricing) before syncing the Blueprint.

Existing records in PostgreSQL, MySQL, or the former H2 file under `/tmp` are not automatically copied into the persistent H2 database. Back up and migrate them separately before syncing the Blueprint if they need to be retained.

Render persistent disks require a paid web service; storage is billed separately. Review [Render's current pricing](https://render.com/pricing) before creating the service. To deploy, push this project to a Git provider and create a Render Blueprint from its repository, or use the connected Render integration. The generated `*.onrender.com` URL is then shareable.

## Marathi WhatsApp reminders

The **स्मरणपत्रे** tab stores the Marathi reminder reason, requested date and time, and the opted-in mobile number in H2. The backend checks due reminders every 15 seconds and sends them through Meta's WhatsApp Cloud API. Reminder times use India Standard Time (`Asia/Kolkata`). API responses show only a masked mobile number.

To enable delivery, configure a Meta WhatsApp Business sender and an approved Marathi utility message template with three body parameters for the reason, date, and time. For example, the template body can be `स्वर्णभूमी स्मरणपत्र: {{1}}. तारीख: {{2}}, वेळ: {{3}}.`. The configured template name and language code must match the template in WhatsApp Manager.

Set these backend environment variables locally or in the deployed service's environment settings. For Docker Compose, put the required values in the root `.env` file (for example, `WHATSAPP_ACCESS_TOKEN=...`); Compose passes them into the backend container:

| Variable | Required | Purpose |
| --- | --- | --- |
| `WHATSAPP_ACCESS_TOKEN` | Yes | Meta Cloud API access token |
| `WHATSAPP_PHONE_NUMBER_ID` | Yes | Registered WhatsApp Business sender's phone number ID |
| `WHATSAPP_REMINDER_TEMPLATE` | Yes | Approved template name |
| `WHATSAPP_TEMPLATE_LANGUAGE` | No | Template language code; defaults to `mr` |
| `WHATSAPP_API_VERSION` | No | Graph API version; defaults to `v23.0` |
| `REMINDER_POLL_INTERVAL_MS` | No | Scheduler interval in milliseconds; defaults to `15000` |

For the Render Blueprint, set `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID`, and `WHATSAPP_REMINDER_TEMPLATE` in the service's Environment settings after creating or syncing the service. The Blueprint deliberately does not contain these secret or account-specific values. Redeploy after setting them, then confirm the reminders page no longer shows the WhatsApp configuration warning. The number ID must be the Meta **phone number ID**, not the public phone number, and the template name and language must exactly match an approved template in WhatsApp Manager.

The form requires the person creating the reminder to confirm WhatsApp opt-in. A reminder is marked as sent when Meta accepts the message request; this app does not yet consume delivery-status webhooks. Failed sends are marked for review and are not retried automatically.

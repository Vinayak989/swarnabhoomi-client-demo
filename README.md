# Swarnabhoomi — Daily Farm Log

A Marathi-first web application with a React frontend and Java Spring Boot API. By default, diary entries and uploaded photo bytes are stored in a file-based H2 database. Production deployments can use PostgreSQL for relational data and a private S3 bucket for photos and database backups.

## Run locally

Requirements: Java 17+, Maven, Node.js 20+. No database server is required. Docker Desktop is optional.

1. H2 starts automatically when Spring Boot launches. By default, its database file is `backend/data/swarnabhoomi.mv.db`.
2. To store the H2 database elsewhere, set `H2_FILE_PATH` to a file path without the `.mv.db` suffix. Optional credentials are `H2_USER` and `H2_PASSWORD`.
3. In one terminal: `cd backend` then `mvn spring-boot:run`. Spring Boot loads the optional project-root `.env` file for local environment settings.
4. In another terminal: `cd frontend` then `npm install` and `npm run dev`.
5. Open the Vite URL in your browser. The API runs on `http://localhost:8080`.

To run the packaged web app with Docker, use `docker compose up --build` from the project root and open `http://localhost:8080`. Compose stores the H2 database and photo uploads in a named local volume so they survive container recreation. To enable the optional AI assistant, start Ollama with `docker compose --profile ai up -d ollama`, then download the model once with `docker compose exec ollama ollama pull qwen2.5vl:3b`. The Ollama model is stored in its own named volume, and Ollama is only reachable inside the Compose network; it is not published as a host port. Web-push reminders use `WEB_PUSH_PUBLIC_KEY`, `WEB_PUSH_PRIVATE_KEY`, and `WEB_PUSH_SUBJECT` in the ignored root `.env` file.

For a shareable EC2 deployment, configure `APP_ACCESS_ENABLED=true`, `APP_ACCESS_USERNAME`, and a strong `APP_ACCESS_PASSWORD` in the server's ignored `.env` file. Keep the application bound to localhost and serve it through the EC2 host's HTTPS reverse proxy. This EC2 setup uses Apache with a Certbot-managed Let's Encrypt certificate; allow inbound TCP ports 80 and 443 in the security group, and redirect all HTTP requests to HTTPS.

The backend reads `H2_FILE_PATH` for the database file location, plus optional `H2_USER` and `H2_PASSWORD` values. The JDBC URL sets `DB_CLOSE_DELAY=-1`, so H2 stays open until the Java process exits even if all database connections close. Do not place the H2 file inside a temporary directory if its data must survive restarts.

The AI farmer assistant uses a local Ollama server; no cloud API key is required. Install Ollama, start its server, and download a vision-capable model:

```powershell
ollama pull qwen2.5vl:3b
ollama serve
```

The backend connects to `http://localhost:11434` by default. Set `OLLAMA_BASE_URL` to use another Ollama host or `OLLAMA_MODEL` to choose another installed vision-capable model. Farmers can type or speak a question and attach a photo from the camera or photo library; images are compressed in the browser and limited to 4 MB before being sent to the local Ollama server for analysis. The browser uses Marathi speech recognition and speech synthesis when supported; typed questions remain available in other browsers. Keep Ollama running while using the assistant.

The weather lookup reads active district alerts from the India Meteorological Department (IMD) public nowcast RSS feed. PIN codes are mapped to a district through `api.postalpincode.in` (a non-government postal lookup service); set `POSTAL_API_URL` to use another compatible PIN lookup endpoint. The IMD feed provides district-level alerts, not exact farm conditions, so users can select the observed weather manually and save the entry. The app handles unavailable or non-JSON API responses with a Marathi error message.

By default, the app uses H2 for both diary entries and photo bytes. New photos are stored in the `farm_photo` table as binary large objects (up to 10 MB) and served by the `/api/photos/{id}` endpoint. When an S3 bucket is configured, new photos are stored in S3 while the database keeps their object key. Existing filesystem photos remain readable as a legacy fallback when their files are available.

## AWS PostgreSQL and S3 storage

For an AWS deployment, configure a PostgreSQL database and set these environment variables on the app:

| Variable | Example | Purpose |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<RDS-endpoint>:5432/swarnabhoomi?sslmode=require` | PostgreSQL JDBC connection |
| `SPRING_DATASOURCE_DRIVER` | `org.postgresql.Driver` | Select the PostgreSQL driver |
| `SPRING_DATASOURCE_USERNAME` | `swarnabhoomi` | PostgreSQL application user |
| `SPRING_DATASOURCE_PASSWORD` | Secret value | PostgreSQL application password |
| `S3_BUCKET` | `your-private-bucket-name` | Private bucket for photos and backups |
| `AWS_REGION` | `ap-southeast-2` | Region containing the bucket |
| `AWS_ACCESS_KEY_ID` | Secret value | Access key for a dedicated, restricted AWS IAM user |
| `AWS_SECRET_ACCESS_KEY` | Secret value | Secret key for that IAM user |
| `AWS_SESSION_TOKEN` | Optional | Required when using temporary AWS credentials |
| `DATABASE_BACKUP_ENABLED` | `true` | Enable scheduled PostgreSQL backups to S3 |
| `DATABASE_BACKUP_CRON` | `0 0 2 * * *` | Backup schedule in UTC; default is 02:00 daily |

To use access keys instead of an EC2 instance role, create a dedicated AWS IAM user with only the required S3 permissions for this bucket, then set its keys as environment variables. Never use root-user keys, commit keys, put them in the Docker image, or send them in chat. For Docker Compose, store the values in the root `.env` file on the server; `.env` is ignored by Git. Restrict access to that file and rotate the keys if they are exposed. With `S3_BUCKET` set, the app requires both access-key variables; an optional session token supports temporary credentials. Keep the bucket private, enable encryption, and restrict the RDS security group to the app's security group. Scheduled backups use `pg_dump`, are encrypted with S3-managed encryption, and are uploaded under `backups/postgres/`; configure an S3 lifecycle rule to expire old backups according to your retention needs. RDS automated backups should also remain enabled.

Example server-side `.env` settings (replace placeholders locally; do not commit the file):

```dotenv
SPRING_DATASOURCE_URL=jdbc:postgresql://<RDS-endpoint>:5432/swarnabhoomi?sslmode=require
SPRING_DATASOURCE_DRIVER=org.postgresql.Driver
SPRING_DATASOURCE_USERNAME=<database-user>
SPRING_DATASOURCE_PASSWORD=<database-password>
S3_BUCKET=<private-bucket-name>
AWS_REGION=ap-southeast-2
AWS_ACCESS_KEY_ID=<restricted-iam-user-access-key>
AWS_SECRET_ACCESS_KEY=<restricted-iam-user-secret-key>
DATABASE_BACKUP_ENABLED=true
```

The IAM user's S3 policy should allow `s3:GetObject` and `s3:PutObject` only for `arn:aws:s3:::<private-bucket-name>/photos/*` and `arn:aws:s3:::<private-bucket-name>/backups/postgres/*`. No bucket listing or public-access permission is needed.

To migrate the existing H2 data, first stop the normal app to prevent writes and reminder sends, take a separate copy of its persistent `/var/data` volume, then run the app once with the `migration` Spring profile, `REMINDER_SCHEDULER_ENABLED=false`, the existing H2 volume mounted at `/var/data`, and the PostgreSQL/S3 settings above. The migration copies entries, reminders, and photos, then stores photo objects in S3. It is safe to rerun after an interrupted attempt; existing PostgreSQL rows are not overwritten. Verify record counts and photos before switching normal app traffic to PostgreSQL. Keep the original H2 volume unchanged until verification and rollback are no longer needed.

## Temporary client demo from your PC

The local H2 file stays on your PC. You can share the web app through an HTTPS tunnel such as ngrok. Start the tunnel first and copy its exact hostname (for example, `example.ngrok-free.app`).

In the frontend PowerShell terminal, set `VITE_ALLOWED_HOSTS` to that hostname, then run `npm run dev`. In the backend terminal, set `APP_CORS_ALLOWED_ORIGINS` to `http://localhost:5173,https://<your-tunnel-hostname>`, set `APP_ACCESS_ENABLED=true`, choose `APP_ACCESS_USERNAME` and a temporary `APP_ACCESS_PASSWORD`, and start Spring Boot. The app's Marathi login screen will ask your client for those credentials. Use the same exact HTTPS origin in the CORS setting; the code now permits only the configured origins and Vite hosts. API preflight requests are allowed through so browser login works. Stop the tunnel after the demo.

Environment variables set in PowerShell apply only to that terminal and its child process. Set `APP_ACCESS_PASSWORD` in the backend terminal for the demo login.

## Client demo deployment

The root `Dockerfile` packages the React app into the Spring Boot server. `render.yaml` configures the Singapore web service to write H2 data and uploads under `/var/data` on a 1 GB persistent disk. This requires a paid web service; Render currently lists its smallest paid web compute plan at $7/month and persistent disks at $0.25/GB per month. A disk-backed service runs as a single instance and has brief downtime during deploys. Review [Render's current pricing](https://render.com/pricing) before syncing the Blueprint.

Existing records in PostgreSQL, MySQL, or the former H2 file under `/tmp` are not automatically copied into the persistent H2 database. Back up and migrate them separately before syncing the Blueprint if they need to be retained.

Render persistent disks require a paid web service; storage is billed separately. Review [Render's current pricing](https://render.com/pricing) before creating the service. To deploy, push this project to a Git provider and create a Render Blueprint from its repository, or use the connected Render integration. The generated `*.onrender.com` URL is then shareable.

## Web push reminders

The **स्मरणपत्रे** tab has one delivery method: a browser/device notification at the scheduled time, including when the website is closed. Users must enable notifications on each device and allow browser permission before creating reminders. Reminder times use India Standard Time (`Asia/Kolkata`). Browser and operating-system settings control notification sound and vibration; vibration is best-effort where supported. On iPhone/iPad, install the site to the Home Screen first (iOS 16.4 or later), then allow notifications.

Configure VAPID keys as `WEB_PUSH_PUBLIC_KEY` and `WEB_PUSH_PRIVATE_KEY`. Generate a fresh key pair locally with `npx web-push generate-vapid-keys`; keep the private key secret and enter the values directly in the hosting service's environment settings. Do not commit either key. For Docker Compose, put both keys in the ignored root `.env` file. `WEB_PUSH_SUBJECT` should be a contact `mailto:` address or the service's HTTPS URL; for Render, use `https://swarnabhoomi-demo.onrender.com`. Redeploy after adding the variables. Without these values, the browser push control reports that push is not configured.

The backend checks due reminders every 15 seconds. A reminder is marked sent when the browser push service accepts the notification; handset presentation and sound depend on browser and device settings. Failed sends are marked for review and are not retried automatically.

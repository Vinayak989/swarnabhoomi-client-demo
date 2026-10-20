# Swarnabhoomi — Daily Farm Log

A Marathi-first web application with a React frontend and Java Spring Boot API. Entries persist in a local H2 file database; uploaded photos are stored in `backend/uploads/`.

## Run locally

Requirements: Java 17+, Maven, and Node.js 20+.

1. In one terminal: `cd backend` then `mvn spring-boot:run`.
2. In another terminal: `cd frontend` then `npm install` and `npm run dev`.
3. Open the Vite URL in your browser. The API runs on `http://localhost:8080`.

The weather lookup uses India Meteorological Department (IMD) district warning and rainfall forecast APIs. IMD publishes district-level weather data, not a direct PIN-code weather endpoint. This prototype looks up the PIN code's district through `api.postalpincode.in` (a non-government postal lookup service), then shows the matching IMD district forecast. Set `POSTAL_API_URL` to use a different compatible PIN lookup endpoint. If a remote service is unavailable, users can still select weather manually and save the entry.

The local web release uses a single-user H2 database. The hosted demo is protected by one shared login; production multi-user accounts and cloud object storage can be added later.

## Client demo deployment

The root `Dockerfile` packages the React app into the Spring Boot server. `render.yaml` configures a Singapore Render web service with a 1 GB persistent disk for the H2 database and uploaded photos. The demo API is protected by a shared login; Render generates its password as a secret environment variable. Retrieve it in the Render dashboard before sharing the client URL. Anyone with that password can create and delete entries.

Render persistent disks require a paid web service; storage is billed separately. Review [Render's current pricing](https://render.com/pricing) before creating the service. To deploy, push this project to a Git provider and create a Render Blueprint from its repository, or use the connected Render integration. The generated `*.onrender.com` URL is then shareable.

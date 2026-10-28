# H2 to MySQL migration

`H2ToMySqlMigration.java` migrates a trusted Swarnabhoomi H2 logical snapshot to an **empty MySQL 8 schema**. It does not change the source H2 database or start Spring Boot. Java 17 and the backend runtime dependencies are required.

## Prepare a snapshot

Keep the SQL snapshot and its `manifest.json` together in a private directory outside Git. Keep an additional copy of the downloaded archive and verify its SHA-256 checksum. The snapshot includes account information and photo bytes.

For the live H2 app, `POST /api/internal/database-export` creates a consistent native H2 backup and a logical export from a restored copy. It requires an `Authorization: Bearer ...` header matching `DATABASE_EXPORT_TOKEN` (at least 40 characters) and a future ISO-8601 `DATABASE_EXPORT_EXPIRES_AT`. The response includes `X-Backup-SHA256` and `X-Backup-Created-At`. This capability is disabled by default. Clear the token and expire it immediately after downloading; do not commit credentials or exports. `DB_BACKUP_DIRECTORY` controls the server backup directory, defaulting to `/var/data/backups`.

## Inspect, migrate, and verify

Run from `backend` in PowerShell, replacing the private paths below:

```powershell
mvn dependency:build-classpath "-Dmdep.outputFile=target/migration-classpath.txt"
$migrationClasspath = (Get-Content -Raw target/migration-classpath.txt).Trim()
$migrationSnapshot = 'D:/private-backups/h2-snapshot.sql'
$migrationReports = 'D:/private-backups/migration'
java --class-path $migrationClasspath tools/H2ToMySqlMigration.java plan $migrationSnapshot $migrationReports
```

Review `migration-plan.json` and `mysql-schema-plan.sql`. Create the empty destination schema first. Supply `MIGRATION_MYSQL_URL`, `MIGRATION_MYSQL_USER`, and `MIGRATION_MYSQL_PASSWORD` as process environment variables. Set `MIGRATION_MYSQL_SCHEMA` if its name differs from `swarnbhoomi`. Use a JDBC URL that selects that schema, UTF-8, and UTC, with the TLS settings required by your database host.

```powershell
java --class-path $migrationClasspath tools/H2ToMySqlMigration.java apply $migrationSnapshot $migrationReports
java --class-path $migrationClasspath tools/H2ToMySqlMigration.java verify $migrationSnapshot $migrationReports
```

`apply` refuses a nonempty target. It preserves IDs, the next identity values, status enums, indexes, foreign keys, timestamps, Marathi text, and photo bytes. It compares a SHA-256 digest of every row before committing the data. `verify` reads the target again and checks row digests and foreign-key counts. Private JSON reports record the result. MySQL schema changes are not transactional: a failed attempt can leave new empty or populated tables, so inspect the private failure report and use a new empty schema for a retry.

Export a MySQL dump after verification using `mysqldump --single-transaction --hex-blob`; keep a second copy. Remove migration credentials from the process environment when finished. Local `.backups/` and `.env` are ignored by Git.

## Run the app against MySQL

Set the following in the ignored project-root `.env` for local use, or in the Render app service's Environment page for deployment:

| Setting | Value |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `mysql` |
| `MYSQL_JDBC_URL` | JDBC URL selecting the populated schema |
| `MYSQL_USER` | Database application user |
| `MYSQL_PASSWORD` | Database application password |

The local `.env` can alternatively use `spring.profiles.active=mysql`. Start locally from `backend` with `mvn spring-boot:run`. The MySQL profile validates the imported schema instead of creating tables. Hibernate and the JDBC connection use UTC. Reminder delivery is disabled by default in this profile to prevent a copied database from sending duplicate live reminders. Enable `APP_REMINDERS_SCHEDULER_ENABLED=true` on the single production reminder-sending instance after cutover. PostgreSQL-specific backup scheduling remains disabled; configure MySQL backups separately.

## Connect Render to MySQL

`localhost` on Render refers to the Render container, not your laptop. A local MySQL copy does not stay synchronized with the live H2 app.

For a MySQL service on Render, follow [Render's MySQL deployment instructions](https://render.com/docs/deploy-mysql): create a Docker private service from `render-examples/mysql`, in the same workspace and region as the app; set `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, and `MYSQL_ROOT_PASSWORD`; and attach a persistent disk at exactly `/var/lib/mysql`. Use its **Connect > Internal** hostname and port in the app's JDBC URL. The private service and disk require paid resources. An external hosted MySQL server is another option; use that provider's TLS settings and allow the Render app's outbound addresses in the database firewall.

Before production cutover, stop new writes, take a fresh H2 snapshot, migrate it to an empty MySQL schema, and verify it. Then change the app's environment settings and redeploy. Check login, listings, photos, and a new write. Enable reminders only on the production instance. Retain the original H2 database and both backup formats. If rolling back after MySQL has accepted new writes, reconcile those writes before returning to H2.

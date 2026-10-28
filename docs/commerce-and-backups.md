# Orders, traceability, farm materials and backups

The marketplace now includes the following controls in its existing navigation:

- **माझ्या ऑर्डर → पुन्हा कार्टमध्ये जोडा:** reuses the quantity with current stock and current prices. The buyer reviews the cart before placing an order. Changed units require a fresh selection.
- **वितरण कोड:** available to the buyer once an accepted order is ready for collection or out for delivery. The seller records payment and verifies the code before completing the order. Codes expire after 24 hours and lock after five failed attempts. The buyer can replace a code. Completing an order keeps the existing conversation closed.
- **माझे व्यवहार → माझा शेतमाल → ऑफर वेळापत्रक व बॅच QR:** set the offer window, harvest date, batch number and optional public notes. Discount calculations at checkout enforce the window. Publishing a batch is explicit; its public page excludes contact information and exact location. Unpublishing immediately disables that QR page.
- **नियमित खरेदी:** daily or weekly purchases, a maximum unit price, first purchase date and delivery address. Scheduling checks run after 07:00 Asia/Kolkata. This is order creation time, not a promised delivery time. Each order requires separate cash payment and seller acceptance. Buyers can pause, resume, skip or cancel. Missing stock, changed units, an excessive price or a missed date generates a history entry rather than a late or invalid order. A locked subscription and unique occurrence key prevent duplicate orders.
- **घाऊक खरेदी:** buyers publish product, quantity, expected unit price, district and required date. Sellers quote their own listings. The buyer compares quotes, supplies a private delivery address and accepts one quote. Acceptance rechecks stock and reserves it atomically; the order uses the agreed price.
- **परतावा व बदल:** buyers request assistance from a completed order, optionally attaching up to three private photos. Admin approves or rejects, then records the actual refund or completed replacement. Refunds cannot exceed the order value. This records a cash/manual resolution; it does not initiate an automatic payment refund. Admin can see both parties' contact information. Other parties cannot see those contacts; only the buyer and admin can open evidence photos.
- **दैनिक नोंद → शेती साहित्याचा साठा:** opening stock, additions, corrections, unit costs, low-stock indicators and movement history. Select material usage in the diary form to consume stock. Editing/deleting the entry adjusts the ledger in the same transaction. Offline creation retries reuse the existing entry. The optional cost button fills the diary cost explicitly, avoiding silent double-counting. Usage also appears in diary details and PDF exports.

## Database upgrades

`PlatformSchemaEnvironment` runs the additive `commerce-v1` migration before production Hibernate validation. A successful native MySQL logical backup is required before the first DDL change. Existing rows and photo BLOBs are retained. Completed migrations are recorded in `platform_migration`; interrupted column additions are safe to retry. The existing H2 cutover runs first when explicitly enabled. New feature tables and indexes are initialized before feature services start.

The eight feature tables use JDBC inside the same Spring transactions as existing JPA orders and diary entries. Their schema is maintained by `db/platform-v1.sql`, not by Hibernate `ddl-auto`. Keep these table definitions and explicit migrations in sync when adding future fields.

## Automatic MySQL backups

- Nightly at **02:15 Asia/Kolkata**, with a startup catch-up when no backup exists in the previous 24 hours.
- Native MySQL 8.4 `mysqldump`, single transaction, streaming gzip, hex-encoded BLOBs. The Docker build checks that the bundled client runs.
- Stored under `${DB_BACKUP_DIRECTORY:/var/data/backups}/mysql`. Render's web-service disk must include that directory. This is separate from the MySQL service's disk.
- Keep the newest seven local dumps, subject to a 1 GiB retention ceiling and 256 MiB minimum free space before a dump. Never prune the newest successful backup.
- Admin can start a backup, inspect status, and download it with authentication. Each scheduled/manual backup has a JSON manifest containing SHA-256, byte count and offsite-copy status. Pre-upgrade backups are also listed.
- When the existing S3 client and `app.storage.s3.bucket` are configured, completed backups are also uploaded with server-side encryption under `database-backups/`. Otherwise the admin page clearly identifies local disk storage. S3 lifecycle/retention should be configured on that prefix separately.
- `app.database.mysql-backup.enabled` / `APP_DATABASE_MYSQL_BACKUP_ENABLED` controls MySQL scheduling. The older `app.database.backup.enabled` belongs to the PostgreSQL/S3 backup component and stays disabled in the MySQL profile.
- `MYSQLDUMP_PATH` optionally selects an installed native MySQL client for local verification. Passwords are passed through the child process environment, never command arguments.

Download a copy to independent storage regularly. Logical backups include database photo BLOBs and references to S3 photos; external S3 photo objects need their own retention policy.

## Restore verification

Restore into a **new isolated MySQL schema first**, using a matching native MySQL client. Check the manifest checksum before decompressing. Compare row counts and image hashes, and start a separate application against that schema with schedulers disabled. Test authentication, order history, diary photos and profile photos. Only switch a production connection after a verified restore and an approved maintenance window. Never replace the running database directly with an unverified dump.

Development verification restored a native backup into a separate local schema and compared deterministic full-data dumps; all rows and photo BLOBs matched byte for byte. The existing H2 archives were left intact.

The separate low-memory MySQL maintenance task was deferred at the user's request; this rollout does not claim that tuning is applied.

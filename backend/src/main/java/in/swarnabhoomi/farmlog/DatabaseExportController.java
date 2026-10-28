package in.swarnabhoomi.farmlog;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Files;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DatabaseExportController {
    private final DatabaseExportAccess access;
    private final H2DatabaseSnapshotService snapshots;
    public DatabaseExportController(DatabaseExportAccess access, H2DatabaseSnapshotService snapshots) { this.access = access; this.snapshots = snapshots; }

    @PostMapping(DatabaseExportAccess.PATH)
    public ResponseEntity<Resource> export(HttpServletRequest request) throws Exception {
        access.requireAuthorized(request);
        var snapshot = snapshots.create();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(Files.size(snapshot.archive()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store, private")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"render-h2-export.zip\"")
                .header("X-Backup-SHA256", snapshot.sha256()).header("X-Backup-Created-At", snapshot.createdAt().toString())
                .body(new FileSystemResource(snapshot.archive()));
    }
}

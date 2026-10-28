package in.swarnabhoomi.farmlog;

import java.util.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform-admin")
public class PlatformAdminController {
  final MySqlBackupService backups;

  public PlatformAdminController(MySqlBackupService backups) {
    this.backups = backups;
  }

  @GetMapping("/backups")
  Object status(@RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    TradeService.role(u, "admin");
    return backups.status();
  }

  @PostMapping("/backups")
  @ResponseStatus(HttpStatus.ACCEPTED)
  Object backup(@RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    TradeService.role(u, "admin");
    backups.start();
    return Map.of("message", "बॅकअप सुरू झाला आहे.");
  }

  @GetMapping("/backups/{name}")
  Object download(
      @PathVariable String name, @RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser u) {
    TradeService.role(u, "admin");
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("application/gzip"))
        .header("Cache-Control", "no-store")
        .header("Content-Disposition", "attachment; filename=\"" + name + "\"")
        .body(new FileSystemResource(backups.download(name)));
  }
}

package in.swarnabhoomi.farmlog;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/reminders")
public class FarmReminderController {
    private final FarmReminderService service;

    public FarmReminderController(FarmReminderService service) {
        this.service = service;
    }

    @GetMapping
    public List<FarmReminderService.ReminderView> list() {
        return service.list();
    }

    @GetMapping("/configuration")
    public Map<String, Boolean> configuration() {
        return Map.of("whatsAppConfigured", service.isWhatsAppConfigured(),
                "smsConfigured", service.isSmsConfigured(),
                "callConfigured", service.isCallConfigured());
    }

    @GetMapping(value = "/call-audio/{audioId}", produces = "audio/mpeg")
    public ResponseEntity<byte[]> callAudio(@PathVariable UUID audioId) {
        byte[] audio = VoiceReminderSender.getAudio(audioId);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("audio/mpeg"))
                .cacheControl(CacheControl.noStore())
                .body(audio);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FarmReminderService.ReminderView create(@RequestBody(required = false) FarmReminderService.ReminderRequest request) {
        return service.create(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}

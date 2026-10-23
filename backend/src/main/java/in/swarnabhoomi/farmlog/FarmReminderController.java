package in.swarnabhoomi.farmlog;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FarmReminderService.ReminderView create(@RequestBody(required = false) FarmReminderService.ReminderRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}/complete")
    public FarmReminderService.ReminderView complete(@PathVariable Long id) {
        return service.complete(id);
    }

    @PatchMapping("/{id}/reschedule")
    public FarmReminderService.ReminderView reschedule(@PathVariable Long id,
            @RequestBody(required = false) FarmReminderService.RescheduleRequest request) {
        return service.reschedule(id, request);
    }

    @PatchMapping("/{id}/stop-repeat")
    public FarmReminderService.ReminderView stopRepeat(@PathVariable Long id) {
        return service.stopRepeat(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}

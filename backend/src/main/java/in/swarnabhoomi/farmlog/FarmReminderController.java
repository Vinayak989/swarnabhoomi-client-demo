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
    public List<FarmReminderService.ReminderView> list(
            @org.springframework.web.bind.annotation.RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireFarmer(user);
        return service.list(user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FarmReminderService.ReminderView create(@RequestBody(required = false) FarmReminderService.ReminderRequest request,
            @org.springframework.web.bind.annotation.RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireFarmer(user);
        return service.create(request, user);
    }

    @PatchMapping("/{id}/complete")
    public FarmReminderService.ReminderView complete(@PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireFarmer(user);
        return service.complete(id, user);
    }

    @PatchMapping("/{id}/reschedule")
    public FarmReminderService.ReminderView reschedule(@PathVariable Long id,
            @RequestBody(required = false) FarmReminderService.RescheduleRequest request,
            @org.springframework.web.bind.annotation.RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireFarmer(user);
        return service.reschedule(id, request, user);
    }

    @PatchMapping("/{id}/stop-repeat")
    public FarmReminderService.ReminderView stopRepeat(@PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireFarmer(user);
        return service.stopRepeat(id, user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestAttribute(UserAuthFilter.USER_ATTRIBUTE) AppUser user) {
        requireFarmer(user);
        service.delete(id, user);
    }

    private static void requireFarmer(AppUser user) {
        if (!"farmer".equals(user.getRole())) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,
                    "हे वैशिष्ट्य शेतकरी खात्यासाठी आहे.");
        }
    }
}

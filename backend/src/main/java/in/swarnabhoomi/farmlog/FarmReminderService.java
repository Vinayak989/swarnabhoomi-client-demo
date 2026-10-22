package in.swarnabhoomi.farmlog;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FarmReminderService {
    private static final Logger log = LoggerFactory.getLogger(FarmReminderService.class);
    private static final ZoneId FARM_TIME_ZONE = ZoneId.of("Asia/Kolkata");
    private final FarmReminderRepository repository;
    private final BrowserPushSubscriptionRepository pushSubscriptionRepository;
    private final BrowserPushService browserPushService;

    public FarmReminderService(FarmReminderRepository repository,
            BrowserPushSubscriptionRepository pushSubscriptionRepository, BrowserPushService browserPushService) {
        this.repository = repository;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.browserPushService = browserPushService;
    }

    public List<ReminderView> list() {
        return repository.findAllByOrderByScheduledAtAsc().stream().map(this::view).toList();
    }

    public ReminderView create(ReminderRequest request) {
        if (request == null || request.scheduledAt() == null) {
            throw badRequest("कृपया स्मरणपत्राची तारीख आणि वेळ निवडा.");
        }
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.isBlank() || reason.length() > 300) {
            throw badRequest("कारण लिहा (जास्तीत जास्त ३०० अक्षरे).");
        }
        if (request.browserSubscriptionId() == null) {
            throw badRequest("स्मरणपत्रे मिळवण्यासाठी या डिव्हाइसवर सूचना सुरू करा.");
        }
        LocalDateTime now = LocalDateTime.now(FARM_TIME_ZONE);
        if (!request.scheduledAt().isAfter(now)) {
            throw badRequest("कृपया भविष्यातील तारीख आणि वेळ निवडा.");
        }

        FarmReminder reminder = new FarmReminder();
        reminder.setScheduledAt(request.scheduledAt());
        reminder.setReason(reason);
        reminder.setMobileNumber("");
        reminder.setStatus("PENDING");
        reminder.setDeliveryNote("पाठवण्याच्या प्रतीक्षेत");
        BrowserPushSubscription subscription = pushSubscriptionRepository.findById(request.browserSubscriptionId())
                .filter(BrowserPushSubscription::isActive)
                .orElseThrow(() -> badRequest("वेब सूचना सदस्यता कालबाह्य आहे. कृपया सूचना पुन्हा सुरू करा."));
        reminder.setBrowserSubscription(subscription);
        return view(repository.save(reminder));
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        repository.deleteById(id);
    }

    @Scheduled(fixedDelayString = "${app.reminders.poll-interval-ms:15000}")
    public synchronized void sendDueReminders() {
        LocalDateTime now = LocalDateTime.now(FARM_TIME_ZONE);
        List<FarmReminder> due = repository
                .findTop50ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc("PENDING", now);
        for (FarmReminder reminder : due) {
            reminder.setStatus("SENDING");
            reminder.setDeliveryNote("सूचना पाठवत आहे");
            repository.saveAndFlush(reminder);
            boolean delivered = false;
            String deliveryNote;
            try {
                delivered = reminder.getBrowserSubscription() != null && browserPushService.send(reminder);
                deliveryNote = delivered
                        ? "डिव्हाइस सूचनेसाठी पाठवण्याची विनंती स्वीकारली."
                        : "या स्मरणपत्राचे सूचना डिव्हाइस उपलब्ध नाही. पुन्हा सूचना सुरू करून नवे स्मरणपत्र तयार करा.";
            } catch (RuntimeException exception) {
                log.warn("Failed to send push reminder {}", reminder.getId(), exception);
                deliveryNote = "डिव्हाइस सूचना पाठवताना तांत्रिक अडचण आली.";
            }
            if (delivered) {
                reminder.setStatus("SENT");
                reminder.setDeliveryNote(deliveryNote);
                reminder.setSentAt(LocalDateTime.now(FARM_TIME_ZONE));
            } else {
                reminder.setStatus("FAILED");
                reminder.setDeliveryNote(deliveryNote);
            }
            repository.save(reminder);
        }
    }

    @PostConstruct
    void markInterruptedSends() {
        for (FarmReminder reminder : repository.findAllByStatus("SENDING")) {
            reminder.setStatus("FAILED");
            reminder.setDeliveryNote("सर्व्हर थांबल्यामुळे पाठवण्याची स्थिती तपासता आली नाही.");
            repository.save(reminder);
        }
    }

    private ReminderView view(FarmReminder reminder) {
        return new ReminderView(reminder.getId(), reminder.getScheduledAt(), reminder.getReason(),
                reminder.getStatus(), reminder.getDeliveryNote(), reminder.getSentAt());
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record ReminderRequest(LocalDateTime scheduledAt, String reason, Long browserSubscriptionId) {}
    public record ReminderView(Long id, LocalDateTime scheduledAt, String reason, String status,
            String deliveryNote, LocalDateTime sentAt) {}
}

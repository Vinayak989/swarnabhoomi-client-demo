package in.swarnabhoomi.farmlog;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
    private static final int MAX_DELIVERY_ATTEMPTS = 5;
    private final FarmReminderRepository repository;
    private final BrowserPushSubscriptionRepository pushSubscriptionRepository;
    private final BrowserPushService browserPushService;

    public FarmReminderService(FarmReminderRepository repository,
            BrowserPushSubscriptionRepository pushSubscriptionRepository, BrowserPushService browserPushService) {
        this.repository = repository;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.browserPushService = browserPushService;
    }

    public List<ReminderView> list(AppUser user) {
        return repository.findAllByOwnerIdOrderByScheduledAtAsc(user.getId()).stream().map(this::view).toList();
    }

    public ReminderView create(ReminderRequest request, AppUser user) {
        if (request == null || request.scheduledAt() == null) {
            throw badRequest("कृपया स्मरणपत्राची तारीख आणि वेळ निवडा.");
        }
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.isBlank() || reason.length() > 300) {
            throw badRequest("कारण लिहा (जास्तीत जास्त ३०० अक्षरे).");
        }
        String cropOrPlot = request.cropOrPlot() == null ? "" : request.cropOrPlot().trim();
        if (cropOrPlot.length() > 120) {
            throw badRequest("पीक किंवा शेत विभागाचे नाव १२० अक्षरांपेक्षा मोठे असू शकत नाही.");
        }
        String repeatFrequency = normalizeFrequency(request.repeatFrequency());
        if (request.repeatUntil() != null
                && ("NONE".equals(repeatFrequency) || request.repeatUntil().isBefore(request.scheduledAt().toLocalDate()))) {
            throw badRequest("पुनरावृत्तीची शेवटची तारीख योग्य निवडा.");
        }
        LocalDateTime now = LocalDateTime.now(FARM_TIME_ZONE);
        if (!request.scheduledAt().isAfter(now)) {
            throw badRequest("कृपया भविष्यातील तारीख आणि वेळ निवडा.");
        }

        FarmReminder reminder = new FarmReminder();
        reminder.setOwner(user);
        reminder.setScheduledAt(request.scheduledAt());
        reminder.setReason(reason);
        reminder.setMobileNumber("");
        reminder.setStatus("PENDING");
        reminder.setDeliveryNote("पाठवण्याच्या प्रतीक्षेत");
        reminder.setCropOrPlot(cropOrPlot);
        reminder.setRepeatFrequency(repeatFrequency);
        reminder.setRepeatUntil(request.repeatUntil());
        if (request.browserSubscriptionId() != null) {
            BrowserPushSubscription subscription = pushSubscriptionRepository
                    .findByIdAndOwnerIdAndActiveTrue(request.browserSubscriptionId(), user.getId())
                    .orElseThrow(() -> badRequest("वेब सूचना सदस्यता कालबाह्य आहे. कृपया पुन्हा प्रयत्न करा."));
            reminder.setBrowserSubscription(subscription);
        }
        return view(repository.save(reminder));
    }

    public void delete(Long id, AppUser user) {
        FarmReminder reminder = ownedReminder(id, user);
        repository.delete(reminder);
    }

    public synchronized ReminderView complete(Long id, AppUser user) {
        FarmReminder reminder = ownedReminder(id, user);
        if (!"PENDING".equals(reminder.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "फक्त प्रलंबित स्मरणपत्र पूर्ण करता येते.");
        }
        reminder.setStatus("COMPLETED");
        reminder.setDeliveryNote("वापरकर्त्याने पूर्ण केले.");
        FarmReminder completed = repository.save(reminder);
        createNextOccurrence(completed);
        return view(completed);
    }

    public synchronized ReminderView reschedule(Long id, RescheduleRequest request, AppUser user) {
        FarmReminder reminder = ownedReminder(id, user);
        if (!"PENDING".equals(reminder.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "फक्त प्रलंबित स्मरणपत्राची वेळ बदलता येते.");
        }
        if (request == null || request.scheduledAt() == null
                || !request.scheduledAt().isAfter(LocalDateTime.now(FARM_TIME_ZONE))) {
            throw badRequest("कृपया भविष्यातील तारीख आणि वेळ निवडा.");
        }
        reminder.setScheduledAt(request.scheduledAt());
        reminder.setDeliveryNote("वेळ बदलली; सूचनेसाठी प्रतीक्षेत.");
        reminder.setDeliveryAttempts(0);
        reminder.setNextDeliveryAt(null);
        return view(repository.save(reminder));
    }

    public synchronized ReminderView stopRepeat(Long id, AppUser user) {
        FarmReminder reminder = ownedReminder(id, user);
        if (!"PENDING".equals(reminder.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "फक्त प्रलंबित स्मरणपत्राची पुनरावृत्ती थांबवता येते.");
        }
        reminder.setRepeatFrequency("NONE");
        reminder.setRepeatUntil(null);
        reminder.setDeliveryNote("पुनरावृत्ती थांबवली.");
        return view(repository.save(reminder));
    }

    @Scheduled(fixedDelayString = "${app.reminders.poll-interval-ms:5000}")
    public synchronized void sendDueReminders() {
        if (!browserPushService.isConfigured()) return;
        LocalDateTime now = LocalDateTime.now(FARM_TIME_ZONE);
        List<FarmReminder> due = repository.findDueReminders(now, org.springframework.data.domain.PageRequest.of(0, 50));
        for (FarmReminder reminder : due) {
            reminder.setStatus("SENDING");
            reminder.setDeliveryNote("सूचना पाठवत आहे");
            reminder.setDeliveryAttempts(reminder.getDeliveryAttempts() + 1);
            repository.saveAndFlush(reminder);
            boolean delivered = false;
            String deliveryNote;
            try {
                delivered = browserPushService.send(reminder);
                deliveryNote = delivered
                        ? "डिव्हाइस सूचनेसाठी पाठवण्याची विनंती स्वीकारली."
                        : "सूचना मिळाली नाही. डिव्हाइसवरील सूचना सुरू आहेत का ते तपासा.";
            } catch (RuntimeException exception) {
                log.warn("Failed to send push reminder {}", reminder.getId(), exception);
                deliveryNote = "डिव्हाइस सूचना पाठवताना तांत्रिक अडचण आली.";
            }
            if (delivered) {
                reminder.setStatus("SENT");
                reminder.setDeliveryNote(deliveryNote);
                reminder.setSentAt(LocalDateTime.now(FARM_TIME_ZONE));
                reminder.setNextDeliveryAt(null);
            } else if (reminder.getDeliveryAttempts() < MAX_DELIVERY_ATTEMPTS) {
                reminder.setStatus("PENDING");
                reminder.setNextDeliveryAt(now.plusSeconds(60L << (reminder.getDeliveryAttempts() - 1)));
                reminder.setDeliveryNote("सूचना पाठवता आली नाही; पुन्हा प्रयत्न केला जाईल.");
            } else {
                reminder.setStatus("FAILED");
                reminder.setDeliveryNote(deliveryNote);
            }
            repository.save(reminder);
            if (!"PENDING".equals(reminder.getStatus())) createNextOccurrence(reminder);
        }
    }

    @PostConstruct
    void markInterruptedSends() {
        for (FarmReminder reminder : repository.findAllByStatus("SENDING")) {
            reminder.setStatus("PENDING");
            reminder.setNextDeliveryAt(null);
            reminder.setDeliveryNote("सर्व्हर पुन्हा सुरू झाला; सूचना पुन्हा पाठवली जाईल.");
            repository.save(reminder);
        }
    }

    private ReminderView view(FarmReminder reminder) {
        return new ReminderView(reminder.getId(), reminder.getScheduledAt(), reminder.getReason(),
                reminder.getStatus(), reminder.getDeliveryNote(), reminder.getSentAt(),
                reminder.getCropOrPlot(), normalizeFrequency(reminder.getRepeatFrequency()), reminder.getRepeatUntil());
    }

    private void createNextOccurrence(FarmReminder reminder) {
        String frequency = normalizeFrequency(reminder.getRepeatFrequency());
        if ("NONE".equals(frequency)) return;

        LocalDateTime next = "DAILY".equals(frequency)
                ? reminder.getScheduledAt().plusDays(1)
                : reminder.getScheduledAt().plusWeeks(1);
        LocalDateTime now = LocalDateTime.now(FARM_TIME_ZONE);
        if (!next.isAfter(now)) {
            long missedIntervals = "DAILY".equals(frequency)
                    ? ChronoUnit.DAYS.between(next, now) + 1
                    : ChronoUnit.WEEKS.between(next, now) + 1;
            next = "DAILY".equals(frequency)
                    ? next.plusDays(missedIntervals)
                    : next.plusWeeks(missedIntervals);
        }
        LocalDate repeatUntil = reminder.getRepeatUntil();
        if (repeatUntil != null && next.toLocalDate().isAfter(repeatUntil)) return;

        FarmReminder occurrence = new FarmReminder();
        occurrence.setOwner(reminder.getOwner());
        occurrence.setScheduledAt(next);
        occurrence.setReason(reminder.getReason());
        occurrence.setMobileNumber("");
        occurrence.setStatus("PENDING");
        occurrence.setDeliveryNote("पुनरावृत्तीचे पुढील स्मरणपत्र.");
        occurrence.setBrowserSubscription(reminder.getBrowserSubscription());
        occurrence.setCropOrPlot(reminder.getCropOrPlot());
        occurrence.setRepeatFrequency(frequency);
        occurrence.setRepeatUntil(repeatUntil);
        repository.save(occurrence);
    }

    private String normalizeFrequency(String value) {
        String frequency = value == null || value.isBlank() ? "NONE" : value.trim().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("NONE", "DAILY", "WEEKLY").contains(frequency)) {
            throw badRequest("पुनरावृत्ती दररोज, दर आठवड्याला किंवा नाही यापैकी निवडा.");
        }
        return frequency;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private FarmReminder ownedReminder(Long id, AppUser user) {
        return repository.findByIdAndOwnerId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public record ReminderRequest(LocalDateTime scheduledAt, String reason, Long browserSubscriptionId,
            String cropOrPlot, String repeatFrequency, LocalDate repeatUntil) {}
    public record RescheduleRequest(LocalDateTime scheduledAt) {}
    public record ReminderView(Long id, LocalDateTime scheduledAt, String reason, String status,
            String deliveryNote, LocalDateTime sentAt, String cropOrPlot, String repeatFrequency, LocalDate repeatUntil) {}
}

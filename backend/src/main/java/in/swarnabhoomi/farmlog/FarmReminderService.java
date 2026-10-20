package in.swarnabhoomi.farmlog;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FarmReminderService {
    private static final ZoneId FARM_TIME_ZONE = ZoneId.of("Asia/Kolkata");
    private final FarmReminderRepository repository;
    private final WhatsAppReminderSender sender;

    public FarmReminderService(FarmReminderRepository repository, WhatsAppReminderSender sender) {
        this.repository = repository;
        this.sender = sender;
    }

    public List<ReminderView> list() {
        return repository.findAllByOrderByScheduledAtAsc().stream().map(this::view).toList();
    }

    public boolean isWhatsAppConfigured() {
        return sender.isConfigured();
    }

    public ReminderView create(ReminderRequest request) {
        if (request == null || request.scheduledAt() == null) {
            throw badRequest("कृपया स्मरणपत्राची तारीख आणि वेळ निवडा.");
        }
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.isBlank() || reason.length() > 300) {
            throw badRequest("कारण लिहा (जास्तीत जास्त ३०० अक्षरे).");
        }
        if (!request.consent()) {
            throw badRequest("या नंबरवर WhatsApp स्मरणपत्रे पाठवण्यासाठी संमती आवश्यक आहे.");
        }
        LocalDateTime now = LocalDateTime.now(FARM_TIME_ZONE);
        if (!request.scheduledAt().isAfter(now)) {
            throw badRequest("कृपया भविष्यातील तारीख आणि वेळ निवडा.");
        }

        FarmReminder reminder = new FarmReminder();
        reminder.setScheduledAt(request.scheduledAt());
        reminder.setReason(reason);
        reminder.setMobileNumber(normalizeMobileNumber(request.mobileNumber()));
        reminder.setStatus("PENDING");
        reminder.setDeliveryNote("पाठवण्याच्या प्रतीक्षेत");
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
            reminder.setDeliveryNote("WhatsApp वर पाठवत आहे");
            repository.saveAndFlush(reminder);
            try {
                sender.send(reminder);
                reminder.setStatus("SENT");
                reminder.setDeliveryNote("WhatsApp वर पाठवले");
                reminder.setSentAt(LocalDateTime.now(FARM_TIME_ZONE));
            } catch (RuntimeException exception) {
                reminder.setStatus("FAILED");
                reminder.setDeliveryNote("पाठवता आले नाही. WhatsApp जोडणी आणि नंबर तपासा.");
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

    private String normalizeMobileNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            throw badRequest("मोबाईल नंबर लिहा.");
        }
        String number = latinDigits(raw.trim().replaceAll("[\\s().-]", ""));
        if (number.startsWith("00")) number = number.substring(2);
        if (number.startsWith("+")) number = number.substring(1);
        if (number.matches("0[6-9][0-9]{9}")) number = number.substring(1);
        if (number.matches("[6-9][0-9]{9}")) number = "91" + number;
        if (!number.matches("[1-9][0-9]{7,14}")) {
            throw badRequest("वैध मोबाईल नंबर देशाच्या कोडसह लिहा; भारतासाठी १० अंकी नंबर चालेल.");
        }
        return number;
    }

    private String latinDigits(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character >= '०' && character <= '९') {
                character = (char) ('0' + character - '०');
            }
            result.append(character);
        }
        return result.toString();
    }

    private ReminderView view(FarmReminder reminder) {
        String number = reminder.getMobileNumber();
        String masked = number.length() == 12 && number.startsWith("91")
                ? "+91 ••••••" + number.substring(number.length() - 4)
                : "+" + "•".repeat(Math.max(4, number.length() - 4)) + number.substring(number.length() - 4);
        return new ReminderView(reminder.getId(), reminder.getScheduledAt(), reminder.getReason(), masked,
                reminder.getStatus(), reminder.getDeliveryNote(), reminder.getSentAt());
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record ReminderRequest(LocalDateTime scheduledAt, String reason, String mobileNumber, boolean consent) {}
    public record ReminderView(Long id, LocalDateTime scheduledAt, String reason, String mobileNumber,
            String status, String deliveryNote, LocalDateTime sentAt) {}
}

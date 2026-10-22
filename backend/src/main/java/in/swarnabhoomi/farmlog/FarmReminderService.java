package in.swarnabhoomi.farmlog;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
    private final SmsReminderSender smsSender;
    private final VoiceReminderSender voiceSender;
    private final BrowserPushSubscriptionRepository pushSubscriptionRepository;
    private final BrowserPushService browserPushService;

    public FarmReminderService(FarmReminderRepository repository, WhatsAppReminderSender sender,
            SmsReminderSender smsSender, VoiceReminderSender voiceSender,
            BrowserPushSubscriptionRepository pushSubscriptionRepository, BrowserPushService browserPushService) {
        this.repository = repository;
        this.sender = sender;
        this.smsSender = smsSender;
        this.voiceSender = voiceSender;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.browserPushService = browserPushService;
    }

    public List<ReminderView> list() {
        return repository.findAllByOrderByScheduledAtAsc().stream().map(this::view).toList();
    }

    public boolean isWhatsAppConfigured() {
        return sender.isConfigured();
    }

    public boolean isSmsConfigured() {
        return smsSender.isConfigured();
    }

    public boolean isCallConfigured() {
        return voiceSender.isConfigured();
    }

    public ReminderView create(ReminderRequest request) {
        if (request == null || request.scheduledAt() == null) {
            throw badRequest("कृपया स्मरणपत्राची तारीख आणि वेळ निवडा.");
        }
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.isBlank() || reason.length() > 300) {
            throw badRequest("कारण लिहा (जास्तीत जास्त ३०० अक्षरे).");
        }
        boolean hasPhoneChannel = request.consent() || request.smsConsent() || request.callConsent();
        if (!hasPhoneChannel && request.browserSubscriptionId() == null) {
            throw badRequest("किमान एक सूचना माध्यम निवडा.");
        }
        LocalDateTime now = LocalDateTime.now(FARM_TIME_ZONE);
        if (!request.scheduledAt().isAfter(now)) {
            throw badRequest("कृपया भविष्यातील तारीख आणि वेळ निवडा.");
        }

        FarmReminder reminder = new FarmReminder();
        reminder.setScheduledAt(request.scheduledAt());
        reminder.setReason(reason);
        reminder.setMobileNumber(hasPhoneChannel ? normalizeMobileNumber(request.mobileNumber()) : "");
        reminder.setStatus("PENDING");
        reminder.setWhatsappConsent(request.consent());
        reminder.setSmsConsent(request.smsConsent());
        reminder.setCallConsent(request.callConsent());
        reminder.setDeliveryNote("पाठवण्याच्या प्रतीक्षेत");
        if (request.browserSubscriptionId() != null) {
            BrowserPushSubscription subscription = pushSubscriptionRepository.findById(request.browserSubscriptionId())
                    .filter(BrowserPushSubscription::isActive)
                    .orElseThrow(() -> badRequest("वेब सूचना सदस्यता कालबाह्य आहे. कृपया वेब सूचना पुन्हा सुरू करा."));
            reminder.setBrowserSubscription(subscription);
        }
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
            reminder.setDeliveryNote("नियोजित संदेश पाठवत आहे");
            repository.saveAndFlush(reminder);
            List<String> deliveryNotes = new ArrayList<>();
            boolean failed = false;
            if (reminder.isWhatsappConsent()) {
                try {
                    sender.send(reminder);
                    deliveryNotes.add("WhatsApp: पाठवण्याची विनंती स्वीकारली.");
                } catch (WhatsAppReminderSender.DeliveryException exception) {
                    failed = true;
                    deliveryNotes.add("WhatsApp: " + exception.getMessage());
                } catch (RuntimeException exception) {
                    failed = true;
                    deliveryNotes.add("WhatsApp: संदेश पाठवताना तांत्रिक अडचण आली.");
                }
            }
            if (reminder.isSmsConsent()) {
                try {
                    smsSender.send(reminder);
                    deliveryNotes.add("SMS: पाठवण्याची विनंती स्वीकारली.");
                } catch (SmsReminderSender.DeliveryException exception) {
                    failed = true;
                    deliveryNotes.add("SMS: " + exception.getMessage());
                } catch (RuntimeException exception) {
                    failed = true;
                    deliveryNotes.add("SMS: संदेश पाठवताना तांत्रिक अडचण आली.");
                }
            }
            if (reminder.isCallConsent()) {
                try {
                    voiceSender.send(reminder);
                    deliveryNotes.add("कॉल: सुरू करण्याची विनंती स्वीकारली.");
                } catch (VoiceReminderSender.DeliveryException exception) {
                    failed = true;
                    deliveryNotes.add("कॉल: " + exception.getMessage());
                } catch (RuntimeException exception) {
                    failed = true;
                    deliveryNotes.add("कॉल: सुरू करताना तांत्रिक अडचण आली.");
                }
            }
            if (reminder.getBrowserSubscription() != null) {
                try {
                    if (browserPushService.send(reminder)) {
                        deliveryNotes.add("वेब सूचना: पाठवण्याची विनंती स्वीकारली.");
                    } else {
                        failed = true;
                        deliveryNotes.add("वेब सूचना: सदस्यता कालबाह्य आहे.");
                    }
                } catch (RuntimeException exception) {
                    failed = true;
                    deliveryNotes.add("वेब सूचना: पाठवताना तांत्रिक अडचण आली.");
                }
            }
            if (!failed) {
                reminder.setStatus("SENT");
                reminder.setDeliveryNote(String.join(" ", deliveryNotes));
                reminder.setSentAt(LocalDateTime.now(FARM_TIME_ZONE));
            } else {
                reminder.setStatus("FAILED");
                if (deliveryNotes.size() > 1) {
                    deliveryNotes.replaceAll(note -> truncate(note, 90));
                }
                reminder.setDeliveryNote(truncate(String.join(" ", deliveryNotes), 300));
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

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 3) + "...";
    }

    private ReminderView view(FarmReminder reminder) {
        String number = reminder.getMobileNumber();
        String masked = number == null || number.isBlank() ? "" : number.length() == 12 && number.startsWith("91")
                ? "+91 ••••••" + number.substring(number.length() - 4)
                : "+" + "•".repeat(Math.max(4, number.length() - 4)) + number.substring(number.length() - 4);
        return new ReminderView(reminder.getId(), reminder.getScheduledAt(), reminder.getReason(), masked,
                reminder.getStatus(), reminder.getDeliveryNote(), reminder.getSentAt());
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record ReminderRequest(LocalDateTime scheduledAt, String reason, String mobileNumber, boolean consent,
            boolean smsConsent, boolean callConsent, Long browserSubscriptionId) {}
    public record ReminderView(Long id, LocalDateTime scheduledAt, String reason, String mobileNumber,
            String status, String deliveryNote, LocalDateTime sentAt) {}
}

package in.swarnabhoomi.farmlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
    "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:reminder_notifications;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.reminders.scheduler-enabled=false", "app.admin.bootstrap-mobile="
})
@Transactional
class FarmReminderNotificationTest {
    @Autowired private FarmReminderService service;
    @Autowired private FarmReminderRepository reminders;
    @Autowired private BrowserPushSubscriptionRepository subscriptions;
    @Autowired private AppUserRepository users;
    @MockBean private BrowserPushService push;

    private LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Kolkata")); }

    @BeforeEach void configurePush() { when(push.isConfigured()).thenReturn(true); }

    @Test void savedReminderStartsNotifyingWhenItsOwnerEnablesAndroidLater() {
        AppUser owner = users.save(new AppUser("+919800000001", "farmer", "test-hash"));
        AppUser other = users.save(new AppUser("+919800000002", "farmer", "test-hash"));
        FarmReminder mine = dueReminder(owner, "NONE");
        FarmReminder theirs = dueReminder(other, "NONE");
        service.sendDueReminders();
        verify(push, never()).send(any());
        assertThat(mine.getStatus()).isEqualTo("PENDING");

        enableAndroid(owner);
        doReturn(true).when(push).send(any());
        service.sendDueReminders();
        verify(push).send(mine);
        verify(push, never()).send(theirs);
        assertThat(mine.getBrowserSubscription()).isNull();
        assertThat(mine.getStatus()).isEqualTo("SENT");
        assertThat(theirs.getStatus()).isEqualTo("PENDING");
    }

    @Test void futureAndDisabledDeviceRemindersAreNotSentEarly() {
        AppUser owner = users.save(new AppUser("+919800000003", "farmer", "test-hash"));
        BrowserPushSubscription device = enableAndroid(owner);
        var result = service.create(new FarmReminderService.ReminderRequest(now().plusHours(1), "पाणी द्या", null, "", "NONE", null), owner);
        service.sendDueReminders();
        verify(push, never()).send(any());
        FarmReminder reminder = reminders.findById(result.id()).orElseThrow();
        reminder.setScheduledAt(now().minusMinutes(1));
        reminders.saveAndFlush(reminder);
        device.setActive(false);
        subscriptions.saveAndFlush(device);
        service.sendDueReminders();
        verify(push, never()).send(any());
        assertThat(reminder.getStatus()).isEqualTo("PENDING");
    }

    @Test void temporaryFailureRetriesWithoutCreatingDuplicateRepeats() {
        AppUser owner = users.save(new AppUser("+919800000004", "farmer", "test-hash"));
        enableAndroid(owner);
        FarmReminder reminder = dueReminder(owner, "DAILY");
        when(push.send(any())).thenThrow(new IllegalStateException("temporary provider outage"));
        service.sendDueReminders();
        assertThat(reminder.getStatus()).isEqualTo("PENDING");
        assertThat(reminder.getDeliveryAttempts()).isEqualTo(1);
        assertThat(reminder.getNextDeliveryAt()).isAfter(now());
        assertThat(reminders.findAllByOwnerIdOrderByScheduledAtAsc(owner.getId())).hasSize(1);
        service.sendDueReminders();
        verify(push, times(1)).send(any());

        reminder.setNextDeliveryAt(now().minusSeconds(1));
        reminders.saveAndFlush(reminder);
        doReturn(true).when(push).send(any());
        service.sendDueReminders();
        assertThat(reminder.getStatus()).isEqualTo("SENT");
        assertThat(reminder.getNextDeliveryAt()).isNull();
        assertThat(reminders.findAllByOwnerIdOrderByScheduledAtAsc(owner.getId())).hasSize(2);
        service.sendDueReminders();
        verify(push, times(2)).send(any());
    }

    @Test void interruptedSendIsRecoveredAfterRestart() {
        AppUser owner = users.save(new AppUser("+919800000005", "farmer", "test-hash"));
        enableAndroid(owner);
        FarmReminder reminder = dueReminder(owner, "NONE");
        reminder.setStatus("SENDING");
        reminders.saveAndFlush(reminder);
        service.markInterruptedSends();
        assertThat(reminder.getStatus()).isEqualTo("PENDING");
        when(push.send(any())).thenReturn(true);
        service.sendDueReminders();
        assertThat(reminder.getStatus()).isEqualTo("SENT");
    }

    @Test void repeatedProviderFailureStopsAfterFiveAttempts() {
        AppUser owner = users.save(new AppUser("+919800000008", "farmer", "test-hash"));
        enableAndroid(owner);
        FarmReminder reminder = dueReminder(owner, "DAILY");
        when(push.send(any())).thenReturn(false);
        for (int attempt = 0; attempt < 5; attempt++) {
            reminder.setNextDeliveryAt(now().minusSeconds(1));
            reminders.saveAndFlush(reminder);
            service.sendDueReminders();
        }
        assertThat(reminder.getStatus()).isEqualTo("FAILED");
        assertThat(reminder.getDeliveryAttempts()).isEqualTo(5);
        assertThat(reminders.findAllByOwnerIdOrderByScheduledAtAsc(owner.getId())).hasSize(2);
        service.sendDueReminders();
        verify(push, times(5)).send(any());
    }

    private FarmReminder dueReminder(AppUser owner, String frequency) {
        var view = service.create(new FarmReminderService.ReminderRequest(now().plusMinutes(5), "शेतीकामाची आठवण", null, "कांदा", frequency, null), owner);
        FarmReminder reminder = reminders.findById(view.id()).orElseThrow();
        reminder.setScheduledAt(now().minusMinutes(1));
        return reminders.saveAndFlush(reminder);
    }

    private BrowserPushSubscription enableAndroid(AppUser owner) {
        BrowserPushSubscription device = new BrowserPushSubscription();
        device.setOwner(owner);
        device.setEndpoint("https://fcm.googleapis.com/test-" + owner.getId());
        device.setP256dhKey("test-key"); device.setAuthKey("test-auth"); device.setAndroidApp(true);
        return subscriptions.saveAndFlush(device);
    }
}

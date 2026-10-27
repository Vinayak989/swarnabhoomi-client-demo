package in.swarnabhoomi.farmlog;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class BrowserPushServiceTest {
    private final BrowserPushSubscriptionRepository repository = mock(BrowserPushSubscriptionRepository.class);
    private BrowserPushService service;

    @BeforeEach void setup() {
        service = spy(new BrowserPushService(repository, mock(FarmReminderRepository.class), new ObjectMapper()));
        ReflectionTestUtils.setField(service, "publicKey", "test-public-key");
        ReflectionTestUtils.setField(service, "privateKey", "test-private-key");
        doReturn(true).when(service).deliver(any(), any());
    }

    @Test void reminderReachesCurrentDevicesWithCorrectAndroidAndWebLinks() {
        AppUser owner = new AppUser("+919800000006", "farmer", "test");
        ReflectionTestUtils.setField(owner, "id", 7L);
        BrowserPushSubscription android = new BrowserPushSubscription(); android.setAndroidApp(true);
        BrowserPushSubscription web = new BrowserPushSubscription();
        when(repository.findAllByOwnerIdAndActiveTrue(7L)).thenReturn(List.of(android, web));
        FarmReminder reminder = new FarmReminder(); reminder.setId(12L); reminder.setOwner(owner);
        reminder.setReason("पाणी द्या"); reminder.setCropOrPlot("कांदा");
        reminder.setScheduledAt(LocalDateTime.of(2026, 11, 1, 9, 30));
        assertThat(service.send(reminder)).isTrue();
        verify(service).deliver(eq(android), argThat(payload -> payload.url().contains("androidApp=1")
                && payload.url().contains("tab=reminders") && payload.tag().equals("reminder-12")
                && payload.body().contains("कांदा") && payload.timestamp() == 1793505600000L));
        verify(service).deliver(eq(web), argThat(payload -> payload.url().equals("/?tab=reminders")));
    }

    @Test void anotherDevicesFailureDoesNotPreventAndroidDelivery() {
        BrowserPushSubscription broken = new BrowserPushSubscription();
        BrowserPushSubscription android = new BrowserPushSubscription(); android.setAndroidApp(true);
        when(repository.findAllByOwnerIdAndActiveTrue(7L)).thenReturn(List.of(broken, android));
        doThrow(new IllegalStateException("provider down")).when(service).deliver(eq(broken), any());
        assertThat(service.notifyUser(7L, "title", "body", "/?tab=reminders")).isTrue();
        verify(service).deliver(eq(android), any());
    }

    @Test void testNotificationsCannotTargetAnotherUsersDevice() {
        AppUser owner = new AppUser("+919800000007", "farmer", "test");
        ReflectionTestUtils.setField(owner, "id", 7L);
        when(repository.findByIdAndOwnerIdAndActiveTrue(99L, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.sendTest(99L, owner)).isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(404));
        verify(service, never()).deliver(any(), any());
    }
}

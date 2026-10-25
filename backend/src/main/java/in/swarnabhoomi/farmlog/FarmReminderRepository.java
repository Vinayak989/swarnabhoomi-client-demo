package in.swarnabhoomi.farmlog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface FarmReminderRepository extends JpaRepository<FarmReminder, Long> {
    List<FarmReminder> findAllByOwnerIdOrderByScheduledAtAsc(Long ownerId);
    java.util.Optional<FarmReminder> findByIdAndOwnerId(Long id, Long ownerId);
    @Modifying
    @Query("update FarmReminder r set r.browserSubscription = null where r.browserSubscription.id = :subscriptionId and r.owner is null")
    int detachLegacyReminders(@Param("subscriptionId") Long subscriptionId);
    List<FarmReminder> findAllByStatus(String status);
    List<FarmReminder> findTop50ByStatusAndOwnerIsNotNullAndScheduledAtLessThanEqualAndBrowserSubscriptionIsNotNullOrderByScheduledAtAsc(
            String status, LocalDateTime scheduledAt);
}

package in.swarnabhoomi.farmlog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface FarmReminderRepository extends JpaRepository<FarmReminder, Long> {
    List<FarmReminder> findAllByOrderByScheduledAtAsc();
    List<FarmReminder> findAllByStatus(String status);
    List<FarmReminder> findTop50ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            String status, LocalDateTime scheduledAt);
}

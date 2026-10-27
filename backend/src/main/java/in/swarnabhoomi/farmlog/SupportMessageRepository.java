package in.swarnabhoomi.farmlog;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {
    List<SupportMessage> findByThreadIdOrderByIdAsc(Long threadId);
    Optional<SupportMessage> findTopByThreadIdOrderByIdDesc(Long threadId);
    long countByThreadIdAndSenderRoleAndReadAtIsNull(Long threadId, String senderRole);
    long countBySenderRoleNotAndReadAtIsNull(String senderRole);

    @Modifying
    @Query("update SupportMessage message set message.readAt = :readAt "
            + "where message.threadId = :threadId and message.senderRole = :senderRole "
            + "and message.readAt is null")
    int markRead(@Param("threadId") Long threadId, @Param("senderRole") String senderRole,
            @Param("readAt") Instant readAt);
}

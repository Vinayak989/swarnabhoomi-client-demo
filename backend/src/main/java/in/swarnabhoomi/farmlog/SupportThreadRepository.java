package in.swarnabhoomi.farmlog;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportThreadRepository extends JpaRepository<SupportThread, Long> {
    Optional<SupportThread> findByParticipantUserId(Long participantUserId);
    Page<SupportThread> findAllByOrderByUpdatedAtDescIdDesc(Pageable pageable);
}

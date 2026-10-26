package in.swarnabhoomi.farmlog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceConversationBlockRepository extends JpaRepository<MarketplaceConversationBlock, Long> {
    boolean existsByRequestIdAndUserId(Long requestId, Long userId);
    List<MarketplaceConversationBlock> findAllByRequestId(Long requestId);
    Optional<MarketplaceConversationBlock> findByRequestIdAndUserId(Long requestId, Long userId);
}

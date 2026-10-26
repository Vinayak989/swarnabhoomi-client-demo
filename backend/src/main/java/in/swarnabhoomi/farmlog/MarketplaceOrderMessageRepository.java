package in.swarnabhoomi.farmlog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceOrderMessageRepository extends JpaRepository<MarketplaceOrderMessage, Long> {
    List<MarketplaceOrderMessage> findAllByRequestIdOrderByCreatedAtAsc(Long requestId);
    Optional<MarketplaceOrderMessage> findByIdAndRequestId(Long id, Long requestId);
}

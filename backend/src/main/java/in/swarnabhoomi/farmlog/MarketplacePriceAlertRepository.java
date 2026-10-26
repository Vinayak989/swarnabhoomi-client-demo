package in.swarnabhoomi.farmlog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplacePriceAlertRepository extends JpaRepository<MarketplacePriceAlert, Long> {
    List<MarketplacePriceAlert> findAllByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<MarketplacePriceAlert> findByIdAndUserId(Long id, Long userId);
}

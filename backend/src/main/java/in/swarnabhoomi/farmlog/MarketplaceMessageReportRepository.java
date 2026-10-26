package in.swarnabhoomi.farmlog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceMessageReportRepository extends JpaRepository<MarketplaceMessageReport, Long> {
    boolean existsByMessageIdAndReporterUserId(Long messageId, Long reporterUserId);
    Page<MarketplaceMessageReport> findAllByStatusOrderByCreatedAtDesc(MarketplaceMessageReport.Status status, Pageable pageable);
}

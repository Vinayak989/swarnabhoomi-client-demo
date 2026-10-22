package in.swarnabhoomi.farmlog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FarmProductListingRepository extends JpaRepository<FarmProductListing, Long> {
    List<FarmProductListing> findTop200ByOrderByCreatedAtDesc();
}

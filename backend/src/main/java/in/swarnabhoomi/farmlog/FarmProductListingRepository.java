package in.swarnabhoomi.farmlog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

public interface FarmProductListingRepository extends JpaRepository<FarmProductListing, Long> {
    java.util.Optional<FarmProductListing> findByIdAndOwnerId(Long id, Long ownerId);
    List<FarmProductListing> findTop200ByOrderByCreatedAtDesc();
    long countByAvailableTrue();
    @EntityGraph(attributePaths = "owner")
    Page<FarmProductListing> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

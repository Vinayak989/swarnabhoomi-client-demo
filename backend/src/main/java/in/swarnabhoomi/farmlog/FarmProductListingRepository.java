package in.swarnabhoomi.farmlog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface FarmProductListingRepository extends JpaRepository<FarmProductListing, Long> {
    java.util.Optional<FarmProductListing> findByIdAndOwnerId(Long id, Long ownerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select listing from FarmProductListing listing where listing.id = :id")
    java.util.Optional<FarmProductListing> findByIdForUpdate(@Param("id") Long id);
    List<FarmProductListing> findTop200ByOrderByCreatedAtDesc();
    long countByAvailableTrue();
    @EntityGraph(attributePaths = "owner")
    Page<FarmProductListing> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

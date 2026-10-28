package in.swarnabhoomi.farmlog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FarmPhotoRepository extends JpaRepository<FarmPhoto, String> {
    Optional<FarmPhoto> findByIdAndOwnerId(String id, Long ownerId);
    Optional<FarmPhoto> findByIdAndMarketplacePublicTrue(String id);
    boolean existsByIdAndOwnerId(String id, Long ownerId);
    @Query("select p.id from FarmPhoto p where p.id in :ids")
    List<String> findExistingIds(@Param("ids") Collection<String> ids);
}

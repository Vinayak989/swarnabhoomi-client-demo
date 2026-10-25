package in.swarnabhoomi.farmlog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FarmPhotoRepository extends JpaRepository<FarmPhoto, String> {
    Optional<FarmPhoto> findByIdAndOwnerId(String id, Long ownerId);
    Optional<FarmPhoto> findByIdAndMarketplacePublicTrue(String id);
}

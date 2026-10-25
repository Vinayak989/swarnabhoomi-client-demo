package in.swarnabhoomi.farmlog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface FarmEntryRepository extends JpaRepository<FarmEntry, Long> {
    List<FarmEntry> findAllByOwnerIdOrderByEntryDateDescIdDesc(Long ownerId);
    Optional<FarmEntry> findFirstByOwnerIdAndClientRequestId(Long ownerId, String clientRequestId);
    Optional<FarmEntry> findByIdAndOwnerId(Long id, Long ownerId);
}

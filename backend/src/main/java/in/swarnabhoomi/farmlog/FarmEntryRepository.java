package in.swarnabhoomi.farmlog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface FarmEntryRepository extends JpaRepository<FarmEntry, Long> { List<FarmEntry> findAllByOrderByEntryDateDescIdDesc(); }

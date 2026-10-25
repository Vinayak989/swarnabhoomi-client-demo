package in.swarnabhoomi.farmlog;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganicConsultantRepository extends JpaRepository<OrganicConsultant, Long> {
    List<OrganicConsultant> findTop200ByOrderByCreatedAtDesc();
    Page<OrganicConsultant> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

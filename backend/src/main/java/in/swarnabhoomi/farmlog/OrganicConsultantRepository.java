package in.swarnabhoomi.farmlog;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganicConsultantRepository extends JpaRepository<OrganicConsultant, Long> {
    List<OrganicConsultant> findTop200ByDeletedAtIsNullOrderByCreatedAtDesc();
    Page<OrganicConsultant> findAllByDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);
    long countByDeletedAtIsNull();
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select consultant from OrganicConsultant consultant where consultant.id=:id")
    java.util.Optional<OrganicConsultant> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select consultant from OrganicConsultant consultant where consultant.id=:id and consultant.deletedAt is null")
    java.util.Optional<OrganicConsultant> findActiveByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}

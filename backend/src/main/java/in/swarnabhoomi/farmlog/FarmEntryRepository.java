package in.swarnabhoomi.farmlog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.Set;
public interface FarmEntryRepository extends JpaRepository<FarmEntry, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from FarmEntry e where e.id=:id and e.owner.id=:owner")
    java.util.Optional<FarmEntry> lockOwned(@org.springframework.data.repository.query.Param("id") Long id,@org.springframework.data.repository.query.Param("owner") Long owner);
    List<FarmEntry> findAllByOwnerIdOrderByEntryDateDescIdDesc(Long ownerId);
    Optional<FarmEntry> findFirstByOwnerIdAndClientRequestId(Long ownerId, String clientRequestId);
    Optional<FarmEntry> findByIdAndOwnerId(Long id, Long ownerId);

    @Query("select count(e) from FarmEntry e where e.owner.role = :role")
    long countAllByOwnerRole(@Param("role") String role);

    @Query("select count(e) from FarmEntry e where e.owner.role in :roles")
    long countAllByOwnerRoles(@Param("roles") Set<String> roles);

    @Query("select count(e) from FarmEntry e where e.owner.role = :role and e.entryDate = :date")
    long countAllByOwnerRoleAndEntryDate(@Param("role") String role, @Param("date") java.time.LocalDate date);

    @Query("select count(e) from FarmEntry e where e.owner.role in :roles and e.entryDate = :date")
    long countAllByOwnerRolesAndEntryDate(@Param("roles") Set<String> roles, @Param("date") java.time.LocalDate date);

    @EntityGraph(attributePaths = "owner")
    @Query("select e from FarmEntry e where e.owner.role = :role order by e.entryDate desc, e.id desc")
    Page<FarmEntry> findAllByOwnerRole(@Param("role") String role, Pageable pageable);

    @EntityGraph(attributePaths = "owner")
    @Query("select e from FarmEntry e where e.owner.role in :roles order by e.entryDate desc, e.id desc")
    Page<FarmEntry> findAllByOwnerRoles(@Param("roles") Set<String> roles, Pageable pageable);
}

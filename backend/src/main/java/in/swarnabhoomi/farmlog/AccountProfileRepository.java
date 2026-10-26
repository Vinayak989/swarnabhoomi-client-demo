package in.swarnabhoomi.farmlog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountProfileRepository extends JpaRepository<AccountProfile, Long> {
    Optional<AccountProfile> findByUserId(Long userId);

    @Query("select profile from AccountProfile profile where profile.userId in :userIds")
    List<AccountProfile> findAllByUserIds(@Param("userIds") Collection<Long> userIds);
}

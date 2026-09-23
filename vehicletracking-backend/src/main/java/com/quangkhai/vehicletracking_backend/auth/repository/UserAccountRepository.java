package com.quangkhai.vehicletracking_backend.auth.repository;

import com.quangkhai.vehicletracking_backend.auth.entity.UserAccountEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, Long> {
    @EntityGraph(attributePaths = "driver")
    Optional<UserAccountEntity> findByUsername(String username);

    @EntityGraph(attributePaths = "driver")
    List<UserAccountEntity> findAllByOrderByUsernameAsc();

    boolean existsByUsername(String username);

    boolean existsByDriverId(Long driverId);

    /**
     * Re-check a session principal against persistent account and driver state.
     * A driver account is not usable once either record is disabled.
     */
    @Query("""
            select case when count(account) > 0 then true else false end
            from UserAccountEntity account
            left join account.driver driver
            where account.id = :accountId
              and account.active = true
              and (driver is null or driver.active = true)
            """)
    boolean isActiveForAuthentication(@Param("accountId") long accountId);
}

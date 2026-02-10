package com.menval.couriererp.auth.repository;

import com.menval.couriererp.auth.models.BaseUser;
import com.menval.couriererp.auth.models.UserRoles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<BaseUser, Long> {
    Optional<BaseUser> findByEmail(String email);

    @Query("SELECT COUNT(u) FROM BaseUser u WHERE :role MEMBER OF u.roles")
    long countByRolesContaining(@Param("role") UserRoles role);
}

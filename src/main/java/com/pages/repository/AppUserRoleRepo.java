package com.pages.repository;

import com.pages.model.AppUserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppUserRoleRepo extends JpaRepository<AppUserRole,Long> {

    Optional<AppUserRole> findByRole(String role);

    boolean existsByRole(String role);
}

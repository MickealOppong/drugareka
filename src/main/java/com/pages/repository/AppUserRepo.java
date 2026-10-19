package com.pages.repository;

import com.pages.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppUserRepo extends JpaRepository<AppUser,Long> {


    Optional<AppUser> findByUsername(String username);
    Optional<AppUser> findByPasswordResetToken(String token);

    boolean existsByUsername(String username);

}

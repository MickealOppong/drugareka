package com.pages.util;

import com.pages.config.PasswordConfig;
import com.pages.enums.UserRole;
import com.pages.impl.MediaUtilImpl;
import com.pages.model.AppUser;
import com.pages.model.AppUserRole;
import com.pages.repository.AppUserRepo;
import com.pages.repository.AppUserRoleRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

@Component
public class AdminInitializer implements CommandLineRunner {

    private final AppUserRepo appUserRepository;
    private final PasswordConfig passwordConfig;
    private final AppUserRoleRepo appUserRoleRepo;
    private MediaUtilImpl mediaUtilImpl;


    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    public AdminInitializer(AppUserRepo appUserRepository, PasswordConfig passwordConfig, AppUserRoleRepo appUserRoleRepo, MediaUtilImpl mediaUtilImpl) {
        this.appUserRepository = appUserRepository;
        this.passwordConfig = passwordConfig;
        this.appUserRoleRepo = appUserRoleRepo;
        this.mediaUtilImpl = mediaUtilImpl;
    }


    @Override
    public void run(String... args) throws IOException {

        mediaUtilImpl.init();

        boolean roleExistsAdmin= appUserRoleRepo.existsByRole("ROLE_ADMIN");
        boolean roleExistsUser= appUserRoleRepo.existsByRole("ROLE_USER");
        boolean roleExistsSeller= appUserRoleRepo.existsByRole("ROLE_SELLER");

        if(!roleExistsUser){
            AppUserRole USER = new AppUserRole("ROLE_USER");
            appUserRoleRepo.save(USER);
        }
        if(!roleExistsAdmin){
            AppUserRole USER = new AppUserRole("ROLE_ADMIN");
            appUserRoleRepo.save(USER);
        }

        if(!roleExistsSeller){
            AppUserRole SELLER = new AppUserRole("ROLE_SELLER");
            appUserRoleRepo.save(SELLER);
        }


        if (appUserRepository.existsByUsername(adminUsername)) {
            return;
        }


        AppUser admin = new AppUser();


        admin.setUsername(adminUsername);
        admin.setSysAdmin(true);
        admin.setFirstName("System");
        admin.setLastName("Administrator");

        admin.setPassword(
                passwordConfig.passwordEncoder().encode(adminPassword)
        );

        appUserRoleRepo.findByRole(UserRole.ROLE_ADMIN.name()).ifPresent(role -> admin.setUserRoles(Set.of(role)));

        admin.setTermsAccepted(true);

        appUserRepository.save(admin);

        System.out.println(
                "Initial administrator created: "
                        + adminUsername
        );
    }
}

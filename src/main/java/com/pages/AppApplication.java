package com.pages;

import com.pages.impl.MediaUtilImpl;
import com.pages.model.AppUserRole;
import com.pages.repository.AppUserRoleRepo;
import com.pages.util.RsaKeyProperties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@EnableAsync
@SpringBootApplication
@EnableConfigurationProperties(RsaKeyProperties.class)
@AllArgsConstructor
@EnableScheduling
public class AppApplication {

	private MediaUtilImpl mediaUtilImpl;
	private AppUserRoleRepo appUserRoleRepo;

	public static void main(String[] args) {
		SpringApplication.run(AppApplication.class, args);
	}

	/*
	@Bean
	public CommandLineRunner init(){
		return args -> {
			mediaUtilImpl.init();

			boolean roleExistsAdmin= appUserRoleRepo.existsByRole("ROLE_ADMIN");
			boolean roleExistsUser= appUserRoleRepo.existsByRole("ROLE_USER");

			if(!roleExistsUser){
				AppUserRole USER = new AppUserRole("ROLE_USER");
				appUserRoleRepo.save(USER);
			}
			if(!roleExistsAdmin){
				AppUserRole USER = new AppUserRole("ROLE_ADMIN");
				appUserRoleRepo.save(USER);
			}


		};
	}

	 */
}

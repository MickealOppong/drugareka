package com.pages.service;

import com.pages.config.PasswordConfig;
import com.pages.dto.*;
import com.pages.enums.Request_Status;
import com.pages.enums.UserRole;
import com.pages.exception.EntityNotFoundException;
import com.pages.exception.InvalidOperationException;
import com.pages.model.*;
import com.pages.repository.*;
import com.pages.util.Media;
import com.pages.util.Notification;
import jakarta.transaction.Transactional;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.RollbackOn;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.security.Principal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.pages.util.UtilService.formatRoleInput;


@Slf4j
@Service
public class AppUserDetailsService implements UserDetailsService {

    @Autowired
    private PasswordConfig passwordConfig;


    private final AppUserRepo appUserRepo;
    private final AppUserRoleRepo appUserRoleRepo;

    public AppUserDetailsService(AppUserRepo appUserRepo, AppUserRoleRepo appUserRoleRepo) {
        this.appUserRepo = appUserRepo;
        this.appUserRoleRepo = appUserRoleRepo;
    }


    @Override
    public UserDetails loadUserByUsername( String username) throws UsernameNotFoundException {
        AppUser appUser = appUserRepo.findByUsername(username).orElseThrow(()->new UsernameNotFoundException(""));
        return new User(appUser.getUsername(),appUser.getPassword(),appUser.getAuthorities());
    }



    public void addSellerRole(AppUser appUser){
        AppUser user = getAppUserByUsername(appUser.getUsername());

        //find or create role
        AppUserRole sellerRole = appUserRoleRepo.findByRole(UserRole.ROLE_SELLER.name()).orElseGet(()->{
            AppUserRole seller = new AppUserRole("ROEL_SELLER");
            return appUserRoleRepo.save(seller);
        });

        //update user role
        if(!user.getUserRoles().contains(sellerRole)){
            //if not present add
            user.addRole(sellerRole);
            //save user
            appUserRepo.save(user);
        }


    }

    @Transactional
    public ResponseDto<Object> deleteMyAccount(Jwt jwt) {

        String username = jwt.getSubject();

        AppUser user = appUserRepo.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User does not exist"));

        Long userId = user.getId();

        // delete post media here (or after commit if external storage)


        //log.info("{}",mediaToDelete);
        //delete profile media
      // Media profileMedia = user.getMedia();

        //delete all notifications






        appUserRepo.delete(user);





        return ResponseDto.builder()
                .httpStatus(HttpStatus.OK.value())
                .message("Account deleted successfully")
                .data(true)
                .build();
    }



    public void changePassword(String username, ChangePasswordDto dto) {

        AppUser user = appUserRepo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User profile not found."));

        if (!passwordConfig.passwordEncoder().matches(dto.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("The current password you entered is incorrect.");
        }


        if (!dto.getNewPassword().equals(dto.getConfirmNewPassword())) {
            throw new IllegalArgumentException("New password and confirmation password do not match.");
        }


        if (passwordConfig.passwordEncoder().matches(dto.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("New password cannot be the same as your old password.");
        }

        String encryptedPassword = passwordConfig.passwordEncoder().encode(dto.getNewPassword());
        user.setPassword(encryptedPassword);
       appUserRepo.save(user); // Triggers transactional database flush
    }

    @Transactional
    public ResponseDto<Object> add(UserRegistrationRequest userRegistrationRequest){

        try{
            if(alreadyExist(userRegistrationRequest.getEmail().trim())){
                throw new InvalidOperationException("Choose a different email");
            }

            AppUserRole userRole = appUserRoleRepo.findByRole("ROLE_USER").orElseThrow(()->new EntityNotFoundException("Role does not exist"));

            //create user
            AppUser appUser = new AppUser(userRegistrationRequest.getFirstName(),userRegistrationRequest.getLastName(),userRegistrationRequest.getEmail()
            ,passwordConfig.passwordEncoder().encode(userRegistrationRequest.getPassword()),userRegistrationRequest.isTermsAccepted());

            //set user Role
            appUser.setUserRoles(Set.of(userRole));
            //persist user
            appUserRepo.save(appUser);
            return ResponseDto.builder()
                    .data(true)
                    .httpStatus(HttpStatus.OK.value())
                    .message("Created")
                    .build();
        }catch (Exception e){
            return ResponseDto.builder()
                    .data(false)
                    .httpStatus(HttpStatus.FORBIDDEN.value())
                    .message(e.getMessage())
                    .build();
        }
    }

    @Transactional
    public ResponseDto<Object> addUser(UserDetailsUpdateDto userRegistrationRequest){

        try{

            if(alreadyExist(userRegistrationRequest.getEmail().trim())){
                throw new InvalidOperationException("Choose a different email");
            }

            AppUser appUser = AppUser.builder()
                    .firstName(userRegistrationRequest.getFirstName())
                    .lastName(userRegistrationRequest.getLastName())
                    .username(userRegistrationRequest.getEmail())
                    .dateOfBirth(userRegistrationRequest.getDob())
                    .password(passwordConfig.passwordEncoder().encode(userRegistrationRequest.getPassword()))
                    .isTermsAccepted(userRegistrationRequest.isTermsAccepted())
                    .enabled(userRegistrationRequest.isTermsAccepted())
                    .accountNonExpired(userRegistrationRequest.isTermsAccepted())
                    .accountNonLocked(userRegistrationRequest.isTermsAccepted())
                    .credentialsNonExpired(userRegistrationRequest.isTermsAccepted())
                    .build();

            appUserRoleRepo.findByRole(formatRoleInput(userRegistrationRequest.getRole())).ifPresent(role -> appUser.setUserRoles(Set.of(role)));
            appUserRepo.save(appUser);
            return ResponseDto.builder()
                    .httpStatus(HttpStatus.OK.value())
                    .data(true)
                    .message("Created")
                    .build();
        }catch (Exception e){
            return ResponseDto.builder()
                    .httpStatus(HttpStatus.FORBIDDEN.value())
                    .data(false)
                    .message(e.getMessage())
                    .build();
        }
    }

    public AppUser getAppUserByUsername(String username){
        return appUserRepo.findByUsername(username).orElseThrow(()->new UsernameNotFoundException(username+ " "+"does not exist"));
    }

    public AppUser getAppUserId(Long id){
        return appUserRepo.findById(id).orElseThrow(()->new UsernameNotFoundException(id+ " "+"does not exist"));
    }

    public ResponseDto<Object> getAppUser(String username){
       AppUser appUser= appUserRepo.findByUsername(username)
                .orElseThrow(()->new UsernameNotFoundException("User does not exist"));
       UserDetailsDto userDetailsDto = UserDetailsDto.builder()
               .userId(appUser.getId())
               .email(appUser.getUsername())
               .firstName(appUser.getFirstName())
               .lastName(appUser.getLastName())
               .isTermsAccepted(appUser.isTermsAccepted())
               .roles(appUser.getUserRoles().stream().map(AppUserRole::getRole).collect(Collectors.toSet()))
               .dob(appUser.getDateOfBirth())
               .build();
       return ResponseDto.builder()
               .httpStatus(HttpStatus.OK.value())
               .data(userDetailsDto)
               .message("Success")
               .build();
    }

    public boolean alreadyExist(String username){
       return appUserRepo.findByUsername(username).isPresent();
    }

    public List<UserDetailsDto> allUsers(){
       return appUserRepo.findAll().stream().map(appUser -> {
            return UserDetailsDto.builder()
                    .userId(appUser.getId())
                    .firstName(appUser.getFirstName())
                    .lastName(appUser.getLastName())
                    .dob(appUser.getDateOfBirth())
                    .email(appUser.getUsername())
                    .roles(appUser.getUserRoles().stream().map(AppUserRole::getRole).collect(Collectors.toSet()))
                    .build();
        }).toList();
    }

/*
    public ResponseDto<Object> updateUser(UserDetailsUpdateDto userDetailsDto) {
        try {

            log.info("{}",userDetailsDto);
            // 2. Safely verify identity data presence before running lazy mutations
            Optional<AppUser> appUserOptional = appUserRepo.findById(userDetailsDto.getUserId());
            if (appUserOptional.isEmpty()) {
                return ResponseDto.builder()
                        .data(false)
                        .message("User profile not found in database registry")
                        .httpStatus(HttpStatus.NOT_FOUND.value())
                        .build();
            }

            AppUser appUser = appUserOptional.get();

            // 3. StringUtils.hasText checks for null AND sweeps empty space text strings safely
            if (StringUtils.hasText(userDetailsDto.getFirstName())) {
                appUser.setFirstName(userDetailsDto.getFirstName().trim());
            }
            if (StringUtils.hasText(userDetailsDto.getLastName())) {
                appUser.setLastName(userDetailsDto.getLastName().trim());
            }

            if (StringUtils.hasText(userDetailsDto.getEmail())) {
                appUser.setUsername(userDetailsDto.getEmail().trim());
            }


            if (StringUtils.hasText(userDetailsDto.getRole())) {
                appUserRoleRepo.findByRole(formatRoleInput(userDetailsDto.getRole().trim())).ifPresent(appUser::assignSingleRole);

            }

            if (StringUtils.hasText(userDetailsDto.getPassword().trim())) {
                appUser.setPassword(passwordConfig.passwordEncoder().encode(userDetailsDto.getLastName().trim()));
            }
            if (userDetailsDto.getDob() != null) {
                appUser.setDateOfBirth(userDetailsDto.getDob());
            }


            if (!userDetailsDto.isTermsAccepted()) {
                appUser.setTermsAccepted(false);
                appUser.setEnabled(false);
                appUser.setAccountNonExpired(false);
                appUser.setCredentialsNonExpired(false);
                appUser.setAccountNonLocked(false);
            }


            appUserRepo.save(appUser);

            return ResponseDto.builder()
                    .data(true)
                    .message("User updated successfully")
                    .httpStatus(HttpStatus.OK.value())
                    .build();

        } catch (Exception e) {
            return ResponseDto.builder()
                    .data(false)
                    .message("Update aborted: " + e.getMessage())
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .build();
        }
    }

*/

    public Set<String> getRoles(){
        return appUserRoleRepo.findAll().stream().map(AppUserRole::getRole).collect(Collectors.toSet());
    }

    public String getUsernameById(Long id){
        AppUser appUser =appUserRepo.findById(id).orElse(null);
        if(appUser!=null){
            return appUser.getFirstName()+" "+appUser.getLastName();
        }
        return null;
    }

    @Transactional
    public ResponseDto<Object> updateUser(UserDetailsUpdateDto userDetailsDto) {

        try {

            log.info("Updating user: {}", userDetailsDto.getUserId());

            Optional<AppUser> appUserOptional =
                    appUserRepo.findById(userDetailsDto.getUserId());

            if (appUserOptional.isEmpty()) {

                return ResponseDto.builder()
                        .data(false)
                        .message("User profile not found in database registry")
                        .httpStatus(HttpStatus.NOT_FOUND.value())
                        .build();
            }

            AppUser appUser = appUserOptional.get();



            if (StringUtils.hasText(userDetailsDto.getFirstName())) {

                appUser.setFirstName(
                        userDetailsDto.getFirstName().trim()
                );
            }


            if (StringUtils.hasText(userDetailsDto.getLastName())) {

                appUser.setLastName(
                        userDetailsDto.getLastName().trim()
                );
            }


            if (StringUtils.hasText(userDetailsDto.getEmail())) {

                appUser.setUsername(
                        userDetailsDto.getEmail().trim()
                );
            }




            if (StringUtils.hasText(userDetailsDto.getRole())) {

                String roleName =
                        userDetailsDto.getRole().trim();

                AppUserRole newRole =
                        appUserRoleRepo.findByRole(formatRoleInput(roleName))
                                .orElseThrow(() ->
                                        new RuntimeException(
                                                "Role not found: " + roleName
                                        )
                                );

                appUser.assignSingleRole(newRole);
            }


            if (StringUtils.hasText(userDetailsDto.getPassword())) {

                String encodedPassword =
                        passwordConfig
                                .passwordEncoder()
                                .encode(
                                        userDetailsDto
                                                .getPassword()
                                                .trim()
                                );

                appUser.setPassword(encodedPassword);
            }


            if (userDetailsDto.getDob() != null) {

                appUser.setDateOfBirth(
                        userDetailsDto.getDob()
                );
            }



            if (!userDetailsDto.isTermsAccepted()) {

                appUser.setTermsAccepted(false);
                appUser.setEnabled(false);
                appUser.setAccountNonExpired(false);
                appUser.setCredentialsNonExpired(false);
                appUser.setAccountNonLocked(false);
            }


            appUserRepo.save(appUser);


            return ResponseDto.builder()
                    .data(true)
                    .message("User updated successfully")
                    .httpStatus(HttpStatus.OK.value())
                    .build();


        } catch (Exception e) {

            log.error(
                    "Failed to update user {}",
                    userDetailsDto.getUserId(),
                    e
            );

            return ResponseDto.builder()
                    .data(false)
                    .message("Update aborted: " + e.getMessage())
                    .httpStatus(
                            HttpStatus.INTERNAL_SERVER_ERROR.value()
                    )
                    .build();
        }
    }




    public ResponseDto<Object> deleteUserById(Long userId){

        try{
           AppUser appUser = appUserRepo.findById(userId).orElse(null);
           if(appUser != null && appUser.isSysAdmin()){
               throw  new InvalidOperationException("You cant delete system administrator");
           }
           if(appUser!=null ){
               appUserRepo.deleteById(appUser.getId());
               return ResponseDto.builder()
                       .httpStatus(HttpStatus.OK.value())
                       .message("user deleted")
                       .build();
           }
            return ResponseDto.builder()
                    .httpStatus(HttpStatus.BAD_REQUEST.value())
                    .message("User not found")
                    .build();

        }catch (Exception e){
            return ResponseDto.builder()
                    .httpStatus(HttpStatus.FORBIDDEN.value())
                    .message(e.getMessage())
                    .build();
        }
    }

}

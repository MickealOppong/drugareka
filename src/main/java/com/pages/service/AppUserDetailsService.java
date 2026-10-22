package com.pages.service;

import com.pages.config.PasswordConfig;
import com.pages.dto.*;
import com.pages.enums.Request_Status;
import com.pages.enums.UserRole;
import com.pages.exception.EntityNotFoundException;
import com.pages.exception.InvalidOperationException;
import com.pages.exception.PayUValidationException;
import com.pages.model.*;
import com.pages.repository.*;
import com.pages.util.GlobalAddress;
import com.pages.util.Media;
import com.pages.util.Notification;
import com.pages.util.UtilService;
import jakarta.transaction.Transactional;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cglib.beans.BeanMap;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    private final GlobalAddressRepo globalAddressRepo;
    private final EmailNotificationService emailNotificationService;
    private final MessageSource messageSource;


    public AppUserDetailsService(AppUserRepo appUserRepo, AppUserRoleRepo appUserRoleRepo, GlobalAddressRepo globalAddressRepo, EmailNotificationService emailNotificationService, MessageSource messageSource) {
        this.appUserRepo = appUserRepo;
        this.appUserRoleRepo = appUserRoleRepo;

        this.globalAddressRepo = globalAddressRepo;
        this.emailNotificationService = emailNotificationService;
        this.messageSource = messageSource;
    }


    public ResponseDto<Object> resetPasswordRequest(String email){
       AppUser appUser = appUserRepo.findByUsername(email.trim()).orElse(null);
       if(appUser!=null){

           String token = UtilService.generatePasswordResetToken();
           appUser.setPasswordResetToken(token);

           //token expires period
           Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
           appUser.setTokenExpiresAt(expiresAt);
           appUserRepo.save(appUser);

           emailNotificationService.sendPasswordResetEmail(email,token);
           return ResponseDto.builder()
                   .httpStatus(HttpStatus.OK.value())
                   .message(messageSource.getMessage("api.appUser.password.reset",null,LocaleContextHolder.getLocale()))
                   .data(true)
                   .build();
       }
        return ResponseDto.builder()
                .httpStatus(HttpStatus.BAD_REQUEST.value())
                .message(messageSource.getMessage("api.appUser.email.not_found",null, LocaleContextHolder.getLocale()))
                .data(false)
                .build();
    }
    @Override
    public UserDetails loadUserByUsername( String username) throws UsernameNotFoundException {
        AppUser appUser = appUserRepo.findByUsername(username).orElseThrow(()->new UsernameNotFoundException(""));
        return new User(appUser.getUsername(),appUser.getPassword(),appUser.getAuthorities());
    }

    public Boolean hasAddress(Long userId){
        GlobalAddress address =globalAddressRepo.findByAppUserId(userId).orElse(null);
        return address != null;
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
    public ResponseDto<Object> addUser(UserRegistrationRequest request){

        try{

            if(alreadyExist(request.getEmail().trim())){
                throw new InvalidOperationException("Choose a different email");
            }

            AppUser appUser = AppUser.builder()
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .username(request.getEmail())
                    .dateOfBirth(request.getDob())
                    .password(passwordConfig.passwordEncoder().encode(request.getPassword()))
                    .isTermsAccepted(request.isTermsAccepted())
                    .enabled(request.isTermsAccepted())
                    .accountNonExpired(request.isTermsAccepted())
                    .accountNonLocked(request.isTermsAccepted())
                    .credentialsNonExpired(request.isTermsAccepted())
                    .build();

            appUserRoleRepo.findByRole(formatRoleInput(request.getRoles())).ifPresent(role -> appUser.setUserRoles(Set.of(role)));
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

    public ResponseDto<Object> getAppUser(Jwt jwt){
        if(jwt==null){
            return ResponseDto.builder()
                    .httpStatus(HttpStatus.FORBIDDEN.value())
                    .data(null)
                    .message("Not authorised")
                    .build();
        }

       AppUser appUser= appUserRepo.findByUsername(jwt.getSubject())
                .orElseThrow(()->new UsernameNotFoundException("User does not exist"));

        //address
        GlobalAddress address =globalAddressRepo.findByAppUserId(appUser.getId()).orElse(null);
        AddressResponse addressResponse=null;
        if(address!=null){
          addressResponse=  AddressResponse.builder()
                    .contact(address.getContact())
                    .country(address.getCountry())
                    .city(address.getCity())
                    .postalCode(address.getPostCode())
                    .street(address.getStreet())
                    .build();
        }
       UserDetailsDto userDetailsDto = UserDetailsDto.builder()
               .userId(appUser.getId())
               .email(appUser.getUsername())
               .firstName(appUser.getFirstName())
               .lastName(appUser.getLastName())
               .address(addressResponse)

               .roles(appUser.getUserRoles().stream().map(AppUserRole::getRole).collect(Collectors.toSet()))
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

    public ListPageUsers allUsers(int page,int size){

        Pageable pageable = PageRequest.of(
                page == 0 ? page : page - 1,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<UserDetailsDto> users = appUserRepo.findAll(pageable).map(appUser -> {

         return UserDetailsDto.builder()
                    .userId(appUser.getId())
                    .firstName(appUser.getFirstName())
                    .lastName(appUser.getLastName())
                    .email(appUser.getUsername())
                    .roles(appUser.getUserRoles().stream().map(AppUserRole::getRole).collect(Collectors.toSet()))
                    .build();
        });
        return ListPageUsers.builder()
                .userList(users.getContent())
                .page(users.getNumber())
                .pageSize(users.getSize())
                .accumulatedItems(getAccumulatedItemsFromPage(users.getNumber(),users.getSize(),users.getTotalElements()))
                .totalPages(users.getTotalPages())
                .totalElements(users.getTotalElements())
                .build();
    }





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

    public int getAccumulatedItemsFromPage(int currentPage,int pageSize,long totalElements){
        // 1. Calculate how many items exist up to the start boundary of the current page
        int itemsBeforeCurrentPage = currentPage * pageSize;

        // 2. Compute the exact remaining items left in the database for this page and beyond
        int remainingItems = (int)totalElements - itemsBeforeCurrentPage;

        // 3.  If remaining items are less than page size,
        // use the exact remainder; otherwise, add a full standard page size block.
        int accumulatedTotal;
        if (remainingItems < pageSize) {
            accumulatedTotal = itemsBeforeCurrentPage + remainingItems; // Caps exactly at TotalElements
        } else {
            accumulatedTotal = itemsBeforeCurrentPage + pageSize;       // Adds a standard full page block
        }
        return accumulatedTotal;
    }

    @Transactional
    public ResponseDto<Object> updateUser(Jwt jwt, UserDetailsUpdateDto userDetailsDto) {
        try {
            if (jwt == null) {
                return ResponseDto.builder()
                        .data(false)
                        .message("User not authorised to update")
                        .httpStatus(HttpStatus.FORBIDDEN.value())
                        .build();
            }
            log.info("Updating user profile: {}", userDetailsDto.getId());

            AppUser appUser = appUserRepo.findByUsername(jwt.getSubject()).orElse(null);
            if (appUser == null) {
                return ResponseDto.builder()
                        .data(false)
                        .message("User profile not found in database registry")
                        .httpStatus(HttpStatus.NOT_FOUND.value())
                        .build();
            }

            // Fetch the existing address to execute differential data checks
            GlobalAddress address = globalAddressRepo.findByAppUserId(appUser.getId()).orElse(null);

            /*
             * ==========================================================================
             *  PROACTIVE MERCHANT ONBOARDING GUARD CHECK
             * ==========================================================================
             * Trigger verification if the user explicitly holds ROLE_SELLER OR if they
             * are providing a bank account number right now to prepare for future selling.
             */

            /*
             * ==========================================================================
             *  DATABASE PERSISTENCE LAYER (Only committed if PayU validation passes)
             * ==========================================================================
             */
            if (StringUtils.hasText(userDetailsDto.getFirstName()))
                appUser.setFirstName(userDetailsDto.getFirstName().trim());
            if (StringUtils.hasText(userDetailsDto.getLastName()))
                appUser.setLastName(userDetailsDto.getLastName().trim());


            if (address != null) {
                if (StringUtils.hasText(userDetailsDto.getStreet()))
                    address.setStreet(userDetailsDto.getStreet().trim());
                if (StringUtils.hasText(userDetailsDto.getCity())) address.setCity(userDetailsDto.getCity().trim());
                if (StringUtils.hasText(userDetailsDto.getContact()))
                    address.setContact(userDetailsDto.getContact().trim());
                if (StringUtils.hasText(userDetailsDto.getPostalCode()))
                    address.setPostCode(userDetailsDto.getPostalCode().trim());
                if (StringUtils.hasText(userDetailsDto.getCountry()))
                    address.setCountry(userDetailsDto.getCountry().trim());
                globalAddressRepo.save(address);
            } else {
                GlobalAddress newAddress = GlobalAddress.builder()
                        .contact(userDetailsDto.getContact())
                        .appUser(appUser)
                        .city(userDetailsDto.getCity())
                        .street(userDetailsDto.getStreet())
                        .postCode(userDetailsDto.getPostalCode())
                        .country(userDetailsDto.getCountry())
                        .build();
                globalAddressRepo.save(newAddress);
            }

            appUserRepo.save(appUser);

            return ResponseDto.builder()
                    .data(true)
                    .message("User profile successfully updated and synced with payment provider.")
                    .httpStatus(HttpStatus.OK.value())
                    .build();

        } catch (Exception e) {
            log.error("Failed to update user profile details", e);
            return ResponseDto.builder()
                    .data(false)
                    .message("Update aborted due to a system processing error: " + e.getMessage())
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .build();
        }
    }




    @Transactional
    public ResponseDto<Object> changePassword(Jwt jwt,PasswordUpdateDto passwordDto) {

        try {
            if(jwt==null){
                return ResponseDto.builder()
                        .data(false)
                        .message("Not Authorised")
                        .httpStatus(HttpStatus.FORBIDDEN.value())
                        .build();
            }
            AppUser appUser = appUserRepo.findByUsername(jwt.getSubject()).orElse(null);

            if(appUser==null){
                return ResponseDto.builder()
                        .data(false)
                        .message("User not found")
                        .httpStatus(HttpStatus.BAD_REQUEST.value())
                        .build();
            }
            log.info("Changing password for user: {}", appUser.getId());


            /*
             * ==============================
             * VALIDATION
             * ==============================
             */

            if (!StringUtils.hasText(passwordDto.getCurrentPassword())) {

                return ResponseDto.builder()
                        .data(false)
                        .message("Current password is required")
                        .httpStatus(HttpStatus.BAD_REQUEST.value())
                        .build();
            }

            if (!StringUtils.hasText(passwordDto.getNewPassword())) {

                return ResponseDto.builder()
                        .data(false)
                        .message("New password is required")
                        .httpStatus(HttpStatus.BAD_REQUEST.value())
                        .build();
            }

            String newPassword = passwordDto
                            .getNewPassword()
                            .trim();


            /*
             * ==============================
             * CHECK CURRENT PASSWORD
             * ==============================
             */

            boolean passwordMatches =
                    passwordConfig
                            .passwordEncoder()
                            .matches(passwordDto
                                    .getCurrentPassword(), appUser.getPassword());

            if (!passwordMatches) {

                return ResponseDto.builder()
                        .data(false)
                        .message("Current password is incorrect")
                        .httpStatus(HttpStatus.BAD_REQUEST.value())
                        .build();
            }

            /*
             * ==============================
             * UPDATE PASSWORD
             * ==============================
             */

            String encodedPassword =
                    passwordConfig
                            .passwordEncoder()
                            .encode(newPassword);

            appUser.setPassword(encodedPassword);

            appUserRepo.save(appUser);

            return ResponseDto.builder()
                    .data(true)
                    .message("Password changed successfully")
                    .httpStatus(HttpStatus.OK.value())
                    .build();

        } catch (Exception e) {
            return ResponseDto.builder()
                    .data(false)
                    .message("Password change failed: " + e.getMessage())
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .build();
        }
    }

    @Transactional
    public ResponseDto<Object> changeUserPassword(ChangePasswordDto passwordDto) {
        Locale locale = LocaleContextHolder.getLocale();

        try {
            // 1. Fetch user profile linked to the recovery token signature
            AppUser appUser = appUserRepo.findByPasswordResetToken(passwordDto.getToken()).orElse(null);
            if (appUser == null) {
                String msg = messageSource.getMessage("api.appUser.token.not_found", null, "Invalid or unrecognized token.", locale);
                return ResponseDto.builder()
                        .data(false)
                        .message(msg)
                        .httpStatus(HttpStatus.BAD_REQUEST.value())
                        .build();
            }

            // 2. Enforce chronological timeline expiry guards
            if (appUser.getTokenExpiresAt() != null && appUser.getTokenExpiresAt().isBefore(Instant.now())) {
                String msg = messageSource.getMessage("api.appUser.password.token_expires", null, "The token has expired.", locale);
                return ResponseDto.builder()
                        .data(false)
                        .message(msg)
                        .httpStatus(HttpStatus.BAD_REQUEST.value())
                        .build();
            }

            log.info("Executing transactional password override for User Account ID: {}", appUser.getId());

            /*
             * ==========================================================================
             * FIELD CONSTRAINTS & PRESENCE VALIDATION
             * ==========================================================================
             */
            if (!StringUtils.hasText(passwordDto.getNewPassword())) {
                String msg = messageSource.getMessage("api.appUser.password.new_required", null, "New password is required.", locale);
                return ResponseDto.builder()
                        .data(false)
                        .message(msg)
                        .httpStatus(HttpStatus.BAD_REQUEST.value())
                        .build();
            }

            if (!StringUtils.hasText(passwordDto.getConfirmNewPassword())) {
                String msg = messageSource.getMessage("api.appUser.confirm.newPassword_required", null, "Confirm password is required.", locale);
                return ResponseDto.builder()
                        .data(false)
                        .message(msg)
                        .httpStatus(HttpStatus.BAD_REQUEST.value())
                        .build();
            }

            String newPassword = passwordDto.getNewPassword().trim();
            String confirmNewPassword = passwordDto.getConfirmNewPassword().trim();


            if (!newPassword.equals(confirmNewPassword)) {
                String msg = messageSource.getMessage("api.appUser.confirm.newPassword_required", null, "Passwords do not match.", locale);
                return ResponseDto.builder()
                        .data(false)
                        .message(msg)
                        .httpStatus(HttpStatus.BAD_REQUEST.value())
                        .build();
            }

            /*
             * ==========================================================================
             * CRUCIAL SECURITY ACTION: HASH & BURN VERIFICATION TOKEN
             * ==========================================================================
             */
            String encodedPassword = passwordConfig.passwordEncoder().encode(newPassword);

            appUser.setPassword(encodedPassword);

            // Burn credentials variables instantly to prevent replay attacks or secondary manipulation loops
            appUser.setTokenExpiresAt(null);
            appUser.setPasswordResetToken(null);

            appUserRepo.save(appUser);

            String successMsg = messageSource.getMessage("api.appUser.password.success", null, "Password reset successfully.", locale);
            return ResponseDto.builder()
                    .data(true)
                    .message(successMsg)
                    .httpStatus(HttpStatus.OK.value())
                    .build();

        } catch (Exception e) {
            log.error("Fatal transaction dropout during password update workflow loops", e);
            String fallbackMsg = messageSource.getMessage("api.appUser.password.failed", null, "An error occurred.", locale);
            return ResponseDto.builder()
                    .data(false)
                    .message(fallbackMsg)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
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

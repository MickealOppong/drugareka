package com.pages.service;

import com.pages.config.PasswordConfig;
import com.pages.dto.*;
import com.pages.enums.Request_Status;
import com.pages.enums.UserRole;
import com.pages.exception.EntityNotFoundException;
import com.pages.exception.InvalidOperationException;
import com.pages.model.*;
import com.pages.repository.*;
import com.pages.util.GlobalAddress;
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
    private final GlobalAddressRepo globalAddressRepo;

    public AppUserDetailsService(AppUserRepo appUserRepo, AppUserRoleRepo appUserRoleRepo, GlobalAddressRepo globalAddressRepo) {
        this.appUserRepo = appUserRepo;
        this.appUserRoleRepo = appUserRoleRepo;

        this.globalAddressRepo = globalAddressRepo;
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
               .accountNumber(appUser.getAccountNumber())
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

    public List<UserDetailsDto> allUsers(){
       return appUserRepo.findAll().stream().map(appUser -> {
            return UserDetailsDto.builder()
                    .userId(appUser.getId())
                    .firstName(appUser.getFirstName())
                    .lastName(appUser.getLastName())
                    .email(appUser.getUsername())
                    .roles(appUser.getUserRoles().stream().map(AppUserRole::getRole).collect(Collectors.toSet()))
                    .build();
        }).toList();
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

    @Transactional
    public ResponseDto<Object> updateUser(Jwt jwt,UserDetailsUpdateDto userDetailsDto) {

        try {

            if(jwt==null){
                return ResponseDto.builder()
                        .data(false)
                        .message(
                                "User not authorised to update"
                        )
                        .httpStatus(
                                HttpStatus.FORBIDDEN.value()
                        )
                        .build();
            }
            log.info("Updating user: {}", userDetailsDto.getId());

            AppUser appUser = appUserRepo.findByUsername(jwt.getSubject()).orElse(null);

            if(appUser==null){
                return ResponseDto.builder()
                        .data(false)
                        .message("User profile not found in database registry")
                        .httpStatus(HttpStatus.NOT_FOUND.value())
                        .build();
            }


            /*
             * ==============================
             * PERSONAL INFORMATION
             * ==============================
             */

            if (StringUtils.hasText(
                    userDetailsDto.getFirstName()
            )) {
                appUser.setFirstName(userDetailsDto
                        .getFirstName()
                        .trim());
            }

            if (StringUtils.hasText(
                    userDetailsDto.getLastName()
            )) {
                appUser.setLastName(
                        userDetailsDto
                                .getLastName()
                                .trim()
                );
            }

            /*
             * ==============================
             * SELLER ACCOUNT
             * ==============================
             */

            if (StringUtils.hasText(
                    userDetailsDto.getAccountNumber()
            )) {
                appUser.setAccountNumber(
                        userDetailsDto
                                .getAccountNumber()
                                .trim()
                );
            }

            /*
             * ==============================
             * ADDRESS
             * ==============================
             */

            GlobalAddress address =
                    globalAddressRepo
                            .findByAppUserId(appUser.getId())
                            .orElse(null);

            if (address != null) {

                if (StringUtils.hasText(
                        userDetailsDto.getStreet()
                )) {
                    address.setStreet(
                            userDetailsDto
                                    .getStreet()
                                    .trim()
                    );
                }

                if (StringUtils.hasText(
                        userDetailsDto.getCity()
                )) {
                    address.setCity(
                            userDetailsDto
                                    .getCity()
                                    .trim()
                    );
                }

                if (StringUtils.hasText(
                        userDetailsDto.getContact()
                )) {
                    address.setContact(
                            userDetailsDto
                                    .getContact()
                                    .trim()
                    );
                }

                if (StringUtils.hasText(
                        userDetailsDto.getPostalCode()
                )) {
                    address.setPostCode(
                            userDetailsDto
                                    .getPostalCode()
                                    .trim()
                    );
                }

                if (StringUtils.hasText(
                        userDetailsDto.getCountry()
                )) {
                    address.setCountry(
                            userDetailsDto
                                    .getCountry()
                                    .trim()
                    );
                }

                globalAddressRepo.save(address);
            }else{
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
                    .message("User updated successfully")
                    .httpStatus(
                            HttpStatus.OK.value()
                    )
                    .build();

        } catch (Exception e) {

            log.error(
                    "Failed to update user {}",
                    userDetailsDto.getId(),
                    e
            );

            return ResponseDto.builder()
                    .data(false)
                    .message(
                            "Update aborted: "
                                    + e.getMessage()
                    )
                    .httpStatus(
                            HttpStatus.INTERNAL_SERVER_ERROR.value()
                    )
                    .build();
        }
    }


    @Transactional
    public ResponseDto<Object> changePassword(Jwt jwt,ChangePasswordDto passwordDto) {

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

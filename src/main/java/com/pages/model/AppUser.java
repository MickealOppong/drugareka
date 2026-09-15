package com.pages.model;

import com.fasterxml.jackson.annotation.JsonDeserializeAs;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pages.util.GlobalAddress;
import com.pages.util.LogEntity;
import com.pages.util.Media;
import com.pages.util.Notification;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import javax.swing.text.View;
import java.sql.Ref;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@EqualsAndHashCode(
        callSuper = false,
        onlyExplicitlyIncluded = true
)
@Entity
@Table
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AppUser extends LogEntity implements UserDetails {

    @Id @GeneratedValue
    private Long id;

    private String firstName;
    private String lastName;
    private String username;

    private String password;

    private LocalDate dateOfBirth;
    private boolean isSysAdmin;

    @Builder.Default
    private boolean isTermsAccepted =false;

    //account status
    @Column(nullable = false)
    @Builder.Default
    private boolean enabled =true;

    @Column(nullable = false)
    @Builder.Default
    private boolean accountNonExpired =true;
    @Column(nullable = false)
    @Builder.Default
    private boolean accountNonLocked =true;
    @Column(nullable = false)
    @Builder.Default
    private boolean credentialsNonExpired =true;


    //User role
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "app_user_roles", // The name of your join table
            joinColumns = @JoinColumn(name = "user_id"), // Foreign key for AppUser
            inverseJoinColumns = @JoinColumn(name = "role_id") // Foreign key for AppUserRole
    )
    private Set<AppUserRole> userRoles = new HashSet<>();


    @OneToMany(mappedBy = "appUser",cascade = CascadeType.ALL)
    private List<RefreshToken> tokens = new ArrayList<>();

public void addRole(AppUserRole role){
    this.userRoles.add(role);
    role.getAppUsers().add(this);
}

    public void removeRole(AppUserRole role){
        this.userRoles.remove(role);
        role.getAppUsers().remove(this);
    }

    public void assignSingleRole(AppUserRole newRole) {

        if (newRole == null) {
            throw new IllegalArgumentException("Role cannot be null");
        }

        // Remove the user from the old roles
        for (AppUserRole existingRole : this.userRoles) {
            existingRole.getAppUsers().remove(this);
        }

        // Remove old roles from this user
        this.userRoles.clear();

        // Add the new role
        this.userRoles.add(newRole);
        newRole.getAppUsers().add(this);
    }


    public AppUser(String firstName, String lastName, String email, String password,boolean isTermsAccepted) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.username = email;
        this.password = password;
        this.isTermsAccepted = isTermsAccepted;
        this.enabled = true;
        this.accountNonExpired = true;
        this.accountNonLocked = true;
        this.credentialsNonExpired = true;
    }




    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        for(AppUserRole role: userRoles){
            authorities.add(new SimpleGrantedAuthority(role.getRole()));
        }
        return authorities;
    }

    @Override
    public @Nullable String getPassword() {
        return this.password;
    }

    @Override
    public String getUsername() {
        return this.username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return this.accountNonExpired;
    }

    @Override
    public boolean isAccountNonLocked() {
        return this.accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return this.credentialsNonExpired;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }


}

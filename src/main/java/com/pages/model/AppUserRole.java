package com.pages.model;

import com.pages.util.LogEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AppUserRole extends LogEntity {

    @Id @GeneratedValue
    private Long roleId;
    private String role;

    @ManyToMany(mappedBy = "userRoles")
    private Set<AppUser> appUsers = new HashSet<>();

    public AppUserRole(String role) {
        this.role = role;
    }
}

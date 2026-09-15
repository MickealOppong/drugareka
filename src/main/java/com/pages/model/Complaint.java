package com.pages.model;

import com.pages.enums.ComplaintStatus;
import com.pages.util.LogEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Complaint extends LogEntity {

    @Id @GeneratedValue
    private Long id;
    private String ticketId;
    @ManyToOne
   @JoinColumn(name = "user_id",nullable = false)
    private AppUser user;
    @Column(length = 1024,nullable = false)
    private String issue;
    private String description;
    private ComplaintStatus complaintStatus;
    private String orderNumber;

}

package com.pages.model;

// ChatMessage.java
import com.pages.util.LogEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Entity
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatMessage extends LogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // This links directly to the primary key ID of your match_requests table
    private Long matchId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "senderId", nullable = false)
    private AppUser sender;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "receiverId", nullable = false)
    private AppUser receiver;

    @Column(length = 1000, nullable = false)
    private String message;




}


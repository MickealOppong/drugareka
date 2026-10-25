package com.pages.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderConfirmationException {
    @Id @GeneratedValue
    private Long id;
    private Long orderItemId;
    private String status;
    private String reason;
    private Instant confirmedAt;
}

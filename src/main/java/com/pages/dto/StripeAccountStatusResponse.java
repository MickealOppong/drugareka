package com.pages.dto;


import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class StripeAccountStatusResponse {

    private String stripeAccountId;

    private boolean identityVerified;

    private boolean canReceivePayouts;

    private boolean actionRequired;

    private List<String> requirementsCurrentlyDue;
}


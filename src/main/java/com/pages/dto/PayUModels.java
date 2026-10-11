
        package com.pages.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

public final class PayUModels {

    private PayUModels() {
    }

    // Seller identity and AML verification
    @Builder
    public record PrivateSellerProfile(
            String verificationId,
            String sellerId,
            String email,
            String name,
            String surname,
            String personalIdentificationNumber,
            String dateOfBirth,
            String accountNumberRequested,
            String legalForm,
            String phone,
            AddressPL address,
            String gusCode,
            String registryNumber
    ) {
    }

    @Builder
    public record AddressPL(
            String country,
            String street,
            String zipcode,
            String city
    ) {
    }

    // Verification advice
    public record VerificationAdviceResponse(
            String advice
    ) {
    }

    // Verification transfer
    public record ManualTransferRequest(
            String verificationId,
            String amount,
            String currency
    ) {
    }

    public record ManualTransferResponse(
            String verificationId,
            BigDecimal amount,
            String currency
    ) {
    }

    public record BankAccountDetails(
            String accountNumber
    ) {
    }

    // Initialize verification
    @Builder
    public record InitVerificationRequest(
            String sellerId,
            String type
    ) {
    }

    public record InitVerificationResponse(
            String verificationId
    ) {
    }

    // Company or business identity
    public record Address(
            String street,
            String city,
            String zipcode,
            String country
    ) {
    }

    @Builder
    public record IdentityRequest(
            String verificationId,
            String sellerId,
            String email,
            String companyName,
            String legalForm,
            Address address
    ) {
    }

    // Company associates
    public record Associate(
            String associateType,
            String firstName,
            String lastName,
            String pesel,
            String birthDate,
            String citizenship
    ) {
    }

    public record AssociatesRequest(
            String verificationId,
            List<Associate> associates
    ) {
    }

    // Payout bank account
    @Builder
    public record BankAccountRequest(
            String verificationId,
            String bankDataId,
            String accountNumberRequested,
            String transferId,
            String hasDocument,
            String payoutDataVerificationType,
            String verificationTransferId,
            String foreign,
            String verified
    ) {
    }

    // Complete verification
    @Builder
    public record CompleteRequest(
            String verificationId
    ) {
    }

    // Verification status
    public record StatusResponse(
            @JsonProperty("customerVerificationStatus")
            String customerVerificationStatus,

            @JsonProperty("reason")
            String reason
    ) {
    }
}

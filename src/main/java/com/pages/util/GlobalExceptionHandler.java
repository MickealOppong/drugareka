package com.pages.util;

import com.pages.dto.ResponseDto;
import com.pages.exception.*;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.exception.oauth.InvalidRequestException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.MessagingException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {


    private final MessageSource messageSource;

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        log.warn("Fulfillment validation gate intercepted: {}", ex.getMessage());

        Map<String, Object> body = new LinkedHashMap<>();

        String errorMsg = messageSource.getMessage("api.error.conflict_generic", null, "Error", LocaleContextHolder.getLocale());

        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", errorMsg);
        body.put("message", ex.getMessage());

        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InsufficientPublicPresenceException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientPublicPresence(InsufficientPublicPresenceException ex, Locale locale) {
        Map<String, Object> body = new LinkedHashMap<>();

        String errorMsg = messageSource.getMessage("api.error.feature_locked", null, "Feature Locked", locale);

        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("error", errorMsg);
        body.put("message", ex.getMessage());

        return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(MessagingException.class)
    public ResponseEntity<Map<String, Object>> handleMessagingException(MessagingException ex, Locale locale) {
        Map<String, Object> body = new LinkedHashMap<>();

        String errorMsg = messageSource.getMessage("api.error.conflict_generic", null, "Error", locale);

        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", errorMsg);
        body.put("message", ex.getMessage());

        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }



    /**
     * Catches declined cards, expired cards, and incorrect CVVs.
     */
    @ExceptionHandler(CardException.class)
    public ResponseEntity<Map<String, Object>>handleCardException(
            CardException ex, HttpServletRequest request) {
        log.info("Card declined: Code={}, Message={}", ex.getCode(), ex.getMessage());

        Map<String, Object> body = new LinkedHashMap<>();
        String errorMsg = messageSource.getMessage("api.error.access_denied", null, "an error occurred", LocaleContextHolder.getLocale());

        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("error", errorMsg);
        body.put("message", ex.getMessage());

        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(body);
    }


    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        HttpStatus statusCode = HttpStatus.valueOf(ex.getStatusCode().value());

        body.put("timestamp", Instant.now().toString());
        body.put("status", statusCode.value());
        body.put("error", statusCode.getReasonPhrase());
        body.put("message", ex.getReason());

        return new ResponseEntity<>(body, statusCode);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validationException(MethodArgumentNotValidException ex, Locale locale) {
        Map<String, Object> body = new LinkedHashMap<>();
        String fallbackInvalidValue = messageSource.getMessage("api.error.validation.invalid_value", null, "Invalid value", locale);
        String errorLabel = messageSource.getMessage("api.error.validation.label", null, "error", locale);

        // Map errors safely using the dynamic fallback string
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : fallbackInvalidValue,
                        (existingMessage, newMessage) -> existingMessage + ", " + newMessage
                ));

        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("error", errorLabel);
        body.put("message", errors);

        return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Map<String, Object>> bindException(
            BindException ex,
            Locale locale) {

        Map<String, Object> body = new LinkedHashMap<>();

        String errorLabel = messageSource.getMessage(
                "api.error.validation.label",
                null,
                "error",
                locale);

        String invalidNumber = messageSource.getMessage(
                "api.listing.price.invalid_number",
                null,
                "Minimum price must be a valid number.",
                locale);

        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> {
                            if ("minPrice".equals(fieldError.getField())
                                    && fieldError.getRejectedValue() != null) {
                                return invalidNumber;
                            }

                            return fieldError.getDefaultMessage() != null
                                    ? fieldError.getDefaultMessage()
                                    : invalidNumber;
                        },
                        (a, b) -> a
                ));

        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", errorLabel);
        body.put("message", errors);

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidOperationException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidOperationException(InvalidOperationException ex, Locale locale) {
        Map<String, Object> body = new LinkedHashMap<>();
        String errorMsg = messageSource.getMessage("api.error.invalid_operation", null, "An error occurred", locale);

        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", errorMsg);
        body.put("message", ex.getMessage());

        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }



    @ExceptionHandler(AccessPermissionException.class)
    public ResponseEntity<Map<String, Object>> accessPermission(AccessPermissionException ex, Locale locale) {
        Map<String, Object> body = new LinkedHashMap<>();
        String errorMsg = messageSource.getMessage("api.error.access_denied", null, "an error occurred", locale);

        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("error", errorMsg);
        body.put("message", ex.getMessage());

        return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
    }




    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Map<String, Object>> duplicateKey(DuplicateKeyException ex, Locale locale) {
        Map<String, Object> body = new LinkedHashMap<>();
        String errorMsg = messageSource.getMessage("api.error.duplicate_key", null, "an error occurred", locale);

        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("error", errorMsg);
        body.put("message", ex.getMessage());

        return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Map<String, Object>> userNotFound(UsernameNotFoundException ex, Locale locale) {
        Map<String, Object> body = new LinkedHashMap<>();
        String errorMsg = messageSource.getMessage("api.error.user_not_found", null, "an error occurred", locale);

        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("error", errorMsg);
        body.put("message", ex.getMessage());

        return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(PhotoNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleMissingImage(PhotoNotFoundException ex, Locale locale) {
        Map<String, Object> body = new LinkedHashMap<>();
        String errorMsg = messageSource.getMessage("api.error.photo_not_found", null, "error occurred", locale);

        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", errorMsg);
        body.put("message", ex.getMessage());

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> illegalArgument(IllegalArgumentException ex, Locale locale) {
        Map<String, Object> body = new LinkedHashMap<>();
        String errorMsg = messageSource.getMessage("api.error.illegal_argument", null, "Error occurred", locale);

        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", errorMsg);
        body.put("message", ex.getMessage());

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex, Locale locale) {
        Map<String, Object> body = new LinkedHashMap<>();
        String errorLabel = messageSource.getMessage("api.error.file_too_large", null, "Content too large", locale);

        body.put("status", HttpStatus.CONTENT_TOO_LARGE.value());
        body.put("error", errorLabel + " " + ex.getCause());
        body.put("message", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(body);
    }

    // 🚀 FIXED: Fully completed and closed out the PayU handling logic safely
    @ExceptionHandler(PayUValidationException.class)
    public ResponseEntity<Map<String, Object>> handlePayUValidation(PayUValidationException ex) {
        Map<String, Object> body = Map.of(
                "success", false,
                "error_source", "PAYU_AML_PROVIDER",
                "status", ex.getStatusCode(),
                "code", ex.getPayuErrorCode() != null ? ex.getPayuErrorCode() : "UNKNOWN_PAYU_GATEWAY_FAULT",
                "message", ex.getMessage() != null ? ex.getMessage() : "Payment gateway processing anomaly detected"
        );
        return new ResponseEntity<>(body, HttpStatus.valueOf(ex.getStatusCode()));
    }
}

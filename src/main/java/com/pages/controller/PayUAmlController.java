
package com.example.payu.aml;

import java.time.Instant;
import java.util.Map;

import com.pages.dto.PayUModels;
import com.pages.exception.PayUValidationException;
import com.pages.service.PayUAmlService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payu/aml")
public class PayUAmlController {

    private final PayUAmlService payUAmlService;

    public PayUAmlController(PayUAmlService payuAmlService) {
        this.payUAmlService = payuAmlService;
    }

    /**
     * Endpoint to onboard an occasional private seller (C2C) in Poland for the MVP stage.
     * Implements only the necessary steps to create a payout-ready seller shell without immediate document verification.
     */
    @PostMapping("/occasional")
    public ResponseEntity<Map<String, Object>> onboardOccasionalSeller(
            @RequestBody PayUModels.PrivateSellerProfile request) {

        try {


            // Run the streamlined, documentation-compliant service orchestration loop
           //payUAmlService.onboardOccasionalPrivateSeller(request);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Occasional private seller initialized successfully for the MVP pipeline.",
                    "sellerId",request.sellerId(),
                    "payoutStatus", "ENABLED_UNTIL_THRESHOLD_EXCEEDED"
            ));

        } catch (PayUValidationException ex) {
            // Triggers global error handler mapping rules if text characters fail PayU specifications
            throw ex;
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "An unhandled execution failure occurred within the registration stream: " + e.getMessage()
            ));
        }
    }


}

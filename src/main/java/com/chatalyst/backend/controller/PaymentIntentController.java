package com.chatalyst.backend.controller;

import com.chatalyst.backend.dto.StripeCreatePaymentIntentRequest;
import com.chatalyst.backend.dto.StripePaymentResponse;
import com.chatalyst.backend.service.StripeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment-intents")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class PaymentIntentController {

    private final StripeService stripeService;

    @PostMapping
    public ResponseEntity<StripePaymentResponse> createPaymentIntent(@RequestBody StripeCreatePaymentIntentRequest request) {
        try {
            log.info("Creating payment intent for user {} with priceId {}", request.getUserId(), request.getPriceId());
            
            StripePaymentResponse response = stripeService.createPaymentIntent(request);
            
            if ("failed".equals(response.getStatus())) {
                return ResponseEntity.badRequest().body(response);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error creating payment intent: {}", e.getMessage(), e);
            StripePaymentResponse errorResponse = new StripePaymentResponse();
            errorResponse.setStatus("failed");
            errorResponse.setMessage("Error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}

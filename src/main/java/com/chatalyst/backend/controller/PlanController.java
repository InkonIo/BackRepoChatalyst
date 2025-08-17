package com.chatalyst.backend.controller;

import com.chatalyst.backend.dto.StripeCreatePaymentIntentRequest;
import com.chatalyst.backend.dto.StripePaymentResponse;
import com.chatalyst.backend.service.StripeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class PlanController {

    private final StripeService stripeService;

    @GetMapping("/available")
    public ResponseEntity<Map<String, Object>> getAvailablePlans() {
        Map<String, Object> plans = Map.of(
            "BASIC", Map.of(
                "name", "Basic Plan",
                "price", 9.99,
                "priceInCents", 999,
                "messages", 5000,
                "bots", 1,
                "features", new String[]{"5000 сообщений в месяц", "1 бот", "Базовая поддержка"}
            ),
            "PREMIUM", Map.of(
                "name", "Premium Plan", 
                "price", 19.99,
                "priceInCents", 1999,
                "messages", 20000,
                "bots", 5,
                "features", new String[]{"20000 сообщений в месяц", "5 ботов", "Приоритетная поддержка"}
            )
        );
        
        return ResponseEntity.ok(plans);
    }

    @PostMapping("/purchase")
    public ResponseEntity<StripePaymentResponse> purchasePlan(@RequestBody StripeCreatePaymentIntentRequest request) {
        log.info("User {} purchasing plan with priceId {}", request.getUserId(), request.getPriceId());
        
        StripePaymentResponse response = stripeService.createPaymentIntent(request);
        
        if ("failed".equals(response.getStatus())) {
            return ResponseEntity.badRequest().body(response);
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stripe-config")
    public ResponseEntity<Map<String, String>> getStripeConfig() {
        return ResponseEntity.ok(stripeService.getStripeConfig());
    }
}

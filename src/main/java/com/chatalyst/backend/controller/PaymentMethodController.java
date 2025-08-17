package com.chatalyst.backend.controller;

import com.chatalyst.backend.service.StripeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/payment-methods")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class PaymentMethodController {

    private final StripeService stripeService;

    // Эндпоинт для получения платежных методов (карт) пользователя
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getPaymentMethods() {
        try {
            log.info("GET /api/payment-methods called");
            
            // Используем фиксированный userId для тестирования
            // В реальном приложении здесь должен быть userId из JWT токена
            Long testUserId = 1L;
            
            List<Map<String, Object>> paymentMethods = stripeService.getPaymentMethods(testUserId);
            log.info("Found {} payment methods", paymentMethods.size());
            
            return ResponseEntity.ok(paymentMethods);
        } catch (Exception e) {
            log.error("Error getting payment methods: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(List.of());
        }
    }
    
    // Эндпоинт для привязки нового платежного метода к пользователю
    @PostMapping
    public ResponseEntity<Map<String, String>> attachPaymentMethod(@RequestBody Map<String, String> request) {
        try {
            String paymentMethodId = request.get("paymentMethodId");
            log.info("POST /api/payment-methods called with paymentMethodId: {}", paymentMethodId);
            
            if (paymentMethodId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "paymentMethodId is required"));
            }
            
            // Используем фиксированный userId для тестирования
            Long testUserId = 1L;
            
            boolean success = stripeService.attachPaymentMethodToCustomer(testUserId, paymentMethodId);
            
            if (success) {
                log.info("Payment method {} successfully attached to user {}", paymentMethodId, testUserId);
                return ResponseEntity.ok(Map.of("message", "Payment method attached successfully"));
            } else {
                log.error("Failed to attach payment method {} to user {}", paymentMethodId, testUserId);
                return ResponseEntity.status(500).body(Map.of("error", "Failed to attach payment method"));
            }
        } catch (Exception e) {
            log.error("Error attaching payment method: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to attach payment method: " + e.getMessage()));
        }
    }
    
    // Эндпоинт для удаления платежного метода
    @DeleteMapping("/{paymentMethodId}")
    public ResponseEntity<Map<String, String>> removePaymentMethod(@PathVariable String paymentMethodId) {
        try {
            log.info("DELETE /api/payment-methods/{} called", paymentMethodId);
            
            boolean success = stripeService.detachPaymentMethod(paymentMethodId);
            
            if (success) {
                log.info("Payment method {} successfully removed", paymentMethodId);
                return ResponseEntity.ok(Map.of("message", "Payment method removed successfully"));
            } else {
                log.error("Failed to remove payment method {}", paymentMethodId);
                return ResponseEntity.status(500).body(Map.of("error", "Failed to remove payment method"));
            }
        } catch (Exception e) {
            log.error("Error removing payment method: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to remove payment method: " + e.getMessage()));
        }
    }
}


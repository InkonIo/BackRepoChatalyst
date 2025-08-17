package com.chatalyst.backend.controller;

import com.chatalyst.backend.dto.StripeCreatePaymentIntentRequest;
import com.chatalyst.backend.dto.StripePaymentResponse;
import com.chatalyst.backend.service.StripeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/stripe")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class StripeController {

    private final StripeService stripeService;

    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> getStripeConfig() {
        return ResponseEntity.ok(stripeService.getStripeConfig());
    }

    // Эндпоинт для получения списка доступных планов
    @GetMapping("/plans")
    public ResponseEntity<Map<String, Object>> getAvailablePlans() {
        // TODO: Перенести эту логику в отдельный сервис или в БД
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

    // Эндпоинт для создания платежного намерения
    @PostMapping("/create-payment-intent")
    public ResponseEntity<StripePaymentResponse> createPaymentIntent(@RequestBody StripeCreatePaymentIntentRequest request) {
        log.info("Creating payment intent for user {} with priceId {}", request.getUserId(), request.getPriceId());
        StripePaymentResponse response = stripeService.createPaymentIntent(request);
        if ("failed".equals(response.getStatus())) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }
    
    // Эндпоинт для получения платежных методов (карт) пользователя
    @GetMapping("/payment-methods/{userId}")
    public ResponseEntity<List<Map<String, Object>>> getPaymentMethods(@PathVariable Long userId) {
        List<Map<String, Object>> paymentMethods = stripeService.getPaymentMethods(userId);
        return ResponseEntity.ok(paymentMethods);
    }
    
    // Эндпоинт для привязки нового платежного метода к пользователю
    @PostMapping("/payment-methods/{userId}")
    public ResponseEntity<String> attachPaymentMethod(@PathVariable Long userId, @RequestBody Map<String, String> request) {
        String paymentMethodId = request.get("paymentMethodId");
        if (paymentMethodId == null) {
            return ResponseEntity.badRequest().body("paymentMethodId is required");
        }
        boolean success = stripeService.attachPaymentMethodToCustomer(userId, paymentMethodId);
        if (success) {
            return ResponseEntity.ok("Payment method attached successfully");
        } else {
            return ResponseEntity.status(500).body("Failed to attach payment method");
        }
    }
    
    // Эндпоинт для активации плана после успешной оплаты
    @PostMapping("/activate-plan")
    public ResponseEntity<String> activatePlan(@RequestParam String paymentIntentId,
                                                @RequestParam String planType,
                                                @RequestParam Long userId) {
        try {
            stripeService.activatePlanAfterPayment(paymentIntentId, planType, userId);
            return ResponseEntity.ok("Plan activated successfully");
        } catch (Exception e) {
            log.error("Error activating plan: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Failed to activate plan: " + e.getMessage());
        }
    }

    // Эндпоинт для обработки вебхуков Stripe
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody String payload, @RequestHeader("Stripe-Signature") String sigHeader) {
        log.info("Received Stripe webhook");

        // TODO: В реальном приложении здесь нужно валидировать подпись webhook'а
        try {
            // Здесь можно добавить логику обработки webhook'а, например, активация плана
            log.info("Webhook payload: {}", payload);
            return ResponseEntity.ok("Webhook received");
        } catch (Exception e) {
            log.error("Error processing webhook: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Webhook processing failed");
        }
    }
}

package com.chatalyst.backend.service;

import com.chatalyst.backend.Entity.User;
import com.chatalyst.backend.Repository.UserRepository;
import com.chatalyst.backend.dto.StripeCreatePaymentIntentRequest;
import com.chatalyst.backend.dto.StripePaymentResponse;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentMethodListParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeService {

    private final UserRepository userRepository;

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${stripe.publishable-key}")
    private String stripePublishableKey;

    @Value("${stripe.test-customer-id}")
    private String testCustomerId;

    /**
     * Создает платежное намерение (PaymentIntent) на основе запроса.
     */
    public StripePaymentResponse createPaymentIntent(StripeCreatePaymentIntentRequest request) {
        try {
            Stripe.apiKey = stripeSecretKey;

            String planType = getPlanTypeFromPriceId(request.getPriceId());
            int amount = getPlanAmount(planType);

            // Используем тестовый Customer ID
            String customerId = testCustomerId;

            PaymentIntentCreateParams params =
                PaymentIntentCreateParams.builder()
                    .setAmount((long) amount)
                    .setCurrency("usd")
                    .setCustomer(customerId)
                    .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                            .setEnabled(true)
                            .build()
                    )
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params);

            return new StripePaymentResponse("success", paymentIntent.getClientSecret(), paymentIntent.getId(), null);
        } catch (StripeException e) {
            log.error("Stripe error during PaymentIntent creation: {}", e.getMessage(), e);
            return new StripePaymentResponse("failed", null, null, "Stripe error: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("Invalid plan type or price ID: {}", e.getMessage(), e);
            return new StripePaymentResponse("failed", null, null, "Invalid plan type or price ID: " + e.getMessage());
        }
    }
    
    /**
     * Получает список платежных методов (карт) для клиента.
     */
    public List<Map<String, Object>> getPaymentMethods(Long userId) {
        try {
            Stripe.apiKey = stripeSecretKey;
            
            // Используем тестовый Customer ID
            String customerId = testCustomerId;

            PaymentMethodListParams listParams = PaymentMethodListParams.builder()
                .setCustomer(customerId)
                .setType(PaymentMethodListParams.Type.CARD)
                .build();
            
            List<PaymentMethod> paymentMethods = PaymentMethod.list(listParams).getData();
            
            return paymentMethods.stream()
                .map(pm -> {
                    Map<String, Object> cardInfo = new HashMap<>();
                    cardInfo.put("id", pm.getId());
                    
                    // Создаем объект card для совместимости с фронтендом
                    Map<String, Object> card = new HashMap<>();
                    card.put("brand", pm.getCard().getBrand());
                    card.put("last4", pm.getCard().getLast4());
                    card.put("expMonth", pm.getCard().getExpMonth());
                    card.put("expYear", pm.getCard().getExpYear());
                    
                    cardInfo.put("card", card);
                    
                    // Также добавляем поля напрямую для обратной совместимости
                    cardInfo.put("brand", pm.getCard().getBrand());
                    cardInfo.put("last4", pm.getCard().getLast4());
                    cardInfo.put("exp_month", pm.getCard().getExpMonth());
                    cardInfo.put("exp_year", pm.getCard().getExpYear());
                    
                    return cardInfo;
                })
                .collect(Collectors.toList());
        } catch (StripeException e) {
            log.error("Stripe error during payment methods retrieval: {}", e.getMessage(), e);
            return List.of();
        }
    }
    
    /**
     * Привязывает платежный метод к пользователю.
     */
    public boolean attachPaymentMethodToCustomer(Long userId, String paymentMethodId) {
        try {
            Stripe.apiKey = stripeSecretKey;
            
            // Используем тестовый Customer ID
            String customerId = testCustomerId;

            PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);
            paymentMethod.attach(
                Map.of("customer", customerId)
            );
            return true;
        } catch (StripeException e) {
            log.error("Stripe error attaching payment method: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Отвязывает платежный метод от пользователя.
     */
    public boolean detachPaymentMethod(String paymentMethodId) {
        try {
            Stripe.apiKey = stripeSecretKey;

            PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);
            paymentMethod.detach();
            return true;
        } catch (StripeException e) {
            log.error("Stripe error detaching payment method: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Получает или создает Stripe Customer для пользователя.
     */
    private String getOrCreateStripeCustomer(Long userId) throws StripeException {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            
            // В тестовом режиме всегда используем тестовый Customer ID
            log.info("Using test Stripe customer ID: {} for user {}", testCustomerId, userId);
            return testCustomerId;
        } else {
            throw new IllegalArgumentException("User not found: " + userId);
        }
    }

    private String getStripeCustomer(Long userId) throws StripeException {
        // В тестовом режиме всегда возвращаем тестовый Customer ID
        return testCustomerId;
    }

    @Transactional
    public void activatePlanAfterPayment(String paymentIntentId, String planType, Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            // В зависимости от типа плана, обновляем лимиты пользователя
            if ("BASIC".equals(planType)) {
                user.setMonthlyMessagesLimit(5000);
                user.setBotsAllowed(1);
                user.setSupportLevel("BASIC");
            } else if ("PREMIUM".equals(planType)) {
                user.setMonthlyMessagesLimit(20000);
                user.setBotsAllowed(5);
                user.setSupportLevel("PREMIUM");
            }

            user.setMonthlyMessagesUsed(0);
            user.setSubscriptionStart(LocalDateTime.now());
            user.setSubscriptionEnd(LocalDateTime.now().plusMonths(1));

            userRepository.save(user);
            log.info("Plan {} activated for user {}", planType, userId);

        } catch (Exception e) {
            log.error("Error activating plan: {}", e.getMessage());
            throw new RuntimeException("Failed to activate plan", e);
        }
    }

    private int getPlanAmount(String planType) {
        return switch (planType) {
            case "BASIC" -> 999; // $9.99 в центах
            case "PREMIUM" -> 1999; // $19.99 в центах
            default -> throw new IllegalArgumentException("Unknown plan type: " + planType);
        };
    }

    private String getPlanTypeFromPriceId(String priceId) {
        if (priceId.contains("_basic")) {
            return "BASIC";
        } else if (priceId.contains("_premium")) {
            return "PREMIUM";
        } else {
            throw new IllegalArgumentException("Unknown price ID: " + priceId);
        }
    }

    public Map<String, String> getStripeConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("publishableKey", stripePublishableKey);
        return config;
    }
}


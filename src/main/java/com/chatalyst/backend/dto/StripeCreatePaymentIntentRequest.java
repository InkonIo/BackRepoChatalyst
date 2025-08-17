package com.chatalyst.backend.dto;

import lombok.Data;

@Data
public class StripeCreatePaymentIntentRequest {
    private String priceId;
    private Long userId;
}

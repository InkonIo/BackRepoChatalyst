package com.chatalyst.backend.controller;

import com.chatalyst.backend.security.services.UserPrincipal;
import com.chatalyst.backend.service.QuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Контроллер для получения информации о лимитах текущего пользователя
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Usage", description = "API для получения информации о лимитах текущего пользователя")
public class UserUsageController {

    private final QuotaService quotaService;

    /**
     * Получает информацию о текущем использовании лимитов аутентифицированного пользователя
     * 
     * @param userPrincipal текущий аутентифицированный пользователь
     * @return информация о лимитах
     */
    @GetMapping("/usage")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @Operation(summary = "Получить информацию о лимитах", 
               description = "Возвращает информацию о текущем использовании лимитов аутентифицированного пользователя")
    public ResponseEntity<?> getMyUsage(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.debug("Запрос информации о лимитах для пользователя {}", userPrincipal.getEmail());
        
        try {
            var quotaInfo = quotaService.getQuotaInfo(userPrincipal.getId());
            log.debug("Информация о лимитах получена для пользователя {}", userPrincipal.getEmail());
            return ResponseEntity.ok(quotaInfo);
        } catch (Exception e) {
            log.error("Ошибка при получении информации о лимитах для пользователя {}: {}", 
                     userPrincipal.getEmail(), e.getMessage());
            return ResponseEntity.internalServerError()
                .body("Ошибка при получении информации о лимитах");
        }
    }
}

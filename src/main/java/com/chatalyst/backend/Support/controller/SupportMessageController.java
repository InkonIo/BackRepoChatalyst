package com.chatalyst.backend.Support.controller;

import com.chatalyst.backend.Support.Entity.MessageStatus;
import com.chatalyst.backend.Support.Entity.MessagePriority;
import com.chatalyst.backend.Support.dto.*;
import com.chatalyst.backend.Support.service.SupportMessageService;
import com.chatalyst.backend.dto.MessageResponse;
import com.chatalyst.backend.security.services.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Support Messages", description = "Support messaging system APIs")
@CrossOrigin(origins = "*", maxAge = 3600)
public class SupportMessageController {

    private final SupportMessageService supportMessageService;

    @PostMapping("/messages")
    @Operation(summary = "Create support message", description = "Create a new support message")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Message created successfully",
                    content = @Content(schema = @Schema(implementation = SupportMessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    })
    public ResponseEntity<?> createMessage(
            @Valid @RequestBody CreateSupportMessageRequest request,
            Authentication authentication) {
        try {
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            SupportMessageResponse response = supportMessageService.createMessage(userPrincipal.getId(), request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error creating support message", e);
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: " + e.getMessage()));
        }
    }

    @GetMapping("/messages/my")
    @Operation(summary = "Get user's messages", description = "Get all messages created by the current user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Messages retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<?> getUserMessages(Authentication authentication) {
        try {
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            List<SupportMessageResponse> messages = supportMessageService.getUserMessages(userPrincipal.getId());
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.error("Error retrieving user messages", e);
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: " + e.getMessage()));
        }
    }

    @GetMapping("/messages")
    @Operation(summary = "Get all messages (Admin only)", description = "Get all support messages with optional filters")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Messages retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<?> getAllMessages(
            @RequestParam(required = false) MessageStatus status,
            @RequestParam(required = false) MessagePriority priority,
            @RequestParam(required = false) Long adminId,
            Authentication authentication) {
        try {
            // Проверяем что пользователь админ
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            boolean isAdmin = userPrincipal.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

            if (!isAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new MessageResponse("Error: Admin access required"));
            }

            List<SupportMessageResponse> messages;
            if (status != null || priority != null || adminId != null) {
                messages = supportMessageService.getMessagesWithFilters(status, priority, adminId);
            } else {
                messages = supportMessageService.getAllMessages();
            }

            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.error("Error retrieving all messages", e);
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: " + e.getMessage()));
        }
    }

    @GetMapping("/messages/{id}")
    @Operation(summary = "Get message details", description = "Get detailed message with replies")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Message details retrieved successfully",
                    content = @Content(schema = @Schema(implementation = MessageDetailResponse.class))),
            @ApiResponse(responseCode = "404", description = "Message not found"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<?> getMessageDetail(
            @PathVariable Long id,
            Authentication authentication) {
        try {
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            MessageDetailResponse response = supportMessageService.getMessageDetail(id, userPrincipal.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving message detail", e);
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: " + e.getMessage()));
        }
    }

    @PostMapping("/messages/{id}/replies")
    @Operation(summary = "Add reply to message", description = "Add a reply to an existing support message")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Reply added successfully",
                    content = @Content(schema = @Schema(implementation = SupportMessageReplyResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Message not found")
    })
    public ResponseEntity<?> addReply(
            @PathVariable Long id,
            @Valid @RequestBody CreateReplyRequest request,
            Authentication authentication) {
        try {
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            SupportMessageReplyResponse response = supportMessageService.addReply(id, userPrincipal.getId(), request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error adding reply to message", e);
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: " + e.getMessage()));
        }
    }

    @PutMapping("/messages/{id}/status")
    @Operation(summary = "Update message status (Admin only)", description = "Update the status of a support message")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status updated successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Message not found")
    })
    public ResponseEntity<?> updateMessageStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMessageStatusRequest request,
            Authentication authentication) {
        try {
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            SupportMessageResponse response = supportMessageService.updateMessageStatus(id, userPrincipal.getId(), request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error updating message status", e);
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: " + e.getMessage()));
        }
    }

    @PutMapping("/messages/{id}/assign")
    @Operation(summary = "Assign message to admin (Admin only)", description = "Assign a support message to the current admin")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Message assigned successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Message not found")
    })
    public ResponseEntity<?> assignMessage(
            @PathVariable Long id,
            Authentication authentication) {
        try {
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            SupportMessageResponse response = supportMessageService.assignMessageToAdmin(id, userPrincipal.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error assigning message", e);
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: " + e.getMessage()));
        }
    }
}


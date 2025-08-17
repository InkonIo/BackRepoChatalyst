package com.chatalyst.backend.Support.service;

import com.chatalyst.backend.Entity.*;
import com.chatalyst.backend.Repository.*;
import com.chatalyst.backend.Support.Entity.MessagePriority;
import com.chatalyst.backend.Support.Entity.MessageStatus;
import com.chatalyst.backend.Support.Entity.SupportMessage;
import com.chatalyst.backend.Support.Entity.SupportMessageReply;
import com.chatalyst.backend.Support.Repository.SupportMessageReplyRepository;
import com.chatalyst.backend.Support.Repository.SupportMessageRepository;
import com.chatalyst.backend.Support.dto.CreateReplyRequest;
import com.chatalyst.backend.Support.dto.CreateSupportMessageRequest;
import com.chatalyst.backend.Support.dto.MessageDetailResponse;
import com.chatalyst.backend.Support.dto.SupportMessageReplyResponse;
import com.chatalyst.backend.Support.dto.SupportMessageResponse;
import com.chatalyst.backend.Support.dto.UpdateMessageStatusRequest;
import com.chatalyst.backend.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportMessageService {

    private final SupportMessageRepository supportMessageRepository;
    private final SupportMessageReplyRepository supportMessageReplyRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Transactional
    public SupportMessageResponse createMessage(Long userId, CreateSupportMessageRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        SupportMessage message = new SupportMessage();
        message.setUser(user);
        message.setSubject(request.getSubject());
        message.setMessage(request.getMessage());
        message.setPriority(request.getPriority());
        message.setStatus(MessageStatus.OPEN);

        SupportMessage savedMessage = supportMessageRepository.save(message);
        log.info("Support message created by user {}: {}", userId, savedMessage.getId());

        return SupportMessageResponse.fromEntity(savedMessage);
    }

    @Transactional(readOnly = true)
    public List<SupportMessageResponse> getUserMessages(Long userId) {
        return supportMessageRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(SupportMessageResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SupportMessageResponse> getAllMessages() {
        return supportMessageRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(SupportMessageResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SupportMessageResponse> getMessagesWithFilters(MessageStatus status, MessagePriority priority, Long adminId) {
        return supportMessageRepository.findWithFilters(status, priority, adminId)
                .stream()
                .map(SupportMessageResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MessageDetailResponse getMessageDetail(Long messageId, Long requesterId) {
        SupportMessage message = supportMessageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        // Проверяем права доступа - пользователь может видеть только свои сообщения, админы - все
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean isAdmin = requester.getRoles().stream()
                .anyMatch(role -> role.getName() == RoleName.ROLE_ADMIN);

        if (!isAdmin && !message.getUser().getId().equals(requesterId)) {
            throw new RuntimeException("Access denied");
        }

        return MessageDetailResponse.fromEntity(message);
    }

    @Transactional
    public SupportMessageReplyResponse addReply(Long messageId, Long senderId, CreateReplyRequest request) {
        SupportMessage message = supportMessageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Проверяем права доступа
        boolean isAdmin = sender.getRoles().stream()
                .anyMatch(role -> role.getName() == RoleName.ROLE_ADMIN);

        if (!isAdmin && !message.getUser().getId().equals(senderId)) {
            throw new RuntimeException("Access denied");
        }

        SupportMessageReply reply = new SupportMessageReply();
        reply.setMessage(message);
        reply.setSender(sender);
        reply.setReplyText(request.getReplyText());
        reply.setIsAdminReply(isAdmin);

        SupportMessageReply savedReply = supportMessageReplyRepository.save(reply);

        // Если админ отвечает, меняем статус на IN_PROGRESS
        if (isAdmin && message.getStatus() == MessageStatus.OPEN) {
            message.setStatus(MessageStatus.IN_PROGRESS);
            supportMessageRepository.save(message);
        }

        log.info("Reply added to message {} by user {}", messageId, senderId);

        return SupportMessageReplyResponse.fromEntity(savedReply);
    }

    @Transactional
    public SupportMessageResponse updateMessageStatus(Long messageId, Long adminId, UpdateMessageStatusRequest request) {
        SupportMessage message = supportMessageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Проверяем что это админ
        boolean isAdmin = admin.getRoles().stream()
                .anyMatch(role -> role.getName() == RoleName.ROLE_ADMIN);

        if (!isAdmin) {
            throw new RuntimeException("Access denied - admin role required");
        }

        message.setStatus(request.getStatus());
        SupportMessage savedMessage = supportMessageRepository.save(message);

        log.info("Message {} status updated to {} by admin {}", messageId, request.getStatus(), adminId);

        return SupportMessageResponse.fromEntity(savedMessage);
    }

    @Transactional
    public SupportMessageResponse assignMessageToAdmin(Long messageId, Long adminId) {
        SupportMessage message = supportMessageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Проверяем что это админ
        boolean isAdmin = admin.getRoles().stream()
                .anyMatch(role -> role.getName() == RoleName.ROLE_ADMIN);

        if (!isAdmin) {
            throw new RuntimeException("Access denied - admin role required");
        }

        message.setAdmin(admin);
        if (message.getStatus() == MessageStatus.OPEN) {
            message.setStatus(MessageStatus.IN_PROGRESS);
        }

        SupportMessage savedMessage = supportMessageRepository.save(message);

        log.info("Message {} assigned to admin {}", messageId, adminId);

        return SupportMessageResponse.fromEntity(savedMessage);
    }
}


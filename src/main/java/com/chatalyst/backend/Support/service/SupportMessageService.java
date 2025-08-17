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
import com.chatalyst.backend.Support.dto.UpdateReplyRequest;
import com.chatalyst.backend.Support.dto.UpdateSupportMessageRequest;
import com.chatalyst.backend.Support.dto.SupportStatsResponse;
import com.chatalyst.backend.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SupportMessageService {

    private final SupportMessageRepository supportMessageRepository;
    private final SupportMessageReplyRepository supportMessageReplyRepository;
    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager em;

    public SupportMessageService(SupportMessageRepository supportMessageRepository, SupportMessageReplyRepository supportMessageReplyRepository, UserRepository userRepository) {
        this.supportMessageRepository = supportMessageRepository;
        this.supportMessageReplyRepository = supportMessageReplyRepository;
        this.userRepository = userRepository;
    }

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
    public List<SupportMessageResponse> getMessagesWithFilters(
            MessageStatus status,
            MessagePriority priority,
            Long adminId,
            String search,
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            String sortBy,
            String sortDirection) {

        return supportMessageRepository.findWithAdvancedFilters(
                        status,
                        priority,
                        adminId,
                        search,
                        dateFrom,
                        dateTo,
                        sortBy,
                        sortDirection)
                .stream()
                .map(SupportMessageResponse::fromEntity)
                .collect(Collectors.toList());
    }


    @Transactional(readOnly = true)
    public List<SupportMessageResponse> getMessagesWithAdvancedFilters(
            MessageStatus status, MessagePriority priority, Long adminId,
            String search, String sortBy, String sortDirection,
            String dateFrom, String dateTo) {

        // Приведение search к строке и обрезка пробелов
        String safeSearch = (search == null || search.isBlank()) ? null : "%" + search.toLowerCase() + "%";


        List<SupportMessage> messages = supportMessageRepository.findWithAdvancedFilters(
                status,
                priority,
                adminId,
                safeSearch,
                parseDate(dateFrom),
                parseDate(dateTo),
                sortBy,
                sortDirection
        );

        return messages.stream()
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

    @Transactional
    public SupportMessageResponse updateMessage(Long messageId, Long adminId, UpdateSupportMessageRequest request) {
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

        message.setSubject(request.getSubject());
        message.setMessage(request.getMessage());
        message.setPriority(request.getPriority());
        message.setUpdatedAt(LocalDateTime.now());

        SupportMessage savedMessage = supportMessageRepository.save(message);

        log.info("Message {} updated by admin {}", messageId, adminId);

        return SupportMessageResponse.fromEntity(savedMessage);
    }

    @Transactional
    public void deleteMessage(Long messageId, Long adminId) {
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

        // Сначала удаляем все ответы
        supportMessageReplyRepository.deleteByMessageId(messageId);

        // Затем удаляем само сообщение
        supportMessageRepository.delete(message);

        log.info("Message {} deleted by admin {}", messageId, adminId);
    }

    @Transactional
    public void deleteReply(Long replyId, Long adminId) {
        SupportMessageReply reply = supportMessageReplyRepository.findById(replyId)
                .orElseThrow(() -> new RuntimeException("Reply not found"));

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Проверяем что это админ
        boolean isAdmin = admin.getRoles().stream()
                .anyMatch(role -> role.getName() == RoleName.ROLE_ADMIN);

        if (!isAdmin) {
            throw new RuntimeException("Access denied - admin role required");
        }

        supportMessageReplyRepository.delete(reply);

        log.info("Reply {} deleted by admin {}", replyId, adminId);
    }

    @Transactional
    public SupportMessageReplyResponse updateReply(Long replyId, Long adminId, UpdateReplyRequest request) {
        SupportMessageReply reply = supportMessageReplyRepository.findById(replyId)
                .orElseThrow(() -> new RuntimeException("Reply not found"));

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Проверяем что это админ
        boolean isAdmin = admin.getRoles().stream()
                .anyMatch(role -> role.getName() == RoleName.ROLE_ADMIN);

        if (!isAdmin) {
            throw new RuntimeException("Access denied - admin role required");
        }

        reply.setReplyText(request.getReplyText());
        reply.setUpdatedAt(LocalDateTime.now());

        SupportMessageReply savedReply = supportMessageReplyRepository.save(reply);

        log.info("Reply {} updated by admin {}", replyId, adminId);

        return SupportMessageReplyResponse.fromEntity(savedReply);
    }

    @Transactional(readOnly = true)
    public SupportStatsResponse getSupportStats() {
        // Get today\"s date and the date from 7 days ago (a week ago)
        LocalDate today = LocalDate.now();
        LocalDate weekAgo = today.minusDays(6);

        // --- Statistics by status and priority ---
        List<SupportMessageRepository.StatusPriorityCount> statusPriorityCounts = supportMessageRepository.countByStatusAndPriority();

        // Calculate total messages by summing up the counts for all statuses and priorities
        Long totalMessages = statusPriorityCounts.stream()
                .mapToLong(SupportMessageRepository.StatusPriorityCount::getCount)
                .sum();

        // Calculate messages by status using a stream filter
        Long openMessages = statusPriorityCounts.stream()
                .filter(c -> c.getStatus() == MessageStatus.OPEN)
                .mapToLong(SupportMessageRepository.StatusPriorityCount::getCount)
                .sum();
        Long inProgressMessages = statusPriorityCounts.stream()
                .filter(c -> c.getStatus() == MessageStatus.IN_PROGRESS)
                .mapToLong(SupportMessageRepository.StatusPriorityCount::getCount)
                .sum();
        Long closedMessages = statusPriorityCounts.stream()
                .filter(c -> c.getStatus() == MessageStatus.CLOSED)
                .mapToLong(SupportMessageRepository.StatusPriorityCount::getCount)
                .sum();

        // Calculate messages by priority using a stream filter
        Long highPriorityMessages = statusPriorityCounts.stream()
                .filter(c -> c.getPriority() == MessagePriority.HIGH)
                .mapToLong(SupportMessageRepository.StatusPriorityCount::getCount)
                .sum();
        Long mediumPriorityMessages = statusPriorityCounts.stream()
                .filter(c -> c.getPriority() == MessagePriority.MEDIUM)
                .mapToLong(SupportMessageRepository.StatusPriorityCount::getCount)
                .sum();
        Long lowPriorityMessages = statusPriorityCounts.stream()
                .filter(c -> c.getPriority() == MessagePriority.LOW)
                .mapToLong(SupportMessageRepository.StatusPriorityCount::getCount)
                .sum();

        // --- Statistics by admin ---
        Map<String, Long> messagesByAdmin = new HashMap<>();
        List<Object[]> adminCounts = em.createQuery("SELECT COALESCE(m.admin.name, \'UNASSIGNED\'), COUNT(m) FROM SupportMessage m GROUP BY m.admin")
                .getResultList();

        for (Object[] result : adminCounts) {
            messagesByAdmin.put((String) result[0], (Long) result[1]);
        }

        // --- Statistics for unassigned messages ---
        Long unassignedMessages = supportMessageRepository.countByAdminIsNull();

        // --- Statistics by day ---
        List<SupportMessageRepository.MessagesByDay> messagesByDayList = supportMessageRepository.countByCreatedAtSince(weekAgo);
        Map<LocalDate, Long> last7DaysStats = messagesByDayList.stream()
                .collect(Collectors.toMap(
                        SupportMessageRepository.MessagesByDay::getDate,
                        SupportMessageRepository.MessagesByDay::getCount
                ));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<String, Long> messagesByDayStr = last7DaysStats.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().format(formatter),
                        Map.Entry::getValue
                ));

        // --- Build SupportStatsResponse ---
        return SupportStatsResponse.builder()
                .totalMessages(totalMessages)
                .openMessages(openMessages)
                .inProgressMessages(inProgressMessages)
                .closedMessages(closedMessages)
                .highPriorityMessages(highPriorityMessages)
                .mediumPriorityMessages(mediumPriorityMessages)
                .lowPriorityMessages(lowPriorityMessages)
                .unassignedMessages(unassignedMessages)
                .messagesByAdmin(messagesByAdmin)
                .last7DaysStats(last7DaysStats)
                .build();
    }

    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr).atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }
}


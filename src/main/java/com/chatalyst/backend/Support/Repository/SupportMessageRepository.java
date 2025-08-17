package com.chatalyst.backend.Support.Repository;

import com.chatalyst.backend.Support.Entity.SupportMessage;
import com.chatalyst.backend.Support.Entity.MessageStatus;
import com.chatalyst.backend.Support.Entity.MessagePriority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {
    
    List<SupportMessage> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    List<SupportMessage> findByStatusOrderByCreatedAtDesc(MessageStatus status);
    
    List<SupportMessage> findByAdminIdOrderByCreatedAtDesc(Long adminId);
    
    List<SupportMessage> findAllByOrderByCreatedAtDesc();
    
    @Query("SELECT m FROM SupportMessage m WHERE " +
           "(:status IS NULL OR m.status = :status) AND " +
           "(:priority IS NULL OR m.priority = :priority) AND " +
           "(:adminId IS NULL OR m.admin.id = :adminId) " +
           "ORDER BY m.createdAt DESC")
    List<SupportMessage> findWithFilters(
        @Param("status") MessageStatus status,
        @Param("priority") MessagePriority priority,
        @Param("adminId") Long adminId
    );
}

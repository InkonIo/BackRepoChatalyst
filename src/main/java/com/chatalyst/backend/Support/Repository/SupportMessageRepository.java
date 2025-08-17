package com.chatalyst.backend.Support.Repository;

import com.chatalyst.backend.Support.Entity.MessagePriority;
import com.chatalyst.backend.Support.Entity.MessageStatus;
import com.chatalyst.backend.Support.Entity.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long>, SupportMessageRepositoryCustom {

    List<SupportMessage> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<SupportMessage> findAllByOrderByCreatedAtDesc();

    Long countByStatus(MessageStatus status);
    Long countByPriority(MessagePriority priority);
    Long countByAdminIsNull();

    @Query("SELECT COUNT(m) FROM SupportMessage m WHERE DATE(m.createdAt) = :date")
    Long countByCreatedAtDate(LocalDate date);



    // Статистика по статусу и приоритету
    @Query("""
        SELECT m.status AS status, m.priority AS priority, COUNT(m) AS count
        FROM SupportMessage m
        GROUP BY m.status, m.priority
    """)
    List<StatusPriorityCount> countByStatusAndPriority();



    // Статистика по дням, начиная с определенной даты
    @Query("""
        SELECT CAST(m.createdAt AS LocalDate) AS date, COUNT(m) AS count
        FROM SupportMessage m
        WHERE m.createdAt >= :date
        GROUP BY CAST(m.createdAt AS LocalDate)
    """)
    List<MessagesByDay> countByCreatedAtSince(@Param("date") LocalDate date);


    interface StatusPriorityCount {
        MessageStatus getStatus();
        MessagePriority getPriority();
        Long getCount();
    }



    interface MessagesByDay {
        LocalDate getDate();
        Long getCount();
    }
}
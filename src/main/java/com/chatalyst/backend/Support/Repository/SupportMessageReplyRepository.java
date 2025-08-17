package com.chatalyst.backend.Support.Repository;

import com.chatalyst.backend.Support.Entity.SupportMessageReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportMessageReplyRepository extends JpaRepository<SupportMessageReply, Long> {
    
    List<SupportMessageReply> findByMessageIdOrderByCreatedAtAsc(Long messageId);
}

package com.example.webdienthoai.repository;

import com.example.webdienthoai.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** Lấy N tin nhắn mới nhất của user, sắp xếp tăng dần (để hiển thị đúng thứ tự). */
    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.userId = :userId
            ORDER BY m.sentAt DESC
            """)
    List<ChatMessage> findTopByUserIdOrderByDesc(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            SELECT m.userId FROM ChatMessage m
            GROUP BY m.userId
            ORDER BY MAX(m.sentAt) DESC
            """)
    List<Long> findConversationUserIds(Pageable pageable);

    Optional<ChatMessage> findTopByUserIdOrderBySentAtDesc(Long userId);

    List<ChatMessage> findByUserIdOrderBySentAtDesc(Long userId, Pageable pageable);
}

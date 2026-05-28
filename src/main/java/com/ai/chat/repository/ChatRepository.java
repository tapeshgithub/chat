package com.ai.chat.repository;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.ai.chat.models.AppUser;
import com.ai.chat.models.ChatMessage;

@Repository
public interface ChatRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByUserOrderByCreatedAtAsc(AppUser user);

    @Query("SELECT m FROM ChatMessage m WHERE m.user = :user ORDER BY m.createdAt DESC")
    List<ChatMessage> findRecentByUser(@Param("user") AppUser user, Pageable pageable);
}
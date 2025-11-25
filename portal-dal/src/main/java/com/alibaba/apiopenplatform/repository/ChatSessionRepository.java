package com.alibaba.apiopenplatform.repository;

import com.alibaba.apiopenplatform.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends BaseRepository<ChatSession, Long> {

    /**
     * Find a session by session ID
     */
    Optional<ChatSession> findBySessionId(String sessionId);

    /**
     * Find sessions by user ID
     */
    Page<ChatSession> findByUserId(String userId, Pageable pageable);

    /**
     * Calculate the number of sessions for a user
     */
    int countByUserId(String userId);

    /**
     * Find a session by session ID and user ID
     */
    Optional<ChatSession> findBySessionIdAndUserId(String sessionId, String userId);

    /**
     * Find all sessions for a user
     */
    List<ChatSession> findAllByUserId(String userId);

    /**
     * Find the first session for a user
     */
    Optional<ChatSession> findFirstByUserId(String userId, Sort sort);
}

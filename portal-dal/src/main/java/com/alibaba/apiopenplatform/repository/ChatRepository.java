package com.alibaba.apiopenplatform.repository;

import com.alibaba.apiopenplatform.entity.Chat;
import com.alibaba.apiopenplatform.support.enums.ChatStatus;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRepository extends BaseRepository<Chat, Long> {

    /**
     * Find by sessionId and status
     *
     * @param sessionId
     * @param status
     * @param sort
     * @return
     */
    List<Chat> findBySessionIdAndStatus(String sessionId, ChatStatus status, Sort sort);

    /**
     * Find by chatId
     *
     * @param chatId
     * @return
     */
    Optional<Chat> findByChatId(String chatId);

    /**
     * Find all chats by sessionId ordered by creation time ascending
     *
     * @param sessionId
     * @return
     */
    List<Chat> findBySessionIdOrderByCreateAtAsc(String sessionId);

    /**
     * Check if user has access to the session
     *
     * @param sessionId
     * @param userId
     * @return
     */
    boolean existsBySessionIdAndUserId(String sessionId, String userId);
}

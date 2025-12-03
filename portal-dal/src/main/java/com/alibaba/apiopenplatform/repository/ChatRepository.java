package com.alibaba.apiopenplatform.repository;

import com.alibaba.apiopenplatform.entity.Chat;
import com.alibaba.apiopenplatform.support.enums.ChatStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
     * Find all chats for given sessionId and userId
     *
     * @param sessionId
     * @param userId
     * @param sort
     * @return
     */
    List<Chat> findAllBySessionIdAndUserId(String sessionId, String userId, Sort sort);

    /**
     * Find next sequence for given conversationId and questionId
     *
     * @param conversationId
     * @param questionId
     * @return
     */
    @Query("SELECT COALESCE(MAX(c.sequence), 0) " +
            "FROM Chat c " +
            "WHERE c.sessionId = :sessionId " +
            "AND c.conversationId = :conversationId " +
            "AND c.questionId = :questionId " +
            "AND c.productId = :productId")
    Integer findCurrentSequence(@Param("sessionId") String sessionId, @Param("conversationId") String conversationId,
                                @Param("questionId") String questionId, @Param("productId") String productId);

    /**
     * Delete all chats for given sessionId
     *
     * @param sessionId
     */
    void deleteAllBySessionId(String sessionId);
}

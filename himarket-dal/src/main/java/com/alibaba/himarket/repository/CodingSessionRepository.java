package com.alibaba.himarket.repository;

import com.alibaba.himarket.entity.CodingSession;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public interface CodingSessionRepository extends BaseRepository<CodingSession, Long> {

    Optional<CodingSession> findBySessionId(String sessionId);

    Optional<CodingSession> findBySessionIdAndUserId(String sessionId, String userId);

    Page<CodingSession> findByUserIdOrderByUpdatedAtDesc(String userId, Pageable pageable);

    int countByUserId(String userId);

    void deleteBySessionId(String sessionId);
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.alibaba.himarket.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.himarket.entity.RevokedToken;
import com.alibaba.himarket.repository.RevokedTokenRepository;
import com.alibaba.himarket.service.RevokedTokenService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class RevokedTokenServiceImpl implements RevokedTokenService {

    private final RevokedTokenRepository revokedTokenRepository;

    private final Cache<String, Boolean> revokedTokenCache =
            Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(1, TimeUnit.HOURS).build();

    public RevokedTokenServiceImpl(RevokedTokenRepository revokedTokenRepository) {
        this.revokedTokenRepository = revokedTokenRepository;
    }

    @Override
    public void revokeToken(String token, long expiresAtMillis) {
        if (StrUtil.isBlank(token)) {
            return;
        }
        String tokenHash = DigestUtil.sha256Hex(token);
        LocalDateTime expiresAt =
                LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(expiresAtMillis), ZoneId.systemDefault());

        RevokedToken revokedToken =
                RevokedToken.builder().tokenHash(tokenHash).expiresAt(expiresAt).build();
        revokedTokenRepository.save(revokedToken);
        revokedTokenCache.put(tokenHash, Boolean.TRUE);
    }

    @Override
    public boolean isTokenRevoked(String token) {
        if (StrUtil.isBlank(token)) {
            return false;
        }
        String tokenHash = DigestUtil.sha256Hex(token);

        Boolean cached = revokedTokenCache.getIfPresent(tokenHash);
        if (cached != null) {
            return cached;
        }

        boolean revoked = revokedTokenRepository.existsByTokenHash(tokenHash);
        if (revoked) {
            revokedTokenCache.put(tokenHash, Boolean.TRUE);
        }
        return revoked;
    }

    @Override
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupExpiredTokens() {
        revokedTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        log.info("Cleaned up expired revoked tokens");
    }
}

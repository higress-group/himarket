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

package com.alibaba.himarket.core.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.alibaba.himarket.repository.RevokedTokenRepository;
import com.alibaba.himarket.service.RevokedTokenService;
import com.alibaba.himarket.service.impl.RevokedTokenServiceImpl;
import com.alibaba.himarket.support.common.User;
import com.alibaba.himarket.support.enums.UserType;
import com.github.benmanes.caffeine.cache.Cache;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import net.jqwik.api.*;

/**
 * Property-based tests for token revocation persistence.
 *
 * <p>Property 1: Bug Condition — Revoked Token Lost After Restart (tests RevokedTokenServiceImpl
 * directly with a mock repository to verify DB-backed persistence survives cache eviction)
 *
 * <p>Property 2: Preservation — Non-Revoked Token Authentication Unchanged (tests
 * RevokedTokenService for non-revoked and blank tokens, and TokenUtil for parsing)
 */
class TokenRevocationPersistencePropertyTest {

    /**
     * One-time setup: inject JWT_SECRET and JWT_EXPIRE_MILLIS into TokenUtil via reflection, since
     * there is no Spring context in this unit test.
     */
    static {
        try {
            Field secretField = TokenUtil.class.getDeclaredField("JWT_SECRET");
            secretField.setAccessible(true);
            secretField.set(null, "YourJWTSecret");

            Field expireField = TokenUtil.class.getDeclaredField("JWT_EXPIRE_MILLIS");
            expireField.setAccessible(true);
            expireField.setLong(null, 7L * 24 * 60 * 60 * 1000); // 7 days in millis
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize TokenUtil fields via reflection", e);
        }
    }

    @Provide
    Arbitrary<String> randomUserIds() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(4).ofMaxLength(32);
    }

    @Provide
    Arbitrary<UserType> randomUserTypes() {
        return Arbitraries.of(UserType.ADMIN, UserType.DEVELOPER);
    }

    /**
     * <b>Validates: Requirements 1.1, 1.3</b>
     *
     * <p>Bug Condition: For any revoked, non-expired token, clearing the Caffeine cache
     * (simulating restart) must NOT cause isTokenRevoked to return false. The fix delegates to
     * RevokedTokenService which checks the DB when the cache misses.
     *
     * <p>This test directly exercises RevokedTokenServiceImpl with a mock repository that tracks
     * persisted token hashes. After revoking a token and clearing the Caffeine cache, the service
     * falls back to the DB (mock) and still correctly identifies the token as revoked.
     */
    @Property(tries = 50)
    @SuppressWarnings("unchecked")
    void revokedTokenLostAfterSimulatedRestart(@ForAll("randomUserIds") String userId) {
        // Track which token hashes have been "persisted" to the mock DB
        Set<String> persistedHashes = new HashSet<>();

        // Create a mock repository that simulates DB persistence
        RevokedTokenRepository mockRepository = mock(RevokedTokenRepository.class);
        when(mockRepository.save(any()))
                .thenAnswer(
                        invocation -> {
                            Object entity = invocation.getArgument(0);
                            // Extract tokenHash from the entity via reflection
                            Field hashField = entity.getClass().getDeclaredField("tokenHash");
                            hashField.setAccessible(true);
                            String hash = (String) hashField.get(entity);
                            persistedHashes.add(hash);
                            return entity;
                        });
        when(mockRepository.existsByTokenHash(anyString()))
                .thenAnswer(invocation -> persistedHashes.contains(invocation.getArgument(0)));

        // Create the service under test with the mock repository
        RevokedTokenServiceImpl service = new RevokedTokenServiceImpl(mockRepository);

        // Generate a valid admin token
        String token = TokenUtil.generateAdminToken(userId);
        long expiresAtMillis = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000;

        // Revoke the token (persists to mock DB + puts in Caffeine cache)
        service.revokeToken(token, expiresAtMillis);

        // Verify token is revoked before restart simulation (served from cache)
        assertTrue(
                service.isTokenRevoked(token),
                "Token should be revoked immediately after revokeToken call");

        // Simulate application restart by clearing the Caffeine cache via reflection
        try {
            Field cacheField = RevokedTokenServiceImpl.class.getDeclaredField("revokedTokenCache");
            cacheField.setAccessible(true);
            Cache<String, Boolean> cache = (Cache<String, Boolean>) cacheField.get(service);
            cache.invalidateAll();
        } catch (Exception e) {
            fail("Failed to clear revokedTokenCache via reflection: " + e.getMessage());
        }

        // Assert the expected (fixed) behavior: isTokenRevoked should still return true
        // because the service falls back to the DB (mock repository) after cache miss
        assertTrue(
                service.isTokenRevoked(token),
                "Bug confirmed: isTokenRevoked returns false after clearing Caffeine cache"
                        + " (simulated restart) for a revoked, non-expired token. Token: "
                        + token.substring(0, Math.min(20, token.length()))
                        + "...");
    }

    // ==================== Property 2: Preservation Tests ====================

    /**
     * <b>Validates: Requirements 3.1</b>
     *
     * <p>Preservation: For all randomly generated valid JWT tokens that have NOT been revoked,
     * isTokenRevoked returns false. This ensures no false positives — non-revoked tokens must never
     * be reported as revoked.
     */
    @Property(tries = 50)
    void nonRevokedTokenIsNotReportedAsRevoked(
            @ForAll("randomUserIds") String userId, @ForAll("randomUserTypes") UserType userType) {
        // Create a mock repository that has no persisted tokens
        RevokedTokenRepository mockRepository = mock(RevokedTokenRepository.class);
        when(mockRepository.existsByTokenHash(anyString())).thenReturn(false);
        RevokedTokenService service = new RevokedTokenServiceImpl(mockRepository);

        // Generate a valid token (admin or developer) but do NOT revoke it
        String token =
                userType == UserType.ADMIN
                        ? TokenUtil.generateAdminToken(userId)
                        : TokenUtil.generateDeveloperToken(userId);

        // Assert: non-revoked token must not be reported as revoked
        assertFalse(
                service.isTokenRevoked(token),
                "Non-revoked token should not be reported as revoked. UserId: "
                        + userId
                        + ", UserType: "
                        + userType);
    }

    /**
     * <b>Validates: Requirements 3.6</b>
     *
     * <p>Preservation: For blank and null token inputs, isTokenRevoked returns false. Blank/null
     * tokens must be handled gracefully without errors.
     */
    @Property(tries = 10)
    void blankAndNullTokensAreNotRevoked(@ForAll("blankOrNullTokens") String token) {
        // Create a mock repository — should never be called for blank tokens
        RevokedTokenRepository mockRepository = mock(RevokedTokenRepository.class);
        RevokedTokenService service = new RevokedTokenServiceImpl(mockRepository);

        assertFalse(
                service.isTokenRevoked(token),
                "Blank/null token should not be reported as revoked. Token: "
                        + (token == null ? "null" : "\"" + token + "\""));
    }

    @Provide
    Arbitrary<String> blankOrNullTokens() {
        return Arbitraries.of(null, "", " ", "  ", "\t", "\n");
    }

    /**
     * <b>Validates: Requirements 3.1, 3.2</b>
     *
     * <p>Preservation: For all randomly generated valid JWT tokens, parseUser returns a User with
     * the correct userId and userType. This ensures token parsing is preserved and unaffected.
     */
    @Property(tries = 50)
    void parseUserExtractsCorrectUserInfo(
            @ForAll("randomUserIds") String userId, @ForAll("randomUserTypes") UserType userType) {
        // Generate a valid token
        String token =
                userType == UserType.ADMIN
                        ? TokenUtil.generateAdminToken(userId)
                        : TokenUtil.generateDeveloperToken(userId);

        // Parse the token and verify user info is correctly extracted
        User user = TokenUtil.parseUser(token);

        assertNotNull(user, "parseUser should return a non-null User");
        assertEquals(userId, user.getUserId(), "parseUser should extract the correct userId");
        assertEquals(userType, user.getUserType(), "parseUser should extract the correct userType");
    }
}

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.entity.Developer;
import com.alibaba.himarket.entity.DeveloperExternalIdentity;
import com.alibaba.himarket.repository.DeveloperExternalIdentityRepository;
import com.alibaba.himarket.repository.DeveloperRepository;
import com.alibaba.himarket.repository.PortalRepository;
import com.alibaba.himarket.service.ConsumerService;
import com.alibaba.himarket.service.TokenService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;

class DeveloperServiceImplProfileTest {

    private static final String PROVIDER = "feishu";
    private static final String SUBJECT = "ou_123";
    private static final String EMAIL = "alice@example.com";
    private static final String AVATAR_URL = "https://example.com/avatar.png";

    private DeveloperRepository developerRepository;
    private DeveloperExternalIdentityRepository externalRepository;
    private DeveloperServiceImpl service;

    @BeforeEach
    void setUp() {
        developerRepository = mock(DeveloperRepository.class);
        externalRepository = mock(DeveloperExternalIdentityRepository.class);
        service =
                new DeveloperServiceImpl(
                        developerRepository,
                        externalRepository,
                        mock(PortalRepository.class),
                        mock(ContextHolder.class),
                        mock(ConsumerService.class),
                        mock(ApplicationEventPublisher.class),
                        mock(TokenService.class));
    }

    @Test
    void backfillsEmailForExistingDeveloper() {
        Developer developer = Developer.builder().avatarUrl(AVATAR_URL).build();
        existingIdentity(developer);

        service.updateExternalDeveloperProfile(PROVIDER, SUBJECT, null, EMAIL);

        assertEquals(EMAIL, developer.getEmail());
        assertEquals(AVATAR_URL, developer.getAvatarUrl());
        verify(developerRepository).save(developer);
    }

    @Test
    void updatesChangedEmailAndAvatarTogether() {
        Developer developer =
                Developer.builder()
                        .email("old@example.com")
                        .avatarUrl("https://example.com/old-avatar.png")
                        .build();
        existingIdentity(developer);

        service.updateExternalDeveloperProfile(PROVIDER, SUBJECT, AVATAR_URL, EMAIL);

        assertEquals(EMAIL, developer.getEmail());
        assertEquals(AVATAR_URL, developer.getAvatarUrl());
        verify(developerRepository).save(developer);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void blankEmailPreservesStoredEmailWhileAvatarChanges(String email) {
        Developer developer = Developer.builder().email(EMAIL).build();
        existingIdentity(developer);

        service.updateExternalDeveloperProfile(PROVIDER, SUBJECT, AVATAR_URL, email);

        assertEquals(EMAIL, developer.getEmail());
        assertEquals(AVATAR_URL, developer.getAvatarUrl());
        verify(developerRepository).save(developer);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void blankAvatarPreservesStoredAvatarWhileEmailChanges(String avatarUrl) {
        Developer developer = Developer.builder().avatarUrl(AVATAR_URL).build();
        existingIdentity(developer);

        service.updateExternalDeveloperProfile(PROVIDER, SUBJECT, avatarUrl, EMAIL);

        assertEquals(EMAIL, developer.getEmail());
        assertEquals(AVATAR_URL, developer.getAvatarUrl());
        verify(developerRepository).save(developer);
    }

    @Test
    void unchangedProfileDoesNotTriggerSave() {
        Developer developer = Developer.builder().email(EMAIL).avatarUrl(AVATAR_URL).build();
        existingIdentity(developer);

        service.updateExternalDeveloperProfile(PROVIDER, SUBJECT, AVATAR_URL, EMAIL);

        assertEquals(EMAIL, developer.getEmail());
        assertEquals(AVATAR_URL, developer.getAvatarUrl());
        verifyNoInteractions(developerRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void blankProfileDoesNotTriggerLookupOrSave(String blankValue) {
        service.updateExternalDeveloperProfile(PROVIDER, SUBJECT, blankValue, blankValue);

        verifyNoInteractions(externalRepository, developerRepository);
    }

    @Test
    void missingExternalIdentityDoesNotTriggerSave() {
        when(externalRepository.findByProviderAndSubject(PROVIDER, SUBJECT))
                .thenReturn(Optional.empty());

        service.updateExternalDeveloperProfile(PROVIDER, SUBJECT, AVATAR_URL, EMAIL);

        verifyNoInteractions(developerRepository);
    }

    private void existingIdentity(Developer developer) {
        DeveloperExternalIdentity identity =
                DeveloperExternalIdentity.builder()
                        .provider(PROVIDER)
                        .subject(SUBJECT)
                        .developer(developer)
                        .build();
        when(externalRepository.findByProviderAndSubject(PROVIDER, SUBJECT))
                .thenReturn(Optional.of(identity));
    }
}

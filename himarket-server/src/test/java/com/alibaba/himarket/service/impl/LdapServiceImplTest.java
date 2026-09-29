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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.ldap.LdapClient;
import com.alibaba.himarket.core.ldap.LdapClient.LdapEntry;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.result.common.AuthResult;
import com.alibaba.himarket.dto.result.developer.DeveloperResult;
import com.alibaba.himarket.dto.result.portal.PortalResult;
import com.alibaba.himarket.service.DeveloperService;
import com.alibaba.himarket.service.PortalService;
import com.alibaba.himarket.service.TokenService;
import com.alibaba.himarket.support.portal.LdapConfig;
import com.alibaba.himarket.support.portal.PortalSettingConfig;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LdapServiceImplTest {

    @Mock private PortalService portalService;
    @Mock private TokenService tokenService;
    @Mock private DeveloperService developerService;
    @Mock private ContextHolder contextHolder;
    @Mock private LdapClient ldapClient;

    private LdapServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new LdapServiceImpl(
                        portalService, tokenService, developerService, contextHolder, ldapClient);
        when(contextHolder.getPortal()).thenReturn("portal-1");
        lenient().when(tokenService.generateDeveloperToken("dev-1")).thenReturn("tok-123");
        lenient().when(tokenService.getTokenExpiresIn()).thenReturn(3600L);
    }

    private PortalResult portalWith(LdapConfig ldap) {
        PortalSettingConfig cfg = new PortalSettingConfig();
        cfg.setLdap(ldap);
        PortalResult portal = new PortalResult();
        portal.setPortalSettingConfig(cfg);
        return portal;
    }

    private LdapConfig patternConfig() {
        LdapConfig c = new LdapConfig();
        c.setUrl("ldap://10.2.7.31:389");
        c.setUserDnPattern("uid={0},ou=people,dc=lyf,dc=intl");
        return c;
    }

    @Test
    @DisplayName("pattern mode: existing developer reuses account and returns token")
    void existingDeveloperReturnsToken() {
        when(portalService.getPortal("portal-1")).thenReturn(portalWith(patternConfig()));
        when(ldapClient.bind(
                        "ldap://10.2.7.31:389", "uid=zhangsan,ou=people,dc=lyf,dc=intl", "secret"))
                .thenReturn(true);

        DeveloperResult existing = new DeveloperResult();
        existing.setDeveloperId("dev-1");
        when(developerService.getExternalDeveloper("ldap", "zhangsan")).thenReturn(existing);

        AuthResult result = service.login("zhangsan", "secret");

        assertThat(result.getAccessToken()).isEqualTo("tok-123");
        verify(developerService, never()).createExternalDeveloper(any());
        verify(developerService).getExternalDeveloper("ldap", "zhangsan");
    }

    @Test
    @DisplayName("pattern mode: unknown developer is auto-created")
    void unknownDeveloperIsCreated() {
        when(portalService.getPortal("portal-1")).thenReturn(portalWith(patternConfig()));
        when(ldapClient.bind(any(), any(), any())).thenReturn(true);
        when(developerService.getExternalDeveloper("ldap", "lisi")).thenReturn(null);

        DeveloperResult created = new DeveloperResult();
        created.setDeveloperId("dev-1");
        when(developerService.createExternalDeveloper(any())).thenReturn(created);

        AuthResult result = service.login("lisi", "secret");

        assertThat(result.getAccessToken()).isEqualTo("tok-123");
        verify(developerService).createExternalDeveloper(any());
    }

    @Test
    @DisplayName("bind failure throws BusinessException (UNAUTHORIZED)")
    void bindFailureThrows() {
        when(portalService.getPortal("portal-1")).thenReturn(portalWith(patternConfig()));
        when(ldapClient.bind(any(), any(), any())).thenReturn(false);

        assertThrows(BusinessException.class, () -> service.login("zhangsan", "wrong"));
    }

    @Test
    @DisplayName("LDAP not configured throws BusinessException (INVALID_REQUEST)")
    void notConfiguredThrows() {
        PortalResult portal = new PortalResult();
        portal.setPortalSettingConfig(new PortalSettingConfig());
        when(portalService.getPortal("portal-1")).thenReturn(portal);

        assertThrows(BusinessException.class, () -> service.login("zhangsan", "x"));
    }

    @Test
    @DisplayName("search mode resolves DN and display name from LDAP attributes")
    void searchModeResolvesDn() {
        LdapConfig c = new LdapConfig();
        c.setUrl("ldap://10.2.7.31:389");
        c.setBindDn("cn=admin");
        c.setBindPassword("adminpw");
        c.setUserSearchBase("ou=people,dc=lyf,dc=intl");
        c.setUserSearchFilter("(uid={0})");
        c.setDisplayNameAttribute("cn");

        when(portalService.getPortal("portal-1")).thenReturn(portalWith(c));

        Attributes attrs = new BasicAttributes();
        attrs.put(new BasicAttribute("cn", "Zhang San"));
        LdapEntry entry = new LdapEntry("uid=zhangsan,ou=people,dc=lyf,dc=intl", attrs);
        when(ldapClient.search(
                        eq("ldap://10.2.7.31:389"),
                        eq("cn=admin"),
                        eq("adminpw"),
                        eq("ou=people,dc=lyf,dc=intl"),
                        eq("(uid={0})"),
                        eq("zhangsan")))
                .thenReturn(entry);
        when(ldapClient.bind(
                        eq("ldap://10.2.7.31:389"),
                        eq("uid=zhangsan,ou=people,dc=lyf,dc=intl"),
                        eq("secret")))
                .thenReturn(true);

        DeveloperResult existing = new DeveloperResult();
        existing.setDeveloperId("dev-1");
        when(developerService.getExternalDeveloper("ldap", "zhangsan")).thenReturn(existing);

        AuthResult result = service.login("zhangsan", "secret");

        assertThat(result.getAccessToken()).isEqualTo("tok-123");
        verify(developerService, never()).createExternalDeveloper(any());
    }
}

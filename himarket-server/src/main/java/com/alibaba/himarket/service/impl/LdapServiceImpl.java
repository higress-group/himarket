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

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.ldap.LdapClient;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.params.developer.CreateExternalDeveloperParam;
import com.alibaba.himarket.dto.result.common.AuthResult;
import com.alibaba.himarket.dto.result.developer.DeveloperResult;
import com.alibaba.himarket.dto.result.portal.PortalResult;
import com.alibaba.himarket.service.DeveloperService;
import com.alibaba.himarket.service.LdapService;
import com.alibaba.himarket.service.PortalService;
import com.alibaba.himarket.service.TokenService;
import com.alibaba.himarket.support.common.Strings;
import com.alibaba.himarket.support.enums.DeveloperAuthType;
import com.alibaba.himarket.support.portal.LdapConfig;
import java.util.Objects;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Enterprise LDAP login (T-34). Reuses the existing external-identity flow: a successful bind
 * links (or creates) a {@code DeveloperExternalIdentity} with provider="ldap" and issues a
 * normal developer token. No browser redirect / state is involved.
 */
@Service
@RequiredArgsConstructor
public class LdapServiceImpl implements LdapService {

    private static final String PROVIDER = "ldap";

    private final PortalService portalService;

    private final TokenService tokenService;

    private final DeveloperService developerService;

    private final ContextHolder contextHolder;

    private final LdapClient ldapClient;

    @Override
    public AuthResult login(String username, String password) {
        LdapConfig ldap = resolveConfig();
        ResolvedUser user = resolveUserDn(ldap, username);

        if (!ldapClient.bind(ldap.getUrl(), user.getDn(), password)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "LDAP authentication failed");
        }

        String developerId = findOrCreateDeveloper(username, user.getDisplayName());
        String token = tokenService.generateDeveloperToken(developerId);
        return AuthResult.of(token, tokenService.getTokenExpiresIn());
    }

    @Override
    public boolean isConfigured() {
        PortalResult portal = portalService.getPortal(contextHolder.getPortal());
        if (portal == null || portal.getPortalSettingConfig() == null) {
            return false;
        }
        LdapConfig ldap = portal.getPortalSettingConfig().getLdap();
        return ldap != null && ldap.enabled();
    }

    private LdapConfig resolveConfig() {
        PortalResult portal = portalService.getPortal(contextHolder.getPortal());
        if (portal == null
                || portal.getPortalSettingConfig() == null
                || portal.getPortalSettingConfig().getLdap() == null
                || !portal.getPortalSettingConfig().getLdap().enabled()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "LDAP login is not configured");
        }
        return portal.getPortalSettingConfig().getLdap();
    }

    private ResolvedUser resolveUserDn(LdapConfig ldap, String username) {
        if (Strings.isNotBlank(ldap.getUserDnPattern())) {
            String dn = ldap.getUserDnPattern().replace("{0}", username);
            return new ResolvedUser(dn, username);
        }
        if (Strings.isNotBlank(ldap.getUserSearchBase())
                && Strings.isNotBlank(ldap.getUserSearchFilter())) {
            LdapClient.LdapEntry entry =
                    ldapClient.search(
                            ldap.getUrl(),
                            ldap.getBindDn(),
                            ldap.getBindPassword(),
                            ldap.getUserSearchBase(),
                            ldap.getUserSearchFilter(),
                            username);
            if (entry == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "LDAP user not found");
            }
            return new ResolvedUser(
                    entry.getDn(),
                    readAttribute(entry.getAttributes(), ldap.getDisplayNameAttribute(), username));
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Invalid LDAP configuration");
    }

    private String readAttribute(Attributes attributes, String attributeName, String fallback) {
        if (Strings.isBlank(attributeName)) {
            return fallback;
        }
        try {
            if (attributes == null) {
                return fallback;
            }
            Attribute attribute = attributes.get(attributeName);
            if (attribute == null) {
                return fallback;
            }
            return Objects.toString(attribute.get(), fallback);
        } catch (NamingException e) {
            return fallback;
        }
    }

    private String findOrCreateDeveloper(String subject, String displayName) {
        DeveloperResult existing = developerService.getExternalDeveloper(PROVIDER, subject);
        if (existing != null) {
            return existing.getDeveloperId();
        }
        return developerService
                .createExternalDeveloper(
                        CreateExternalDeveloperParam.builder()
                                .provider(PROVIDER)
                                .subject(subject)
                                .displayName(displayName)
                                .authType(DeveloperAuthType.LDAP)
                                .build())
                .getDeveloperId();
    }

    private static final class ResolvedUser {
        private final String dn;
        private final String displayName;

        ResolvedUser(String dn, String displayName) {
            this.dn = dn;
            this.displayName = displayName;
        }

        String getDn() {
            return dn;
        }

        String getDisplayName() {
            return displayName;
        }
    }
}

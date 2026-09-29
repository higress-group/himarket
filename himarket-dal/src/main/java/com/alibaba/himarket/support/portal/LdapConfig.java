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

package com.alibaba.himarket.support.portal;

import lombok.Data;

/**
 * Portal-level LDAP authentication configuration (T-34). Stored inside
 * PortalSettingConfig; absent/empty ldap.url disables the LDAP login path.
 */
@Data
public class LdapConfig {

    /** e.g. ldap://10.2.7.31:389 */
    private String url;

    /** Admin/bind DN used for the user search, e.g. cn=root */
    private String bindDn;

    /** Admin/bind password (encrypted at rest by the existing portal config cipher). */
    private String bindPassword;

    /**
     * Search base for locating users, e.g. dc=lyf,dc=intl. When empty, userDnPattern
     * is used directly and no search is performed.
     */
    private String userSearchBase;

    /** LDAP filter with {0} placeholder for the login username, e.g. (uid={0}). */
    private String userSearchFilter;

    /**
     * Direct DN pattern with {0} placeholder, e.g. uid={0},ou=people,dc=lyf,dc=intl.
     * Used when userSearchBase is empty (skips the admin search round-trip).
     */
    private String userDnPattern;

    /** Attribute mapped to the developer display name (optional). */
    private String displayNameAttribute;

    /** enabled reports whether LDAP login is configured. */
    public boolean enabled() {
        return url != null
                && !url.isBlank()
                && ((userDnPattern != null && !userDnPattern.isBlank())
                        || (userSearchBase != null
                                && !userSearchBase.isBlank()
                                && userSearchFilter != null
                                && !userSearchFilter.isBlank()));
    }
}

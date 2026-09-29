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

package com.alibaba.himarket.core.ldap;

import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

/**
 * Thin JNDI LDAP client (zero external dependencies). The {@code connect} field is a
 * function so unit tests can substitute a fake DirContext without a real LDAP server.
 */
public class LdapClient {

    public interface Connect {
        DirContext open(String url, String principalDn, String credentials) throws Exception;
    }

    private final Connect connect;

    public LdapClient() {
        this(
                (url, dn, pwd) -> {
                    Hashtable<String, String> env = new Hashtable<>();
                    env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
                    env.put(Context.PROVIDER_URL, url);
                    env.put(Context.SECURITY_AUTHENTICATION, "simple");
                    env.put(Context.SECURITY_PRINCIPAL, dn);
                    env.put(Context.SECURITY_CREDENTIALS, pwd);
                    return new InitialDirContext(env);
                });
    }

    /** Test constructor. */
    public LdapClient(Connect connect) {
        this.connect = connect;
    }

    /**
     * Authenticates by binding as the resolved user DN. Returns true when bind succeeds.
     */
    public boolean bind(String url, String userDn, String password) {
        if (userDn == null || userDn.isEmpty() || password == null || password.isEmpty()) {
            return false;
        }
        DirContext ctx = null;
        try {
            ctx = connect.open(url, userDn, password);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            close(ctx);
        }
    }

    /**
     * Binds as the admin account, searches for the login user, and returns the matching
     * entry (DN + attributes), or null when not found / search fails.
     */
    public LdapEntry search(
            String url,
            String adminDn,
            String adminPassword,
            String base,
            String filter,
            String username) {
        String resolvedFilter = filter.replace("{0}", escapeFilterValue(username));
        DirContext ctx = null;
        try {
            ctx = connect.open(url, adminDn, adminPassword);
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            controls.setCountLimit(1);
            NamingEnumeration<SearchResult> results = ctx.search(base, resolvedFilter, controls);
            if (!results.hasMore()) {
                return null;
            }
            SearchResult result = results.next();
            return new LdapEntry(result.getNameInNamespace(), result.getAttributes());
        } catch (Exception e) {
            return null;
        } finally {
            close(ctx);
        }
    }

    /**
     * A resolved LDAP entry: its full DN plus attributes. Decoupled from JNDI so the search
     * result can be unit-tested without a real directory server.
     */
    public static final class LdapEntry {
        private final String dn;
        private final Attributes attributes;

        public LdapEntry(String dn, Attributes attributes) {
            this.dn = dn;
            this.attributes = attributes;
        }

        public String getDn() {
            return dn;
        }

        public Attributes getAttributes() {
            return attributes;
        }
    }

    /**
     * RFC 4515 filter value escaping to prevent LDAP injection via the login name.
     */
    static String escapeFilterValue(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '*':
                    escaped.append("\\2a");
                    break;
                case '(':
                    escaped.append("\\28");
                    break;
                case ')':
                    escaped.append("\\29");
                    break;
                case '\\':
                    escaped.append("\\5c");
                    break;
                case 0:
                    escaped.append("\\00");
                    break;
                default:
                    escaped.append(ch);
            }
        }
        return escaped.toString();
    }

    private void close(DirContext ctx) {
        if (ctx != null) {
            try {
                ctx.close();
            } catch (Exception ignore) {
                // noop
            }
        }
    }
}

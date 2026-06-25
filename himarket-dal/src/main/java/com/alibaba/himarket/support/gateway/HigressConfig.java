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

package com.alibaba.himarket.support.gateway;

import com.alibaba.himarket.support.common.Encrypted;
import com.alibaba.himarket.support.common.Strings;
import java.net.URI;
import java.util.Locale;
import lombok.Data;

@Data
public class HigressConfig {

    private String address;

    private String username;

    @Encrypted private String password;

    /**
     * Higress gateway address
     */
    private String gatewayAddress;

    public String buildUniqueKey() {
        return String.join(
                ":",
                String.valueOf(address),
                String.valueOf(username),
                String.valueOf(password),
                String.valueOf(gatewayAddress));
    }

    public boolean validate() {
        if (Strings.isBlank(address) || Strings.isBlank(username) || Strings.isBlank(password)) {
            return false;
        }

        address = normalizeAddress(address);
        if (!isValidUrl(address)) {
            return false;
        }

        if (Strings.isNotBlank(gatewayAddress)) {
            gatewayAddress = normalizeAddress(gatewayAddress);
            return isValidUrl(gatewayAddress);
        }
        return true;
    }

    public boolean matchesGatewayIdentity(HigressConfig other) {
        String normalizedAddress = normalizeAddress(address);
        return other != null
                && Strings.isNotBlank(normalizedAddress)
                && Strings.equals(normalizedAddress, normalizeAddress(other.getAddress()));
    }

    private static String normalizeAddress(String address) {
        String trimmedAddress = address == null ? null : address.trim();
        if (Strings.isBlank(trimmedAddress)) {
            return null;
        }

        String addressWithScheme =
                startsWithHttpScheme(trimmedAddress) ? trimmedAddress : "http://" + trimmedAddress;
        return removeTrailingSlash(addressWithScheme);
    }

    private static boolean startsWithHttpScheme(String value) {
        String lowerCaseValue = value.toLowerCase(Locale.ROOT);
        return lowerCaseValue.startsWith("http://") || lowerCaseValue.startsWith("https://");
    }

    private static String removeTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static boolean isValidUrl(String value) {
        try {
            new URI(value).toURL();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

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

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import com.alibaba.himarket.support.common.Encrypted;
import lombok.Data;

@Data
public class HigressConfig {

    private String address;

    private String username;

    @Encrypted
    private String password;

    /**
     * Higress gateway address
     */
    private String gatewayAddress;

    public String buildUniqueKey() {
        return StrUtil.join(":", address, username, password, gatewayAddress);
    }

    public boolean validate() {
        if (StrUtil.isBlank(address) || StrUtil.isBlank(username) || StrUtil.isBlank(password)) {
            return false;
        }

        try {
            // Normalize address and gatewayAddress
            if (!URLUtil.url(address).getProtocol().contains("http")) {
                address = "http://" + address;
            }

            if (StrUtil.isNotBlank(gatewayAddress) &&
                    !URLUtil.url(gatewayAddress).getProtocol().contains("http")) {
                gatewayAddress = "http://" + gatewayAddress;
            }
        } catch (Exception e) {
            return false;
        }

        return true;
    }
}

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

package com.alibaba.himarket.support.common;

import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Encryptor {

    private static String ROOT_KEY;

    private static AES getAes() {
        if (StrUtil.isBlank(ROOT_KEY)) {
            ROOT_KEY = SpringUtil.getProperty("encryption.root-key");
        }

        if (StrUtil.isBlank(ROOT_KEY)) {
            throw new RuntimeException("Encryption root key is not set");
        }

        return SecureUtil.aes(ROOT_KEY.getBytes(CharsetUtil.CHARSET_UTF_8));
    }

    public static String encrypt(String value) {
        if (StrUtil.isBlank(value)) {
            return value;
        }
        try {
            return getAes().encryptHex(value);
        } catch (Exception e) {
            log.error("Encrypt failed: {}", e.getMessage());
            return value;
        }
    }

    public static String decrypt(String value) {
        if (StrUtil.isBlank(value)) {
            return value;
        }
        try {
            return getAes().decryptStr(value);
        } catch (Exception e) {
            log.error("Decrypt failed: {}", e.getMessage());
            return value;
        }
    }
}

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
import cn.hutool.crypto.Mode;
import cn.hutool.crypto.Padding;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.extra.spring.SpringUtil;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Encryptor {

    private static String ROOT_KEY;

    private static byte[] getRootKeyBytes() {
        if (StrUtil.isBlank(ROOT_KEY)) {
            ROOT_KEY = SpringUtil.getProperty("encryption.root-key");
        }
        if (StrUtil.isBlank(ROOT_KEY)) {
            throw new RuntimeException("Encryption root key is not set");
        }
        return ROOT_KEY.getBytes(CharsetUtil.CHARSET_UTF_8);
    }

    /**
     * CBC mode for new encrypted data. The IV is derived from the key MD5 digest.
     */
    private static AES getCbcAes() {
        byte[] keyBytes = getRootKeyBytes();
        byte[] iv = Arrays.copyOf(SecureUtil.md5().digest(keyBytes), 16);
        return new AES(Mode.CBC, Padding.PKCS5Padding, keyBytes, iv);
    }

    /**
     * ECB mode for legacy data decryption.
     */
    private static AES getEcbAes() {
        return SecureUtil.aes(getRootKeyBytes());
    }

    public static String encrypt(String value) {
        if (StrUtil.isBlank(value)) {
            return value;
        }
        try {
            return getCbcAes().encryptHex(value);
        } catch (Exception e) {
            throw new RuntimeException("Encrypt failed, refusing to store plaintext", e);
        }
    }

    public static String decrypt(String value) {
        if (StrUtil.isBlank(value)) {
            return value;
        }
        // Prefer CBC decryption for the new format.
        try {
            return getCbcAes().decryptStr(value);
        } catch (Exception ignored) {
            // Fall back to ECB for legacy data when CBC decryption fails.
        }
        try {
            return getEcbAes().decryptStr(value);
        } catch (Exception e) {
            log.warn(
                    "Decrypt failed, returning original value for possible legacy plaintext,"
                            + " errorMessage={}",
                    e.getMessage());
            return value;
        }
    }
}

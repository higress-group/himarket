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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Encryptor implements EnvironmentAware {

    private static final String AES = "AES";
    private static final String CBC_TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final String ECB_TRANSFORMATION = "AES/ECB/PKCS5Padding";

    private static String rootKey;

    @Override
    public void setEnvironment(Environment environment) {
        rootKey = environment.getProperty("encryption.root-key");
    }

    private static byte[] getRootKeyBytes() {
        if (Strings.isBlank(rootKey)) {
            throw new RuntimeException("Encryption root key is not set");
        }
        return rootKey.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * CBC mode for new encrypted data. The IV is derived from the key MD5 digest.
     */
    private static Cipher getCbcCipher(int mode) throws Exception {
        byte[] keyBytes = getRootKeyBytes();
        Cipher cipher = Cipher.getInstance(CBC_TRANSFORMATION);
        cipher.init(mode, new SecretKeySpec(keyBytes, AES), new IvParameterSpec(md5(keyBytes)));
        return cipher;
    }

    /**
     * ECB mode for legacy data decryption.
     */
    private static Cipher getEcbCipher(int mode) throws Exception {
        Cipher cipher = Cipher.getInstance(ECB_TRANSFORMATION);
        cipher.init(mode, new SecretKeySpec(getRootKeyBytes(), AES));
        return cipher;
    }

    public static String encrypt(String value) {
        if (Strings.isBlank(value)) {
            return value;
        }
        try {
            byte[] encrypted =
                    getCbcCipher(Cipher.ENCRYPT_MODE)
                            .doFinal(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Encrypt failed, refusing to store plaintext", e);
        }
    }

    public static String decrypt(String value) {
        if (Strings.isBlank(value)) {
            return value;
        }
        // Prefer CBC decryption for the new format.
        try {
            byte[] decrypted =
                    getCbcCipher(Cipher.DECRYPT_MODE).doFinal(HexFormat.of().parseHex(value));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // Fall back to ECB for legacy data when CBC decryption fails.
        }
        try {
            byte[] decrypted =
                    getEcbCipher(Cipher.DECRYPT_MODE).doFinal(HexFormat.of().parseHex(value));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn(
                    "Decrypt failed, returning original value for possible legacy plaintext,"
                            + " errorMessage={}",
                    e.getMessage());
            return value;
        }
    }

    private static byte[] md5(byte[] value) {
        try {
            return MessageDigest.getInstance("MD5").digest(value);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is not available", e);
        }
    }
}

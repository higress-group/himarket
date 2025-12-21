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

package com.alibaba.himarket.service;

import com.alibaba.himarket.support.enums.PublicKeyFormat;
import com.alibaba.himarket.support.portal.OAuth2Config;
import com.alibaba.himarket.support.portal.OidcConfig;
import java.security.PublicKey;
import java.util.List;

public interface IdpService {

    /**
     * 验证OIDC配置
     *
     * @param oidcConfigs
     */
    void validateOidcConfigs(List<OidcConfig> oidcConfigs);

    /**
     * 验证OAuth2配置
     *
     * @param oauth2Configs
     */
    void validateOAuth2Configs(List<OAuth2Config> oauth2Configs);

    /**
     * 加载JWT公钥
     *
     * @param format
     * @param publicKey
     * @return
     */
    PublicKey loadPublicKey(PublicKeyFormat format, String publicKey);
}

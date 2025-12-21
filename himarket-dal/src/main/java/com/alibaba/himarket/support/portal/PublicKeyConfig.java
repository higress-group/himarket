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

import com.alibaba.himarket.support.enums.PublicKeyFormat;
import lombok.Data;

@Data
public class PublicKeyConfig {

    /** 公钥ID */
    private String kid;

    /** 公钥格式：PEM或JWK */
    private PublicKeyFormat format;

    /** 签名算法：RS256，ES256，PS256等 */
    private String algorithm;

    /** 公钥内容，PEM或JWK JSON字符串 */
    private String value;
}

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

package com.alibaba.himarket.core.constant;

public class JwtConstants {

    // region JWT Header

    /** 算法字段 */
    public static final String HEADER_ALG = "alg";

    /** 类型字段 */
    public static final String HEADER_TYP = "typ";

    /** 密钥ID字段 */
    public static final String HEADER_KID = "kid";

    // endregion

    // region JWT Payload

    public static final String PAYLOAD_PROVIDER = "provider";

    /** 过期时间 */
    public static final String PAYLOAD_EXP = "exp";

    /** 签发时间 */
    public static final String PAYLOAD_IAT = "iat";

    /** JWT唯一标识 */
    public static final String PAYLOAD_JTI = "jti";

    /** 签发者 */
    public static final String PAYLOAD_ISS = "iss";

    /** 主题 */
    public static final String PAYLOAD_SUB = "sub";

    /** 受众 */
    public static final String PAYLOAD_AUD = "aud";

    /** 门户ID */
    public static final String PAYLOAD_PORTAL = "portal";

    // endregion

    // region 自定义Payload

    /** 用户ID（默认身份映射字段） */
    public static final String PAYLOAD_USER_ID = "userId";

    /** 用户名（默认身份映射字段） */
    public static final String PAYLOAD_USER_NAME = "name";

    /** 邮箱（默认身份映射字段） */
    public static final String PAYLOAD_EMAIL = "email";

    // endregion

    // region OAuth2相关常量

    /** JWT Bearer Grant类型 */
    public static final String JWT_BEARER_GRANT_TYPE =
            "urn:ietf:params:oauth:grant-type:jwt-bearer";

    /** Token类型 */
    public static final String TOKEN_TYPE_BEARER = "Bearer";

    /** 默认Token过期时间（秒） */
    public static final int DEFAULT_TOKEN_EXPIRES_IN = 3600;

    /** JWT Token类型 */
    public static final String JWT_TOKEN_TYPE = "JWT";

    // endregion
}

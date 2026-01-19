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

    /**
     * Algorithm
     */
    public static final String HEADER_ALG = "alg";

    /**
     * Type
     */
    public static final String HEADER_TYP = "typ";

    /**
     * Key ID
     */
    public static final String HEADER_KID = "kid";

    // endregion

    // region JWT Payload

    public static final String PAYLOAD_PROVIDER = "provider";

    /**
     * Expiration
     */
    public static final String PAYLOAD_EXP = "exp";

    /**
     * Issued at
     */
    public static final String PAYLOAD_IAT = "iat";

    /**
     * JWT ID
     */
    public static final String PAYLOAD_JTI = "jti";

    /**
     * Issuer
     */
    public static final String PAYLOAD_ISS = "iss";

    /**
     * Subject
     */
    public static final String PAYLOAD_SUB = "sub";

    /**
     * Audience
     */
    public static final String PAYLOAD_AUD = "aud";

    /**
     * Portal ID
     */
    public static final String PAYLOAD_PORTAL = "portal";

    // endregion

    // region Custom Payload

    /**
     * User ID (default identity mapping)
     */
    public static final String PAYLOAD_USER_ID = "userId";

    /**
     * User name (default identity mapping)
     */
    public static final String PAYLOAD_USER_NAME = "name";

    /**
     * Email (default identity mapping)
     */
    public static final String PAYLOAD_EMAIL = "email";

    // endregion

    // region OAuth2 Constants

    /**
     * JWT Bearer grant type
     */
    public static final String JWT_BEARER_GRANT_TYPE =
            "urn:ietf:params:oauth:grant-type:jwt-bearer";

    /**
     * Token type
     */
    public static final String TOKEN_TYPE_BEARER = "Bearer";

    /**
     * Default token expiration (seconds)
     */
    public static final int DEFAULT_TOKEN_EXPIRES_IN = 3600;

    /**
     * JWT token type
     */
    public static final String JWT_TOKEN_TYPE = "JWT";

    // endregion
}

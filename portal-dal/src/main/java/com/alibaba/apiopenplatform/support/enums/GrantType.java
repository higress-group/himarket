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


package com.alibaba.apiopenplatform.support.enums;

import lombok.Getter;

/**
 * @author zh
 */
@Getter
public enum GrantType {

    /**
     * 授权码模式
     */
    AUTHORIZATION_CODE("authorization_code"),


    /**
     * JWT断言，OAuth2.0标准拓展
     */
    JWT_BEARER("urn:ietf:params:oauth:grant-type:jwt-bearer"),

    ;

    private final String type;

    GrantType(String type) {
        this.type = type;
    }
}

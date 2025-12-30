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

package com.alibaba.himarket.support.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum GatewayType {

    /**
     * Native API Gateway
     */
    APIG_API("API"),

    /**
     * AI Gateway
     */
    APIG_AI("AI"),

    /**
     * ADP AI Gateway
     */
    ADP_AI_GATEWAY("ADP_AI_GATEWAY"),

    /**
     * Apsara AI Gateway
     */
    APSARA_GATEWAY("APSARA_GATEWAY"),

    /**
     * Higress
     */
    HIGRESS("Higress"),

    /** SOFA Higress */
    SOFA_HIGRESS("SOFA_HIGRESS"),
    ;

    private final String type;

    public boolean isHigress() {
        return this == HIGRESS;
    }

    public boolean isAPIG() {
        return this == APIG_API || this == APIG_AI;
    }

    public boolean isAIGateway() {
        return this == APIG_AI;
    }

    public boolean isAdpAIGateway() {
        return this == ADP_AI_GATEWAY;
    }

    public boolean isApsaraGateway() {
        return this == APSARA_GATEWAY;
    }

    public boolean isSofaHigress() {
        return this == SOFA_HIGRESS;
    }
}

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

/** 数据来源类型枚举 */
public enum SourceType {

    /** 来自Gateway (Higress, APIG等) */
    GATEWAY,

    /** 来自Nacos注册中心 */
    NACOS;
    /**
     * 来自Nacos注册中心
     */
    NACOS,

    /**
     * Himarket 托管的 API Definition
     */
    MANAGED;

    public boolean isGateway() {
        return this == GATEWAY;
    }

    public boolean isNacos() {
        return this == NACOS;
    }
}


    public boolean isManaged() {
        return this == MANAGED;
    }
}
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

/**
 * Hosting type of an MCP Server endpoint.
 */
public enum McpHostingType {
    /**
     * Hosted by a sandbox.
     */
    SANDBOX,
    /**
     * Hosted by a gateway.
     */
    GATEWAY,
    /**
     * Hosted by Nacos.
     */
    NACOS,
    /**
     * Direct connection with a user-provided URL.
     */
    DIRECT;

    /**
     * Infers the default hosting type from the MCP origin.
     */
    public static McpHostingType fromOrigin(McpOrigin origin) {
        if (origin == null) return DIRECT;
        return switch (origin) {
            case GATEWAY -> GATEWAY;
            case NACOS -> NACOS;
            default -> DIRECT;
        };
    }
}

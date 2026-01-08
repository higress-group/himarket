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

package com.alibaba.himarket.core.utils;

import cn.hutool.core.lang.ObjectId;

/**
 * ID Generator
 *
 * <p>Format: prefix + 24-char string
 *
 * <p>Supported ID types: - Portal ID: portal-xxxxxx - API Product ID: product-xxxxxx - Developer
 * ID: dev-xxxxxx - Administrator ID: admin-xxxxxx - Category ID: category-xxxxxx
 *
 * <p>Note: - API ID is synced from gateway
 */
public class IdGenerator {

    private static final String PORTAL_PREFIX = "portal-";
    private static final String API_PRODUCT_PREFIX = "product-";
    private static final String DEVELOPER_PREFIX = "dev-";
    private static final String CONSUMER_PREFIX = "consumer-";
    private static final String ADMINISTRATOR_PREFIX = "admin-";
    private static final String NACOS_PREFIX = "nacos-";
    private static final String HIGRESS_PREFIX = "higress-";
    private static final String CATEGORY_PREFIX = "category-";

    private static final String SESSION_PREFIX = "session-";
    private static final String CHAT_PREFIX = "chat-";
    private static final String SUBSCRIPTION_PREFIX = "subscription-";
    private static final String PUBLICATION_PREFIX = "publication-";
    private static final String LIKE_PREFIX = "like-";

    public static String genHigressGatewayId() {
        return HIGRESS_PREFIX + ObjectId.next();
    }

    public static String genPortalId() {
        return PORTAL_PREFIX + ObjectId.next();
    }

    public static String genApiProductId() {
        return API_PRODUCT_PREFIX + ObjectId.next();
    }

    public static String genDeveloperId() {
        return DEVELOPER_PREFIX + ObjectId.next();
    }

    public static String genConsumerId() {
        return CONSUMER_PREFIX + ObjectId.next();
    }

    public static String genAdministratorId() {
        return ADMINISTRATOR_PREFIX + ObjectId.next();
    }

    public static String genCategoryId() {
        return CATEGORY_PREFIX + ObjectId.next();
    }

    public static String genNacosId() {
        return NACOS_PREFIX + ObjectId.next();
    }

    public static String genSessionId() {
        return SESSION_PREFIX + ObjectId.next();
    }

    public static String genChatId() {
        return CHAT_PREFIX + ObjectId.next();
    }

    public static String genSubscriptionId() {
        return SUBSCRIPTION_PREFIX + ObjectId.next();
    }

    public static String genPublicationId() {
        return PUBLICATION_PREFIX + ObjectId.next();
    }

    public static String genIdWithPrefix(String prefix) {
        return prefix + ObjectId.next();
    }

    public static String genLikeId() {
        return LIKE_PREFIX + ObjectId.next();
    }
}

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

/** API 发布状态枚举 */
public enum PublishStatus {

    /** 活跃 */
    ACTIVE,

    /** 非活跃 */
    INACTIVE,

    /** 发布中 */
    PUBLISHING,

    /** 取消发布中 */
    UNPUBLISHING,

    /** 发布失败 */
    PUBLISH_FAILED,

    /** 下线失败 */
    UNPUBLISH_FAILED,
    ;

    /**
     * Check if status is in processing state (publishing or unpublishing)
     */
    public boolean isProcessing() {
        return this == PUBLISHING || this == UNPUBLISHING;
    }

    /**
     * Check if status is active
     */
    public boolean isActive() {
        return this == ACTIVE;
    }

    /**
     * Check if status is failed
     */
    public boolean isFailed() {
        return this == PUBLISH_FAILED || this == UNPUBLISH_FAILED;
    }

    /**
     * Check if this is a publish-related operation (not unpublish)
     */
    public boolean isPublishOperation() {
        return this == PUBLISHING || this == ACTIVE || this == PUBLISH_FAILED;
    }

    /**
     * Check if this is an unpublish-related operation
     */
    public boolean isUnpublishOperation() {
        return this == UNPUBLISHING || this == INACTIVE || this == UNPUBLISH_FAILED;
    }

    /**
     * Check if can perform new publish operation
     */
    public boolean canPublish() {
        return this == INACTIVE || this == PUBLISH_FAILED || this == UNPUBLISH_FAILED;
    }
}

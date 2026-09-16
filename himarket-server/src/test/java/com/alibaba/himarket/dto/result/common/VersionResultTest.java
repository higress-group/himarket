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

package com.alibaba.himarket.dto.result.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class VersionResultTest {

    private static final String APPROVED_PIPELINE = "{\"status\":\"APPROVED\"}";

    @Test
    void approvedReviewingVersionDefaultsToOnlineForCompatibility() {
        assertEquals("online", VersionResult.resolveStatus("reviewing", APPROVED_PIPELINE));
    }

    @Test
    void approvedReviewingVersionCanRemainPendingOnlineForAiRegistry() {
        assertEquals(
                "approved", VersionResult.resolveStatus("reviewing", APPROVED_PIPELINE, false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void reviewedApprovalAlwaysRequiresExplicitPublish(boolean approvedAsOnline) {
        assertEquals(
                "approved",
                VersionResult.resolveStatus("reviewed", APPROVED_PIPELINE, approvedAsOnline));
    }

    @ParameterizedTest
    @CsvSource({
        "reviewing, APPROVED, approved",
        "reviewed, APPROVED, approved",
        "reviewing, REJECTED, rejected",
        "reviewed, REJECTED, rejected",
        "reviewing, IN_PROGRESS, reviewing",
        "reviewed, IN_PROGRESS, reviewed",
        "online, APPROVED, online",
        "offline, APPROVED, offline",
        "draft, APPROVED, draft",
        "online, REJECTED, online",
        "offline, REJECTED, offline",
        "draft, REJECTED, draft"
    })
    void resolvesReviewResultWithoutChangingOtherLifecycleStates(
            String rawStatus, String pipelineStatus, String expectedStatus) {
        assertEquals(
                expectedStatus,
                VersionResult.resolveStatus(
                        rawStatus, "{\"status\":\"" + pipelineStatus + "\"}", false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"reviewing", "reviewed"})
    void preservesStatusWhenReviewResultIsUnavailable(String rawStatus) {
        assertEquals(rawStatus, VersionResult.resolveStatus(rawStatus, null, false));
        assertEquals(rawStatus, VersionResult.resolveStatus(rawStatus, "invalid-json", false));
        assertEquals(rawStatus, VersionResult.resolveStatus(rawStatus, "{}", false));
    }
}

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

package com.alibaba.himarket.service.hicoding.sandbox.init;

import java.time.Instant;

public record InitEvent(Instant timestamp, String phase, EventType type, String message) {

    public enum EventType {
        PHASE_START,
        PHASE_COMPLETE,
        PHASE_SKIP,
        PHASE_RETRY,
        PHASE_FAIL,
        VERIFY_PASS,
        VERIFY_FAIL,
        WARNING
    }
}

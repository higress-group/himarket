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

package com.alibaba.himarket.service;

import com.alibaba.himarket.dto.params.coding.CreateCodingSessionParam;
import com.alibaba.himarket.dto.params.coding.UpdateCodingSessionParam;
import com.alibaba.himarket.dto.result.coding.CodingSessionResult;
import java.util.List;

public interface CodingSessionService {

    /**
     * Create a new coding session
     */
    CodingSessionResult createSession(CreateCodingSessionParam param);

    /**
     * List all coding sessions for the current user, ordered by updated_at descending.
     * The returned results do NOT include sessionData field.
     */
    List<CodingSessionResult> listSessions();

    /**
     * Get a single coding session detail (including sessionData)
     */
    CodingSessionResult getSession(String sessionId);

    /**
     * Update a coding session
     */
    CodingSessionResult updateSession(String sessionId, UpdateCodingSessionParam param);

    /**
     * Delete a coding session
     */
    void deleteSession(String sessionId);
}

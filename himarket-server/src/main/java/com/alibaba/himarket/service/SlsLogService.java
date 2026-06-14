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

import com.alibaba.himarket.dto.params.sls.GenericSlsQueryRequest;
import com.alibaba.himarket.dto.params.sls.GenericSlsQueryResponse;
import com.alibaba.himarket.dto.params.sls.SlsCheckLogstoreRequest;
import com.alibaba.himarket.dto.params.sls.SlsCheckProjectRequest;
import com.alibaba.himarket.dto.params.sls.SlsCommonQueryRequest;

/**
 * Generic SLS log query service with configurable authentication and scenario queries.
 */
public interface SlsLogService {

    /**
     * Executes a generic SLS SQL query.
     *
     * @param request query request
     * @return query response
     */
    GenericSlsQueryResponse executeQuery(GenericSlsQueryRequest request);

    /**
     * Executes a common SLS SQL query.
     *
     * @param request query request
     * @return query response
     */
    GenericSlsQueryResponse executeQuery(SlsCommonQueryRequest request);

    /**
     * Checks whether the SLS project exists.
     *
     * @param request query request with authentication context
     * @return whether the project exists
     */
    Boolean checkProjectExists(SlsCheckProjectRequest request);

    /**
     * Checks whether the SLS logstore exists.
     *
     * @param request query request with authentication context
     * @return whether the logstore exists
     */
    Boolean checkLogstoreExists(SlsCheckLogstoreRequest request);

    /**
     * Updates indexes for the configured global log logstore.
     *
     * @param userId user ID used when STS authentication is enabled
     */
    void updateLogIndex(String userId);
}

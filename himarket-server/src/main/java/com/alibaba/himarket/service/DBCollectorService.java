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

/**
 * DBCollector log query service backed by the access_logs database table.
 */
public interface DBCollectorService {

    /**
     * Executes a scenario statistics query against access_logs.
     *
     * @param request query request
     * @return query response
     */
    GenericSlsQueryResponse executeQuery(GenericSlsQueryRequest request);
}

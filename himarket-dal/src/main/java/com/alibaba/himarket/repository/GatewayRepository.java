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

package com.alibaba.himarket.repository;

import com.alibaba.himarket.entity.Gateway;
import java.util.Optional;

public interface GatewayRepository extends BaseRepository<Gateway, Long> {

    /**
     * Find gateway by gateway ID
     *
     * @param gatewayId the gateway ID
     * @return the gateway if found
     */
    Optional<Gateway> findByGatewayId(String gatewayId);

    /**
     * Find gateway by gateway name
     *
     * @param gatewayName the gateway name
     * @return the gateway if found
     */
    Optional<Gateway> findByGatewayName(String gatewayName);
}

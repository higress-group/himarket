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

import com.alibaba.himarket.entity.Developer;
import java.util.List;
import java.util.Optional;

public interface DeveloperRepository extends BaseRepository<Developer, Long> {

    /**
     * Find developer by developer ID
     *
     * @param developerId the developer ID
     * @return the developer if found
     */
    Optional<Developer> findByDeveloperId(String developerId);

    /**
     * Find all developers by portal ID
     *
     * @param portalId the portal ID
     * @return the list of developers
     */
    List<Developer> findByPortalId(String portalId);

    /**
     * Find developer by portal ID and username
     *
     * @param portalId the portal ID
     * @param username the username
     * @return the developer if found
     */
    Optional<Developer> findByPortalIdAndUsername(String portalId, String username);
}

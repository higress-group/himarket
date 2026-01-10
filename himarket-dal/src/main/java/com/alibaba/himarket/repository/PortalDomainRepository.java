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

import com.alibaba.himarket.entity.PortalDomain;
import java.util.List;
import java.util.Optional;

public interface PortalDomainRepository extends BaseRepository<PortalDomain, Long> {

    /**
     * Find portal domain by domain
     *
     * @param domain the domain name
     * @return the portal domain if found
     */
    Optional<PortalDomain> findByDomain(String domain);

    /**
     * Find portal domain by portal ID and domain
     *
     * @param portalId the portal ID
     * @param domain the domain name
     * @return the portal domain if found
     */
    Optional<PortalDomain> findByPortalIdAndDomain(String portalId, String domain);

    /**
     * Find all portal domains by portal ID
     *
     * @param portalId the portal ID
     * @return the list of portal domains
     */
    List<PortalDomain> findAllByPortalId(String portalId);

    /**
     * Find all portal domains by portal IDs
     *
     * @param portalIds the list of portal IDs
     * @return the list of portal domains
     */
    List<PortalDomain> findAllByPortalIdIn(List<String> portalIds);

    /**
     * Delete all portal domains by portal ID
     *
     * @param portalId the portal ID
     */
    void deleteAllByPortalId(String portalId);
}

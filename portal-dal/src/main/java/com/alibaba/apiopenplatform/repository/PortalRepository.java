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

package com.alibaba.apiopenplatform.repository;

import com.alibaba.apiopenplatform.entity.Portal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface PortalRepository extends BaseRepository<Portal, Long> {

    Optional<Portal> findFirstByOrderByIdAsc();

    Optional<Portal> findByPortalIdAndAdminId(String portalId, String adminId);

    Optional<Portal> findByPortalId(String portalId);

    Optional<Portal> findByNameAndAdminId(String name, String adminId);

    Optional<Portal> findByName(String name);

    Page<Portal> findByAdminId(String adminId, Pageable pageable);
}

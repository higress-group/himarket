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

import com.alibaba.himarket.entity.Administrator;
import java.util.Optional;

public interface AdministratorRepository extends BaseRepository<Administrator, Long> {

    /**
     * Find administrator by admin ID
     *
     * @param adminId the admin ID
     * @return the administrator if found
     */
    Optional<Administrator> findByAdminId(String adminId);

    /**
     * Find administrator by username
     *
     * @param username the username
     * @return the administrator if found
     */
    Optional<Administrator> findByUsername(String username);
}

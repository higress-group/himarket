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
import org.springframework.data.jpa.repository.JpaRepository;

/** 管理员数据访问接口，提供管理员相关的数据库操作 */
public interface AdministratorRepository extends JpaRepository<Administrator, Long> {
    Optional<Administrator> findByAdminId(String adminId);

    Optional<Administrator> findByUsername(String username);
}

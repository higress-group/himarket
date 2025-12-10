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

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.lang.NonNull;

/**
 * 基础数据访问接口，提供通用的数据库操作方法
 *
 * @param <D> 实体类型(Domain/Entity)
 * @param <I> 主键类型(ID)
 */
@NoRepositoryBean
public interface BaseRepository<D, I> extends JpaRepository<D, I>, JpaSpecificationExecutor<D> {

    /**
     * 根据ID集合批量查询实体列表
     *
     * @param ids
     * @param sort
     * @return
     */
    List<D> findAllByIdIn(@NonNull Collection<I> ids, @NonNull Sort sort);
}

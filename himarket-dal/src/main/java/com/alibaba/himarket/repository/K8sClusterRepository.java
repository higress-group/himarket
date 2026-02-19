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

import com.alibaba.himarket.entity.K8sCluster;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * K8s 集群配置数据访问接口
 */
@Repository
public interface K8sClusterRepository extends BaseRepository<K8sCluster, Long> {

    /**
     * 根据配置 ID 查找集群
     *
     * @param configId 配置 ID
     * @return 集群配置
     */
    Optional<K8sCluster> findByConfigId(String configId);

    /**
     * 根据集群名称查找集群
     *
     * @param clusterName 集群名称
     * @return 集群配置
     */
    Optional<K8sCluster> findByClusterName(String clusterName);

    /**
     * 根据配置 ID 删除集群
     *
     * @param configId 配置 ID
     */
    void deleteByConfigId(String configId);

    /**
     * 检查配置 ID 是否存在
     *
     * @param configId 配置 ID
     * @return 是否存在
     */
    boolean existsByConfigId(String configId);
}

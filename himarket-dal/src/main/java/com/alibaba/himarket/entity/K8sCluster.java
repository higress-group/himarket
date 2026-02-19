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

package com.alibaba.himarket.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * K8s 集群配置实体。
 * 存储 kubeconfig 及集群元数据，支持多集群管理。
 */
@Entity
@Table(
        name = "k8s_cluster",
        uniqueConstraints = {
            @UniqueConstraint(
                    columnNames = {"config_id"},
                    name = "uk_config_id"),
        })
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class K8sCluster extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 配置唯一标识（UUID）
     */
    @Column(name = "config_id", length = 64, nullable = false)
    private String configId;

    /**
     * 集群名称（从 kubeconfig 中提取）
     */
    @Column(name = "cluster_name", length = 128, nullable = false)
    private String clusterName;

    /**
     * K8s API Server 地址
     */
    @Column(name = "server_url", length = 512, nullable = false)
    private String serverUrl;

    /**
     * kubeconfig 内容（YAML 格式，加密存储）
     */
    @Column(name = "kubeconfig", columnDefinition = "TEXT", nullable = false)
    private String kubeconfig;

    /**
     * 集群描述
     */
    @Column(name = "description", length = 512)
    private String description;
}

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

package com.alibaba.himarket.support.product;

import com.alibaba.himarket.support.enums.SkillRegistryType;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillConfig {

    /**
     * List of skill tags
     */
    private List<String> skillTags;

    /**
     * Download count
     */
    private Long downloadCount;

    /**
     * Skill registry backend type. Empty value is treated as NACOS for old data.
     */
    private SkillRegistryType registryType;

    /**
     * Associated Nacos instance ID (nacos_instance.nacos_id)
     */
    private String nacosId;

    /**
     * Associated AIRegistry config ID (airegistry_instance.airegistry_id)
     */
    @JsonProperty("airegistryId")
    private String aiRegistryId;

    /**
     * Skill registry namespace.
     */
    private String namespace;

    /**
     * Skill name (unique identifier)
     */
    private String skillName;
}

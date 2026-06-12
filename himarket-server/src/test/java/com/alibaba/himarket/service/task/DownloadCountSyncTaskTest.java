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

package com.alibaba.himarket.service.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.service.AiRegistrySkillService;
import com.alibaba.himarket.service.NacosService;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.enums.SkillRegistryType;
import com.alibaba.himarket.support.product.ProductFeature;
import com.alibaba.himarket.support.product.SkillConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DownloadCountSyncTaskTest {

    @Test
    void aiRegistrySkillDownloadCountsAreSyncedFromAiRegistry() {
        ProductRepository productRepository = mock(ProductRepository.class);
        NacosService nacosService = mock(NacosService.class);
        AiRegistrySkillService aiRegistrySkillService = mock(AiRegistrySkillService.class);
        SkillConfig skillConfig =
                SkillConfig.builder()
                        .registryType(SkillRegistryType.AIREGISTRY)
                        .aiRegistryId("airegistry-1")
                        .namespace("ns-prod")
                        .skillName("weather-skill")
                        .downloadCount(1L)
                        .build();
        Product product =
                Product.builder()
                        .productId("product-1")
                        .type(ProductType.AGENT_SKILL)
                        .feature(ProductFeature.builder().skillConfig(skillConfig).build())
                        .build();
        when(productRepository.findAllByType(ProductType.AGENT_SKILL)).thenReturn(List.of(product));
        when(productRepository.findAllByType(ProductType.WORKER)).thenReturn(List.of());
        when(aiRegistrySkillService.listSkillDownloadCounts("airegistry-1", "ns-prod"))
                .thenReturn(Map.of("weather-skill", 12L));

        new DownloadCountSyncTask(productRepository, nacosService, aiRegistrySkillService)
                .syncDownloadCounts();

        assertEquals(12L, skillConfig.getDownloadCount());
        verify(productRepository).save(product);
        verify(aiRegistrySkillService).listSkillDownloadCounts("airegistry-1", "ns-prod");
        verify(nacosService, never()).getAiMaintainerService("airegistry-1");
    }
}

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

package com.alibaba.himarket.controller;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alibaba.himarket.dto.result.airegistry.AiRegistryResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.service.AiRegistryService;
import com.alibaba.himarket.service.AiRegistrySkillService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class AiRegistryControllerTest {

    @Test
    void listSkillsChecksConfigOwnershipBeforeFetchingRemoteSkills() {
        AiRegistryService aiRegistryService = mock(AiRegistryService.class);
        AiRegistrySkillService aiRegistrySkillService = mock(AiRegistrySkillService.class);
        when(aiRegistryService.getAiRegistryInstance("airegistry-a"))
                .thenReturn(new AiRegistryResult());
        when(aiRegistrySkillService.listSkills("airegistry-a", "ns-a", 1, 10))
                .thenReturn(PageResult.empty(1, 10));
        AiRegistryController controller =
                new AiRegistryController(aiRegistryService, aiRegistrySkillService);

        controller.listSkills("airegistry-a", "ns-a", 1, 10);

        InOrder inOrder = inOrder(aiRegistryService, aiRegistrySkillService);
        inOrder.verify(aiRegistryService).getAiRegistryInstance("airegistry-a");
        inOrder.verify(aiRegistrySkillService).listSkills("airegistry-a", "ns-a", 1, 10);
    }
}

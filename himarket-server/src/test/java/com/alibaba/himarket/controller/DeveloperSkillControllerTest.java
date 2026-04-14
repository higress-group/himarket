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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.params.skill.CreateDeveloperSkillParam;
import com.alibaba.himarket.dto.params.skill.UpdateDeveloperSkillParam;
import com.alibaba.himarket.dto.result.skill.DeveloperSkillResult;
import com.alibaba.himarket.service.DeveloperSkillService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeveloperSkillControllerTest {

    @Mock private DeveloperSkillService developerSkillService;
    @Mock private ContextHolder contextHolder;

    @InjectMocks private DeveloperSkillController controller;

    private static final String DEV_ID = "dev-abc";

    private void stubUser() {
        when(contextHolder.getUser()).thenReturn(DEV_ID);
    }

    // ── listSkills ──────────────────────────────────────────────────────────

    @Test
    void listSkills_personal_delegatesToService() {
        stubUser();
        DeveloperSkillResult r = buildResult("p1", true, false);
        when(developerSkillService.listSkills(DEV_ID, "personal", null)).thenReturn(List.of(r));

        List<DeveloperSkillResult> result = controller.listSkills("personal", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isOwner()).isTrue();
    }

    @Test
    void listSkills_official_delegatesToService() {
        stubUser();
        DeveloperSkillResult r = buildResult("p2", false, true);
        when(developerSkillService.listSkills(DEV_ID, "official", null)).thenReturn(List.of(r));

        List<DeveloperSkillResult> result = controller.listSkills("official", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isOfficial()).isTrue();
    }

    @Test
    void listSkills_all_delegatesToService() {
        stubUser();
        when(developerSkillService.listSkills(DEV_ID, "all", null)).thenReturn(List.of());

        List<DeveloperSkillResult> result = controller.listSkills("all", null);

        assertThat(result).isEmpty();
    }

    @Test
    void listSkills_invalidType_throwsInvalidParameter() {
        assertThatThrownBy(() -> controller.listSkills("unknown", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("type must be");

        verifyNoInteractions(developerSkillService);
    }

    @Test
    void listSkills_withTag_passesTagToService() {
        stubUser();
        when(developerSkillService.listSkills(DEV_ID, "all", "nlp")).thenReturn(List.of());

        controller.listSkills("all", "nlp");

        verify(developerSkillService).listSkills(DEV_ID, "all", "nlp");
    }

    // ── createSkill ─────────────────────────────────────────────────────────

    @Test
    void createSkill_returnsResult() {
        stubUser();
        CreateDeveloperSkillParam param = new CreateDeveloperSkillParam();
        param.setName("my-skill");

        DeveloperSkillResult expected = buildResult("p3", true, false);
        when(developerSkillService.createSkill(DEV_ID, param)).thenReturn(expected);

        DeveloperSkillResult result = controller.createSkill(param);

        assertThat(result.isOwner()).isTrue();
    }

    // ── updateSkill ─────────────────────────────────────────────────────────

    @Test
    void updateSkill_returnsUpdated() {
        stubUser();
        UpdateDeveloperSkillParam param = new UpdateDeveloperSkillParam();
        param.setName("updated-skill");

        DeveloperSkillResult expected = buildResult("p4", true, false);
        when(developerSkillService.updateSkill(DEV_ID, "p4", param)).thenReturn(expected);

        DeveloperSkillResult result = controller.updateSkill("p4", param);

        assertThat(result.getName()).isEqualTo("skill-p4");
    }

    // ── deleteSkill ─────────────────────────────────────────────────────────

    @Test
    void deleteSkill_callsService() {
        stubUser();
        controller.deleteSkill("p5");

        verify(developerSkillService).deleteSkill(DEV_ID, "p5");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private DeveloperSkillResult buildResult(
            String productId, boolean isOwner, boolean isOfficial) {
        return DeveloperSkillResult.builder()
                .productId(productId)
                .name("skill-" + productId)
                .tags(List.of())
                .isOwner(isOwner)
                .isOfficial(isOfficial)
                .status("PENDING")
                .build();
    }
}

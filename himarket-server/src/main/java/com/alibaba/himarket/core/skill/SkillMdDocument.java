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

package com.alibaba.himarket.core.skill;

import java.util.Map;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SKILL.md 文档模型，包含 YAML frontmatter 和 Markdown body。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillMdDocument {

    /** YAML frontmatter 键值对 */
    private Map<String, Object> frontmatter;

    /** Markdown 正文 */
    private String body;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SkillMdDocument that = (SkillMdDocument) o;
        return Objects.equals(frontmatter, that.frontmatter) && Objects.equals(body, that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(frontmatter, body);
    }
}

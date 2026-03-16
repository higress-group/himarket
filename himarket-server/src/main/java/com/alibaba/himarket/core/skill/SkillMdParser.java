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

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * SKILL.md 解析器，处理 YAML frontmatter + Markdown 格式的解析与序列化。
 */
@Component
public class SkillMdParser {

    private static final String DELIMITER = "---";

    private final Yaml yaml = new Yaml();

    /**
     * 解析 SKILL.md 内容，提取 YAML frontmatter 和 Markdown body。
     *
     * @param content SKILL.md 原始文本
     * @return 解析后的 SkillMdDocument
     * @throws BusinessException 格式不合法时抛出 400 错误
     */
    public SkillMdDocument parse(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "SKILL.md 内容不能为空");
        }

        String trimmed = content.strip();

        if (!trimmed.startsWith(DELIMITER)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "缺少 YAML frontmatter 分隔符");
        }

        // 查找第二个 --- 分隔符（跳过第一个）
        int secondDelimiterIndex = trimmed.indexOf(DELIMITER, DELIMITER.length());
        if (secondDelimiterIndex < 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "缺少 YAML frontmatter 结束分隔符");
        }

        // 提取 frontmatter YAML 文本
        String yamlText = trimmed.substring(DELIMITER.length(), secondDelimiterIndex).strip();

        // 提取 body（第二个 --- 之后的内容）
        String body = trimmed.substring(secondDelimiterIndex + DELIMITER.length());
        // 去掉 body 开头的第一个换行符（如果有的话），保留其余格式
        if (body.startsWith("\n")) {
            body = body.substring(1);
        } else if (body.startsWith("\r\n")) {
            body = body.substring(2);
        }

        // 解析 YAML frontmatter
        Map<String, Object> frontmatter;
        try {
            Object parsed = yaml.load(yamlText);
            if (parsed == null) {
                frontmatter = new LinkedHashMap<>();
            } else if (parsed instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) parsed;
                frontmatter = new LinkedHashMap<>(map);
            } else {
                throw new BusinessException(
                        ErrorCode.INVALID_PARAMETER,
                        "YAML frontmatter 格式错误：期望键值对映射，实际为 " + parsed.getClass().getSimpleName());
            }
        } catch (YAMLException e) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "YAML frontmatter 解析失败：" + e.getMessage());
        }

        return new SkillMdDocument(frontmatter, body);
    }

    /**
     * 将 SkillMdDocument 序列化为 SKILL.md 格式。
     *
     * @param document SkillMdDocument 对象
     * @return SKILL.md 格式的文本
     */
    public String serialize(SkillMdDocument document) {
        if (document == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "SkillMdDocument 不能为空");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(DELIMITER).append("\n");

        Map<String, Object> frontmatter = document.getFrontmatter();
        if (frontmatter != null && !frontmatter.isEmpty()) {
            String yamlStr = yaml.dump(frontmatter);
            // SnakeYAML dump 末尾自带换行符
            sb.append(yamlStr);
        }

        sb.append(DELIMITER).append("\n");

        String body = document.getBody();
        if (body != null && !body.isEmpty()) {
            sb.append(body);
        }

        return sb.toString();
    }
}

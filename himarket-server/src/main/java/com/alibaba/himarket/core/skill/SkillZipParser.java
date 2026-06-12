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
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.yaml.snakeyaml.Yaml;

/**
 * Utility for parsing Skill ZIP packages, based on the Nacos server SkillZipParser.
 * Parses ZIP content, extracts SKILL.md, and builds a Skill object.
 */
public final class SkillZipParser {

    private SkillZipParser() {}

    private static final String SKILL_MD_FILE = "SKILL.md";
    private static final String MACOS_METADATA_PREFIX = "._";
    private static final Pattern YAML_FRONT_MATTER =
            Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n(.*)$", Pattern.DOTALL);

    private static final Set<String> BINARY_EXTENSIONS = new HashSet<>();

    static {
        BINARY_EXTENSIONS.add("ttf");
        BINARY_EXTENSIONS.add("otf");
        BINARY_EXTENSIONS.add("woff");
        BINARY_EXTENSIONS.add("woff2");
        BINARY_EXTENSIONS.add("eot");
        BINARY_EXTENSIONS.add("png");
        BINARY_EXTENSIONS.add("jpg");
        BINARY_EXTENSIONS.add("jpeg");
        BINARY_EXTENSIONS.add("gif");
        BINARY_EXTENSIONS.add("webp");
        BINARY_EXTENSIONS.add("ico");
        BINARY_EXTENSIONS.add("pdf");
        BINARY_EXTENSIONS.add("bin");
    }

    /**
     * Parses a ZIP package into a Nacos Skill object.
     *
     * @param zipBytes ZIP file bytes
     * @param namespaceId Nacos namespace
     * @return Skill object containing name, description, instruction, and resources
     */
    public static Skill parseSkillFromZip(byte[] zipBytes, String namespaceId) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ZIP 文件为空");
        }

        try {
            List<ZipEntryData> entries = unzipToEntries(zipBytes);

            // Find SKILL.md in the root directory or the first-level child directory.
            String skillMdContent = null;
            for (ZipEntryData entry : entries) {
                if (isMacOsMetadataFile(entry.name)) continue;
                if (SKILL_MD_FILE.equals(entry.name) || entry.name.endsWith("/" + SKILL_MD_FILE)) {
                    skillMdContent = new String(entry.data, StandardCharsets.UTF_8);
                    break;
                }
            }

            if (skillMdContent == null || skillMdContent.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ZIP 包中未找到 SKILL.md 文件");
            }

            Skill skill = parseSkillMarkdown(skillMdContent, namespaceId);
            Map<String, SkillResource> resources = parseResources(entries, skill.getName());
            skill.setResource(resources);
            return skill;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ZIP 解析失败: " + e.getMessage());
        }
    }

    /**
     * Parses resource paths from a Skill ZIP without loading resource content.
     *
     * @param zipBytes ZIP file bytes
     * @return Nacos Skill resource paths
     */
    public static List<String> parseResourcePathsFromZip(byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ZIP file is empty");
        }

        try {
            ZipManifest manifest = readZipManifest(zipBytes);
            if (manifest.skillMdContent() == null || manifest.skillMdContent().isBlank()) {
                throw new BusinessException(
                        ErrorCode.INVALID_PARAMETER, "SKILL.md was not found in the ZIP package");
            }

            Skill skill = parseSkillMarkdown(manifest.skillMdContent(), "");
            List<String> resourcePaths = new ArrayList<>();
            for (String entryName : manifest.entryNames()) {
                ResourcePath resourcePath = parseResourcePath(entryName, skill.getName());
                if (resourcePath != null) {
                    resourcePaths.add(resourcePath.path());
                }
            }
            return resourcePaths;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "Failed to parse ZIP package: " + e.getMessage());
        }
    }

    private static List<ZipEntryData> unzipToEntries(byte[] zipBytes) throws IOException {
        List<ZipEntryData> result = new ArrayList<>();
        try (ZipArchiveInputStream zis =
                new ZipArchiveInputStream(
                        new ByteArrayInputStream(zipBytes),
                        StandardCharsets.UTF_8.name(),
                        true,
                        true)) {
            ZipArchiveEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (isIgnoredZipEntry(name)) {
                    drainEntry(zis, buffer);
                    continue;
                }
                result.add(new ZipEntryData(name, readEntryBytes(zis, buffer)));
            }
        }
        return result;
    }

    private static ZipManifest readZipManifest(byte[] zipBytes) throws IOException {
        List<String> entryNames = new ArrayList<>();
        String skillMdContent = null;
        try (ZipArchiveInputStream zis =
                new ZipArchiveInputStream(
                        new ByteArrayInputStream(zipBytes),
                        StandardCharsets.UTF_8.name(),
                        true,
                        true)) {
            ZipArchiveEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (isIgnoredZipEntry(name)) {
                    drainEntry(zis, buffer);
                    continue;
                }
                entryNames.add(name);
                if (skillMdContent == null
                        && (SKILL_MD_FILE.equals(name) || name.endsWith("/" + SKILL_MD_FILE))) {
                    skillMdContent =
                            new String(readEntryBytes(zis, buffer), StandardCharsets.UTF_8);
                } else {
                    drainEntry(zis, buffer);
                }
            }
        }
        return new ZipManifest(entryNames, skillMdContent);
    }

    private static byte[] readEntryBytes(ZipArchiveInputStream zis, byte[] buffer)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int n;
        while ((n = zis.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static void drainEntry(ZipArchiveInputStream zis, byte[] buffer) throws IOException {
        while (zis.read(buffer) != -1) {}
    }

    private static Map<String, SkillResource> parseResources(
            List<ZipEntryData> entries, String skillName) {
        Map<String, SkillResource> resources = new HashMap<>(16);
        for (ZipEntryData entry : entries) {
            String itemName = entry.name;
            ResourcePath path = parseResourcePath(itemName, skillName);
            if (path == null) {
                continue;
            }

            boolean isBinary = isBinaryResource(path.name());
            String content;
            Map<String, Object> metadata = new HashMap<>(4);
            if (isBinary) {
                content = Base64.getEncoder().encodeToString(entry.data);
                metadata.put("encoding", "base64");
            } else {
                content = new String(entry.data, StandardCharsets.UTF_8);
            }

            SkillResource resource = new SkillResource();
            resource.setName(path.name());
            resource.setType(path.type());
            resource.setContent(content);
            resource.setMetadata(metadata.isEmpty() ? null : metadata);
            String key = SkillUtils.generateResourceId(path.type(), path.name());
            resources.put(key, resource);
        }
        return resources;
    }

    private static ResourcePath parseResourcePath(String itemName, String skillName) {
        if (itemName == null || itemName.isEmpty()) return null;
        if (isMacOsMetadataFile(itemName)) return null;
        if (itemName.endsWith(SKILL_MD_FILE) || itemName.endsWith("/")) return null;

        String[] parts = itemName.split("/");
        String type;
        String name;

        if (parts.length == 1) {
            type = "";
            name = parts[0];
        } else if (parts.length == 2 && parts[0].equals(skillName)) {
            type = "";
            name = parts[1];
        } else if (parts.length >= 3 && parts[0].equals(skillName)) {
            type = joinPath(parts, 1, parts.length - 1);
            name = parts[parts.length - 1];
        } else if (parts.length >= 2) {
            type = joinPath(parts, 0, parts.length - 1);
            name = parts[parts.length - 1];
        } else {
            return null;
        }

        return new ResourcePath(type, name);
    }

    private static String joinPath(String[] parts, int startInclusive, int endExclusive) {
        StringBuilder builder = new StringBuilder();
        for (int i = startInclusive; i < endExclusive; i++) {
            if (builder.length() > 0) {
                builder.append('/');
            }
            builder.append(parts[i]);
        }
        return builder.toString();
    }

    private static Skill parseSkillMarkdown(String markdownContent, String namespaceId) {
        if (markdownContent.startsWith("\uFEFF")) {
            markdownContent = markdownContent.substring(1);
        }
        Matcher matcher = YAML_FRONT_MATTER.matcher(markdownContent);
        if (!matcher.matches()) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "SKILL.md 必须包含 YAML front matter (---)");
        }

        String yamlContent = matcher.group(1);
        String instructionContent = matcher.group(2);

        Map<String, String> yamlMap = parseYamlFrontMatter(yamlContent);
        String name = yamlMap.get("name");
        String description = yamlMap.get("description");

        if (name == null || name.isBlank()) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "SKILL.md YAML front matter 中缺少 name");
        }
        if (description == null || description.isBlank()) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "SKILL.md YAML front matter 中缺少 description");
        }

        String instruction = extractInstruction(instructionContent);
        if (instruction == null || instruction.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "SKILL.md 中缺少 instruction 内容");
        }

        Skill skill = new Skill();
        skill.setNamespaceId(namespaceId);
        skill.setName(name.trim());
        skill.setDescription(description.trim());
        skill.setSkillMd(instruction.trim());
        return skill;
    }

    private static Map<String, String> parseYamlFrontMatter(String yamlContent) {
        Yaml yaml = new Yaml();
        Object parsed = yaml.load(yamlContent);
        Map<String, String> result = new HashMap<>(4);
        if (parsed instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) parsed;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (entry.getValue() != null) {
                    result.put(entry.getKey(), entry.getValue().toString().trim());
                }
            }
        }
        return result;
    }

    private static String extractInstruction(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("## Instructions") || trimmed.startsWith("##Instructions")) {
            int headerEnd = trimmed.indexOf('\n');
            if (headerEnd > 0) {
                trimmed = trimmed.substring(headerEnd).trim();
            }
        }
        return trimmed;
    }

    private static boolean isBinaryResource(String fileName) {
        if (fileName == null || !fileName.contains(".")) return false;
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).trim().toLowerCase();
        return BINARY_EXTENSIONS.contains(ext);
    }

    private static boolean isMacOsMetadataFile(String itemName) {
        if (itemName == null || itemName.isEmpty()) return false;
        int lastSlash = itemName.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? itemName.substring(lastSlash + 1) : itemName;
        return fileName.startsWith(MACOS_METADATA_PREFIX);
    }

    private static boolean isIgnoredZipEntry(String itemName) {
        return itemName == null
                || itemName.contains("__MACOSX")
                || itemName.contains("/__MACOSX/")
                || isMacOsMetadataFile(itemName);
    }

    private record ZipEntryData(String name, byte[] data) {}

    private record ZipManifest(List<String> entryNames, String skillMdContent) {}

    private record ResourcePath(String type, String name) {
        String path() {
            if (type == null || type.isBlank()) {
                return name;
            }
            return type + "/" + name;
        }
    }
}

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

/**
 * ZIP 包解析工具类。参考 Nacos 服务端 SkillZipParser 实现。
 * 解析 ZIP → 提取 SKILL.md → 构建 Skill 对象。
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
     * 解析 ZIP 包为 Nacos Skill 对象。
     *
     * @param zipBytes ZIP 文件字节数组
     * @param namespaceId Nacos 命名空间
     * @return Skill 对象（含 name、description、instruction、resources）
     */
    public static Skill parseSkillFromZip(byte[] zipBytes, String namespaceId) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ZIP 文件为空");
        }

        try {
            List<ZipEntryData> entries = unzipToEntries(zipBytes);

            // 查找 SKILL.md（根目录或一级子目录）
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
                if (name != null && (name.contains("__MACOSX") || name.contains("/__MACOSX/")))
                    continue;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int n;
                while ((n = zis.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                }
                result.add(new ZipEntryData(name, out.toByteArray()));
            }
        }
        return result;
    }

    private static Map<String, SkillResource> parseResources(
            List<ZipEntryData> entries, String skillName) {
        Map<String, SkillResource> resources = new HashMap<>(16);
        for (ZipEntryData entry : entries) {
            String itemName = entry.name;
            if (isMacOsMetadataFile(itemName)) continue;
            if (itemName.endsWith(SKILL_MD_FILE) || itemName.endsWith("/")) continue;

            String[] parts = itemName.split("/");
            String type;
            String resourceName;

            if (parts.length == 1) {
                type = "";
                resourceName = parts[0];
            } else if (parts.length == 2 && parts[0].equals(skillName)) {
                type = "";
                resourceName = parts[1];
            } else if (parts.length >= 3 && parts[0].equals(skillName)) {
                StringBuilder typeSb = new StringBuilder();
                for (int i = 1; i < parts.length - 1; i++) {
                    if (typeSb.length() > 0) typeSb.append('/');
                    typeSb.append(parts[i]);
                }
                type = typeSb.toString();
                resourceName = parts[parts.length - 1];
            } else if (parts.length >= 2) {
                StringBuilder typeSb = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    if (typeSb.length() > 0) typeSb.append('/');
                    typeSb.append(parts[i]);
                }
                type = typeSb.toString();
                resourceName = parts[parts.length - 1];
            } else {
                continue;
            }

            boolean isBinary = isBinaryResource(resourceName);
            String content;
            Map<String, Object> metadata = new HashMap<>(4);
            if (isBinary) {
                content = Base64.getEncoder().encodeToString(entry.data);
                metadata.put("encoding", "base64");
            } else {
                content = new String(entry.data, StandardCharsets.UTF_8);
            }

            SkillResource resource = new SkillResource();
            resource.setName(resourceName);
            resource.setType(type);
            resource.setContent(content);
            resource.setMetadata(metadata.isEmpty() ? null : metadata);
            String key = SkillUtils.generateResourceId(type, resourceName);
            resources.put(key, resource);
        }
        return resources;
    }

    private static Skill parseSkillMarkdown(String markdownContent, String namespaceId) {
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
        skill.setInstruction(instruction.trim());
        return skill;
    }

    private static Map<String, String> parseYamlFrontMatter(String yamlContent) {
        Map<String, String> result = new HashMap<>(4);
        for (String line : yamlContent.split("\\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                    value = value.replace("\\\\", "\\").replace("\\\"", "\"");
                } else if (value.startsWith("'") && value.endsWith("'")) {
                    value = value.substring(1, value.length() - 1);
                    value = value.replace("''", "'");
                }
                result.put(key, value);
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

    private record ZipEntryData(String name, byte[] data) {}
}

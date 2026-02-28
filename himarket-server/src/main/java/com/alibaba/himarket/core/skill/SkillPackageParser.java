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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * Skill 包解析器，支持 ZIP 和 TAR.GZ 格式。
 * 解析压缩包内文件，提取 SKILL.md front matter，过滤无效条目，判断文件编码。
 */
@Slf4j
@Component
public class SkillPackageParser {

    private static final int MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final int MAX_FILE_COUNT = 500;
    private static final String SKILL_MD = "SKILL.md";
    private static final String FRONT_MATTER_DELIMITER = "---";
    private static final java.util.Set<String> TEXT_EXTENSIONS =
            java.util.Set.of(
                    "md",
                    "txt",
                    "yaml",
                    "yml",
                    "json",
                    "toml",
                    "xml",
                    "html",
                    "htm",
                    "css",
                    "js",
                    "ts",
                    "tsx",
                    "jsx",
                    "py",
                    "java",
                    "go",
                    "rs",
                    "cpp",
                    "c",
                    "h",
                    "sh",
                    "bash",
                    "zsh",
                    "fish",
                    "rb",
                    "php",
                    "swift",
                    "kt",
                    "scala",
                    "r",
                    "sql",
                    "graphql",
                    "proto",
                    "ini",
                    "cfg",
                    "conf",
                    "env",
                    "gitignore",
                    "dockerfile",
                    "makefile",
                    "csv",
                    "tsv");
    private static final java.util.Set<String> BINARY_EXTENSIONS =
            java.util.Set.of(
                    // 图片
                    "png",
                    "jpg",
                    "jpeg",
                    "gif",
                    "bmp",
                    "webp",
                    "ico",
                    "tiff",
                    "tif",
                    // 文档
                    "pdf",
                    "doc",
                    "docx",
                    "xls",
                    "xlsx",
                    "ppt",
                    "pptx",
                    // 压缩包
                    "zip",
                    "tar",
                    "gz",
                    "tgz",
                    "bz2",
                    "xz",
                    "7z",
                    "rar",
                    // 音视频
                    "mp3",
                    "mp4",
                    "wav",
                    "ogg",
                    "flac",
                    "avi",
                    "mov",
                    "mkv",
                    "webm",
                    // 字体
                    "ttf",
                    "otf",
                    "woff",
                    "woff2",
                    "eot",
                    // 二进制/编译产物
                    "exe",
                    "dll",
                    "so",
                    "dylib",
                    "class",
                    "pyc",
                    "pyo",
                    "o",
                    "a",
                    "lib",
                    // 数据库
                    "db",
                    "sqlite",
                    "sqlite3");

    private final Yaml yaml = new Yaml();

    /**
     * 解析结果
     */
    public static class ParseResult {
        public String name;
        public String description;
        public String skillMdContent;
        public List<SkillFileEntry> files = new ArrayList<>();
    }

    /**
     * 单个文件条目
     */
    public static class SkillFileEntry {
        public String path;
        public String encoding;
        public String content;
        public int size;
    }

    /**
     * 解析 ZIP 格式的 skill 包
     *
     * @param inputStream 输入流
     * @return 解析结果
     * @throws ParseException 解析失败时抛出，携带具体原因
     * @throws IOException    IO 错误
     */
    public ParseResult parseZip(InputStream inputStream) throws ParseException, IOException {
        List<RawEntry> rawEntries = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                // 跳过目录项
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }

                // 路径穿越检查
                if (name.contains("..")) {
                    log.warn("跳过路径含 '..' 的条目: {}", name);
                    zis.closeEntry();
                    continue;
                }

                // 隐藏文件检查（文件名以 . 开头）
                String fileName = getFileName(name);
                if (fileName.startsWith(".")) {
                    zis.closeEntry();
                    continue;
                }

                byte[] bytes = readBytes(zis, name);
                if (bytes == null) {
                    zis.closeEntry();
                    continue;
                }

                rawEntries.add(new RawEntry(normalizePath(name), bytes));
                zis.closeEntry();
            }
        }

        return buildResult(rawEntries);
    }

    /**
     * 解析 TAR.GZ 格式的 skill 包
     *
     * @param inputStream 输入流
     * @return 解析结果
     * @throws ParseException 解析失败时抛出，携带具体原因
     * @throws IOException    IO 错误
     */
    public ParseResult parseTarGz(InputStream inputStream) throws ParseException, IOException {
        List<RawEntry> rawEntries = new ArrayList<>();

        try (GzipCompressorInputStream gzis = new GzipCompressorInputStream(inputStream);
                TarArchiveInputStream tais = new TarArchiveInputStream(gzis)) {

            TarArchiveEntry entry;
            while ((entry = tais.getNextEntry()) != null) {
                String name = entry.getName();

                // 跳过目录项
                if (entry.isDirectory()) {
                    continue;
                }

                // 路径穿越检查
                if (name.contains("..")) {
                    log.warn("跳过路径含 '..' 的条目: {}", name);
                    continue;
                }

                // 隐藏文件检查
                String fileName = getFileName(name);
                if (fileName.startsWith(".")) {
                    continue;
                }

                byte[] bytes = readBytes(tais, name);
                if (bytes == null) {
                    continue;
                }

                rawEntries.add(new RawEntry(normalizePath(name), bytes));
            }
        }

        return buildResult(rawEntries);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static class RawEntry {
        final String path;
        final byte[] bytes;

        RawEntry(String path, byte[] bytes) {
            this.path = path;
            this.bytes = bytes;
        }
    }

    /**
     * 读取字节，超过 5MB 则跳过
     */
    private byte[] readBytes(InputStream is, String entryName) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int read;
        int total = 0;
        while ((read = is.read(buf)) != -1) {
            total += read;
            if (total > MAX_FILE_SIZE) {
                log.warn("跳过超过 5MB 的文件: {}", entryName);
                return null;
            }
            baos.write(buf, 0, read);
        }
        return baos.toByteArray();
    }

    /**
     * 构建解析结果：校验 SKILL.md，提取 front matter，组装文件列表
     */
    private ParseResult buildResult(List<RawEntry> rawEntries) throws ParseException {
        // 找 SKILL.md（支持根目录或一级子目录下）
        RawEntry skillMdEntry = null;
        for (RawEntry e : rawEntries) {
            String path = e.path;
            // 匹配 "SKILL.md" 或 "xxx/SKILL.md"（只允许一级前缀）
            if (path.equals(SKILL_MD) || path.matches("[^/]+/" + SKILL_MD)) {
                skillMdEntry = e;
                break;
            }
        }

        if (skillMdEntry == null) {
            throw new ParseException("压缩包中未找到 SKILL.md 文件", 0);
        }

        String skillMdContent = new String(skillMdEntry.bytes, StandardCharsets.UTF_8);
        Map<String, Object> frontmatter = parseFrontMatter(skillMdContent);

        // 校验 name
        Object nameObj = frontmatter.get("name");
        if (nameObj == null || nameObj.toString().isBlank()) {
            throw new ParseException("SKILL.md 缺少 name 字段", 0);
        }

        // 校验 description
        Object descObj = frontmatter.get("description");
        if (descObj == null || descObj.toString().isBlank()) {
            throw new ParseException("SKILL.md 缺少 description 字段", 0);
        }

        ParseResult result = new ParseResult();
        result.name = nameObj.toString().trim();
        result.description = descObj.toString().trim();
        result.skillMdContent = skillMdContent;

        // 确定根目录前缀（用于去除压缩包顶层目录）
        String rootPrefix = detectRootPrefix(rawEntries);

        // 组装文件列表，截断超过 500 个
        int count = 0;
        for (RawEntry e : rawEntries) {
            if (count >= MAX_FILE_COUNT) {
                log.warn("文件数量超过 {} 个，截断剩余文件", MAX_FILE_COUNT);
                break;
            }
            String relativePath = stripPrefix(e.path, rootPrefix);
            SkillFileEntry fileEntry = new SkillFileEntry();
            fileEntry.path = relativePath;
            fileEntry.size = e.bytes.length;

            // 三段式编码判断：
            // 1. 已知文本后缀 → text
            // 2. 已知二进制后缀 → base64
            // 3. 其余（小众后缀或无后缀）→ 尝试 UTF-8 严格解码，失败才 base64
            String ext =
                    relativePath.contains(".")
                            ? relativePath
                                    .substring(relativePath.lastIndexOf('.') + 1)
                                    .toLowerCase()
                            : "";
            if (TEXT_EXTENSIONS.contains(ext)) {
                fileEntry.encoding = "text";
                fileEntry.content = new String(e.bytes, StandardCharsets.UTF_8);
            } else if (BINARY_EXTENSIONS.contains(ext)) {
                fileEntry.encoding = "base64";
                fileEntry.content = Base64.getEncoder().encodeToString(e.bytes);
            } else {
                try {
                    java.nio.charset.CharsetDecoder decoder =
                            StandardCharsets.UTF_8
                                    .newDecoder()
                                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                                    .onUnmappableCharacter(
                                            java.nio.charset.CodingErrorAction.REPORT);
                    fileEntry.encoding = "text";
                    fileEntry.content =
                            decoder.decode(java.nio.ByteBuffer.wrap(e.bytes)).toString();
                } catch (Exception ex) {
                    fileEntry.encoding = "base64";
                    fileEntry.content = Base64.getEncoder().encodeToString(e.bytes);
                }
            }

            result.files.add(fileEntry);
            count++;
        }

        return result;
    }

    /**
     * 解析 SKILL.md 的 YAML front matter
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseFrontMatter(String content) throws ParseException {
        String trimmed = content.strip();
        if (!trimmed.startsWith(FRONT_MATTER_DELIMITER)) {
            throw new ParseException("SKILL.md 缺少 YAML front matter（--- 块）", 0);
        }
        int end = trimmed.indexOf(FRONT_MATTER_DELIMITER, FRONT_MATTER_DELIMITER.length());
        if (end < 0) {
            throw new ParseException("SKILL.md front matter 缺少结束分隔符 ---", 0);
        }
        String yamlText = trimmed.substring(FRONT_MATTER_DELIMITER.length(), end).strip();
        try {
            Object parsed = yaml.load(yamlText);
            if (parsed instanceof Map) {
                return (Map<String, Object>) parsed;
            }
            throw new ParseException("SKILL.md front matter 格式错误", 0);
        } catch (Exception e) {
            throw new ParseException("SKILL.md front matter 解析失败：" + e.getMessage(), 0);
        }
    }

    /**
     * 检测压缩包顶层目录前缀（如 "my-skill/"），若所有文件都在同一顶层目录下则去除
     */
    private String detectRootPrefix(List<RawEntry> entries) {
        if (entries.isEmpty()) return "";
        String first = entries.get(0).path;
        int slash = first.indexOf('/');
        if (slash < 0) return "";
        String prefix = first.substring(0, slash + 1);
        for (RawEntry e : entries) {
            if (!e.path.startsWith(prefix)) return "";
        }
        return prefix;
    }

    private String stripPrefix(String path, String prefix) {
        if (!prefix.isEmpty() && path.startsWith(prefix)) {
            return path.substring(prefix.length());
        }
        return path;
    }

    private String getFileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    /**
     * 规范化路径：去除开头的 ./
     */
    private String normalizePath(String path) {
        if (path.startsWith("./")) {
            return path.substring(2);
        }
        return path;
    }
}

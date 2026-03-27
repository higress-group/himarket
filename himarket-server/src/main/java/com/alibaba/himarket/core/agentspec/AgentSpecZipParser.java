package com.alibaba.himarket.core.agentspec;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecResource;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class AgentSpecZipParser {

    private static final String MANIFEST_FILE = "manifest.json";

    /**
     * Parses a ZIP archive into an AgentSpec JSON string. The ZIP must contain a
     * {@code manifest.json} with a {@code worker.suggested_name} field.
     *
     * @param zipBytes  raw ZIP bytes
     * @param namespace Nacos namespace
     * @param fixedName expected AgentSpec name; if non-blank, must match {@code suggested_name}
     * @return AgentSpec as a JSON string
     * @throws BusinessException if the ZIP is empty, missing manifest, or name mismatch
     */
    public static String parse(byte[] zipBytes, String namespace, String fixedName) {
        if (ArrayUtil.isEmpty(zipBytes)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ZIP file is empty");
        }

        try {
            // Extract and validate files
            Map<String, byte[]> files = extractFiles(zipBytes);

            // Read and validate manifest
            String manifest = new String(files.get(MANIFEST_FILE), StandardCharsets.UTF_8);
            String suggestedName =
                    JSONUtil.parseObj(manifest).getJSONObject("worker").getStr("suggested_name");

            if (StrUtil.isBlank(suggestedName)) {
                throw new BusinessException(
                        ErrorCode.INVALID_PARAMETER, "manifest.json missing worker.suggested_name");
            }
            if (StrUtil.isNotBlank(fixedName) && !StrUtil.equals(fixedName, suggestedName)) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "manifest worker.suggested_name must match existing AgentSpec name");
            }

            // Build AgentSpec
            AgentSpec spec = new AgentSpec();
            spec.setNamespaceId(namespace);
            spec.setName(StrUtil.nullToDefault(fixedName, suggestedName));
            spec.setContent(manifest);
            spec.setResource(buildResources(files));

            return JSONUtil.toJsonStr(spec);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "ZIP parse failed: " + e.getMessage());
        }
    }

    /**
     * Extracts file entries from a ZIP archive, skipping directories and OS metadata
     * files (e.g. {@code __MACOSX/}, {@code .DS_Store}).
     *
     * @param zipBytes raw ZIP bytes
     * @return map of relative path to file content bytes
     * @throws IOException       if an I/O error occurs during extraction
     * @throws BusinessException if {@code manifest.json} is not found
     */
    private static Map<String, byte[]> extractFiles(byte[] zipBytes) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();

        try (ZipInputStream zis =
                new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                // Skip directories and metadata files
                if (entry.isDirectory()
                        || name.startsWith("__MACOSX/")
                        || name.endsWith(".DS_Store")
                        || name.substring(name.lastIndexOf('/') + 1).startsWith("._")) {
                    continue;
                }

                // Handle manifest path
                if (name.endsWith(MANIFEST_FILE)) {
                    files.put(MANIFEST_FILE, zis.readAllBytes());
                } else {
                    files.put(name, zis.readAllBytes());
                }
            }
        }

        if (!files.containsKey(MANIFEST_FILE)) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "manifest.json not found in ZIP");
        }

        return files;
    }

    /**
     * Builds AgentSpec resource mappings from extracted files (excluding {@code manifest.json}).
     * Text files are stored as UTF-8 strings; binary files are Base64-encoded with an
     * {@code encoding=base64} metadata entry.
     *
     * @param files map of relative path to file content bytes
     * @return resource map keyed by generated resource ID, or {@code null} if empty
     */
    private static Map<String, AgentSpecResource> buildResources(Map<String, byte[]> files) {
        Map<String, AgentSpecResource> resources = new LinkedHashMap<>();

        files.forEach(
                (path, content) -> {
                    if (!MANIFEST_FILE.equals(path)) {
                        int slash = path.lastIndexOf('/');
                        String type = slash > 0 ? path.substring(0, slash) : "";
                        String name = slash >= 0 ? path.substring(slash + 1) : path;

                        AgentSpecResource resource = new AgentSpecResource();
                        resource.setType(type);
                        resource.setName(name);

                        // Check if it's a text file
                        boolean isText = true;
                        int max = Math.min(content.length, 1024);
                        for (int i = 0; i < max; i++) {
                            int value = content[i] & 0xFF;
                            if (value < 0x09 || (value > 0x0D && value < 0x20)) {
                                isText = false;
                                break;
                            }
                        }

                        if (isText) {
                            resource.setContent(new String(content, StandardCharsets.UTF_8));
                        } else {
                            resource.setContent(Base64.getEncoder().encodeToString(content));
                            resource.setMetadata(Map.of("encoding", "base64"));
                        }

                        resources.put(AgentSpecUtils.generateResourceId(type, name), resource);
                    }
                });

        return resources.isEmpty() ? null : resources;
    }
}

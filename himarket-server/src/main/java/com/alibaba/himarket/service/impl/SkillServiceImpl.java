package com.alibaba.himarket.service.impl;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.skill.FileTreeBuilder;
import com.alibaba.himarket.core.skill.SkillMdBuilder;
import com.alibaba.himarket.dto.result.skill.SkillFileContentResult;
import com.alibaba.himarket.dto.result.skill.SkillFileTreeNode;
import com.alibaba.himarket.service.NacosService;
import com.alibaba.himarket.service.SkillService;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Nacos Skill SDK 透传服务实现。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SkillServiceImpl implements SkillService {

    private final NacosService nacosService;

    @Override
    public String createSkill(String nacosId, String namespace, Skill skill) {
        try {
            AiMaintainerService service = nacosService.getAiMaintainerService(nacosId);
            return service.registerSkill(namespace, skill);
        } catch (NacosException e) {
            throw wrapNacosException("创建 Skill 失败", e);
        }
    }

    @Override
    public Skill getSkillDetail(String nacosId, String namespace, String skillName) {
        try {
            AiMaintainerService service = nacosService.getAiMaintainerService(nacosId);
            Skill skill = service.getSkillDetail(namespace, skillName);
            if (skill == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Skill", skillName);
            }
            return skill;
        } catch (NacosException e) {
            throw wrapNacosException("查询 Skill 详情失败", e);
        }
    }

    @Override
    public void updateSkill(String nacosId, String namespace, Skill skill) {
        try {
            AiMaintainerService service = nacosService.getAiMaintainerService(nacosId);
            service.updateSkill(namespace, skill);
        } catch (NacosException e) {
            throw wrapNacosException("更新 Skill 失败", e);
        }
    }

    @Override
    public void deleteSkill(String nacosId, String namespace, String skillName) {
        try {
            AiMaintainerService service = nacosService.getAiMaintainerService(nacosId);
            service.deleteSkill(namespace, skillName);
        } catch (NacosException e) {
            throw wrapNacosException("删除 Skill 失败", e);
        }
    }

    @Override
    public Page<SkillBasicInfo> listSkills(
            String nacosId, String namespace, String search, int pageNo, int pageSize) {
        try {
            AiMaintainerService service = nacosService.getAiMaintainerService(nacosId);
            return service.listSkills(namespace, null, search, pageNo, pageSize);
        } catch (NacosException e) {
            throw wrapNacosException("查询 Skill 列表失败", e);
        }
    }

    @Override
    public String getSkillDocument(String nacosId, String namespace, String skillName) {
        Skill skill = getSkillDetail(nacosId, namespace, skillName);
        return SkillMdBuilder.build(skill);
    }

    @Override
    public List<SkillFileTreeNode> getFileTree(String nacosId, String namespace, String skillName) {
        Skill skill = getSkillDetail(nacosId, namespace, skillName);
        return FileTreeBuilder.build(skill);
    }

    @Override
    public List<SkillFileContentResult> getAllFiles(
            String nacosId, String namespace, String skillName) {
        Skill skill = getSkillDetail(nacosId, namespace, skillName);
        List<SkillFileContentResult> results = new ArrayList<>();

        // SKILL.md 虚拟文件
        String skillMd = SkillMdBuilder.build(skill);
        SkillFileContentResult mdResult = new SkillFileContentResult();
        mdResult.setPath("SKILL.md");
        mdResult.setContent(skillMd);
        mdResult.setEncoding("text");
        mdResult.setSize(skillMd.getBytes(StandardCharsets.UTF_8).length);
        results.add(mdResult);

        // resource 文件
        if (skill.getResource() != null) {
            for (Map.Entry<String, SkillResource> entry : skill.getResource().entrySet()) {
                SkillResource resource = entry.getValue();
                SkillFileContentResult fileResult = new SkillFileContentResult();
                String path = buildResourcePath(resource);
                fileResult.setPath(path);
                fileResult.setContent(resource.getContent());
                boolean isBinary =
                        resource.getMetadata() != null
                                && "base64".equals(resource.getMetadata().get("encoding"));
                fileResult.setEncoding(isBinary ? "base64" : "text");
                fileResult.setSize(
                        resource.getContent() != null
                                ? resource.getContent().getBytes(StandardCharsets.UTF_8).length
                                : 0);
                results.add(fileResult);
            }
        }
        return results;
    }

    @Override
    public SkillFileContentResult getFileContent(
            String nacosId, String namespace, String skillName, String path) {
        Skill skill = getSkillDetail(nacosId, namespace, skillName);

        if ("SKILL.md".equals(path)) {
            String skillMd = SkillMdBuilder.build(skill);
            SkillFileContentResult result = new SkillFileContentResult();
            result.setPath("SKILL.md");
            result.setContent(skillMd);
            result.setEncoding("text");
            result.setSize(skillMd.getBytes(StandardCharsets.UTF_8).length);
            return result;
        }

        if (skill.getResource() != null) {
            for (Map.Entry<String, SkillResource> entry : skill.getResource().entrySet()) {
                SkillResource resource = entry.getValue();
                String resourcePath = buildResourcePath(resource);
                if (path.equals(resourcePath)) {
                    SkillFileContentResult result = new SkillFileContentResult();
                    result.setPath(resourcePath);
                    result.setContent(resource.getContent());
                    boolean isBinary =
                            resource.getMetadata() != null
                                    && "base64".equals(resource.getMetadata().get("encoding"));
                    result.setEncoding(isBinary ? "base64" : "text");
                    result.setSize(
                            resource.getContent() != null
                                    ? resource.getContent().getBytes(StandardCharsets.UTF_8).length
                                    : 0);
                    return result;
                }
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "Skill 资源文件", path);
    }

    @Override
    public void downloadZip(
            String nacosId, String namespace, String skillName, HttpServletResponse response)
            throws IOException {
        Skill skill = getSkillDetail(nacosId, namespace, skillName);

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + skillName + ".zip\"");

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            // SKILL.md
            String skillMd = SkillMdBuilder.build(skill);
            zos.putNextEntry(new ZipEntry(skillName + "/SKILL.md"));
            zos.write(skillMd.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // resource 文件
            if (skill.getResource() != null) {
                for (Map.Entry<String, SkillResource> entry : skill.getResource().entrySet()) {
                    SkillResource resource = entry.getValue();
                    String path = buildResourcePath(resource);
                    zos.putNextEntry(new ZipEntry(skillName + "/" + path));
                    boolean isBinary =
                            resource.getMetadata() != null
                                    && "base64".equals(resource.getMetadata().get("encoding"));
                    if (isBinary && resource.getContent() != null) {
                        zos.write(java.util.Base64.getDecoder().decode(resource.getContent()));
                    } else if (resource.getContent() != null) {
                        zos.write(resource.getContent().getBytes(StandardCharsets.UTF_8));
                    }
                    zos.closeEntry();
                }
            }
        }
    }

    private String buildResourcePath(SkillResource resource) {
        String type = resource.getType();
        String name = resource.getName();
        if (type != null && !type.isEmpty()) {
            return type + "/" + name;
        }
        return name;
    }

    private BusinessException wrapNacosException(String message, NacosException e) {
        log.error("{}: {}", message, e.getErrMsg(), e);
        return new BusinessException(ErrorCode.INTERNAL_ERROR, message + ": " + e.getErrMsg());
    }
}

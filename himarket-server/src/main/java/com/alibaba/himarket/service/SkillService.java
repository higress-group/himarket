package com.alibaba.himarket.service;

import com.alibaba.himarket.dto.result.skill.SkillFileContentResult;
import com.alibaba.himarket.dto.result.skill.SkillFileTreeNode;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
import com.alibaba.nacos.api.model.Page;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Nacos Skill SDK 透传服务。
 * 所有操作通过 NacosService.getAiMaintainerService(nacosId) 获取 SDK 实例。
 */
public interface SkillService {

    /** 创建 Skill → SDK registerSkill() */
    String createSkill(String nacosId, String namespace, Skill skill);

    /** 查询 Skill 详情 → SDK getSkillDetail() */
    Skill getSkillDetail(String nacosId, String namespace, String skillName);

    /** 更新 Skill → SDK updateSkill() */
    void updateSkill(String nacosId, String namespace, Skill skill);

    /** 删除 Skill → SDK deleteSkill() */
    void deleteSkill(String nacosId, String namespace, String skillName);

    /** 分页列表 → SDK listSkills() */
    Page<SkillBasicInfo> listSkills(
            String nacosId, String namespace, String search, int pageNo, int pageSize);

    /** 获取 SKILL.md 文档（拼装 frontmatter + instruction） */
    String getSkillDocument(String nacosId, String namespace, String skillName);

    /** 获取文件树 */
    List<SkillFileTreeNode> getFileTree(String nacosId, String namespace, String skillName);

    /** 获取所有文件内容 */
    List<SkillFileContentResult> getAllFiles(String nacosId, String namespace, String skillName);

    /** 获取单文件内容 */
    SkillFileContentResult getFileContent(
            String nacosId, String namespace, String skillName, String path);

    /** ZIP 流式下载 */
    void downloadZip(
            String nacosId, String namespace, String skillName, HttpServletResponse response)
            throws IOException;
}

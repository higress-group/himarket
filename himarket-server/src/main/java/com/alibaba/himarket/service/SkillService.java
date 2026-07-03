package com.alibaba.himarket.service;

import com.alibaba.himarket.dto.params.skill.CreateSkillDraftParam;
import com.alibaba.himarket.dto.params.skill.UpdateSkillDraftParam;
import com.alibaba.himarket.dto.params.skill.UpdateSkillVersionParam;
import com.alibaba.himarket.dto.result.cli.CliDownloadInfo;
import com.alibaba.himarket.dto.result.common.FileContentResult;
import com.alibaba.himarket.dto.result.common.FileTreeNode;
import com.alibaba.himarket.dto.result.common.ImportResult;
import com.alibaba.himarket.dto.result.common.SkillDraftResult;
import com.alibaba.himarket.dto.result.common.VersionResult;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * Skill management service.
 * Product-based operations resolve Nacos coordinates from Product.skillConfig.
 * Direct Nacos operations are used by admin management endpoints.
 */
public interface SkillService {

    /**
     * Uploads a ZIP package and creates a new draft for the given product.
     * On first upload, creates the Skill from ZIP (draft v1).
     * On subsequent uploads, creates a new draft version and updates content.
     *
     * @param productId the product identifier
     * @param file the ZIP file to upload
     * @throws IOException if an I/O error occurs
     */
    void uploadPackage(String productId, MultipartFile file) throws IOException;

    /**
     * Deletes the Skill associated with the given product.
     *
     * @param productId the product identifier
     */
    void deleteSkill(String productId);

    /**
     * Deletes the Skill associated with the given product.
     *
     * @param productId the product identifier
     * @param ignoreError whether to ignore source deletion errors
     */
    void deleteSkill(String productId, boolean ignoreError);

    /**
     * Returns a hierarchical file tree of the Skill contents.
     *
     * @param productId the product identifier
     * @param version   optional version; null for latest
     * @return the file tree nodes
     */
    List<FileTreeNode> getFileTree(String productId, String version);

    /**
     * Returns the content of a single file within the Skill.
     *
     * @param productId the product identifier
     * @param path the file path relative to the Skill root
     * @param version optional version; null for latest
     * @return the file content result
     */
    FileContentResult getFileContent(String productId, String path, String version);

    /**
     * Returns all versions for the Skill (including draft, reviewing, online, offline).
     *
     * @param productId the product identifier
     * @return the version list
     */
    List<VersionResult> listVersions(String productId);

    /**
     * Updates mutable fields of a specific version.
     *
     * @param productId the product identifier
     * @param version the target version
     * @param param the partial version update request
     */
    void updateVersion(String productId, String version, UpdateSkillVersionParam param);

    /**
     * Creates a draft by copying an existing version.
     *
     * @param productId the product identifier
     * @param param the draft creation request
     */
    void createDraft(String productId, CreateSkillDraftParam param);

    /**
     * Returns the current draft SkillCard.
     *
     * @param productId the product identifier
     * @return the draft version and SkillCard
     */
    SkillDraftResult getDraft(String productId);

    /**
     * Updates the current draft with a full SkillCard.
     *
     * @param productId the product identifier
     * @param param the draft update request
     */
    void updateDraft(String productId, UpdateSkillDraftParam param);

    /**
     * Deletes the current editing draft.
     *
     * @param productId the product identifier
     */
    void deleteDraft(String productId);

    /**
     * Downloads the Skill as a ZIP archive.
     *
     * @param productId the product identifier
     * @param version optional version; null for latest
     * @param response the HTTP response to write the ZIP to
     * @throws IOException if an I/O error occurs
     */
    void downloadPackage(String productId, String version, HttpServletResponse response)
            throws IOException;

    /**
     * Queries Skill detail via SDK getSkillVersionDetail().
     *
     * @param nacosId Nacos instance ID
     * @param namespace Nacos namespace
     * @param skillName the skill name
     * @param version optional version; null for latest
     * @return the Skill detail
     */
    Skill getSkillDetail(String nacosId, String namespace, String skillName, String version);

    /**
     * Deletes a Skill via SDK deleteSkill().
     *
     * @param nacosId Nacos instance ID
     * @param namespace Nacos namespace
     * @param skillName the skill name
     */
    void deleteSkill(String nacosId, String namespace, String skillName);

    /**
     * Uploads a Skill from ZIP via SDK uploadSkillFromZip().
     *
     * @param nacosId Nacos instance ID
     * @param namespace Nacos namespace
     * @param zipBytes raw ZIP bytes
     * @return the created skill name
     */
    String uploadSkillFromZip(String nacosId, String namespace, byte[] zipBytes);

    /**
     * Gets CLI download info for the frontend detail page.
     *
     * @param productId the product identifier
     * @return the CLI download info containing nacosHost and resource name
     */
    CliDownloadInfo getCliDownloadInfo(String productId);

    /**
     * Import skills from Nacos
     *
     * @param nacosId   Nacos instance ID
     * @param namespace Nacos namespace
     * @return import result with success and skipped counts
     */
    ImportResult importFromNacos(String nacosId, String namespace);
}

package com.alibaba.himarket.service;

import com.alibaba.himarket.dto.result.cli.CliDownloadInfo;
import com.alibaba.himarket.dto.result.common.FileContentResult;
import com.alibaba.himarket.dto.result.common.FileTreeNode;
import com.alibaba.himarket.dto.result.common.ImportResult;
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

    // ==================== Product-based operations ====================

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
     * Submits the current draft for review (publish pipeline).
     *
     * @param productId the product identifier
     * @param version the draft version to submit
     */
    void publishVersion(String productId, String version);

    /**
     * Changes the online status of a specific version.
     *
     * @param productId the product identifier
     * @param version the target version
     * @param online true to put online, false to take offline
     */
    void changeVersionStatus(String productId, String version, boolean online);

    /**
     * Force-publishes a version, bypassing Nacos pipeline validation.
     *
     * @param productId        the product identifier
     * @param version          the version to force-publish
     * @param updateLatestLabel whether to update the "latest" label, null defaults to true
     */
    void forcePublishVersion(String productId, String version, Boolean updateLatestLabel);

    /**
     * Auto-publishes the latest draft/reviewing version to online state.
     * Reads the current editing/reviewing version from Nacos meta, submits it, then force-publishes.
     * Used for personal skills that bypass the admin review workflow.
     *
     * @param productId the product identifier
     */
    void autoPublishLatest(String productId);

    /**
     * Deletes the current editing draft.
     *
     * @param productId the product identifier
     */
    void deleteDraft(String productId);

    /**
     * Sets a version as the latest (current) version.
     *
     * @param productId the product identifier
     * @param version the target version
     */
    void setLatestVersion(String productId, String version);

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

    // ==================== Direct Nacos operations ====================

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

package com.alibaba.himarket.service;

import com.alibaba.himarket.dto.result.cli.CliDownloadInfo;
import com.alibaba.himarket.dto.result.common.FileContentResult;
import com.alibaba.himarket.dto.result.common.FileTreeNode;
import com.alibaba.himarket.dto.result.common.ImportResult;
import com.alibaba.himarket.dto.result.common.VersionResult;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface WorkerService {

    /**
     * Uploads a ZIP package as the AgentSpec for the given product.
     *
     * @param productId the product identifier
     * @param file the ZIP file to upload
     * @throws IOException if an I/O error occurs
     */
    void uploadPackage(String productId, MultipartFile file) throws IOException;

    /**
     * Deletes the AgentSpec associated with the given product.
     *
     * @param productId the product identifier
     */
    void deleteAgentSpec(String productId);

    /**
     * Returns a hierarchical file tree of the AgentSpec contents.
     *
     * @param productId the product identifier
     * @return the file tree nodes
     */
    List<FileTreeNode> getFileTree(String productId, String version);

    /**
     * Returns the content of a single file within the AgentSpec.
     *
     * @param productId the product identifier
     * @param path the file path relative to the AgentSpec root
     * @return the file content result
     */
    FileContentResult getFileContent(String productId, String path, String version);

    /**
     * Returns all published/editing versions for the AgentSpec.
     *
     * @param productId the product identifier
     * @return the version list
     */
    List<VersionResult> listVersions(String productId);

    /**
     * Publishes a specific version.
     *
     * @param productId the product identifier
     * @param version the target version
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
     * Force-publishes a specific version, bypassing the review pipeline.
     *
     * @param productId the product identifier
     * @param version the target version
     * @param updateLatestLabel whether to update the "latest" label, null defaults to true
     */
    void forcePublishVersion(String productId, String version, Boolean updateLatestLabel);

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
     * Downloads the AgentSpec as a ZIP archive.
     *
     * @param productId the product identifier
     * @param version optional version; null for latest
     * @param response the HTTP response to write the ZIP to
     * @throws IOException if an I/O error occurs
     */
    void downloadPackage(String productId, String version, HttpServletResponse response)
            throws IOException;

    /**
     * Gets CLI download info for the frontend detail page.
     *
     * @param productId the product identifier
     * @return the CLI download info containing nacosHost and resource name
     */
    CliDownloadInfo getCliDownloadInfo(String productId);

    /**
     * Import workers from Nacos
     *
     * @param nacosId Nacos instance ID
     * @param namespace Nacos namespace
     * @return import result with success and skipped counts
     */
    ImportResult importFromNacos(String nacosId, String namespace);
}

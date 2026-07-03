package com.alibaba.himarket.service;

import com.alibaba.himarket.dto.params.worker.CreateWorkerDraftParam;
import com.alibaba.himarket.dto.params.worker.UpdateWorkerDraftParam;
import com.alibaba.himarket.dto.params.worker.UpdateWorkerVersionParam;
import com.alibaba.himarket.dto.result.cli.CliDownloadInfo;
import com.alibaba.himarket.dto.result.common.FileContentResult;
import com.alibaba.himarket.dto.result.common.FileTreeNode;
import com.alibaba.himarket.dto.result.common.ImportResult;
import com.alibaba.himarket.dto.result.common.VersionResult;
import com.alibaba.himarket.dto.result.common.WorkerDraftResult;
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
     * Deletes the AgentSpec associated with the given product.
     *
     * @param productId the product identifier
     * @param ignoreError whether to ignore source deletion errors
     */
    void deleteAgentSpec(String productId, boolean ignoreError);

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
     * Updates mutable fields of a specific version.
     *
     * @param productId the product identifier
     * @param version the target version
     * @param param the partial version update request
     */
    void updateVersion(String productId, String version, UpdateWorkerVersionParam param);

    /**
     * Creates a draft by copying an existing AgentSpec version.
     *
     * @param productId the product identifier
     * @param param the draft creation request
     */
    void createDraft(String productId, CreateWorkerDraftParam param);

    /**
     * Returns the current draft AgentSpec card.
     *
     * @param productId the product identifier
     * @return the draft version and AgentSpec card
     */
    WorkerDraftResult getDraft(String productId);

    /**
     * Updates the current draft with a full AgentSpec card.
     *
     * @param productId the product identifier
     * @param param the draft update request
     */
    void updateDraft(String productId, UpdateWorkerDraftParam param);

    /**
     * Deletes the current editing draft.
     *
     * @param productId the product identifier
     */
    void deleteDraft(String productId);

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

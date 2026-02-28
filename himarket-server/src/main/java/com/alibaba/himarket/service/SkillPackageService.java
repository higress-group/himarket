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

package com.alibaba.himarket.service;

import com.alibaba.himarket.dto.result.skill.SkillFileContentResult;
import com.alibaba.himarket.dto.result.skill.SkillFileTreeNode;
import com.alibaba.himarket.dto.result.skill.SkillPackageUploadResult;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface SkillPackageService {

    /**
     * Upload and parse a skill package (zip or tar.gz).
     *
     * @param productId the product ID
     * @param file the uploaded file
     * @return upload result with file count
     * @throws IOException if an IO error occurs
     * @throws ParseException if the package is invalid (e.g., missing SKILL.md)
     */
    SkillPackageUploadResult uploadPackage(String productId, MultipartFile file)
            throws IOException, ParseException;

    /**
     * Get the file tree for a skill product.
     *
     * @param productId the product ID
     * @return list of root-level tree nodes
     */
    List<SkillFileTreeNode> getFileTree(String productId);

    /**
     * Get the content of a single file.
     *
     * @param productId the product ID
     * @param path the file path
     * @return file content result
     */
    SkillFileContentResult getFileContent(String productId, String path);

    /**
     * Get all files with their content, sorted by path.
     *
     * @param productId the product ID
     * @return list of all file content results
     */
    List<SkillFileContentResult> getAllFiles(String productId);

    /**
     * Stream the skill package as a zip file to the HTTP response.
     *
     * @param productId the product ID
     * @param response the HTTP response to write to
     * @throws IOException if an IO error occurs
     */
    void downloadPackage(String productId, HttpServletResponse response) throws IOException;

    /**
     * Update SKILL.md content, syncing both skill_file table and product.document.
     *
     * @param productId the product ID
     * @param content the new SKILL.md content
     */
    void updateSkillMd(String productId, String content);
}

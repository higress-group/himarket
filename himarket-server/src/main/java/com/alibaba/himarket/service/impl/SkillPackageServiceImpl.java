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

package com.alibaba.himarket.service.impl;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.skill.SkillPackageParser;
import com.alibaba.himarket.core.skill.SkillPackageParser.ParseResult;
import com.alibaba.himarket.dto.result.skill.SkillFileContentResult;
import com.alibaba.himarket.dto.result.skill.SkillFileTreeNode;
import com.alibaba.himarket.dto.result.skill.SkillPackageUploadResult;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.entity.SkillFile;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.repository.SkillFileRepository;
import com.alibaba.himarket.service.SkillPackageService;
import com.alibaba.himarket.support.enums.ProductType;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillPackageServiceImpl implements SkillPackageService {

    private static final String PRODUCT = "Product";

    private final ProductRepository productRepository;
    private final SkillFileRepository skillFileRepository;
    private final SkillPackageParser skillPackageParser;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public SkillPackageUploadResult uploadPackage(String productId, MultipartFile file)
            throws IOException, ParseException {
        // 1. 查询 product，不存在抛异常
        Product product =
                productRepository
                        .findByProductId(productId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND, PRODUCT, productId));

        // 2. 校验产品类型
        if (product.getType() != ProductType.AGENT_SKILL) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "产品类型不是 AGENT_SKILL: " + productId);
        }

        // 3. 根据文件名判断格式，调用对应解析方法
        String originalFilename =
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        ParseResult parseResult;
        if (originalFilename.endsWith(".tar.gz") || originalFilename.endsWith(".tgz")) {
            parseResult = skillPackageParser.parseTarGz(file.getInputStream());
        } else if (originalFilename.endsWith(".zip")) {
            parseResult = skillPackageParser.parseZip(file.getInputStream());
        } else {
            // 尝试按 zip 解析（兜底）
            parseResult = skillPackageParser.parseZip(file.getInputStream());
        }

        // 4. 更新 product 字段（name 由用户创建时指定，不覆盖）
        product.setDescription(parseResult.description);
        product.setDocument(parseResult.skillMdContent);
        productRepository.save(product);

        // 5. 删除旧的 skill_file 记录，flush 确保 delete 先于 insert 执行，再批量保存新的
        skillFileRepository.deleteByProductId(productId);
        entityManager.flush();

        List<SkillFile> skillFiles = new ArrayList<>();
        for (SkillPackageParser.SkillFileEntry entry : parseResult.files) {
            SkillFile skillFile =
                    SkillFile.builder()
                            .productId(productId)
                            .path(entry.path)
                            .encoding(entry.encoding)
                            .content(entry.content)
                            .size(entry.size)
                            .build();
            skillFiles.add(skillFile);
        }
        skillFileRepository.saveAll(skillFiles);

        // 6. 返回结果
        SkillPackageUploadResult result = new SkillPackageUploadResult();
        result.setFileCount(skillFiles.size());
        return result;
    }

    @Override
    public List<SkillFileTreeNode> getFileTree(String productId) {
        List<SkillFile> files = skillFileRepository.findByProductId(productId);
        return buildTree(files);
    }

    @Override
    public SkillFileContentResult getFileContent(String productId, String path) {
        SkillFile skillFile =
                skillFileRepository
                        .findByProductIdAndPath(productId, path)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND, "SkillFile", path));

        return toContentResult(skillFile);
    }

    @Override
    public List<SkillFileContentResult> getAllFiles(String productId) {
        return skillFileRepository.findByProductId(productId).stream()
                .sorted(Comparator.comparing(SkillFile::getPath))
                .map(this::toContentResult)
                .collect(Collectors.toList());
    }

    @Override
    public void downloadPackage(String productId, HttpServletResponse response) throws IOException {
        // 1. 查询 product 获取 skillName
        Product product =
                productRepository
                        .findByProductId(productId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND, PRODUCT, productId));

        String skillName = product.getName() != null ? product.getName() : productId;

        // 2. 查询所有 skill_file
        List<SkillFile> files = skillFileRepository.findByProductId(productId);

        // 3. 设置 response 头
        response.setContentType("application/zip");
        String encodedName =
                java.net.URLEncoder.encode(skillName + ".zip", StandardCharsets.UTF_8)
                        .replace("+", "%20");
        response.setHeader(
                "Content-Disposition",
                "attachment; filename=\"" + skillName + ".zip\"; filename*=UTF-8''" + encodedName);

        // 4. 流式写入 zip
        try (OutputStream os = response.getOutputStream();
                ZipOutputStream zos = new ZipOutputStream(os)) {
            for (SkillFile file : files) {
                ZipEntry entry = new ZipEntry(file.getPath());
                zos.putNextEntry(entry);

                if ("base64".equals(file.getEncoding())) {
                    byte[] decoded = Base64.getDecoder().decode(file.getContent());
                    zos.write(decoded);
                } else {
                    zos.write(file.getContent().getBytes(StandardCharsets.UTF_8));
                }

                zos.closeEntry();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void updateSkillMd(String productId, String content) {
        Product product =
                productRepository
                        .findByProductId(productId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND, PRODUCT, productId));

        // 更新 product.document
        product.setDocument(content);
        productRepository.save(product);

        // 同步更新 skill_file 表里的 SKILL.md
        skillFileRepository
                .findByProductIdAndPath(productId, "SKILL.md")
                .ifPresentOrElse(
                        skillFile -> {
                            skillFile.setContent(content);
                            skillFile.setSize(content.getBytes(StandardCharsets.UTF_8).length);
                            skillFileRepository.save(skillFile);
                        },
                        () -> {
                            SkillFile skillFile =
                                    SkillFile.builder()
                                            .productId(productId)
                                            .path("SKILL.md")
                                            .encoding("utf-8")
                                            .content(content)
                                            .size(content.getBytes(StandardCharsets.UTF_8).length)
                                            .build();
                            skillFileRepository.save(skillFile);
                        });
    }

    private SkillFileContentResult toContentResult(SkillFile skillFile) {
        SkillFileContentResult result = new SkillFileContentResult();
        result.setPath(skillFile.getPath());
        result.setContent(skillFile.getContent());
        result.setEncoding(skillFile.getEncoding());
        result.setSize(skillFile.getSize());
        return result;
    }

    /**
     * 按路径构建树形结构，目录在前，按名称排序。
     */
    private List<SkillFileTreeNode> buildTree(List<SkillFile> files) {
        // 用 Map 维护目录节点：path -> node
        Map<String, SkillFileTreeNode> dirMap = new HashMap<>();
        List<SkillFileTreeNode> roots = new ArrayList<>();

        // 先按路径排序，确保父目录先于子文件处理
        List<SkillFile> sorted =
                files.stream()
                        .sorted(Comparator.comparing(SkillFile::getPath))
                        .collect(Collectors.toList());

        for (SkillFile file : sorted) {
            String path = file.getPath();
            String[] parts = path.split("/");

            if (parts.length == 1) {
                // 根目录下的文件
                SkillFileTreeNode node = createFileNode(file, parts[0]);
                roots.add(node);
            } else {
                // 多级路径，确保所有父目录节点存在
                List<SkillFileTreeNode> currentLevel = roots;
                StringBuilder currentPath = new StringBuilder();

                for (int i = 0; i < parts.length - 1; i++) {
                    if (currentPath.length() > 0) currentPath.append("/");
                    currentPath.append(parts[i]);
                    String dirPath = currentPath.toString();

                    if (!dirMap.containsKey(dirPath)) {
                        SkillFileTreeNode dirNode = createDirNode(parts[i], dirPath);
                        dirMap.put(dirPath, dirNode);
                        currentLevel.add(dirNode);
                    }
                    SkillFileTreeNode dirNode = dirMap.get(dirPath);
                    currentLevel = dirNode.getChildren();
                }

                // 添加文件节点到最后一级目录
                SkillFileTreeNode fileNode = createFileNode(file, parts[parts.length - 1]);
                currentLevel.add(fileNode);
            }
        }

        // 递归排序：目录在前，同类型按名称排序
        sortNodes(roots);
        return roots;
    }

    private SkillFileTreeNode createFileNode(SkillFile file, String name) {
        SkillFileTreeNode node = new SkillFileTreeNode();
        node.setName(name);
        node.setPath(file.getPath());
        node.setType("file");
        node.setEncoding(file.getEncoding());
        node.setSize(file.getSize());
        return node;
    }

    private SkillFileTreeNode createDirNode(String name, String path) {
        SkillFileTreeNode node = new SkillFileTreeNode();
        node.setName(name);
        node.setPath(path);
        node.setType("directory");
        node.setChildren(new ArrayList<>());
        return node;
    }

    private void sortNodes(List<SkillFileTreeNode> nodes) {
        nodes.sort(
                Comparator.comparing(
                                (SkillFileTreeNode n) -> "directory".equals(n.getType()) ? 0 : 1)
                        .thenComparing(SkillFileTreeNode::getName));
        for (SkillFileTreeNode node : nodes) {
            if ("directory".equals(node.getType()) && node.getChildren() != null) {
                sortNodes(node.getChildren());
            }
        }
    }
}

package com.alibaba.himarket.controller;

import com.alibaba.himarket.core.annotation.AdminAuth;
import com.alibaba.himarket.core.annotation.PublicAccess;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.skill.SkillZipParser;
import com.alibaba.himarket.dto.result.skill.SkillFileContentResult;
import com.alibaba.himarket.dto.result.skill.SkillFileTreeNode;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.service.SkillService;
import com.alibaba.himarket.support.product.ProductFeature;
import com.alibaba.himarket.support.product.SkillConfig;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
import com.alibaba.nacos.api.model.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Skill 管理", description = "Skill CRUD 操作和视图查询")
@RestController
@RequestMapping("/skills")
@Slf4j
@RequiredArgsConstructor
public class SkillController {

    private static final long MAX_ZIP_SIZE = 10 * 1024 * 1024;

    private final SkillService skillService;
    private final ProductRepository productRepository;

    // ==================== 通过 productId 解析 Nacos 坐标的接口（前端使用） ====================

    /**
     * 从 Product 的 skillConfig 中解析 nacosId、namespace、skillName。
     */
    private SkillCoordinate resolveSkillCoordinate(String productId) {
        Product product =
                productRepository
                        .findByProductId(productId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND, "Product", productId));
        ProductFeature feature = product.getFeature();
        if (feature == null || feature.getSkillConfig() == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "该产品未关联 Nacos 实例，请先在 Link Nacos 页面关联");
        }
        SkillConfig sc = feature.getSkillConfig();
        if (sc.getNacosId() == null || sc.getNacosId().isBlank()) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "该产品未关联 Nacos 实例，请先在 Link Nacos 页面关联");
        }
        String namespace = sc.getNamespace() != null ? sc.getNamespace() : "public";
        return new SkillCoordinate(sc.getNacosId(), namespace, sc.getSkillName());
    }

    private record SkillCoordinate(String nacosId, String namespace, String skillName) {}

    @Operation(summary = "ZIP 上传 Skill（通过 productId）")
    @PostMapping(value = "/{productId}/package", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AdminAuth
    public String uploadSkillByProduct(
            @PathVariable String productId, @RequestParam("file") MultipartFile file)
            throws IOException {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ZIP 文件不能为空");
        }
        if (file.getSize() > MAX_ZIP_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ZIP 文件大小不能超过 10MB");
        }
        SkillCoordinate coord = resolveSkillCoordinate(productId);
        Skill skill = SkillZipParser.parseSkillFromZip(file.getBytes(), coord.namespace());
        String skillName = skillService.createSkill(coord.nacosId(), coord.namespace(), skill);

        // 回写 skillName 和 description 到 product
        Product product = productRepository.findByProductId(productId).orElseThrow();
        product.getFeature().getSkillConfig().setSkillName(skillName);
        product.setDescription(skill.getDescription());
        productRepository.save(product);

        return skillName;
    }

    @Operation(summary = "获取文件树（通过 productId）")
    @GetMapping("/{productId}/files")
    @PublicAccess
    public List<SkillFileTreeNode> getFileTreeByProduct(@PathVariable String productId) {
        SkillCoordinate coord = resolveSkillCoordinate(productId);
        if (coord.skillName() == null || coord.skillName().isBlank()) {
            return List.of();
        }
        return skillService.getFileTree(coord.nacosId(), coord.namespace(), coord.skillName());
    }

    @Operation(summary = "获取单文件内容（通过 productId）")
    @GetMapping("/{productId}/files/{*filePath}")
    @PublicAccess
    public SkillFileContentResult getFileContentByProduct(
            @PathVariable String productId, @PathVariable String filePath) {
        SkillCoordinate coord = resolveSkillCoordinate(productId);
        if (coord.skillName() == null || coord.skillName().isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Skill 尚未上传");
        }
        // 去掉前导斜杠
        String path = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        return skillService.getFileContent(
                coord.nacosId(), coord.namespace(), coord.skillName(), path);
    }

    @Operation(summary = "ZIP 下载 Skill（通过 productId）")
    @GetMapping("/{productId}/download")
    public void downloadSkillByProduct(@PathVariable String productId, HttpServletResponse response)
            throws IOException {
        SkillCoordinate coord = resolveSkillCoordinate(productId);
        if (coord.skillName() == null || coord.skillName().isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Skill 尚未上传");
        }
        skillService.downloadZip(coord.nacosId(), coord.namespace(), coord.skillName(), response);
    }

    // ==================== 直接通过 nacosId 操作的接口（高级管理） ====================

    @Operation(summary = "创建 Skill（直接 Nacos）")
    @PostMapping("/nacos")
    @AdminAuth
    public String createSkill(
            @RequestParam String nacosId,
            @RequestParam(defaultValue = "public") String namespace,
            @RequestBody Skill skill) {
        return skillService.createSkill(nacosId, namespace, skill);
    }

    @Operation(summary = "ZIP 上传创建 Skill（直接 Nacos）")
    @PostMapping(value = "/nacos/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AdminAuth
    public String uploadSkill(
            @RequestParam String nacosId,
            @RequestParam(defaultValue = "public") String namespace,
            @RequestParam("file") MultipartFile file)
            throws IOException {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ZIP 文件不能为空");
        }
        if (file.getSize() > MAX_ZIP_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ZIP 文件大小不能超过 10MB");
        }
        Skill skill = SkillZipParser.parseSkillFromZip(file.getBytes(), namespace);
        return skillService.createSkill(nacosId, namespace, skill);
    }

    @Operation(summary = "分页查询 Skill 列表（直接 Nacos）")
    @GetMapping("/nacos")
    @AdminAuth
    public Page<SkillBasicInfo> listSkills(
            @RequestParam String nacosId,
            @RequestParam(defaultValue = "public") String namespace,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return skillService.listSkills(nacosId, namespace, search, pageNo, pageSize);
    }

    @Operation(summary = "查询 Skill 详情（直接 Nacos）")
    @GetMapping("/nacos/{name}")
    @AdminAuth
    public Skill getSkillDetail(
            @RequestParam String nacosId,
            @RequestParam(defaultValue = "public") String namespace,
            @PathVariable String name) {
        return skillService.getSkillDetail(nacosId, namespace, name);
    }

    @Operation(summary = "更新 Skill（直接 Nacos）")
    @PutMapping("/nacos/{name}")
    @AdminAuth
    public void updateSkill(
            @RequestParam String nacosId,
            @RequestParam(defaultValue = "public") String namespace,
            @PathVariable String name,
            @RequestBody Skill skill) {
        skill.setName(name);
        skillService.updateSkill(nacosId, namespace, skill);
    }

    @Operation(summary = "删除 Skill（直接 Nacos）")
    @DeleteMapping("/nacos/{name}")
    @AdminAuth
    public void deleteSkill(
            @RequestParam String nacosId,
            @RequestParam(defaultValue = "public") String namespace,
            @PathVariable String name) {
        skillService.deleteSkill(nacosId, namespace, name);
    }
}

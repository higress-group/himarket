package com.alibaba.himarket.controller;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.service.acp.runtime.K8sClusterInfo;
import com.alibaba.himarket.service.acp.runtime.K8sConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * K8s 集群配置管理 REST API。
 *
 * <p>提供 kubeconfig 的注册、查询和删除功能，用于 K8s 运行时连接目标 K8s 集群。
 *
 * <p>Requirements: 10.1, 10.5
 */
@Tag(name = "K8s 集群配置", description = "管理 K8s 集群连接配置")
@RestController
@RequestMapping("/k8s")
@RequiredArgsConstructor
public class K8sConfigController {

    private final K8sConfigService k8sConfigService;

    @Operation(summary = "注册 kubeconfig")
    @PostMapping("/config")
    public Map<String, String> registerConfig(@RequestBody Map<String, String> request) {
        String kubeconfig = request.get("kubeconfig");
        if (kubeconfig == null || kubeconfig.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "kubeconfig 内容不能为空");
        }
        String description = request.get("description");
        try {
            String configId = k8sConfigService.registerConfig(kubeconfig, description);
            return Map.of("configId", configId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, e, e.getMessage());
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, e, e.getMessage());
        }
    }

    @Operation(summary = "列出已注册的 K8s 集群")
    @GetMapping("/clusters")
    public List<K8sClusterInfo> listClusters() {
        return k8sConfigService.listClusters();
    }

    @Operation(summary = "删除集群配置")
    @DeleteMapping("/config/{configId}")
    public void removeConfig(@Parameter(description = "集群配置 ID") @PathVariable String configId) {
        try {
            k8sConfigService.removeConfig(configId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "K8s 配置", configId);
        }
    }
}

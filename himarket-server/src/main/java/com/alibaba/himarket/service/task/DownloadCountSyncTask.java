package com.alibaba.himarket.service.task;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.service.NacosService;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.product.SkillConfig;
import com.alibaba.himarket.support.product.WorkerConfig;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecSummary;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task to sync download counts for Skill and Worker products from Nacos.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DownloadCountSyncTask {

    private static final int PAGE_SIZE = 100;

    private final ProductRepository productRepository;
    private final NacosService nacosService;

    @Scheduled(fixedRate = 300_000)
    public void syncDownloadCounts() {
        try {
            syncSkillDownloadCounts();
            syncWorkerDownloadCounts();
        } catch (Exception e) {
            log.error("Unexpected error during download count sync", e);
        }
    }

    private void syncSkillDownloadCounts() {
        List<Product> products =
                productRepository.findAllByType(ProductType.AGENT_SKILL).stream()
                        .filter(
                                p ->
                                        p.getFeature() != null
                                                && p.getFeature().getSkillConfig() != null
                                                && p.getFeature().getSkillConfig().getNacosId()
                                                        != null)
                        .toList();

        if (products.isEmpty()) {
            return;
        }

        products.stream()
                .collect(
                        Collectors.groupingBy(
                                p -> {
                                    SkillConfig c = p.getFeature().getSkillConfig();
                                    return c.getNacosId() + ":" + c.getNamespace();
                                }))
                .forEach(
                        (key, group) -> {
                            Product first = group.get(0);
                            SkillConfig config = first.getFeature().getSkillConfig();
                            syncSkillGroup(config.getNacosId(), config.getNamespace(), group);
                        });
    }

    private void syncWorkerDownloadCounts() {
        List<Product> products =
                productRepository.findAllByType(ProductType.WORKER).stream()
                        .filter(
                                p ->
                                        p.getFeature() != null
                                                && p.getFeature().getWorkerConfig() != null
                                                && p.getFeature().getWorkerConfig().getNacosId()
                                                        != null)
                        .toList();

        if (products.isEmpty()) {
            return;
        }

        products.stream()
                .collect(
                        Collectors.groupingBy(
                                p -> {
                                    WorkerConfig c = p.getFeature().getWorkerConfig();
                                    return c.getNacosId() + ":" + c.getNamespace();
                                }))
                .forEach(
                        (key, group) -> {
                            Product first = group.get(0);
                            WorkerConfig config = first.getFeature().getWorkerConfig();
                            syncWorkerGroup(config.getNacosId(), config.getNamespace(), group);
                        });
    }

    private void syncSkillGroup(String nacosId, String namespace, List<Product> products) {
        try {
            AiMaintainerService aiService = nacosService.getAiMaintainerService(nacosId);

            Map<String, Long> downloadCountMap = new HashMap<>();
            int pageNo = 1;
            while (true) {
                Page<SkillSummary> page =
                        aiService.skill().listSkills(namespace, null, null, pageNo, PAGE_SIZE);
                if (page == null || CollUtil.isEmpty(page.getPageItems())) {
                    break;
                }
                for (SkillSummary summary : page.getPageItems()) {
                    downloadCountMap.putIfAbsent(summary.getName(), summary.getDownloadCount());
                }
                if (page.getPageItems().size() < PAGE_SIZE) {
                    break;
                }
                pageNo++;
            }

            if (downloadCountMap.isEmpty()) {
                return;
            }

            for (Product product : products) {
                try {
                    SkillConfig config = product.getFeature().getSkillConfig();
                    Long count = downloadCountMap.get(config.getSkillName());

                    if (count != null && !Objects.equals(config.getDownloadCount(), count)) {
                        config.setDownloadCount(count);
                        productRepository.save(product);
                        log.info(
                                "Synced download count for skill product {}: {}",
                                product.getProductId(),
                                count);
                    }
                } catch (Exception e) {
                    log.warn(
                            "Failed to sync download count for skill product {}",
                            product.getProductId(),
                            e);
                }
            }
        } catch (Exception e) {
            log.warn(
                    "Failed to sync download counts for skill products from Nacos {}: {}",
                    nacosId,
                    e.getMessage());
        }
    }

    private void syncWorkerGroup(String nacosId, String namespace, List<Product> products) {
        try {
            AiMaintainerService aiService = nacosService.getAiMaintainerService(nacosId);

            Map<String, Long> downloadCountMap = new HashMap<>();
            int pageNo = 1;
            while (true) {
                Page<AgentSpecSummary> page =
                        aiService
                                .agentSpec()
                                .listAgentSpecAdminItems(namespace, null, null, pageNo, PAGE_SIZE);
                if (page == null || CollUtil.isEmpty(page.getPageItems())) {
                    break;
                }
                for (AgentSpecSummary summary : page.getPageItems()) {
                    downloadCountMap.putIfAbsent(summary.getName(), summary.getDownloadCount());
                }
                if (page.getPageItems().size() < PAGE_SIZE) {
                    break;
                }
                pageNo++;
            }

            if (downloadCountMap.isEmpty()) {
                return;
            }

            for (Product product : products) {
                try {
                    WorkerConfig config = product.getFeature().getWorkerConfig();
                    Long count = downloadCountMap.get(config.getAgentSpecName());

                    if (count != null && !Objects.equals(config.getDownloadCount(), count)) {
                        config.setDownloadCount(count);
                        productRepository.save(product);
                        log.info(
                                "Synced download count for worker product {}: {}",
                                product.getProductId(),
                                count);
                    }
                } catch (Exception e) {
                    log.warn(
                            "Failed to sync download count for worker product {}",
                            product.getProductId(),
                            e);
                }
            }
        } catch (Exception e) {
            log.warn(
                    "Failed to sync download counts for worker products from Nacos {}: {}",
                    nacosId,
                    e.getMessage());
        }
    }
}

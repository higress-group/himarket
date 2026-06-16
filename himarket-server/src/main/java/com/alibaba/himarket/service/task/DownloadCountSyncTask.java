package com.alibaba.himarket.service.task;

import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.service.AiRegistrySkillService;
import com.alibaba.himarket.service.NacosService;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.enums.SkillRegistryType;
import com.alibaba.himarket.support.product.SkillConfig;
import com.alibaba.himarket.support.product.WorkerConfig;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecSummary;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final AiRegistrySkillService aiRegistrySkillService;

    @Scheduled(fixedRate = 300_000)
    public void syncDownloadCounts() {
        try {
            syncSkillDownloadCounts();
            syncWorkerDownloadCounts();
        } catch (Exception e) {
            log.error(
                    "Unexpected error during download count sync, errorMessage={}",
                    e.getMessage(),
                    e);
        }
    }

    private void syncSkillDownloadCounts() {
        List<Product> skillProducts = productRepository.findAllByType(ProductType.AGENT_SKILL);
        List<Product> nacosProducts =
                skillProducts.stream()
                        .filter(
                                p ->
                                        p.getFeature() != null
                                                && p.getFeature().getSkillConfig() != null
                                                && isNacosSkillConfig(
                                                        p.getFeature().getSkillConfig()))
                        .toList();

        if (!nacosProducts.isEmpty()) {
            Map<String, List<Product>> productsByNacos = new HashMap<>();
            for (Product product : nacosProducts) {
                SkillConfig config = product.getFeature().getSkillConfig();
                String groupKey = config.getNacosId() + ":" + config.getNamespace();
                productsByNacos.computeIfAbsent(groupKey, key -> new ArrayList<>()).add(product);
            }

            for (List<Product> group : productsByNacos.values()) {
                SkillConfig config = group.get(0).getFeature().getSkillConfig();
                syncSkillGroup(config.getNacosId(), config.getNamespace(), group);
            }
        }

        Map<String, List<Product>> productsByAiRegistry = new HashMap<>();
        for (Product product : skillProducts) {
            if (product.getFeature() == null || product.getFeature().getSkillConfig() == null) {
                continue;
            }

            SkillConfig config = product.getFeature().getSkillConfig();
            if (!isAiRegistrySkillConfig(config)) {
                continue;
            }

            String groupKey = config.getAiRegistryId() + ":" + config.getNamespace();
            productsByAiRegistry.computeIfAbsent(groupKey, key -> new ArrayList<>()).add(product);
        }

        for (List<Product> group : productsByAiRegistry.values()) {
            SkillConfig config = group.get(0).getFeature().getSkillConfig();
            syncAiRegistrySkillGroup(config.getAiRegistryId(), config.getNamespace(), group);
        }
    }

    private boolean isNacosSkillConfig(SkillConfig config) {
        SkillRegistryType registryType = config.getRegistryType();
        return (registryType == null || registryType == SkillRegistryType.NACOS)
                && config.getNacosId() != null;
    }

    private boolean isAiRegistrySkillConfig(SkillConfig config) {
        return config.getRegistryType() == SkillRegistryType.AIREGISTRY
                && config.getAiRegistryId() != null;
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

        Map<String, List<Product>> productsByNacos = new HashMap<>();
        for (Product product : products) {
            WorkerConfig config = product.getFeature().getWorkerConfig();
            String groupKey = config.getNacosId() + ":" + config.getNamespace();
            productsByNacos.computeIfAbsent(groupKey, key -> new ArrayList<>()).add(product);
        }

        for (List<Product> group : productsByNacos.values()) {
            WorkerConfig config = group.get(0).getFeature().getWorkerConfig();
            syncWorkerGroup(config.getNacosId(), config.getNamespace(), group);
        }
    }

    private void syncSkillGroup(String nacosId, String namespace, List<Product> products) {
        try {
            AiMaintainerService aiService = nacosService.getAiMaintainerService(nacosId);

            Map<String, Long> downloadCountMap = new HashMap<>();
            int pageNo = 1;
            while (true) {
                Page<SkillSummary> page =
                        aiService.skill().listSkills(namespace, null, null, pageNo, PAGE_SIZE);
                if (page == null || page.getPageItems() == null || page.getPageItems().isEmpty()) {
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
                                "Synced download count for skill product, productId={},"
                                        + " downloadCount={}",
                                product.getProductId(),
                                count);
                    }
                } catch (Exception e) {
                    log.warn(
                            "Failed to sync download count for skill product, productId={}",
                            product.getProductId(),
                            e);
                }
            }
        } catch (Exception e) {
            log.warn(
                    "Failed to sync download counts for skill products from Nacos, nacosId={},"
                            + " errorMessage={}",
                    nacosId,
                    e.getMessage(),
                    e);
        }
    }

    private void syncAiRegistrySkillGroup(
            String aiRegistryId, String namespace, List<Product> products) {
        try {
            Map<String, Long> downloadCountMap =
                    aiRegistrySkillService.listSkillDownloadCounts(aiRegistryId, namespace);
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
                                "Synced download count for AIRegistry skill product, productId={},"
                                        + " downloadCount={}",
                                product.getProductId(),
                                count);
                    }
                } catch (Exception e) {
                    log.warn(
                            "Failed to sync download count for AIRegistry skill product,"
                                    + " productId={}",
                            product.getProductId(),
                            e);
                }
            }
        } catch (Exception e) {
            log.warn(
                    "Failed to sync download counts for skill products from AIRegistry,"
                            + " aiRegistryId={}, errorMessage={}",
                    aiRegistryId,
                    e.getMessage(),
                    e);
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
                if (page == null || page.getPageItems() == null || page.getPageItems().isEmpty()) {
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
                                "Synced download count for worker product, productId={},"
                                        + " downloadCount={}",
                                product.getProductId(),
                                count);
                    }
                } catch (Exception e) {
                    log.warn(
                            "Failed to sync download count for worker product, productId={}",
                            product.getProductId(),
                            e);
                }
            }
        } catch (Exception e) {
            log.warn(
                    "Failed to sync download counts for worker products from Nacos, nacosId={},"
                            + " errorMessage={}",
                    nacosId,
                    e.getMessage(),
                    e);
        }
    }
}

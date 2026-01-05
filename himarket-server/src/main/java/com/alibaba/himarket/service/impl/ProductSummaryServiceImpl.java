package com.alibaba.himarket.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.core.event.ProductDeletingEvent;
import com.alibaba.himarket.core.event.ProductSummaryUpdateEvent;
import com.alibaba.himarket.core.event.ProductUpdateEvent;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.params.product.QueryProductParam;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.product.ProductSummaryResult;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.entity.ProductCategoryRelation;
import com.alibaba.himarket.entity.ProductPublication;
import com.alibaba.himarket.entity.ProductSummary;
import com.alibaba.himarket.repository.ChatRepository;
import com.alibaba.himarket.repository.ProductLikeRepository;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.repository.ProductSummaryRepository;
import com.alibaba.himarket.repository.SubscriptionRepository;
import com.alibaba.himarket.service.ProductCategoryService;
import com.alibaba.himarket.service.ProductSummaryService;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ProductSummaryServiceImpl implements ProductSummaryService {
    private final ContextHolder contextHolder;

    private final ProductRepository productRepository;
    private final ProductSummaryRepository productSummaryRepository;
    private final ProductCategoryService productCategoryService;
    private final SubscriptionRepository subscriptionRepository;
    private final ChatRepository chatRepository;
    private final ProductLikeRepository productLikeRepository;

    @Override
    public void syncProductSummary(String productId) {
        try {
            // Get product information
            Optional<Product> productOptional = productRepository.findByProductId(productId);
            if (!productOptional.isPresent()) {
                log.warn("Product not found with id: {}", productId);
                // If product does not exist, delete the corresponding statistics record
                productSummaryRepository.deleteByProductId(productId);
                return;
            }

            Product product = productOptional.get();

            // Get or create statistics record
            ProductSummary summary =
                    productSummaryRepository
                            .findByProductId(productId)
                            .orElse(new ProductSummary());

            // Synchronize all product fields
            summary.setProductId(product.getProductId());
            summary.setName(product.getName());
            summary.setType(product.getType());
            summary.setDescription(product.getDescription());
            summary.setIcon(product.getIcon());

            // Get and set subscription count
            Long subscriptionCount =
                    subscriptionRepository.countApprovedSubscriptionsByProductId(productId);
            summary.setSubscriptionCount(subscriptionCount);

            // Get and set usage count
            Long usageCount = chatRepository.countChatsByProductId(productId);
            summary.setUsageCount(usageCount);

            // Get and set like count
            Long likesCount = productLikeRepository.countByProductIdAndStatus(productId);
            summary.setLikesCount(likesCount);

            // Save record
            productSummaryRepository.save(summary);
            log.info("Successfully synced product summary for product: {}", productId);
        } catch (Exception e) {
            log.error("Error syncing product summary for product: {}", productId, e);
        }
    }

    /**
     * Batch update statistics table to ensure consistency between statistics and product table
     */
    @Override
    public void syncAllProductSummary() {
        List<Product> allProducts = productRepository.findAll();
        // Get statistics data for all products
        Map<String, Long> subscriptionCounts = getSubscriptionCounts();
        Map<String, Long> usageCounts = getUsageCounts();
        Map<String, Long> likesCounts = getLikesCounts();
        // Get all existing statistics records
        List<ProductSummary> existingStats = productSummaryRepository.findAll();
        Map<String, ProductSummary> existingStatsMap =
                existingStats.stream()
                        .collect(Collectors.toMap(ProductSummary::getProductId, s -> s));

        // Prepare list of records to update
        List<ProductSummary> productSummaries = new ArrayList<>();

        // Create or update ProductSummary for each product
        for (Product product : allProducts) {
            String productId = product.getProductId();
            ProductSummary summary = existingStatsMap.getOrDefault(productId, new ProductSummary());

            // Synchronize all product fields
            summary.setProductId(product.getProductId());
            summary.setName(product.getName());
            summary.setType(product.getType());
            summary.setDescription(product.getDescription());
            summary.setIcon(product.getIcon());
            summary.setCreateAt(product.getCreateAt());
            summary.setUpdatedAt(product.getUpdatedAt());

            // Set subscription count (default to 0 if no subscriptions)
            Long subscriptionCount = subscriptionCounts.getOrDefault(productId, 0L);
            summary.setSubscriptionCount(subscriptionCount);

            // Set usage count (default to 0 if no usage records)
            Long usageCount = usageCounts.getOrDefault(productId, 0L);
            summary.setUsageCount(usageCount);

            // Set like count (default to 0 if no like records)
            Long likesCount = likesCounts.getOrDefault(productId, 0L);
            summary.setLikesCount(likesCount);

            productSummaries.add(summary);
        }

        // Batch save updates
        if (!productSummaries.isEmpty()) {
            // Batch save to avoid large transactions
            saveProductSummaryBatch(productSummaries);
            log.info("Updated {} product statistics records", productSummaries.size());
        }

        // Delete statistics records for products that no longer exist
        List<String> allProductIds =
                allProducts.stream().map(Product::getProductId).collect(Collectors.toList());

        List<ProductSummary> statsToDelete =
                existingStats.stream()
                        .filter(summary -> !allProductIds.contains(summary.getProductId()))
                        .collect(Collectors.toList());

        if (!statsToDelete.isEmpty()) {
            // Batch delete to avoid large transactions
            deleteProductSummaryBatch(statsToDelete);
            log.info("Deleted {} outdated product statistics records", statsToDelete.size());
        }
    }

    /**
     * Get subscription count for all products
     *
     * @return mapping from product ID to subscription count
     */
    private Map<String, Long> getSubscriptionCounts() {
        List<Object[]> results =
                subscriptionRepository.countApprovedSubscriptionsGroupedByProductId();
        Map<String, Long> subscriptionCounts = new HashMap<>();
        for (Object[] result : results) {
            String productId = (String) result[0];
            Long count = (Long) result[1];
            subscriptionCounts.put(productId, count);
        }
        return subscriptionCounts;
    }

    /**
     * Get usage count for all products
     *
     * @return mapping from product ID to usage count
     */
    private Map<String, Long> getUsageCounts() {
        List<Object[]> results = chatRepository.countChatsGroupedByProductId();
        Map<String, Long> usageCounts = new HashMap<>();
        for (Object[] result : results) {
            String productId = (String) result[0];
            Long count = (Long) result[1];
            usageCounts.put(productId, count);
        }
        return usageCounts;
    }

    /**
     * Get like count for all products
     *
     * @return mapping from product ID to like count
     */
    private Map<String, Long> getLikesCounts() {
        List<Object[]> results = productLikeRepository.countLikesGroupedByProductId();
        Map<String, Long> likesCounts = new HashMap<>();
        for (Object[] result : results) {
            String productId = (String) result[0];
            Long count = (Long) result[1];
            likesCounts.put(productId, count);
        }
        return likesCounts;
    }

    @Override
    public PageResult<ProductSummaryResult> listPortalProducts(
            QueryProductParam param, Pageable pageable) {
        if (contextHolder.isDeveloper()) {
            param.setPortalId(contextHolder.getPortal());
        }
        if (productSummaryRepository.count() != productRepository.count()) {
            syncAllProductSummary();
        }
        Page<ProductSummary> products =
                productSummaryRepository.findAll(buildSpecificationSummary(param), pageable);
        return new PageResult<ProductSummaryResult>()
                .convertFrom(
                        products,
                        product -> {
                            ProductSummaryResult result =
                                    new ProductSummaryResult().convertFrom(product);
                            fillProduct(result);
                            return result;
                        });
    }

    private void fillProduct(ProductSummaryResult product) {
        // Fill product category information
        product.setCategories(
                productCategoryService.listCategoriesForProduct(product.getProductId()));
    }

    private Specification<ProductSummary> buildSpecificationSummary(QueryProductParam param) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StrUtil.isNotBlank(param.getPortalId())) {
                Subquery<String> subquery = query.subquery(String.class);
                Root<ProductPublication> publicationRoot = subquery.from(ProductPublication.class);
                subquery.select(publicationRoot.get("productId"))
                        .where(cb.equal(publicationRoot.get("portalId"), param.getPortalId()));
                predicates.add(root.get("productId").in(subquery));
            }

            if (param.getType() != null) {
                predicates.add(cb.equal(root.get("type"), param.getType()));
            }

            if (param.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), param.getStatus()));
            }

            if (StrUtil.isNotBlank(param.getName())) {
                String likePattern = "%" + param.getName() + "%";
                predicates.add(cb.like(root.get("name"), likePattern));
            }

            if (CollUtil.isNotEmpty(param.getCategoryIds())) {
                Subquery<String> subquery = query.subquery(String.class);
                Root<ProductCategoryRelation> relationRoot =
                        subquery.from(ProductCategoryRelation.class);
                subquery.select(relationRoot.get("productId"))
                        .where(relationRoot.get("categoryId").in(param.getCategoryIds()));
                predicates.add(root.get("productId").in(subquery));
            }

            if (StrUtil.isNotBlank(param.getExcludeCategoryId())) {
                Subquery<String> subquery = query.subquery(String.class);
                Root<ProductCategoryRelation> relationRoot =
                        subquery.from(ProductCategoryRelation.class);
                subquery.select(relationRoot.get("productId"))
                        .where(
                                cb.equal(
                                        relationRoot.get("categoryId"),
                                        param.getExcludeCategoryId()));
                predicates.add(cb.not(root.get("productId").in(subquery)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Batch delete product statistics data
     *
     * @param statisticsList list of statistics data
     */
    @Transactional
    public void deleteProductSummaryBatch(List<ProductSummary> statisticsList) {
        // Batch delete to avoid large transactions
        int batchSize = 100;
        for (int i = 0; i < statisticsList.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, statisticsList.size());
            List<ProductSummary> batch = statisticsList.subList(i, endIndex);
            productSummaryRepository.deleteAll(batch);
            log.debug("Deleted {} product statistics records", batch.size());
        }
    }

    /**
     * Batch save product statistics data
     *
     * @param statisticsList list of statistics data
     */
    @Transactional
    public void saveProductSummaryBatch(List<ProductSummary> statisticsList) {
        // Batch save to avoid large transactions
        int batchSize = 100;
        for (int i = 0; i < statisticsList.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, statisticsList.size());
            List<ProductSummary> batch = statisticsList.subList(i, endIndex);
            productSummaryRepository.saveAll(batch);
            log.debug("Saved {} product statistics records", batch.size());
        }
    }

    /**
     * Listen for product statistics update events and handle them asynchronously
     *
     * @param event product statistics update event
     */
    @EventListener
    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductSummaryUpdate(ProductSummaryUpdateEvent event) {
        try {
            String productId = event.getProductId();
            // Switch to full statistics, recalculate all statistics data for this product
            syncProductSummary(productId);

            log.info("Recomputed product statistics for product: {}", productId);
        } catch (Exception e) {
            log.error(
                    "Error recomputing product statistics for product: " + event.getProductId(), e);
        }
    }

    @EventListener
    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductSummaryCreation(ProductUpdateEvent event) {
        String productId = event.getProductId();
        log.info("Handling product update for product {}", productId);
        this.syncProductSummary(productId);
    }

    @EventListener
    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductSummaryDeletion(ProductDeletingEvent event) {
        String productId = event.getProductId();
        log.info("Handling product deletion for product {}", productId);
        productSummaryRepository.deleteByProductId(productId);
    }
}

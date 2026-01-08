package com.alibaba.himarket.repository;

import com.alibaba.himarket.entity.ProductSummary;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductSummaryRepository
        extends JpaRepository<ProductSummary, Long>, JpaSpecificationExecutor<ProductSummary> {
    void deleteByProductId(String productId);

    Optional<ProductSummary> findByProductId(String productId);
}

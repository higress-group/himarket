package com.alibaba.himarket.entity;

import com.alibaba.himarket.converter.IconConverter;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.product.Icon;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "product_summary")
@Data
public class ProductSummary extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", length = 64, nullable = false)
    private String productId;

    @Column(name = "name", length = 64, nullable = false)
    private String name;

    @Column(name = "type", length = 64)
    @Enumerated(EnumType.STRING)
    private ProductType type;

    @Column(name = "description", length = 256)
    private String description;

    @Column(name = "icon", columnDefinition = "json")
    @Convert(converter = IconConverter.class)
    private Icon icon;

    @Column(name = "subscription_count", nullable = false)
    private Long subscriptionCount;

    @Column(name = "usage_count", nullable = false)
    private Long usageCount;

    @Column(name = "likes_count", nullable = false)
    private Long likesCount;
}

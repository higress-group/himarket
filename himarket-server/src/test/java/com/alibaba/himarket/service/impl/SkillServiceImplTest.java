package com.alibaba.himarket.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.repository.ProductPublicationRepository;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.support.enums.ProductStatus;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.product.ProductFeature;
import com.alibaba.himarket.support.product.SkillConfig;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SkillServiceImplTest {

    @Mock private ProductRepository productRepository;

    @Mock private ProductPublicationRepository publicationRepository;

    @InjectMocks private SkillServiceImpl skillService;

    private Product publishedSkillProduct;

    @BeforeEach
    void setUp() {
        SkillConfig skillConfig = new SkillConfig();
        skillConfig.setDownloadCount(5L);

        ProductFeature feature = new ProductFeature();
        feature.setSkillConfig(skillConfig);

        publishedSkillProduct = new Product();
        publishedSkillProduct.setProductId("prod-001");
        publishedSkillProduct.setType(ProductType.AGENT_SKILL);
        publishedSkillProduct.setStatus(ProductStatus.PUBLISHED);
        publishedSkillProduct.setDocument("---\nname: test-skill\n---\n# Test Skill\n");
        publishedSkillProduct.setFeature(feature);
    }

    @Test
    void downloadSkill_success_returnsDocumentAndIncrementsCount() {
        when(productRepository.findByProductId("prod-001"))
                .thenReturn(Optional.of(publishedSkillProduct));
        when(publicationRepository.existsByProductId("prod-001")).thenReturn(true);
        when(productRepository.save(any(Product.class))).thenReturn(publishedSkillProduct);

        String result = skillService.downloadSkill("prod-001");

        assertEquals("---\nname: test-skill\n---\n# Test Skill\n", result);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertEquals(6L, captor.getValue().getFeature().getSkillConfig().getDownloadCount());
    }

    @Test
    void downloadSkill_productNotFound_throws404() {
        when(productRepository.findByProductId("nonexistent")).thenReturn(Optional.empty());

        BusinessException ex =
                assertThrows(
                        BusinessException.class, () -> skillService.downloadSkill("nonexistent"));
        assertEquals(404, ex.getStatus().value());
    }

    @Test
    void downloadSkill_notAgentSkillType_throws404() {
        Product restApiProduct = new Product();
        restApiProduct.setProductId("prod-002");
        restApiProduct.setType(ProductType.REST_API);

        when(productRepository.findByProductId("prod-002")).thenReturn(Optional.of(restApiProduct));

        BusinessException ex =
                assertThrows(BusinessException.class, () -> skillService.downloadSkill("prod-002"));
        assertEquals(404, ex.getStatus().value());
    }

    @Test
    void downloadSkill_notPublished_throws404() {
        when(productRepository.findByProductId("prod-001"))
                .thenReturn(Optional.of(publishedSkillProduct));
        when(publicationRepository.existsByProductId("prod-001")).thenReturn(false);

        BusinessException ex =
                assertThrows(BusinessException.class, () -> skillService.downloadSkill("prod-001"));
        assertEquals(404, ex.getStatus().value());
    }

    @Test
    void downloadSkill_nullFeature_initializesAndIncrementsCount() {
        publishedSkillProduct.setFeature(null);

        when(productRepository.findByProductId("prod-001"))
                .thenReturn(Optional.of(publishedSkillProduct));
        when(publicationRepository.existsByProductId("prod-001")).thenReturn(true);
        when(productRepository.save(any(Product.class))).thenReturn(publishedSkillProduct);

        skillService.downloadSkill("prod-001");

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertEquals(1L, captor.getValue().getFeature().getSkillConfig().getDownloadCount());
    }

    @Test
    void downloadSkill_nullDownloadCount_initializesToOne() {
        publishedSkillProduct.getFeature().getSkillConfig().setDownloadCount(null);

        when(productRepository.findByProductId("prod-001"))
                .thenReturn(Optional.of(publishedSkillProduct));
        when(publicationRepository.existsByProductId("prod-001")).thenReturn(true);
        when(productRepository.save(any(Product.class))).thenReturn(publishedSkillProduct);

        skillService.downloadSkill("prod-001");

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertEquals(1L, captor.getValue().getFeature().getSkillConfig().getDownloadCount());
    }
}

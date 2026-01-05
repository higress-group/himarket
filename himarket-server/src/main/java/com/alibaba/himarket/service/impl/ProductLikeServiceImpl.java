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

import com.alibaba.himarket.core.event.ProductDeletingEvent;
import com.alibaba.himarket.core.event.ProductSummaryUpdateEvent;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.core.utils.IdGenerator;
import com.alibaba.himarket.dto.result.product.ProductLikeResult;
import com.alibaba.himarket.entity.ProductLike;
import com.alibaba.himarket.repository.ProductLikeRepository;
import com.alibaba.himarket.service.ProductLikeService;
import com.alibaba.himarket.support.enums.LikeStatus;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ProductLikeServiceImpl implements ProductLikeService {

    private final ProductLikeRepository productLikeRepository;
    private final ContextHolder contextHolder;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public ProductLikeResult toggleLike(String productId) {
        String developerId = contextHolder.getUser();

        // Find the existing like record for the product by the current user
        Optional<ProductLike> existingLikeOpt =
                productLikeRepository.findByProductIdAndDeveloperId(productId, developerId);
        ProductLike like = new ProductLike();

        if (existingLikeOpt.isPresent()) {
            // If there is an existing like record, toggle its status
            like = existingLikeOpt.get();
            like.setStatus(
                    LikeStatus.LIKED.equals(like.getStatus())
                            ? LikeStatus.UNLIKED
                            : LikeStatus.LIKED);
        } else {
            // If there is no like record, create a new like record
            like.setLikeId(IdGenerator.genLikeId());
            like.setProductId(productId);
            like.setDeveloperId(developerId);
            like.setPortalId(contextHolder.getPortal());
            like.setStatus(LikeStatus.LIKED);
        }
        eventPublisher.publishEvent(
                new ProductSummaryUpdateEvent(
                        this, productId, ProductSummaryUpdateEvent.UpdateType.LIKES_COUNT));
        ProductLike save = productLikeRepository.save(like);
        return new ProductLikeResult().convertFrom(save);
    }

    @EventListener
    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductLikeDeletion(ProductDeletingEvent event) {
        String productId = event.getProductId();
        log.info("Cleaning like for product {}", productId);
        productLikeRepository.deleteAllByProductId(productId);
    }
}

package com.alibaba.himarket.core.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ProductSummaryUpdateEvent extends ApplicationEvent {

    private final String productId;

    public ProductSummaryUpdateEvent(String productId) {
        super(productId);
        this.productId = productId;
    }
}

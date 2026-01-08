package com.alibaba.himarket.core.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ProductSummaryUpdateEvent extends ApplicationEvent {

    private final String productId;
    private final UpdateType updateType;

    public enum UpdateType {
        SUBSCRIPTION_COUNT,
        USAGE_COUNT,
        LIKES_COUNT
    }

    public ProductSummaryUpdateEvent(Object source, String productId, UpdateType updateType) {
        super(source);
        this.productId = productId;
        this.updateType = updateType;
    }
}

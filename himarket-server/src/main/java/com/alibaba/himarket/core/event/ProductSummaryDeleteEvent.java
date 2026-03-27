package com.alibaba.himarket.core.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ProductSummaryDeleteEvent extends ApplicationEvent {

    private final String productId;

    public ProductSummaryDeleteEvent(String productId) {
        super(productId);
        this.productId = productId;
    }
}

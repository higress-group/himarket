package com.alibaba.himarket.core.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ProductUpdateEvent extends ApplicationEvent {

    private final String productId;

    public ProductUpdateEvent(String productId) {
        super(productId);
        this.productId = productId;
    }
}

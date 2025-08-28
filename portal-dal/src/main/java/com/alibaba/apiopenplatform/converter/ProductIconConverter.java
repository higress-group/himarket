package com.alibaba.apiopenplatform.converter;

import com.alibaba.apiopenplatform.support.product.ProductIcon;

import javax.persistence.Converter;

/**
 * @author zh
 */
@Converter(autoApply = true)
public class ProductIconConverter extends JsonConverter<ProductIcon> {

    protected ProductIconConverter() {
        super(ProductIcon.class);
    }
}


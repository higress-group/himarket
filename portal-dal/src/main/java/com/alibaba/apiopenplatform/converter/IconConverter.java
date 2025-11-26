package com.alibaba.apiopenplatform.converter;

import com.alibaba.apiopenplatform.support.product.Icon;

import jakarta.persistence.Converter;

/**
 * @author zh
 */
@Converter(autoApply = true)
public class IconConverter extends JsonConverter<Icon> {

    protected IconConverter() {
        super(Icon.class);
    }
}


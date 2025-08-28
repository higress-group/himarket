package com.alibaba.apiopenplatform.support.product;

import com.alibaba.apiopenplatform.support.enums.ProductIconType;
import lombok.Data;

/**
 * @author zh
 */
@Data
public class ProductIcon {

    private ProductIconType type;

    private String value;
}

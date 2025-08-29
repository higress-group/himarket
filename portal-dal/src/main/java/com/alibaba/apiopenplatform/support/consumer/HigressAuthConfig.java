package com.alibaba.apiopenplatform.support.consumer;

import lombok.Builder;
import lombok.Data;

/**
 * @author zh
 */
@Data
@Builder
public class HigressAuthConfig {

    private String resourceType;

    private String resourceName;
}

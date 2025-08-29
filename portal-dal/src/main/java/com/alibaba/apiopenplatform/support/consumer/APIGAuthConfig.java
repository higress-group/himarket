package com.alibaba.apiopenplatform.support.consumer;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @author zh
 */
@Data
@Builder
public class APIGAuthConfig {

    private List<String> authorizationRuleIds;
}

package com.alibaba.apiopenplatform.dto.result.temp;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @author zh
 */
@Data
@Builder
public class HttpRouteMatchResult {

    private List<String> methods;
    private HttpRouteMatchPath path;

    private List<HttpRouteMatchHeader> headers;
    private List<HttpRouteMatchQuery> queryParams;
}
package com.alibaba.apiopenplatform.dto.result.temp;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @author zh
 */
@Data
@Builder
public class HigressRouteMatchResult {

    private List<String> methods;
    private HigressRouteMatchPath path;

    private List<HigressRouteMatchHeader> headers;
    private List<HigressRouteMatchQuery> queryParams;
}
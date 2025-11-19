package com.alibaba.apiopenplatform.dto.result.temp;

import lombok.Builder;
import lombok.Data;

/**
 * @author zh
 */
@Data
@Builder
public class HigressRouteMatchPath {
    private String value;
    private String type;
    private Boolean caseSensitive;
}

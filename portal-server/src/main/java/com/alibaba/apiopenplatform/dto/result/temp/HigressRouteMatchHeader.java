package com.alibaba.apiopenplatform.dto.result.temp;

import lombok.Builder;
import lombok.Data;

/**
 * @author zh
 */
@Data
@Builder
public class HigressRouteMatchHeader {
    private String name;
    private String type;
    private String value;
    private Boolean caseSensitive;
}

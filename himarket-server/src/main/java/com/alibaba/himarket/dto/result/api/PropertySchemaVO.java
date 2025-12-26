package com.alibaba.himarket.dto.result.api;

import com.alibaba.himarket.support.enums.PropertyType;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PropertySchemaVO {
    private PropertyType type;
    private String name;
    private String description;
    private List<PropertyFieldVO> fields;
}

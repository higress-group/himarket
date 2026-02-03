package com.alibaba.himarket.dto.result.api;

import com.alibaba.himarket.support.enums.APIType;
import com.alibaba.himarket.support.enums.PolicyType;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertySchemaVO {
    private PolicyType type;
    private String name;
    private String description;
    private List<PropertyFieldVO> fields;
    private List<APIType> supportedApiTypes;
}

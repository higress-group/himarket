package com.alibaba.himarket.dto.result.api;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyFieldVO {
    private String name;
    private String label;
    private String type; // string, integer, boolean, select
    private String description;
    private boolean required;
    private List<String> options; // for select
    private Object defaultValue;
}

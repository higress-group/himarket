package com.alibaba.himarket.dto.result.api;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PropertyFieldVO {
    private String name;
    private String label;
    private String type; // string, integer, boolean, select
    private String description;
    private boolean required;
    private List<String> options; // for select
    private Object defaultValue;
}

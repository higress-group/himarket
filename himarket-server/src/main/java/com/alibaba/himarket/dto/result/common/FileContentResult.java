package com.alibaba.himarket.dto.result.common;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FileContentResult {

    private String path;

    private String content;

    private String encoding;

    private Integer size;
}

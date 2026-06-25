package com.alibaba.himarket.dto.result.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileContentResult {

    private String path;

    private String content;

    private String encoding;

    private Integer size;
}

package com.alibaba.himarket.dto.result.common;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileTreeNode {

    /**
     * Name of the file or directory
     */
    private String name;

    /**
     * Full path relative to template root
     */
    private String path;

    /**
     * file or directory
     */
    private String type;

    /**
     * text or base64 (file nodes only)
     */
    private String encoding;

    /**
     * Size in bytes (file nodes only)
     */
    private Integer size;

    /**
     * Children (directory nodes only)
     */
    private List<FileTreeNode> children;
}

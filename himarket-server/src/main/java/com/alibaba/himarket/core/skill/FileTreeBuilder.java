package com.alibaba.himarket.core.skill;

import com.alibaba.himarket.dto.result.common.FileTreeNode;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 Nacos Skill 的 resource Map 构建文件树。
 * 在根节点下添加 SKILL.md 虚拟节点。
 * 按目录优先、同类型按名称字母序排列。
 */
public final class FileTreeBuilder {

    private FileTreeBuilder() {}

    public static List<FileTreeNode> build(Skill skill) {
        Map<String, FileTreeNode> dirMap = new LinkedHashMap<>();
        List<FileTreeNode> rootChildren = new ArrayList<>();

        // Add virtual SKILL.md node
        String skillMdContent = SkillMdBuilder.build(skill);
        FileTreeNode skillMdNode = new FileTreeNode();
        skillMdNode.setName("SKILL.md");
        skillMdNode.setPath("SKILL.md");
        skillMdNode.setType("file");
        skillMdNode.setEncoding("text");
        skillMdNode.setSize(skillMdContent.getBytes(StandardCharsets.UTF_8).length);
        rootChildren.add(skillMdNode);

        // Strip skill name prefix from resource paths if Nacos prepends it
        String skillNamePrefix =
                skill.getName() != null && !skill.getName().isEmpty() ? skill.getName() + "/" : "";

        // Build file nodes from resource map
        if (skill.getResource() != null) {
            for (Map.Entry<String, SkillResource> entry : skill.getResource().entrySet()) {
                String resourceKey = entry.getKey();
                SkillResource resource = entry.getValue();
                addResourceNode(rootChildren, dirMap, resourceKey, resource, skillNamePrefix);
            }
        }

        // Sort: directories first, then files, alphabetical within type
        sortNodes(rootChildren);
        return rootChildren;
    }

    private static void addResourceNode(
            List<FileTreeNode> rootChildren,
            Map<String, FileTreeNode> dirMap,
            String resourceKey,
            SkillResource resource,
            String skillNamePrefix) {
        // Build full path: type/name or just name
        String name = resource.getName() != null ? resource.getName() : resourceKey;
        String type = resource.getType();
        String path;
        if (type != null && !type.isEmpty()) {
            path = type + "/" + name;
        } else {
            path = name;
        }

        // Remove skill name prefix to avoid redundant directory layer
        if (!skillNamePrefix.isEmpty() && path.startsWith(skillNamePrefix)) {
            path = path.substring(skillNamePrefix.length());
        }

        String[] parts = path.split("/");

        if (parts.length == 1) {
            rootChildren.add(createFileNode(path, path, resource));
        } else {
            List<FileTreeNode> currentLevel = rootChildren;
            StringBuilder currentPath = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                if (currentPath.length() > 0) currentPath.append("/");
                currentPath.append(parts[i]);
                String dirPath = currentPath.toString();

                FileTreeNode dirNode = dirMap.get(dirPath);
                if (dirNode == null) {
                    dirNode = new FileTreeNode();
                    dirNode.setName(parts[i]);
                    dirNode.setPath(dirPath);
                    dirNode.setType("directory");
                    dirNode.setChildren(new ArrayList<>());
                    dirMap.put(dirPath, dirNode);
                    currentLevel.add(dirNode);
                }
                currentLevel = dirNode.getChildren();
            }
            currentLevel.add(createFileNode(parts[parts.length - 1], path, resource));
        }
    }

    private static FileTreeNode createFileNode(String name, String path, SkillResource resource) {
        FileTreeNode node = new FileTreeNode();
        node.setName(name);
        node.setPath(path);
        node.setType("file");
        String content = resource.getContent();
        boolean isBinary = isBinaryContent(resource);
        node.setEncoding(isBinary ? "base64" : "text");
        node.setSize(content != null ? content.getBytes(StandardCharsets.UTF_8).length : 0);
        return node;
    }

    private static boolean isBinaryContent(SkillResource resource) {
        if (resource.getMetadata() != null) {
            Object encoding = resource.getMetadata().get("encoding");
            if ("base64".equals(encoding)) return true;
        }
        return false;
    }

    private static void sortNodes(List<FileTreeNode> nodes) {
        nodes.sort(
                Comparator.comparing((FileTreeNode n) -> "file".equals(n.getType()) ? 1 : 0)
                        .thenComparing(FileTreeNode::getName, String.CASE_INSENSITIVE_ORDER));
        for (FileTreeNode node : nodes) {
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                sortNodes(node.getChildren());
            }
        }
    }
}

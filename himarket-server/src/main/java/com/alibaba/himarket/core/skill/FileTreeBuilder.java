package com.alibaba.himarket.core.skill;

import com.alibaba.himarket.dto.result.skill.SkillFileTreeNode;
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

    public static List<SkillFileTreeNode> build(Skill skill) {
        Map<String, SkillFileTreeNode> dirMap = new LinkedHashMap<>();
        List<SkillFileTreeNode> rootChildren = new ArrayList<>();

        // 添加 SKILL.md 虚拟节点
        String skillMdContent = SkillMdBuilder.build(skill);
        SkillFileTreeNode skillMdNode = new SkillFileTreeNode();
        skillMdNode.setName("SKILL.md");
        skillMdNode.setPath("SKILL.md");
        skillMdNode.setType("file");
        skillMdNode.setEncoding("text");
        skillMdNode.setSize(skillMdContent.getBytes(StandardCharsets.UTF_8).length);
        rootChildren.add(skillMdNode);

        // 从 resource Map 构建文件节点
        if (skill.getResource() != null) {
            for (Map.Entry<String, SkillResource> entry : skill.getResource().entrySet()) {
                String resourceKey = entry.getKey();
                SkillResource resource = entry.getValue();
                addResourceNode(rootChildren, dirMap, resourceKey, resource);
            }
        }

        // 排序：目录优先，同类型按名称字母序
        sortNodes(rootChildren);
        return rootChildren;
    }

    private static void addResourceNode(
            List<SkillFileTreeNode> rootChildren,
            Map<String, SkillFileTreeNode> dirMap,
            String resourceKey,
            SkillResource resource) {
        // 构建完整路径：type 非空时拼接 type/name，否则只用 name
        String name = resource.getName() != null ? resource.getName() : resourceKey;
        String type = resource.getType();
        String path;
        if (type != null && !type.isEmpty()) {
            path = type + "/" + name;
        } else {
            path = name;
        }
        String[] parts = path.split("/");

        if (parts.length == 1) {
            // 根级文件
            rootChildren.add(createFileNode(path, path, resource));
        } else {
            // 需要创建目录层级
            List<SkillFileTreeNode> currentLevel = rootChildren;
            StringBuilder currentPath = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                if (currentPath.length() > 0) currentPath.append("/");
                currentPath.append(parts[i]);
                String dirPath = currentPath.toString();

                SkillFileTreeNode dirNode = dirMap.get(dirPath);
                if (dirNode == null) {
                    dirNode = new SkillFileTreeNode();
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

    private static SkillFileTreeNode createFileNode(
            String name, String path, SkillResource resource) {
        SkillFileTreeNode node = new SkillFileTreeNode();
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

    private static void sortNodes(List<SkillFileTreeNode> nodes) {
        nodes.sort(
                Comparator.comparing((SkillFileTreeNode n) -> "file".equals(n.getType()) ? 1 : 0)
                        .thenComparing(SkillFileTreeNode::getName, String.CASE_INSENSITIVE_ORDER));
        for (SkillFileTreeNode node : nodes) {
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                sortNodes(node.getChildren());
            }
        }
    }
}

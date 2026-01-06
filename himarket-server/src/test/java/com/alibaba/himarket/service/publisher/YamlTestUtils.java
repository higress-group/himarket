package com.alibaba.himarket.service.publisher;

import java.util.*;
import org.yaml.snakeyaml.Yaml;

/** YAML 测试工具类 - 用于深度对比 YAML 对象 */
public class YamlTestUtils {

    /**
     * 深度对比两个 YAML 对象是否相等
     *
     * @param expected 期望的 YAML 内容（字符串）
     * @param actual 实际生成的 YAML 内容（字符串）
     * @return 比较结果
     */
    public static ComparisonResult compareYaml(String expected, String actual) {
        Yaml yaml = new Yaml();
        Object expectedObj = yaml.load(expected);
        Object actualObj = yaml.load(actual);

        ComparisonResult result = new ComparisonResult();
        compareObjects(expectedObj, actualObj, "", result);
        return result;
    }

    /**
     * 递归对比两个对象
     *
     * @param expected 期望对象
     * @param actual 实际对象
     * @param path 当前路径（用于错误定位）
     * @param result 比较结果
     */
    private static void compareObjects(
            Object expected, Object actual, String path, ComparisonResult result) {
        if (expected == null && actual == null) {
            return;
        }

        if (expected == null) {
            result.addDifference(path, "Expected null but got: " + actual);
            return;
        }

        if (actual == null) {
            result.addDifference(path, "Expected " + expected + " but got null");
            return;
        }

        // 类型检查
        if (!expected.getClass().equals(actual.getClass())) {
            result.addDifference(
                    path,
                    "Type mismatch: expected "
                            + expected.getClass().getSimpleName()
                            + " but got "
                            + actual.getClass().getSimpleName());
            return;
        }

        if (expected instanceof Map) {
            compareMap((Map<?, ?>) expected, (Map<?, ?>) actual, path, result);
        } else if (expected instanceof List) {
            compareList((List<?>) expected, (List<?>) actual, path, result);
        } else {
            // 基本类型和字符串
            if (!expected.equals(actual)) {
                result.addDifference(
                        path,
                        "Value mismatch: expected [" + expected + "] but got [" + actual + "]");
            }
        }
    }

    /** 对比两个 Map */
    @SuppressWarnings("unchecked")
    private static void compareMap(
            Map<?, ?> expected, Map<?, ?> actual, String path, ComparisonResult result) {
        // 检查所有期望的 key 是否存在
        for (Object key : expected.keySet()) {
            String currentPath = path.isEmpty() ? key.toString() : path + "." + key;

            if (!actual.containsKey(key)) {
                result.addDifference(currentPath, "Missing key in actual YAML");
                continue;
            }

            compareObjects(expected.get(key), actual.get(key), currentPath, result);
        }

        // 检查是否有多余的 key
        for (Object key : actual.keySet()) {
            if (!expected.containsKey(key)) {
                String currentPath = path.isEmpty() ? key.toString() : path + "." + key;
                result.addDifference(currentPath, "Unexpected key in actual YAML");
            }
        }
    }

    /** 对比两个 List */
    private static void compareList(
            List<?> expected, List<?> actual, String path, ComparisonResult result) {
        if (expected.size() != actual.size()) {
            result.addDifference(
                    path,
                    "List size mismatch: expected "
                            + expected.size()
                            + " but got "
                            + actual.size());
            return;
        }

        for (int i = 0; i < expected.size(); i++) {
            String currentPath = path + "[" + i + "]";
            compareObjects(expected.get(i), actual.get(i), currentPath, result);
        }
    }

    /** 比较结果 */
    public static class ComparisonResult {
        private final List<String> differences = new ArrayList<>();

        void addDifference(String path, String message) {
            differences.add(path + ": " + message);
        }

        public boolean isMatch() {
            return differences.isEmpty();
        }

        public List<String> getDifferences() {
            return differences;
        }

        public String getDifferencesReport() {
            if (differences.isEmpty()) {
                return "✅ YAML structures match perfectly!";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("❌ Found ").append(differences.size()).append(" difference(s):\n");
            for (int i = 0; i < differences.size(); i++) {
                sb.append("  ").append(i + 1).append(". ").append(differences.get(i)).append("\n");
            }
            return sb.toString();
        }
    }

    /**
     * 从 URL 加载 YAML 文件
     *
     * @param url YAML 文件 URL
     * @return YAML 内容字符串
     */
    public static String loadYamlFromUrl(String url) throws Exception {
        java.net.URL yamlUrl = new java.net.URL(url);
        try (java.io.InputStream is = yamlUrl.openStream();
                java.util.Scanner scanner =
                        new java.util.Scanner(is, java.nio.charset.StandardCharsets.UTF_8)) {
            return scanner.useDelimiter("\\A").next();
        }
    }
}

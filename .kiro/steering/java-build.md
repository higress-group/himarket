---
inclusion: fileMatch
fileMatchPattern: "**/*.java"
---

# Java 构建规范

## Spotless 代码格式化

本项目使用 Spotless Maven 插件强制代码格式。`mvn test` 会先执行 `spotless:check`，格式不一致会直接导致构建失败。

规则：在执行 `mvn test` 或 `mvn verify` 之前，必须先运行：

```bash
mvn spotless:apply -pl himarket-server -q
```

这会自动格式化所有 Java 文件。不要跳过这一步，否则测试会因为格式检查失败而报错。

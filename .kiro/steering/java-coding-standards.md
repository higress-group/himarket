---
inclusion: fileMatch
fileMatchPattern: "**/*.java"
---

# Java 编码规范

适用于项目中所有 Java 17+ / Spring Boot 代码。

## 核心原则

- 清晰优于聪明，可维护性优先
- 默认不可变，最小化共享可变状态
- 快速失败，异常信息有意义
- 命名和包结构保持一致

## 命名规范

```java
// 类/Record：PascalCase
public class MarketService {}
public record Money(BigDecimal amount, Currency currency) {}

// 方法/字段：camelCase
private final MarketRepository marketRepository;
public Market findBySlug(String slug) {}

// 常量：UPPER_SNAKE_CASE
private static final int MAX_PAGE_SIZE = 100;
```

## 不可变性

```java
// 优先使用 record 和 final 字段
public record MarketDto(Long id, String name, MarketStatus status) {}

public class Market {
  private final Long id;
  private final String name;
  // 只有 getter，没有 setter
}
```

## Optional 用法

```java
// find* 方法返回 Optional
Optional<Market> market = marketRepository.findBySlug(slug);

// 用 map/orElseThrow，不要直接 get()
return market
    .map(MarketResponse::from)
    .orElseThrow(() -> new EntityNotFoundException("Market not found"));
```

## Stream 最佳实践

```java
// pipeline 保持简短
List<String> names = markets.stream()
    .map(Market::name)
    .filter(Objects::nonNull)
    .toList();

// 复杂逻辑用普通循环，不要嵌套 stream
```

## 异常处理

- 领域错误用非受检异常，如 `MarketNotFoundException`
- 技术异常包装后加上下文信息再抛出
- 避免宽泛的 `catch (Exception ex)`，除非是顶层统一处理

```java
throw new MarketNotFoundException(slug);
```

## 泛型与类型安全

- 不用原始类型，声明泛型参数
- 可复用工具类用有界泛型

```java
public <T extends Identifiable> Map<Long, T> indexById(Collection<T> items) { ... }
```

## 项目结构

```
src/main/java/com/alibaba/himarket/
  config/
  controller/
  service/
  repository/
  domain/
  dto/
  util/
```

## 格式与风格

- 每个文件只有一个顶层 public 类型
- 方法短小聚焦，提取辅助方法
- 成员顺序：常量 → 字段 → 构造器 → public 方法 → protected → private

## 要避免的代码坏味道

- 长参数列表 → 用 DTO 或 Builder
- 深嵌套 → 提前 return
- 魔法数字 → 命名常量
- 静态可变状态 → 依赖注入
- 空 catch 块 → 记录日志或重新抛出

## 日志

```java
private static final Logger log = LoggerFactory.getLogger(MarketService.class);
log.info("fetch_market slug={}", slug);
log.error("failed_fetch_market slug={}", slug, ex);
```

用结构化 `key=value` 格式，方便日志检索。

## Null 处理

- 尽量避免接受 `@Nullable`，优先 `@NonNull`
- 输入参数用 Bean Validation：`@NotNull`、`@NotBlank`

## 测试期望

- JUnit 5 + AssertJ 流式断言
- Mockito mock 依赖，避免 partial mock
- 测试确定性，不依赖 sleep 或时序

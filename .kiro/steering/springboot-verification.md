---
inclusion: manual
---

# Spring Boot 验证循环

在以下场景执行：PR 提交前、重大重构后、预发布验证。

## Phase 1 — 构建

```bash
mvn -T 4 clean verify -DskipTests
# 或
./gradlew clean assemble -x test
```

构建失败立即停止，修复后再继续。

## Phase 2 — 静态分析

```bash
mvn -T 4 spotbugs:check pmd:check checkstyle:check
# 或
./gradlew checkstyleMain pmdMain spotbugsMain
```

## Phase 3 — 测试 + 覆盖率

```bash
mvn -T 4 test
mvn jacoco:report   # 目标 80%+ 覆盖率
# 或
./gradlew test jacocoTestReport
```

### 单元测试示例

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @InjectMocks private UserService userService;

  @Test
  void createUser_validInput_returnsUser() {
    var dto = new CreateUserDto("Alice", "alice@example.com");
    var expected = new User(1L, "Alice", "alice@example.com");
    when(userRepository.save(any(User.class))).thenReturn(expected);

    var result = userService.create(dto);

    assertThat(result.name()).isEqualTo("Alice");
    verify(userRepository).save(any(User.class));
  }

  @Test
  void createUser_duplicateEmail_throwsException() {
    var dto = new CreateUserDto("Alice", "existing@example.com");
    when(userRepository.existsByEmail(dto.email())).thenReturn(true);

    assertThatThrownBy(() -> userService.create(dto))
        .isInstanceOf(DuplicateEmailException.class);
  }
}
```

### Testcontainers 集成测试示例

用真实数据库替代 H2，避免兼容性问题（项目使用 MySQL）：

```java
@SpringBootTest
@Testcontainers
class UserRepositoryIntegrationTest {

  @Container
  static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
      .withDatabaseName("testdb");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", mysql::getJdbcUrl);
    registry.add("spring.datasource.username", mysql::getUsername);
    registry.add("spring.datasource.password", mysql::getPassword);
  }

  @Autowired private UserRepository userRepository;

  @Test
  void findByEmail_existingUser_returnsUser() {
    userRepository.save(new User("Alice", "alice@example.com"));
    var found = userRepository.findByEmail("alice@example.com");
    assertThat(found).isPresent();
  }
}
```

### MockMvc API 测试示例

```java
@WebMvcTest(UserController.class)
class UserControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockBean private UserService userService;

  @Test
  void createUser_validInput_returns201() throws Exception {
    var user = new UserDto(1L, "Alice", "alice@example.com");
    when(userService.create(any())).thenReturn(user);

    mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name": "Alice", "email": "alice@example.com"}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Alice"));
  }

  @Test
  void createUser_invalidEmail_returns400() throws Exception {
    mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name": "Alice", "email": "not-an-email"}
                """))
        .andExpect(status().isBadRequest());
  }
}
```

## Phase 4 — 安全扫描

```bash
# 依赖 CVE 扫描
mvn org.owasp:dependency-check-maven:check
# 或
./gradlew dependencyCheckAnalyze

# 检查硬编码密码
grep -rn "password\s*=\s*\"" src/ --include="*.java" --include="*.yml" --include="*.properties"

# 检查 System.out（应用日志应用 SLF4J）
grep -rn "System\.out\.print" src/main/ --include="*.java"

# 检查异常信息直接返回给客户端
grep -rn "e\.getMessage()" src/main/ --include="*.java"

# 检查 CORS 通配符
grep -rn "allowedOrigins.*\*" src/main/ --include="*.java"
```

## Phase 5 — 格式化（可选）

```bash
mvn spotless:apply
# 或
./gradlew spotlessApply
```

## Phase 6 — Diff 审查

```bash
git diff --stat
git diff
```

检查项：
- 没有遗留调试日志（`System.out`、无条件 `log.debug`）
- 错误处理和 HTTP 状态码正确
- 需要事务的地方加了 `@Transactional`
- 配置变更已记录

## 验证报告模板

```
VERIFICATION REPORT
===================
Build:     [PASS/FAIL]
Static:    [PASS/FAIL] (spotbugs/pmd/checkstyle)
Tests:     [PASS/FAIL] (X/Y passed, Z% coverage)
Security:  [PASS/FAIL] (CVE findings: N)
Diff:      [X files changed]

Overall:   [READY / NOT READY]

Issues to Fix:
1. ...
2. ...
```

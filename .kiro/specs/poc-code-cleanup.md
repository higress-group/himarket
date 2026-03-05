# POC 残留代码清理清单

## 概述

HiMarket 在早期 POC 阶段为了快速验证功能，引入了一些临时性的简化逻辑。现在需要统一移除这些 POC 代码，改为正式的生产级实现。

## 1. 匿名访问模式（Anonymous Access）

### 1.1 AcpHandshakeInterceptor - WebSocket 匿名访问

**文件：** `himarket-server/src/main/java/com/alibaba/himarket/service/acp/AcpHandshakeInterceptor.java`

**位置：** 88-92 行

```java
if (StrUtil.isBlank(token)) {
    // POC mode: allow anonymous access
    logger.info("WebSocket handshake allowed: anonymous (no token, POC mode)");
    attributes.put("userId", "anonymous");
    return true;
}
```

**问题：**
- 允许无 token 的匿名用户建立 WebSocket 连接
- 存在安全风险，任何人都可以连接并使用沙箱资源
- 无法追踪用户行为和资源使用

**修复方案：**
```java
if (StrUtil.isBlank(token)) {
    logger.warn("WebSocket handshake rejected: missing token");
    return false;
}
```

**影响范围：**
- 需要确保前端始终传递有效的 token
- 需要更新相关测试用例

---

### 1.2 WorkspaceController - 匿名用户回退

**文件：** `himarket-server/src/main/java/com/alibaba/himarket/controller/WorkspaceController.java`

**位置：** 425-430 行

```java
private String getCurrentUserId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof String principal) {
        return principal;
    }
    return "anonymous";  // POC 残留
}
```

**问题：**
- 当认证失败时回退到 "anonymous"
- 可能导致多个用户共享同一个工作空间
- 无法正确隔离用户数据

**修复方案：**
```java
private String getCurrentUserId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof String principal) {
        return principal;
    }
    throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户未认证");
}
```

---

### 1.3 测试用例中的匿名访问

**文件：** `himarket-server/src/test/java/com/alibaba/himarket/service/acp/AcpHandshakeInterceptorTest.java`

**位置：** 132 行

```java
// ===== 匿名访问（POC 模式） =====
```

**问题：**
- 测试用例验证了匿名访问的行为
- 移除匿名访问后需要删除或修改这些测试

**修复方案：**
- 删除匿名访问相关的测试用例
- 或修改为验证"拒绝无 token 连接"的行为

---

## 2. K8s 集群自动选择（Default Cluster Selection）

### 2.1 K8sConfigService - 默认集群方法

**文件：** `himarket-server/src/main/java/com/alibaba/himarket/service/acp/runtime/K8sConfigSer`

**位置：** 176-203 行

```java
/**
 * 获取默认的 K8s 客户端（POC 阶段使用第一个已注册的集群）。
 * <p>
 * 优先从内存缓存获取，避免每次查数据库。
 * 适用于不关心具体集群的场景（如 Pod 清理、健康检查等）。
 *
 * @return KubernetesClient 实例
 * @throws IllegalStateException 如果没有已注册的集群
 */
public KubernetesClient getDefaultClient() {
    // 优先从缓存取第一个
    Map.Entry<String, KubernetesClient> first =
            clientCache.entrySet().stream().findFirst().orElse(null);
    if (first != null) {
        return first.getValue();
    }

    // 缓存为空，尝试从数据库加载
    List<K8sCluster> clusters = k8sClusterRepository.findAll();
    if (clusters.isEmpty()) {
        throw new IllegalStateException("没有已注册的 K8s 集群");
    }

    K8sCluster cluster = clusters.get(0);
    KubernetesClient client = createClient(cluster.getKubeconfig());
    clientCache.put(cluster.getConfigId(), client);
    return client;
}
```

**位置：** 205-228 行

```java
/**
 * 获取默认集群的 configId（POC 阶段使用第一个已注册的集群）。
 * <p>
 * 优先从内存缓存获取，避免每次查数据库。
 *
 * @return 默认集群的 configId
 * @throws IllegalStateException 如果没有已注册的集群
 */
public String getDefaultConfigId() {
    // 优先从缓存取第一个
    String firstKey = clientCache.keySet().stream().findFirst().orElse(null);
    if (firstKey != null) {
        return firstKey;
    }

    // 缓存为空，尝试从数据库加载
    List<K8sCluster> clusters = k8sClusterRepository.findAll();
    if (clusters.isEmpty()) {
        throw new IllegalStateException("没有已注册的 K8s 集群");
    }

    return clusters.get(0).getConfigId();
}
```

**问题：**
- 自动选择第一个集群，无法支持多集群场景
- 用户无法指定使用哪个集群
- 不同用户可能需要使用不同的集群（如按地域、按权限）

**调用位置：**
1. `AcpWebSocketHandler.java:1030` - 沙箱初始化时自动选择集群
2. `TerminalWebSocketHandler.java:84` - Terminal 连接时自动选择集群
3. `PodReuseManager.java:256` - Pod 复用时自动选PodReuseManager.java:325` - Pod 驱逐时自动选择集群

**修复方案：**

**方案 A：要求前端传递 k8sConfigId**
```java
// 移除 getDefaultClient() 和 getDefaultConfigId() 方法
// 所有调用处改为显式传递 k8sConfigId

// AcpWebSocketHandler.java
if (sandboxType == SandboxType.K8S) {
    String k8sConfigId = (String) attributes.get("k8sConfigId");
    if (StrUtil.isBlank(k8sConfigId)) {
        throw new IllegalArgumentException("K8s 运行时必须指定 k8sConfigId");
    }
    config.setK8sConfigId(k8sConfigId);
    config.setContainerImage(providerConfig.getContainerImage());
}
```

**方案 B：基于用户配置选择集群**
```java
/**
 * 根据用户配置获取 K8s 集群。
 * 优先级：用户指定 > 用户默认集群 > 系统默认集群
 */
public String getClusterForUser(String userId, String preferredConfigId) {
    if (StrUtil.isNotBlank(preferredConfigId)) {
        return preferredConfigId;
    }

    // 查询用户的默认集群配置
    UserK8sConfig userConfig = userK8sConfigRepository.findByUserId(userId);
    if (userConfig != null) {
        return userConfig.getDefaultConfigId();
    }

    // 查询系统默认集群
    K8sCluster defaultCluster = k8sClusterRepository.findByIsDefault(true);
    if (defaultCluster != null) {
        return defaultCluster.getConfigId();
    }

    throw new IllegalStateException("无法为用户 " + userId + " 确定 K8s 集群");n**推荐方案：** 方案 B，更灵活且向后兼容

---

### 2.2 AcpWebSocketHandler - 自动使用第一个集群

**文件：** `himarket-server/src/main/java/com/alibaba/himarket/service/acp/AcpWebSocketHandler.java`

**位置：** 1027-1035 行

```java
// K8s 运行时：填充容器镜像和集群配置
if (sandboxType == SandboxType.K8S) {
    config.setContainerImage(providerConfig.getContainerImage());
    // POC 阶段：自动使用第一个已注册的 K8s 集群
    config.setK8sConfigId(k8sConfigService.getDefaultConfigId());
    logger.info(
            "K8s runtime: using cluster configId={}, image={}",
            config.getK8sConfigId(),
            config.getContainerImage());
}
```

**问题：**
- 硬编码使用第一个集 无法支持多集群部署
- 无法根据用户权限或地域选择集群

**修复方案：**
```java
// K8s 运行时：填充容器镜像和集群配置
if (sandboxType == SandboxType.K8S) {
    config.setContainerImage(providerConfig.getContainerImage());

    // 从 WebSocket 参数或用户配置中获取集群 ID
    String k8sConfigId = (String) attributes.get("k8sConfigId");
    if (StrUtil.isBlank(k8sConfigId)) {
        k8sConfigId = k8sConfigService.getClusterForUser(userId, null);
    }
    config.setK8sConfigId(k8sConfigId);

    logger.info(
            "K8s runtime: userId={}, cluster={}, image={}",
            userId,
            config.getK8sConfigId(),
            config.getContainerImage());
}
```

---

### 2.3 TerminalWebSocketHandler - 自动选择集群

**文件：** `himarket-server/src/main/java/com/alibaba/himarket/service/terminal/TerminalWebSocketHandler.java`

**位置：** 82-84 行

```java
if (isK8s) {
    // 获取默认 K8s client
    KubernetesClient client = k8sConfigService.getDefaultClient();
    // ...
}
```

**问题：**
- Terminal 连接时自动使用第一个集群
- 可能连接到错误的集群

**修复方案：**
```java
if (isK8s) {
    String k8sConfigId = (String) attributes.get("k8sConfigId");
    if (StrUtil.isBlank(k8sConfigId)) {
        k8sConfigId = k8sConfigService.getClusterForUser(userId, null);
    }
    KubernetesClient client = k8sConfigService.getClient(k8sConfigId);
    // ...
}
```

---

### 2.4 PodReuseManager - 默认集群调用

**文件：** `himarket-server/src/main/java/com/alibaba/himarket/service/acp/runtime/PodReuseManager.java`

**位置：** 255-257 行

```java
public PodEntry getHealthyPodEntryWithDefaultClient(String userId) {
    return getHealthyPodEntry(userId, k8sConfigService.getDefaultClient());
}
```

**位置：** 324-327 行

```java
try {
    KubernetesClient client = k8sConfigService.getDefaultClient();
    client.pods().inNamespace(namespace).withName(entry.getPodName()).delete();
    deleteServiceFor, entry.getPodName());
    // ...
}
```

**问题：**
- Pod 管理操作使用默认集群
- 可能操作错误的集群中的 Pod

**修复方案：**
```java
// 方案 1：移除 getHealthyPodEntryWithDefaultClient，强制传递 client
// 删除该方法，所有调用处改为显式传递 KubernetesClient

// 方案 2：在 PodEntry 中记录 configId
public class PodEntry {
    private String configId;  // 新增字段
    // ...
}

// evictPod 时使用记录的 configId
try {
    KubernetesClient client = k8sConfigService.getClient(entry.getConfigId());
    client.pods().inNamespace(namespace).withName(entry.getPodName()).delete();
    // ...
}
```

**推荐方案：** 方案 2，在 PodEntry 中记录 configId

---

## 3. 前端 POC 标识

### 3.1 RuntimeSelector 测试用例

**文件：** `himarket-web/himarket-frontend/src/components/common/__tests__/RuntimeSelector.test.tsx`

**位置：** 9-14 行

```typescript
const localRuntime: RuntimeOption = {
  type: 'local',
  label: 'POC 本地启动',  // POC 标识
  description: '通过 ProcessBuilder 在本机启动 CLI 子进程',
  available: true,
};
```

**位置：** 45 行

```typescript
expect(screen.getByText('POC 本地启动')).toBeInTheDocument();
```

**问题：**
- UI 文案中包含 "POC" 字样，不适合生产环境
- 给用户传递"这是临时功能"的错误印象

**修复方案：**
```typescript
const localRuntime: RuntimeOption = {
  type: 'local',
  label: '本地运行',  // 移除 POC iption: '在本地环境启动 CLI 进程，适合开发调试',
  available: true,
};
```

```typescript
expect(screen.getByText('本地运行')).toBeInTheDocument();
```

---

## 4. 清理优先级

### P0 - 安全风险（必须立即修复）

1. ✅ **移除匿名访问模式**
   - `AcpHandshakeInterceptor.java:88-92` - 拒绝无 token 连接
   - `WorkspaceController.java:429` - 认证失败时抛出异常
   - 影响：提升系统安全性，防止未授权访问

### P1 - 功能缺陷（近期修复）

2. ✅ **实现正式的集群选择机制**
   - 新增 `K8sConfigService.getClusterForUser()` 方法
   - 支持用户级别的集群配置
   - 支持系统默认集群
   - 影响：支持多集群部署，提升灵活性

3. ✅ **修改所有默认集群调用**
   - `AcpWebSocketHandler.java:1030` - 使用 getClusterForUser
   - `TerminalWebSocketHandler.java:84` - 使用 getClusterForUser
   - `PodReuseManager.java` - 在 PodEntry 中记录 configId
   - 影响：确保集群选择的一致性

### P2 - 用户体验（中期优化）

4. ✅ **移除前端 POC 标识**
   - `RuntimeSelector.test.tsx` - 修改文案
   - 检查其他前端组件是否有类似标识
   - 影响：提升产品专业度

5. ✅ **清理测试用例**
   - 删除匿名访问相关测试
   - 更新集群选择相关测试
   - 影响：保持测试覆盖率

---

## 5. 数据库变更（如需要）

### 5.1 新增用户集群配置表（可选）

如果采用"用户级别集群配置"方案，需要新增表：

```sql
CREATE TABLE user_k8s_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(64) NOT NULL,
    default_config_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_id (user_id),
    KEY idx_config_id (default_config_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户 K8s 集群配置';
```

### 5.2 K8sCluster 表新增默认标识（可选）

```sql
ALTER TABLE k8s_cluster ADD COLUMN is_default BOOLEAN DEFAULT FALSE COMMENT '是否为系统默认集群';
```

---

## 6. 前端变更

### 6.1 WebSocket 连接参数

**当前：**
```typescript
const wsUrl = `ws://localhost:8080/acp?token=${token}&provider=${provider}&runtime=${runtime}`;
```

**修改后：**
```typescript
const wsUrl = `ws://localhost:8080/acp?token=${token}&provider=${provider}&runtime=${runtime}&k8sConfigId=${k8sConfigId}`;
```

**注意：**
- `k8sConfigId` 可选，如果不传则使用用户默认集群
- 需要在前端添加集群选择器（如果支持多集群）

---

## 7. 配置文件变更

### 7.1 application.yml（如需要）

如果需要配置系统默认集群：

```yaml
himarket:
  k8s:
    default-config-id: "default-cluster"  # 系统默认集群 ID
```

---

## 8. 迁移步骤

### 步骤 1：准备阶段
1. 备份数据库
2. 创建功能分支 `feat/remove-poc-code`
3. 通知团队即将进行的变更

### 步骤 2：后端修改
1. 新增 `K8sConfigService.getClusterForUser()` 方法
2. 修改所有 `getDefaultClient()` 调用处
3. 移除匿名访问逻辑
4. 更新测试用例

### 步骤 3：前端修改
1. 修改 WebSocket 连接参数
2. 更新 案（移除 POC 标识）
3. 添加集群选择器（如需要）
4. 更新测试用例

### 步骤 4：测试验证
1. 单元测试：验证集群选择逻辑
2. 集成测试：验证 WebSocket 连接和沙箱创建
3. 端到端测试：验证完整的用户流程
4. 安全测试：验证无 token 连接被拒绝

### 步骤 5：部署上线
1. 灰度发布：先在测试环境验证
2. 监控告警：关注错误率和连接失败
3. 回滚预案：准备快速回滚方案

---

## 9. 风险评估

### 高风险项

1. **移除匿名访问**
   - 风险：可能影响现有的测试环境或演示环境
   - 缓解：提前通知，确保所有环境都配置了认证

2. **集群选择逻辑变更**
   - 风险：可能导致用户连接到错误的集群
   - 缓解：充分测试，添加详细日志

### 中风险项

1. **前端参数变更**
   - 风险：前后端不兼容
   - 缓解：向后兼容，k8sConfigId 参数可选

2. **测试用例更新**
   - 风险：测试覆盖率下降
   - 缓解：补充新的测试用例

---

## 10. 验收标准

### 功能验收
- [ ] 无 token 的 WebSocket 连接被拒绝
- [ ] 用户可以指定 K8s 集群
- [ ] 用户未指定集群时使用默认集群
- [ ] 多集群场景下 Pod 管理正确

### 代码质量
- [ ] 所有 POC 注释已移除
- [ ] 代码通过 Spotless 检查
- [ ] 测试覆盖率不低于 80%

### 文档更新
- [ ] API 文档更新（WebSocket 参数）
- [ ] 部署文档更新（集群配置）
- [ ] 用户手册更新（集群选择）

---

## 11. 总结

本次清理涉及：
- **3 个安全问题**（匿名访问）
- **6 个功能问题**（默认集群选择）
- **2 个 UI 问题**（POC 标识）

预计工作量：
- 后端开发：2-3 天
- 前端开发：1 天
- 测试验证：1-2 天
- 总计：4-6 天

建议分两个阶段进行：
1. **第一阶段（P0）**：移除匿名访问，提升安全性
2. **第二阶段（P1）**：实现正式的集群选择机制，提升灵活性

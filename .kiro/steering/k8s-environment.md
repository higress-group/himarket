---
inclusion: manual
---

# K8s 集群环境上下文

当前开发机器已连接 K8s 集群，可直接使用 `kubectl` 进行操作。本地 kubeconfig 指向的集群即为沙箱运行所在的集群，目标命名空间为 `himarket`。

## 排查思路

遇到 K8s 相关问题时，按以下顺序排查：

1. `kubectl get pods -n himarket` 获取当前 Pod 状态
2. 对异常 Pod 执行 `kubectl describe pod <pod-name> -n himarket` 查看 Events
3. `kubectl logs <pod-name> -n himarket` 查看容器日志
4. `kubectl get events -n himarket --sort-by='.lastTimestamp'` 查看命名空间事件
5. `kubectl get pvc -n himarket` 检查存储卷状态

## 沙箱 Pod

himarket 平台会在 `himarket` 命名空间中动态创建沙箱 Pod，命名规则为：
```
sandbox-{env}-{sandboxId}-{randomSuffix}
```

沙箱 Pod 使用 PVC 挂载工作空间，PVC 名称格式: `workspace-{env}-{sandboxId}`。

## 注意事项

- 集群信息是动态的，排查时先用 kubectl 实时获取，不要依赖缓存信息
- 沙箱 Pod 是动态创建和销毁的，名称每次不同
- 排查沙箱创建失败时，优先检查 Events 中的镜像拉取和调度信息

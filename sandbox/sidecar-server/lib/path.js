import path from 'node:path';
import { WORKSPACE_ROOT } from '../config.js';

// ---------------------------------------------------------------------------
// 路径安全校验
// ---------------------------------------------------------------------------

/**
 * 解析并校验路径。
 *
 * 设计参考 OpenSandbox execd：接受绝对路径，由调用方负责构建正确路径。
 * 安全边界由容器本身提供（沙箱隔离），不在应用层做路径限制。
 *
 * 对于相对路径，以 WORKSPACE_ROOT 为基准解析（向后兼容）。
 */
export function resolvePath(inputPath) {
  if (path.isAbsolute(inputPath)) {
    return path.resolve(inputPath);
  }
  // 向后兼容：相对路径以 WORKSPACE_ROOT 为基准
  return path.resolve(WORKSPACE_ROOT, inputPath);
}

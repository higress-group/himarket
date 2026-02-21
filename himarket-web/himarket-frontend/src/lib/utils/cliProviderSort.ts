import type { ICliProvider } from "../apis/cliProvider";

/**
 * 对 CLI Provider 列表进行排序：
 * 1. Qwen Code 排第一位（key 包含 "qwen"）
 * 2. 其余可用工具按原始顺序
 * 3. 不可用工具排末尾
 */
export function sortCliProviders(providers: ICliProvider[]): ICliProvider[] {
  const available = providers.filter(p => p.available);
  const unavailable = providers.filter(p => !p.available);

  const qwenIndex = available.findIndex(p => p.key.toLowerCase().includes('qwen'));
  if (qwenIndex > 0) {
    const [qwen] = available.splice(qwenIndex, 1);
    available.unshift(qwen);
  }

  return [...available, ...unavailable];
}

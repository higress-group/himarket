import type { ICliProvider } from '../apis/cliProvider';

/**
 * 暂不支持的 CLI Provider，从列表中隐藏
 * 待对接完成后可从此集合中移除
 */
const HIDDEN_PROVIDERS = new Set(['claude-code', 'qodercli']);

/**
 * 对 CLI Provider 列表进行排序：
 * 1. 过滤掉 HIDDEN_PROVIDERS 中的 provider
 * 2. Qwen Code 排第一位（key 包含 "qwen"）
 * 3. 其余可用工具按原始顺序
 * 4. 不可用工具排末尾
 */
export function sortCliProviders(providers: ICliProvider[]): ICliProvider[] {
  const visible = providers.filter((p) => !HIDDEN_PROVIDERS.has(p.key));
  const available = visible.filter((p) => p.available);
  const unavailable = visible.filter((p) => !p.available);

  const qwenIndex = available.findIndex((p) => p.key.toLowerCase().includes('qwen'));
  if (qwenIndex > 0) {
    const [qwen] = available.splice(qwenIndex, 1);
    if (qwen) {
      available.unshift(qwen);
    }
  }

  return [...available, ...unavailable];
}

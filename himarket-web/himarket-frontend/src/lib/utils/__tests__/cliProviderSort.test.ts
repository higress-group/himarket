import { describe, it, expect } from 'vitest';
import { sortCliProviders } from '../cliProviderSort';
import type { ICliProvider } from '../../apis/cliProvider';

/** 快速构造 provider 的辅助函数 */
function makeProvider(key: string, available: boolean, extra?: Partial<ICliProvider>): ICliProvider {
  return {
    key,
    displayName: key,
    isDefault: false,
    available,
    ...extra,
  };
}

describe('sortCliProviders', () => {
  it('空列表返回空数组', () => {
    expect(sortCliProviders([])).toEqual([]);
  });

  it('Qwen Code 排在第一位', () => {
    const providers = [
      makeProvider('cursor', true),
      makeProvider('qwen-code', true),
      makeProvider('vscode', true),
    ];
    const sorted = sortCliProviders(providers);
    expect(sorted[0].key).toBe('qwen-code');
  });

  it('Qwen 已在第一位时保持不变', () => {
    const providers = [
      makeProvider('qwen-code', true),
      makeProvider('cursor', true),
    ];
    const sorted = sortCliProviders(providers);
    expect(sorted[0].key).toBe('qwen-code');
    expect(sorted[1].key).toBe('cursor');
  });

  it('不可用工具排在末尾', () => {
    const providers = [
      makeProvider('unavailable-tool', false),
      makeProvider('cursor', true),
      makeProvider('qwen-code', true),
    ];
    const sorted = sortCliProviders(providers);
    expect(sorted[0].key).toBe('qwen-code');
    expect(sorted[1].key).toBe('cursor');
    expect(sorted[2].key).toBe('unavailable-tool');
    expect(sorted[2].available).toBe(false);
  });

  it('全部不可用时保持原序', () => {
    const providers = [
      makeProvider('a', false),
      makeProvider('b', false),
    ];
    const sorted = sortCliProviders(providers);
    expect(sorted.map(p => p.key)).toEqual(['a', 'b']);
  });

  it('无 Qwen 时可用工具保持原序', () => {
    const providers = [
      makeProvider('cursor', true),
      makeProvider('vscode', true),
      makeProvider('windsurf', true),
    ];
    const sorted = sortCliProviders(providers);
    expect(sorted.map(p => p.key)).toEqual(['cursor', 'vscode', 'windsurf']);
  });

  it('可见 provider 排序后长度不变', () => {
    const providers = [
      makeProvider('a', true),
      makeProvider('qwen-cli', true),
      makeProvider('b', false),
      makeProvider('c', true),
    ];
    expect(sortCliProviders(providers)).toHaveLength(providers.length);
  });

  it('隐藏 claude-code 和 qodercli', () => {
    const providers = [
      makeProvider('qwen-code', true),
      makeProvider('claude-code', true),
      makeProvider('qodercli', true),
      makeProvider('cursor', true),
    ];
    const sorted = sortCliProviders(providers);
    const keys = sorted.map(p => p.key);
    expect(keys).not.toContain('claude-code');
    expect(keys).not.toContain('qodercli');
    expect(keys).toEqual(['qwen-code', 'cursor']);
  });

  it('隐藏的 provider 即使不可用也会被过滤', () => {
    const providers = [
      makeProvider('qwen-code', true),
      makeProvider('claude-code', false),
      makeProvider('qodercli', false),
    ];
    const sorted = sortCliProviders(providers);
    expect(sorted).toHaveLength(1);
    expect(sorted[0].key).toBe('qwen-code');
  });

  it('key 大小写不敏感匹配 qwen', () => {
    const providers = [
      makeProvider('cursor', true),
      makeProvider('QWEN-Code', true),
    ];
    const sorted = sortCliProviders(providers);
    expect(sorted[0].key).toBe('QWEN-Code');
  });

  it('不可用的 Qwen 不会被提到可用区域前面', () => {
    const providers = [
      makeProvider('cursor', true),
      makeProvider('qwen-code', false),
    ];
    const sorted = sortCliProviders(providers);
    // cursor 可用排前面，qwen 不可用排后面
    expect(sorted[0].key).toBe('cursor');
    expect(sorted[1].key).toBe('qwen-code');
  });
});

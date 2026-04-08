import { describe, it, expect } from 'vitest';

import { filterByKeyword } from '../filterUtils';

/** 模拟 MCP Server 数据 */
interface MockMcp {
  name: string;
  description: string;
}

/** 模拟 Skill 数据（含数组字段） */
interface MockSkill {
  name: string;
  description: string;
  skillTags: string[];
}

const mcpList: MockMcp[] = [
  { description: 'AI pair programming tool', name: 'GitHub Copilot' },
  { description: 'Browse and query databases', name: 'Database Explorer' },
  { description: 'Manage project files', name: 'File Manager' },
  { description: 'Container management utility', name: 'Docker Helper' },
];

const skillList: MockSkill[] = [
  { description: '代码审查技能', name: 'Code Review', skillTags: ['review', 'quality'] },
  { description: '单元测试生成', name: 'Unit Testing', skillTags: ['test', 'automation'] },
  { description: 'RESTful API 设计', name: 'API Design', skillTags: ['api', 'design'] },
];

describe('filterByKeyword', () => {
  it('空关键词返回全部列表', () => {
    expect(filterByKeyword(mcpList, '', ['name', 'description'])).toEqual(mcpList);
  });

  it('空白关键词返回全部列表', () => {
    expect(filterByKeyword(mcpList, '   ', ['name', 'description'])).toEqual(mcpList);
  });

  it('空列表返回空数组', () => {
    expect(filterByKeyword([], 'test', ['name'])).toEqual([]);
  });

  it('按名称匹配', () => {
    const result = filterByKeyword(mcpList, 'github', ['name', 'description']);
    expect(result).toHaveLength(1);
    expect(result.at(0)?.name).toBe('GitHub Copilot');
  });

  it('按描述匹配', () => {
    const result = filterByKeyword(mcpList, 'container', ['name', 'description']);
    expect(result).toHaveLength(1);
    expect(result.at(0)?.name).toBe('Docker Helper');
  });

  it('大小写不敏感', () => {
    const result = filterByKeyword(mcpList, 'DATABASE', ['name', 'description']);
    expect(result).toHaveLength(1);
    expect(result.at(0)?.name).toBe('Database Explorer');
  });

  it('匹配多个结果', () => {
    const result = filterByKeyword(mcpList, 'manage', ['name', 'description']);
    // "File Manager" 和 "Docker Helper" (Container management)
    expect(result).toHaveLength(2);
  });

  it('无匹配结果返回空数组', () => {
    const result = filterByKeyword(mcpList, 'zzzznotexist', ['name', 'description']);
    expect(result).toEqual([]);
  });

  it('数组字段匹配（skillTags）', () => {
    const result = filterByKeyword(skillList, 'automation', ['name', 'description', 'skillTags']);
    expect(result).toHaveLength(1);
    expect(result.at(0)?.name).toBe('Unit Testing');
  });

  it('数组字段中多个标签均可匹配', () => {
    const result = filterByKeyword(skillList, 'review', ['name', 'description', 'skillTags']);
    expect(result).toHaveLength(1);
    expect(result.at(0)?.name).toBe('Code Review');
  });

  it('仅搜索指定字段', () => {
    // 只搜索 name 字段，不搜索 description
    const result = filterByKeyword(mcpList, 'programming', ['name']);
    expect(result).toEqual([]);
  });

  it('字段值为 null/undefined 时不报错', () => {
    const items = [
      { name: 'Test', value: null },
      { name: 'Hello', value: undefined },
    ];
    const result = filterByKeyword(items, 'test', ['name', 'value']);
    expect(result).toHaveLength(1);
    expect(result.at(0)?.name).toBe('Test');
  });

  it('结果是原列表的子集', () => {
    const result = filterByKeyword(mcpList, 'a', ['name', 'description']);
    for (const item of result) {
      expect(mcpList).toContain(item);
    }
    expect(result.length).toBeLessThanOrEqual(mcpList.length);
  });
});

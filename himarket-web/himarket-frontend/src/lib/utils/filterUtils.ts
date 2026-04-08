/**
 * 通用关键词模糊匹配过滤工具函数
 * 用于 MCP Server 和 Skill 列表的搜索过滤
 */

/**
 * 对列表进行关键词模糊匹配过滤
 * - 大小写不敏感
 * - 支持指定多个字段参与匹配
 * - 数组类型字段会将元素拼接后匹配
 * - 空关键词返回全部列表
 *
 * @param items 待过滤的对象列表
 * @param keyword 搜索关键词
 * @param fields 参与匹配的字段名列表
 * @returns 过滤后的列表（原列表的子集）
 */
export function filterByKeyword<T extends object>(
  items: T[],
  keyword: string,
  fields: (keyof T)[],
): T[] {
  const trimmed = keyword.trim();
  if (!trimmed) {
    return items;
  }

  const lowerKeyword = trimmed.toLowerCase();

  return items.filter((item) =>
    fields.some((field) => {
      const value = (item as Record<keyof T, unknown>)[field];
      if (value === null || value === undefined) return false;

      // 数组类型字段：将元素拼接为字符串后匹配
      if (Array.isArray(value)) {
        return value.join(' ').toLowerCase().includes(lowerKeyword);
      }

      return String(value).toLowerCase().includes(lowerKeyword);
    }),
  );
}

/**
 * 工具配置校验结果
 */
export interface ValidationResult {
  valid: boolean;
  error?: string;
}

/**
 * 校验工具配置 JSON 字符串
 *
 * 校验规则：
 * 1. JSON.parse 成功（否则返回 "JSON 格式不合法"）
 * 2. 解析结果为数组（否则返回 "工具配置必须是 JSON 数组格式"）
 * 3. 每个元素包含非空 name 字符串（否则返回 "每个工具必须包含非空的 name 字段"）
 * 4. 每个元素包含 description 字符串字段（否则返回 "每个工具必须包含 description 字段"）
 *
 * @param jsonStr JSON 字符串
 * @returns 校验结果
 */
export function validateToolsConfig(jsonStr: string): ValidationResult {
  let parsed: unknown;
  try {
    parsed = JSON.parse(jsonStr);
  } catch {
    return { error: 'JSON 格式不合法', valid: false };
  }

  if (!Array.isArray(parsed)) {
    return { error: '工具配置必须是 JSON 数组格式', valid: false };
  }

  for (const item of parsed) {
    if (
      typeof item !== 'object' ||
      item === null ||
      typeof item.name !== 'string' ||
      item.name.trim() === ''
    ) {
      return { error: '每个工具必须包含非空的 name 字段', valid: false };
    }

    if (typeof item.description !== 'string') {
      return { error: '每个工具必须包含 description 字段', valid: false };
    }
  }

  return { valid: true };
}

// 生成符合后端要求的 UUID：24位小写字母和数字组合
export function generateUUID(): string {
  const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
  let result = '';
  for (let i = 0; i < 24; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return result;
}

// 生成对话 ID
export function generateConversationId(): string {
  return `conversation-${generateUUID()}`;
}

// 生成问题 ID
export function generateQuestionId(): string {
  return `question-${generateUUID()}`;
}

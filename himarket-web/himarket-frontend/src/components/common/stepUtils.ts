/**
 * 步骤计算工具函数
 * 根据 CLI Provider 的能力动态计算可见步骤
 */

// 步骤配置
export interface StepConfig {
  key: string;
  title: string;
  visible: boolean;
}

// Provider 能力标志（用于步骤计算的最小输入）
export interface ProviderCapabilities {
  supportsCustomModel?: boolean;
  supportsMcp?: boolean;
  supportsSkill?: boolean;
  authOptions?: string[];
}

/**
 * 根据 provider 能力动态计算可见步骤列表
 *
 * 步骤一"选择工具"（CLI 工具选择 + 运行时选择）— 始终可见
 * 步骤二"认证方案"— 仅当 authOptions 非空且长度 > 0 时可见
 * 步骤三"模型配置"（自定义模型/市场模型）— 仅当 supportsCustomModel === true 时可见
 * 步骤四"扩展配置"（MCP Server + Skill 选择）— 仅当 supportsMcp === true || supportsSkill === true 时可见
 */
export function computeSteps(capabilities: ProviderCapabilities | null | undefined): StepConfig[] {
  const supportsCustomModel = capabilities?.supportsCustomModel === true;
  const supportsMcp = capabilities?.supportsMcp === true;
  const supportsSkill = capabilities?.supportsSkill === true;
  const hasAuthOptions = (capabilities?.authOptions?.length ?? 0) > 0;

  return [
    {
      key: 'select-tool',
      title: '选择工具',
      visible: true, // 始终可见
    },
    {
      key: 'auth-config',
      title: '认证方案',
      visible: hasAuthOptions,
    },
    {
      key: 'model-config',
      title: '模型配置',
      visible: supportsCustomModel,
    },
    {
      key: 'extension-config',
      title: '扩展配置',
      visible: supportsMcp || supportsSkill,
    },
  ];
}

/**
 * 获取可见步骤列表
 */
export function getVisibleSteps(
  capabilities: ProviderCapabilities | null | undefined,
): StepConfig[] {
  return computeSteps(capabilities).filter((step) => step.visible);
}

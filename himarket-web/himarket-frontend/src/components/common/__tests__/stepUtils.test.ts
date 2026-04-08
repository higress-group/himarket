import { describe, it, expect } from 'vitest';

import { computeSteps, getVisibleSteps, type ProviderCapabilities } from '../stepUtils';

describe('computeSteps', () => {
  it('所有能力为 false 时，仅步骤一可见', () => {
    const caps: ProviderCapabilities = {
      supportsCustomModel: false,
      supportsMcp: false,
      supportsSkill: false,
    };
    const steps = computeSteps(caps);
    expect(steps).toHaveLength(4);
    expect(steps[0]?.visible).toBe(true); // select-tool
    expect(steps[1]?.visible).toBe(false); // auth-config
    expect(steps[2]?.visible).toBe(false); // model-config
    expect(steps[3]?.visible).toBe(false); // extension-config
  });

  it('supportsCustomModel 为 true 时，模型配置步骤可见', () => {
    const caps: ProviderCapabilities = {
      supportsCustomModel: true,
      supportsMcp: false,
      supportsSkill: false,
    };
    const steps = computeSteps(caps);
    expect(steps[1]?.visible).toBe(false); // auth-config
    expect(steps[2]?.visible).toBe(true); // model-config
    expect(steps[3]?.visible).toBe(false); // extension-config
  });

  it('supportsMcp 为 true 时，扩展配置步骤可见', () => {
    const caps: ProviderCapabilities = {
      supportsCustomModel: false,
      supportsMcp: true,
      supportsSkill: false,
    };
    const steps = computeSteps(caps);
    expect(steps[1]?.visible).toBe(false); // auth-config
    expect(steps[2]?.visible).toBe(false); // model-config
    expect(steps[3]?.visible).toBe(true); // extension-config
  });

  it('supportsSkill 为 true 时，扩展配置步骤可见', () => {
    const caps: ProviderCapabilities = {
      supportsCustomModel: false,
      supportsMcp: false,
      supportsSkill: true,
    };
    const steps = computeSteps(caps);
    expect(steps[1]?.visible).toBe(false); // auth-config
    expect(steps[2]?.visible).toBe(false); // model-config
    expect(steps[3]?.visible).toBe(true); // extension-config
  });

  it('所有原有能力为 true 时（无 authOptions），三个步骤可见', () => {
    const caps: ProviderCapabilities = {
      supportsCustomModel: true,
      supportsMcp: true,
      supportsSkill: true,
    };
    const steps = computeSteps(caps);
    expect(steps[0]?.visible).toBe(true); // select-tool
    expect(steps[1]?.visible).toBe(false); // auth-config（无 authOptions）
    expect(steps[2]?.visible).toBe(true); // model-config
    expect(steps[3]?.visible).toBe(true); // extension-config
  });

  it('authOptions 非空时，认证方案步骤可见', () => {
    const caps: ProviderCapabilities = {
      authOptions: ['default', 'personal_access_token'],
    };
    const steps = computeSteps(caps);
    expect(steps[1]?.visible).toBe(true); // auth-config
  });

  it('authOptions 为空数组时，认证方案步骤不可见', () => {
    const caps: ProviderCapabilities = {
      authOptions: [],
    };
    const steps = computeSteps(caps);
    expect(steps[1]?.visible).toBe(false); // auth-config
  });

  it('所有能力为 true 且有 authOptions 时，四个步骤全部可见', () => {
    const caps: ProviderCapabilities = {
      authOptions: ['default'],
      supportsCustomModel: true,
      supportsMcp: true,
      supportsSkill: true,
    };
    const steps = computeSteps(caps);
    expect(steps.every((s) => s.visible)).toBe(true);
  });

  it('传入 null 时，仅步骤一可见', () => {
    const steps = computeSteps(null);
    const visible = steps.filter((s) => s.visible);
    expect(visible).toHaveLength(1);
    expect(visible[0]?.key).toBe('select-tool');
  });

  it('传入 undefined 时，仅步骤一可见', () => {
    const steps = computeSteps(undefined);
    const visible = steps.filter((s) => s.visible);
    expect(visible).toHaveLength(1);
    expect(visible[0]?.key).toBe('select-tool');
  });

  it('能力字段为 undefined 时视为 false', () => {
    const caps: ProviderCapabilities = {};
    const steps = computeSteps(caps);
    const visible = steps.filter((s) => s.visible);
    expect(visible).toHaveLength(1);
  });

  it('步骤 key 和 title 正确', () => {
    const steps = computeSteps({
      authOptions: ['default'],
      supportsCustomModel: true,
      supportsMcp: true,
      supportsSkill: true,
    });
    expect(steps[0]).toMatchObject({ key: 'select-tool', title: '选择工具' });
    expect(steps[1]).toMatchObject({ key: 'auth-config', title: '认证方案' });
    expect(steps[2]).toMatchObject({ key: 'model-config', title: '模型配置' });
    expect(steps[3]).toMatchObject({ key: 'extension-config', title: '扩展配置' });
  });
});

describe('getVisibleSteps', () => {
  it('仅返回 visible 为 true 的步骤', () => {
    const caps: ProviderCapabilities = {
      supportsCustomModel: true,
      supportsMcp: false,
      supportsSkill: false,
    };
    const visible = getVisibleSteps(caps);
    expect(visible).toHaveLength(2);
    expect(visible[0]?.key).toBe('select-tool');
    expect(visible[1]?.key).toBe('model-config');
  });

  it('所有能力为 false 时返回 1 个步骤', () => {
    const visible = getVisibleSteps({
      supportsCustomModel: false,
      supportsMcp: false,
      supportsSkill: false,
    });
    expect(visible).toHaveLength(1);
  });

  it('所有能力为 true 且有 authOptions 时返回 4 个步骤', () => {
    const visible = getVisibleSteps({
      authOptions: ['default'],
      supportsCustomModel: true,
      supportsMcp: true,
      supportsSkill: true,
    });
    expect(visible).toHaveLength(4);
  });

  it('所有原有能力为 true 但无 authOptions 时返回 3 个步骤', () => {
    const visible = getVisibleSteps({
      supportsCustomModel: true,
      supportsMcp: true,
      supportsSkill: true,
    });
    expect(visible).toHaveLength(3);
  });
});

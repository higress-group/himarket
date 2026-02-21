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
    expect(steps).toHaveLength(3);
    expect(steps[0].visible).toBe(true);
    expect(steps[1].visible).toBe(false);
    expect(steps[2].visible).toBe(false);
  });

  it('supportsCustomModel 为 true 时，步骤二可见', () => {
    const caps: ProviderCapabilities = {
      supportsCustomModel: true,
      supportsMcp: false,
      supportsSkill: false,
    };
    const steps = computeSteps(caps);
    expect(steps[1].visible).toBe(true);
    expect(steps[2].visible).toBe(false);
  });

  it('supportsMcp 为 true 时，步骤三可见', () => {
    const caps: ProviderCapabilities = {
      supportsCustomModel: false,
      supportsMcp: true,
      supportsSkill: false,
    };
    const steps = computeSteps(caps);
    expect(steps[1].visible).toBe(false);
    expect(steps[2].visible).toBe(true);
  });

  it('supportsSkill 为 true 时，步骤三可见', () => {
    const caps: ProviderCapabilities = {
      supportsCustomModel: false,
      supportsMcp: false,
      supportsSkill: true,
    };
    const steps = computeSteps(caps);
    expect(steps[1].visible).toBe(false);
    expect(steps[2].visible).toBe(true);
  });

  it('所有能力为 true 时，三个步骤全部可见', () => {
    const caps: ProviderCapabilities = {
      supportsCustomModel: true,
      supportsMcp: true,
      supportsSkill: true,
    };
    const steps = computeSteps(caps);
    expect(steps.every(s => s.visible)).toBe(true);
  });

  it('传入 null 时，仅步骤一可见', () => {
    const steps = computeSteps(null);
    const visible = steps.filter(s => s.visible);
    expect(visible).toHaveLength(1);
    expect(visible[0].key).toBe('select-tool');
  });

  it('传入 undefined 时，仅步骤一可见', () => {
    const steps = computeSteps(undefined);
    const visible = steps.filter(s => s.visible);
    expect(visible).toHaveLength(1);
    expect(visible[0].key).toBe('select-tool');
  });

  it('能力字段为 undefined 时视为 false', () => {
    const caps: ProviderCapabilities = {};
    const steps = computeSteps(caps);
    const visible = steps.filter(s => s.visible);
    expect(visible).toHaveLength(1);
  });

  it('步骤 key 和 title 正确', () => {
    const steps = computeSteps({ supportsCustomModel: true, supportsMcp: true, supportsSkill: true });
    expect(steps[0]).toMatchObject({ key: 'select-tool', title: '选择工具' });
    expect(steps[1]).toMatchObject({ key: 'model-config', title: '模型配置' });
    expect(steps[2]).toMatchObject({ key: 'extension-config', title: '扩展配置' });
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
    expect(visible[0].key).toBe('select-tool');
    expect(visible[1].key).toBe('model-config');
  });

  it('所有能力为 false 时返回 1 个步骤', () => {
    const visible = getVisibleSteps({ supportsCustomModel: false, supportsMcp: false, supportsSkill: false });
    expect(visible).toHaveLength(1);
  });

  it('所有能力为 true 时返回 3 个步骤', () => {
    const visible = getVisibleSteps({ supportsCustomModel: true, supportsMcp: true, supportsSkill: true });
    expect(visible).toHaveLength(3);
  });
});

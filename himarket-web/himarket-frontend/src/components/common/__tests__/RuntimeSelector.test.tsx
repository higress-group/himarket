import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { RuntimeSelector, type RuntimeOption } from '../RuntimeSelector';

afterEach(() => cleanup());

// ===== 测试数据 =====

const k8sRuntime: RuntimeOption = {
  type: 'k8s',
  label: 'K8s 沙箱',
  description: '通过 K8s Pod 提供隔离运行环境（生产部署）',
  available: true,
};

const k8sUnavailable: RuntimeOption = {
  type: 'k8s',
  label: 'K8s 沙箱',
  description: '通过 K8s Pod 提供隔离运行环境（生产部署）',
  available: false,
  unavailableReason: '请先配置 K8s 集群',
};

// ===== 单元测试 =====

describe('RuntimeSelector', () => {
  it('渲染所有兼容运行时选项', () => {
    const onSelect = vi.fn();
    render(
      <RuntimeSelector
        cliProvider="claude-code"
        compatibleRuntimes={[k8sRuntime]}
        selectedRuntime=""
        onSelect={onSelect}
      />,
    );

    expect(screen.getByText('K8s 沙箱')).toBeInTheDocument();
  });

  it('不可用的运行时标记为 disabled 并显示"不可用"标签', () => {
    const onSelect = vi.fn();
    render(
      <RuntimeSelector
        cliProvider="qodercli"
        compatibleRuntimes={[k8sUnavailable]}
        selectedRuntime=""
        onSelect={onSelect}
      />,
    );

    expect(screen.getByText('不可用')).toBeInTheDocument();

    const radios = screen.getAllByRole('radio');
    const k8sRadio = radios.find(
      (r) => r.getAttribute('value') === 'k8s',
    );
    expect(k8sRadio).toBeDisabled();
  });

  it('单一兼容运行时时自动选中', () => {
    const onSelect = vi.fn();
    render(
      <RuntimeSelector
        cliProvider="qodercli"
        compatibleRuntimes={[k8sRuntime]}
        selectedRuntime=""
        onSelect={onSelect}
      />,
    );

    expect(onSelect).toHaveBeenCalledWith('k8s');
  });

  it('已选中正确运行时时不重复触发 onSelect', () => {
    const onSelect = vi.fn();
    render(
      <RuntimeSelector
        cliProvider="qodercli"
        compatibleRuntimes={[k8sRuntime]}
        selectedRuntime="k8s"
        onSelect={onSelect}
      />,
    );

    expect(onSelect).not.toHaveBeenCalled();
  });

  it('空兼容列表时显示提示信息', () => {
    const onSelect = vi.fn();
    render(
      <RuntimeSelector
        cliProvider="unknown"
        compatibleRuntimes={[]}
        selectedRuntime=""
        onSelect={onSelect}
      />,
    );

    expect(screen.getByText('没有兼容的运行时方案')).toBeInTheDocument();
  });

  it('K8s 未配置时标记为不可用并显示原因', () => {
    const onSelect = vi.fn();
    render(
      <RuntimeSelector
        cliProvider="qodercli"
        compatibleRuntimes={[k8sUnavailable]}
        selectedRuntime=""
        onSelect={onSelect}
      />,
    );

    expect(screen.getByText('不可用')).toBeInTheDocument();
    expect(screen.getByText('通过 K8s Pod 提供隔离运行环境（生产部署）')).toBeInTheDocument();
    expect(screen.getByText('请先配置 K8s 集群')).toBeInTheDocument();
  });

  it('渲染运行时描述信息', () => {
    const onSelect = vi.fn();
    render(
      <RuntimeSelector
        cliProvider="claude-code"
        compatibleRuntimes={[k8sRuntime]}
        selectedRuntime="k8s"
        onSelect={onSelect}
      />,
    );

    expect(
      screen.getByText('通过 K8s Pod 提供隔离运行环境（生产部署）'),
    ).toBeInTheDocument();
  });
});

import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { RuntimeSelector, type RuntimeOption } from '../RuntimeSelector';

afterEach(() => cleanup());

// ===== 测试数据 =====

const localRuntime: RuntimeOption = {
  type: 'local',
  label: 'POC 本地启动',
  description: '通过 ProcessBuilder 在本机启动 CLI 子进程',
  available: true,
};

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
        compatibleRuntimes={[localRuntime, k8sRuntime]}
        selectedRuntime=""
        onSelect={onSelect}
      />,
    );

    expect(screen.getByText('POC 本地启动')).toBeInTheDocument();
    expect(screen.getByText('K8s 沙箱')).toBeInTheDocument();
  });

  it('不兼容的运行时标记为 disabled 并显示"不可用"标签', () => {
    const onSelect = vi.fn();
    render(
      <RuntimeSelector
        cliProvider="qodercli"
        compatibleRuntimes={[localRuntime, k8sUnavailable]}
        selectedRuntime="local"
        onSelect={onSelect}
      />,
    );

    expect(screen.getByText('不可用')).toBeInTheDocument();

    // K8s 的 radio 应该是 disabled
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
        compatibleRuntimes={[localRuntime]}
        selectedRuntime=""
        onSelect={onSelect}
      />,
    );

    expect(onSelect).toHaveBeenCalledWith('local');
  });

  it('单一可用运行时时自动选中（忽略不可用的）', () => {
    const onSelect = vi.fn();
    render(
      <RuntimeSelector
        cliProvider="qodercli"
        compatibleRuntimes={[localRuntime, k8sUnavailable]}
        selectedRuntime=""
        onSelect={onSelect}
      />,
    );

    // 只有 local 可用，应自动选中
    expect(onSelect).toHaveBeenCalledWith('local');
  });

  it('已选中正确运行时时不重复触发 onSelect', () => {
    const onSelect = vi.fn();
    render(
      <RuntimeSelector
        cliProvider="qodercli"
        compatibleRuntimes={[localRuntime]}
        selectedRuntime="local"
        onSelect={onSelect}
      />,
    );

    // 已经选中 local，不应再次调用
    expect(onSelect).not.toHaveBeenCalled();
  });

  it('点击可用运行时触发 onSelect', () => {
    const onSelect = vi.fn();
    render(
      <RuntimeSelector
        cliProvider="claude-code"
        compatibleRuntimes={[localRuntime, k8sRuntime]}
        selectedRuntime="local"
        onSelect={onSelect}
      />,
    );

    const radios = screen.getAllByRole('radio');
    const k8sRadio = radios.find(
      (r) => r.getAttribute('value') === 'k8s',
    );
    fireEvent.click(k8sRadio!);
    expect(onSelect).toHaveBeenCalledWith('k8s');
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
        compatibleRuntimes={[localRuntime, k8sUnavailable]}
        selectedRuntime="local"
        onSelect={onSelect}
      />,
    );

    // 不可用标签存在
    expect(screen.getByText('不可用')).toBeInTheDocument();
    // 描述文本仍然渲染
    expect(screen.getByText('通过 K8s Pod 提供隔离运行环境（生产部署）')).toBeInTheDocument();
    // 不可用原因内联显示
    expect(screen.getByText('请先配置 K8s 集群')).toBeInTheDocument();
  });

  it('多个可用运行时时不自动选中', () => {
    const onSelect = vi.fn();
    render(
      <RuntimeSelector
        cliProvider="claude-code"
        compatibleRuntimes={[localRuntime, k8sRuntime]}
        selectedRuntime=""
        onSelect={onSelect}
      />,
    );

    // 多个可用运行时，不应自动选中
    expect(onSelect).not.toHaveBeenCalled();
  });

  it('渲染运行时描述信息', () => {
    const onSelect = vi.fn();
    render(
      <RuntimeSelector
        cliProvider="claude-code"
        compatibleRuntimes={[localRuntime, k8sRuntime]}
        selectedRuntime="local"
        onSelect={onSelect}
      />,
    );

    expect(
      screen.getByText('通过 ProcessBuilder 在本机启动 CLI 子进程'),
    ).toBeInTheDocument();
    expect(
      screen.getByText('通过 K8s Pod 提供隔离运行环境（生产部署）'),
    ).toBeInTheDocument();
  });

});

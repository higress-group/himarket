import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, afterEach } from 'vitest';

import { SelectableCard } from '../SelectableCard';

afterEach(() => cleanup());

describe('SelectableCard', () => {
  it('渲染子内容', () => {
    render(
      <SelectableCard onClick={vi.fn()} selected={false}>
        <span>测试内容</span>
      </SelectableCard>,
    );
    expect(screen.getByText('测试内容')).toBeInTheDocument();
  });

  it('未选中状态使用灰色边框', () => {
    render(
      <SelectableCard onClick={vi.fn()} selected={false}>
        内容
      </SelectableCard>,
    );
    const card = screen.getByRole('button');
    expect(card.className).toContain('border-gray-200');
    expect(card.className).not.toContain('border-blue-500');
  });

  it('选中状态使用蓝色边框高亮', () => {
    render(
      <SelectableCard onClick={vi.fn()} selected={true}>
        内容
      </SelectableCard>,
    );
    const card = screen.getByRole('button');
    expect(card.className).toContain('border-blue-500');
    expect(card.className).toContain('bg-blue-50');
  });

  it('禁用状态置灰且不可点击', () => {
    const onClick = vi.fn();
    render(
      <SelectableCard disabled={true} onClick={onClick} selected={false}>
        内容
      </SelectableCard>,
    );
    const card = screen.getByRole('button');
    expect(card.className).toContain('opacity-50');
    expect(card.className).toContain('cursor-not-allowed');
    expect(card).toHaveAttribute('aria-disabled', 'true');

    fireEvent.click(card);
    expect(onClick).not.toHaveBeenCalled();
  });

  it('点击未禁用卡片触发 onClick', () => {
    const onClick = vi.fn();
    render(
      <SelectableCard onClick={onClick} selected={false}>
        内容
      </SelectableCard>,
    );
    fireEvent.click(screen.getByRole('button'));
    expect(onClick).toHaveBeenCalledOnce();
  });

  it('禁用状态下点击不触发 onClick', () => {
    const onClick = vi.fn();
    render(
      <SelectableCard disabled={true} onClick={onClick} selected={true}>
        内容
      </SelectableCard>,
    );
    fireEvent.click(screen.getByRole('button'));
    expect(onClick).not.toHaveBeenCalled();
  });

  it('键盘 Enter 触发 onClick', () => {
    const onClick = vi.fn();
    render(
      <SelectableCard onClick={onClick} selected={false}>
        内容
      </SelectableCard>,
    );
    fireEvent.keyDown(screen.getByRole('button'), { key: 'Enter' });
    expect(onClick).toHaveBeenCalledOnce();
  });

  it('键盘 Space 触发 onClick', () => {
    const onClick = vi.fn();
    render(
      <SelectableCard onClick={onClick} selected={false}>
        内容
      </SelectableCard>,
    );
    fireEvent.keyDown(screen.getByRole('button'), { key: ' ' });
    expect(onClick).toHaveBeenCalledOnce();
  });

  it('禁用状态下键盘操作不触发 onClick', () => {
    const onClick = vi.fn();
    render(
      <SelectableCard disabled={true} onClick={onClick} selected={false}>
        内容
      </SelectableCard>,
    );
    fireEvent.keyDown(screen.getByRole('button'), { key: 'Enter' });
    fireEvent.keyDown(screen.getByRole('button'), { key: ' ' });
    expect(onClick).not.toHaveBeenCalled();
  });

  it('设置正确的 aria 属性', () => {
    render(
      <SelectableCard onClick={vi.fn()} selected={true}>
        内容
      </SelectableCard>,
    );
    const card = screen.getByRole('button');
    expect(card).toHaveAttribute('aria-selected', 'true');
    expect(card).toHaveAttribute('aria-disabled', 'false');
  });

  it('禁用时 tabIndex 为 -1', () => {
    render(
      <SelectableCard disabled={true} onClick={vi.fn()} selected={false}>
        内容
      </SelectableCard>,
    );
    expect(screen.getByRole('button')).toHaveAttribute('tabindex', '-1');
  });

  it('未禁用时 tabIndex 为 0', () => {
    render(
      <SelectableCard onClick={vi.fn()} selected={false}>
        内容
      </SelectableCard>,
    );
    expect(screen.getByRole('button')).toHaveAttribute('tabindex', '0');
  });
});

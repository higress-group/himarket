import { Zap, Terminal } from 'lucide-react';
import { useEffect, useState, useRef } from 'react';

import type { Command } from '../../types/coding-protocol';

interface SlashMenuProps {
  commands: Command[];
  filter: string;
  onSelect: (name: string) => void;
}

/** 根据 command name 推断分类 */
function categorize(cmd: Command): 'skill' | 'builtin' {
  const builtins = ['compact', 'help', 'clear', 'status', 'config'];
  return builtins.includes(cmd.name) ? 'builtin' : 'skill';
}

export function SlashMenu({ commands, filter, onSelect }: SlashMenuProps) {
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  const filtered = commands.filter((c) => c.name.toLowerCase().startsWith(filter.toLowerCase()));

  // Reset selection when filter changes
  useEffect(() => {
    setSelectedIndex(0);
  }, [filter]);

  // Keyboard navigation
  useEffect(() => {
    if (filtered.length === 0) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        setSelectedIndex((prev) => (prev + 1) % filtered.length);
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        setSelectedIndex((prev) => (prev - 1 + filtered.length) % filtered.length);
      } else if (e.key === 'Enter' || e.key === 'Tab') {
        e.preventDefault();
        if (filtered[selectedIndex]) {
          onSelect(filtered[selectedIndex].name);
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [filtered, selectedIndex, onSelect]);

  // Scroll selected item into view
  useEffect(() => {
    if (!menuRef.current) return;
    const el = menuRef.current.querySelector(`[data-index="${selectedIndex}"]`) as HTMLElement;
    el?.scrollIntoView({ block: 'nearest' });
  }, [selectedIndex]);

  if (filtered.length === 0) return null;

  // Group by category
  const skills = filtered.filter((c) => categorize(c) === 'skill');
  const builtins = filtered.filter((c) => categorize(c) === 'builtin');

  // Build flat render list with group headers for index tracking
  type RenderItem =
    | { type: 'header'; label: string; icon: React.ReactNode }
    | { type: 'command'; cmd: Command; flatIndex: number };

  const items: RenderItem[] = [];
  let idx = 0;

  if (skills.length > 0) {
    items.push({ icon: <Zap size={12} />, label: '技能', type: 'header' });
    for (const cmd of skills) {
      items.push({ cmd, flatIndex: idx++, type: 'command' });
    }
  }
  if (builtins.length > 0) {
    items.push({ icon: <Terminal size={12} />, label: '内置命令', type: 'header' });
    for (const cmd of builtins) {
      items.push({ cmd, flatIndex: idx++, type: 'command' });
    }
  }

  // The command being hovered or selected, for detail panel
  const activeIndex = hoveredIndex ?? selectedIndex;
  const activeCmd = filtered[activeIndex];

  return (
    <div className="absolute bottom-full left-0 right-0 mb-1 mx-4 flex gap-0 z-50">
      {/* Command list */}
      <div
        className="flex-1 min-w-0 max-h-[320px] overflow-y-auto rounded-l-xl border border-r-0
                   border-gray-200/80 bg-white/95 backdrop-blur-md shadow-lg"
        ref={menuRef}
      >
        {items.map((item) => {
          if (item.type === 'header') {
            return (
              <div
                className="flex items-center gap-1.5 px-3 py-1.5 text-[11px] font-medium
                           text-gray-400 uppercase tracking-wider select-none
                           bg-gray-50/80 border-b border-gray-100 sticky top-0"
                key={`h-${item.label}`}
              >
                {item.icon}
                {item.label}
              </div>
            );
          }

          const { cmd, flatIndex } = item;
          const isSelected = flatIndex === selectedIndex;
          const isHovered = flatIndex === hoveredIndex;

          return (
            <button
              className={`px-3 py-2 cursor-pointer transition-colors w-full text-left border-0 bg-transparent
                ${isSelected ? 'bg-blue-50 border-l-2 border-blue-500' : 'border-l-2 border-transparent'}
                ${isHovered && !isSelected ? 'bg-gray-50' : ''}
              `}
              data-index={flatIndex}
              key={cmd.name}
              onClick={() => onSelect(cmd.name)}
              onMouseEnter={() => setHoveredIndex(flatIndex)}
              onMouseLeave={() => setHoveredIndex(null)}
              type="button"
            >
              <div className="text-sm font-medium text-gray-700 font-mono">/{cmd.name}</div>
              <div className="text-xs text-gray-400 mt-0.5 line-clamp-1">{cmd.description}</div>
            </button>
          );
        })}
      </div>

      {/* Detail panel */}
      {activeCmd && (
        <div
          className="w-[280px] flex-shrink-0 rounded-r-xl border border-gray-200/80
                     bg-gray-50/95 backdrop-blur-md shadow-lg p-4 overflow-y-auto max-h-[320px]"
        >
          <div className="text-xs font-medium text-gray-400 uppercase tracking-wider mb-2">
            {categorize(activeCmd) === 'skill' ? '技能' : '内置命令'}
          </div>
          <div className="text-sm font-semibold text-gray-800 font-mono mb-2">
            /{activeCmd.name}
          </div>
          <p className="text-xs text-gray-500 leading-relaxed whitespace-pre-wrap">
            {activeCmd.description}
          </p>
          {activeCmd.input?.hint && (
            <div className="mt-3 pt-3 border-t border-gray-200/80">
              <div className="text-[11px] font-medium text-gray-400 mb-1">输入提示</div>
              <div className="text-xs text-gray-500">{activeCmd.input.hint}</div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

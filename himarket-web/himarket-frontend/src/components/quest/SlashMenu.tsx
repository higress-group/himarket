import type { Command } from "../../types/acp";

interface SlashMenuProps {
  commands: Command[];
  filter: string;
  onSelect: (name: string) => void;
}

export function SlashMenu({ commands, filter, onSelect }: SlashMenuProps) {
  const filtered = commands.filter(c =>
    c.name.toLowerCase().startsWith(filter.toLowerCase())
  );

  if (filtered.length === 0) return null;

  return (
    <div
      className="absolute bottom-full left-4 mb-1 w-64 rounded-xl border border-gray-200/80
                    bg-white/95 backdrop-blur-md shadow-lg overflow-hidden"
    >
      {filtered.map(cmd => (
        <div
          key={cmd.name}
          className="px-3 py-2 cursor-pointer hover:bg-gray-50 transition-colors"
          onClick={() => onSelect(cmd.name)}
        >
          <span className="text-sm font-medium text-gray-700">/{cmd.name}</span>
          <span className="text-xs text-gray-400 ml-2">{cmd.description}</span>
        </div>
      ))}
    </div>
  );
}

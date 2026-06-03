import { useTranslation } from 'react-i18next';

import { TerminalOutput } from './TerminalOutput';

import type { ChatItemToolCall } from '../../types/coding-protocol';

interface TerminalViewProps {
  terminalId: string;
  toolCall: ChatItemToolCall | null;
}

export function TerminalView({ terminalId, toolCall }: TerminalViewProps) {
  const { t } = useTranslation('coding');
  const outputs = (toolCall?.content ?? [])
    .filter(
      (c) =>
        c.type === 'content' &&
        c.content?.type === 'text' &&
        typeof c.content.text === 'string' &&
        c.content.text.length > 0,
    )
    .map((c) => (c.type === 'content' && c.content?.type === 'text' ? c.content.text : ''))
    .filter(Boolean);

  return (
    <div className="p-4 space-y-3">
      <div className="rounded-lg border border-gray-200/70 bg-gray-50/70 px-3 py-2">
        <div className="text-xs text-gray-400">Terminal ID</div>
        <div className="text-sm font-mono text-gray-700 mt-0.5">{terminalId}</div>
      </div>
      {outputs.length > 0 ? (
        outputs.map((output, idx) => <TerminalOutput key={idx} text={output} />)
      ) : (
        <div className="rounded-lg border border-amber-200 bg-amber-50/70 px-3 py-2.5">
          <div className="text-xs text-amber-700 font-medium">{t('terminalView.unavailable')}</div>
          <div className="text-xs text-amber-600 mt-1 leading-relaxed">
            {t('terminalView.hint')}
          </div>
        </div>
      )}
    </div>
  );
}

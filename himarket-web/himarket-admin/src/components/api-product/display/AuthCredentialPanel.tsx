import { Button } from 'antd';
import { useState } from 'react';

import { copyToClipboard } from '@/lib/utils';

import { ApiKeyDisplay } from './ApiKeyDisplay';

interface AuthCredentialPanelProps {
  apiKey?: string;
  secretName?: string;
}

export function AuthCredentialPanel({ apiKey, secretName }: AuthCredentialPanelProps) {
  const [expanded, setExpanded] = useState(false);
  if (!expanded) {
    return (
      <div className="mt-2">
        <Button
          className="p-0 text-xs text-green-600"
          onClick={() => setExpanded(true)}
          size="small"
          type="link"
        >
          查看鉴权凭证
        </Button>
      </div>
    );
  }
  return (
    <div className="mt-2 rounded-lg border border-green-100 bg-green-50/50 p-3 space-y-1.5 text-xs">
      <div className="flex items-center justify-between mb-1">
        <span className="text-gray-500 text-[11px]">鉴权凭证</span>
        <Button
          className="p-0 text-[11px]"
          onClick={() => setExpanded(false)}
          size="small"
          type="link"
        >
          收起
        </Button>
      </div>
      {secretName && (
        <div className="flex items-center gap-2">
          <span className="text-gray-500 shrink-0 w-20">Secret</span>
          <span className="font-mono text-gray-700 truncate" title={secretName}>
            {secretName}
          </span>
          <Button
            className="p-0 text-[11px] shrink-0"
            onClick={() => {
              copyToClipboard(secretName);
            }}
            size="small"
            type="link"
          >
            复制
          </Button>
        </div>
      )}
      {apiKey && (
        <div className="flex items-center gap-2">
          <span className="text-gray-500 shrink-0 w-20">API Key</span>
          <ApiKeyDisplay apiKey={apiKey} />
        </div>
      )}
    </div>
  );
}

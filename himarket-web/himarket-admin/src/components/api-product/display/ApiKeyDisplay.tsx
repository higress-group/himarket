import { Button } from 'antd';
import { useState } from 'react';

import { copyToClipboard } from '@/lib/utils';

interface ApiKeyDisplayProps {
  apiKey: string;
}

export function ApiKeyDisplay({ apiKey }: ApiKeyDisplayProps) {
  const [visible, setVisible] = useState(false);
  const masked =
    apiKey.length > 7
      ? apiKey.substring(0, 3) + '****' + apiKey.substring(apiKey.length - 4)
      : '****';
  return (
    <span className="inline-flex items-center gap-1.5">
      <span className="font-mono text-gray-700">{visible ? apiKey : masked}</span>
      <Button
        className="p-0 text-[11px]"
        onClick={() => setVisible(!visible)}
        size="small"
        type="link"
      >
        {visible ? '隐藏' : '查看'}
      </Button>
      <Button
        className="p-0 text-[11px]"
        onClick={() => {
          copyToClipboard(apiKey);
        }}
        size="small"
        type="link"
      >
        复制
      </Button>
    </span>
  );
}

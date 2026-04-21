export type McpVendorType = 'MODELSCOPE' | 'MCP_REGISTRY' | 'LOBEHUB';

export interface RemoteMcpItemResult {
  remoteId: string;
  mcpName: string;
  displayName: string;
  description: string;
  protocolType: string;
  connectionConfig: string;
  tags: string | null;
  icon: string | null;
  repoUrl: string | null;
  extraParams: string | null;
  existsInPlatform: boolean;
}

export interface RemoteMcpItemParam {
  remoteId: string;
  mcpName: string;
  displayName: string;
  description: string;
  protocolType: string;
  connectionConfig: string;
  tags: string | null;
  icon: string | null;
  repoUrl: string | null;
  extraParams: string | null;
}

export interface BatchImportParam {
  vendorType: McpVendorType;
  items: RemoteMcpItemParam[];
}

export interface ImportItemStatus {
  mcpName: string;
  status: 'SUCCESS' | 'SKIPPED' | 'FAILED';
  message: string | null;
}

export interface BatchImportResult {
  successCount: number;
  skippedCount: number;
  failedCount: number;
  details: ImportItemStatus[];
}

export interface PageResult<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
}

export interface VendorOption {
  value: McpVendorType;
  label: string;
  description: string;
  iconUrl: string;
  color: string;
}

export const VENDOR_OPTIONS: VendorOption[] = [
  {
    color: '#6366f1',
    description: '魔搭社区 MCP 广场，9000+ MCP Server',
    iconUrl: 'https://g.alicdn.com/sail-web/maas/2.13.38/favicon/128.ico',
    label: 'ModelScope',
    value: 'MODELSCOPE',
  },
  {
    color: '#0ea5e9',
    description: '官方 MCP Registry，社区驱动的标准化目录',
    iconUrl:
      'https://modelcontextprotocol.io/mintlify-assets/_mintlify/favicons/mcp/ebiVJzri-bsiCfVZ/_generated/favicon/apple-touch-icon.png',
    label: 'MCP Registry',
    value: 'MCP_REGISTRY',
  },
  {
    color: '#8b5cf6',
    description: 'LobeHub MCP 市场，丰富的 AI 工具生态',
    iconUrl: 'https://lobehub.com/icon-192x192.png',
    label: 'LobeHub',
    value: 'LOBEHUB',
  },
];

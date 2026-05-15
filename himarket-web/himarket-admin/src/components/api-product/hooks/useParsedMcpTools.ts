import * as yaml from 'js-yaml';
import { useEffect, useState } from 'react';

import type { ApiProduct } from '@/types/api-product';

export interface ParsedTool {
  name: string;
  description: string;
  args?: Array<{
    name: string;
    description: string;
    type: string;
    required: boolean;
    position: string;
    default?: string;
    enum?: string[];
  }>;
}

function parseYamlConfig(yamlString: string): {
  tools?: ParsedTool[];
} | null {
  try {
    const parsed = yaml.load(yamlString) as { tools?: ParsedTool[] };
    return parsed;
  } catch (_error) {
    console.error('YAML解析失败:', _error);
    return null;
  }
}

export function useParsedMcpTools(apiProduct: ApiProduct) {
  const [parsedTools, setParsedTools] = useState<ParsedTool[]>([]);

  useEffect(() => {
    if (apiProduct.type !== 'MCP_SERVER') {
      setParsedTools([]);
      return;
    }

    if (apiProduct.mcpConfig?.tools) {
      const parsedConfig = parseYamlConfig(apiProduct.mcpConfig.tools);
      if (parsedConfig && parsedConfig.tools && Array.isArray(parsedConfig.tools)) {
        setParsedTools(parsedConfig.tools);
      } else {
        setParsedTools([]);
      }
    } else {
      setParsedTools([]);
    }
  }, [apiProduct]);

  return parsedTools;
}

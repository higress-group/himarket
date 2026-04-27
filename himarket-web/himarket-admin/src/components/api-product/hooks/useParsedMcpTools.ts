import * as yaml from 'js-yaml';
import { useEffect, useState } from 'react';

import type { ApiProduct } from '@/types/api-product';
import type { McpMetaItem } from '@/types/api-product';

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

export function useParsedMcpTools(apiProduct: ApiProduct, mcpMetaList: McpMetaItem[]) {
  const [parsedTools, setParsedTools] = useState<ParsedTool[]>([]);

  useEffect(() => {
    if (apiProduct.type !== 'MCP_SERVER') {
      setParsedTools([]);
      return;
    }

    const metaToolsConfig = mcpMetaList[0]?.toolsConfig;
    if (metaToolsConfig) {
      try {
        let toolsArr = metaToolsConfig;
        if (typeof toolsArr === 'string') {
          toolsArr = JSON.parse(toolsArr);
        }
        if (Array.isArray(toolsArr) && toolsArr.length > 0) {
          const mapped = (
            toolsArr as Array<{
              description?: string;
              inputSchema?: {
                properties?: Record<string, unknown>;
                required?: string[];
              };
              name?: string;
            }>
          ).map((t) => ({
            args: t.inputSchema?.properties
              ? Object.entries(t.inputSchema.properties).map(([key, val]) => ({
                  description: String((val as Record<string, unknown>).description || ''),
                  name: key,
                  position: 'query',
                  required:
                    Array.isArray(t.inputSchema?.required) && t.inputSchema.required.includes(key),
                  type: String((val as Record<string, unknown>).type || 'string'),
                }))
              : undefined,
            description: t.description || '',
            name: t.name || '',
          }));
          setParsedTools(mapped);
          return;
        }
      } catch {
        try {
          const parsedConfig = parseYamlConfig(
            typeof metaToolsConfig === 'string' ? metaToolsConfig : '',
          );
          if (parsedConfig && parsedConfig.tools && Array.isArray(parsedConfig.tools)) {
            setParsedTools(parsedConfig.tools);
            return;
          }
        } catch {
          // fallback
        }
      }
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
  }, [apiProduct, mcpMetaList]);

  return parsedTools;
}

import { useEffect, useState } from 'react';

import { formatDomainWithPort } from '@/lib/utils';
import type { ApiProduct } from '@/types/api-product';
import type { LinkedService } from '@/types/api-product';

export interface DomainOption {
  domain: { domain: string; port?: number; protocol: string; networkType?: string };
  label: string;
  value: number;
}

export interface McpConnectionConfig {
  httpJson: string;
  sseJson: string;
  localJson: string;
  domainOptions: DomainOption[];
}

function generateConnectionConfig(
  domains: Array<{ domain: string; port?: number; protocol: string }> | null | undefined,
  path: string | null | undefined,
  serverName: string,
  localConfig?: unknown,
  protocolType?: string,
  domainIndex: number = 0,
) {
  let httpJson = '';
  let sseJson = '';
  let localJson = '';

  const protoLower = (protocolType || '').toLowerCase();

  // STDIO: 使用 rawConfig 展示
  if (protoLower === 'stdio' && localConfig) {
    localJson = JSON.stringify(localConfig, null, 2);
    return { httpJson, localJson, sseJson };
  }

  // 网络型协议：用 domains + path 生成配置
  if (domains && domains.length > 0 && domainIndex < domains.length) {
    const domain = domains[domainIndex];
    if (!domain) return { httpJson, localJson, sseJson };
    const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
    const baseUrl = `${domain.protocol}://${formattedDomain}`;
    const fullUrl = `${baseUrl}${path && path !== '/' ? path : ''}`;

    if (protoLower === 'sse') {
      sseJson = JSON.stringify(
        { mcpServers: { [serverName]: { type: 'sse', url: `${fullUrl}/sse` } } },
        null,
        2,
      );
    } else if (protoLower === 'streamablehttp') {
      httpJson = JSON.stringify(
        { mcpServers: { [serverName]: { type: 'streamable-http', url: fullUrl } } },
        null,
        2,
      );
    } else if (protoLower === 'dualhttp') {
      sseJson = JSON.stringify(
        { mcpServers: { [serverName]: { type: 'sse', url: `${fullUrl}/sse` } } },
        null,
        2,
      );
      httpJson = JSON.stringify(
        { mcpServers: { [serverName]: { type: 'streamable-http', url: fullUrl } } },
        null,
        2,
      );
    } else {
      // 协议未指定时，默认同时展示 SSE + HTTP
      sseJson = JSON.stringify(
        { mcpServers: { [serverName]: { type: 'sse', url: `${fullUrl}/sse` } } },
        null,
        2,
      );
      httpJson = JSON.stringify({ mcpServers: { [serverName]: { url: fullUrl } } }, null, 2);
    }
    return { httpJson, localJson, sseJson };
  }

  // fallback: 没有 domains 时，如果 localConfig 存在则展示为 localJson
  if (localConfig) {
    localJson = JSON.stringify(localConfig, null, 2);
  }

  return { httpJson, localJson, sseJson };
}

export function useMcpConnectionConfig(
  apiProduct: ApiProduct,
  linkedService: LinkedService | null,
  selectedDomainIndex: number,
): McpConnectionConfig {
  const [httpJson, setHttpJson] = useState('');
  const [sseJson, setSseJson] = useState('');
  const [localJson, setLocalJson] = useState('');

  const domains = apiProduct.mcpConfig?.mcpServerConfig?.domains || [];
  const domainOptions = domains.map((domain, index) => {
    const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
    return {
      domain,
      label: `${domain.protocol}://${formattedDomain}`,
      value: index,
    };
  });

  useEffect(() => {
    if (apiProduct.type !== 'MCP_SERVER' || !apiProduct.mcpConfig) {
      setHttpJson('');
      setSseJson('');
      setLocalJson('');
      return;
    }

    let mcpServerName = apiProduct.mcpConfig?.mcpServerName || apiProduct.name;
    if (linkedService) {
      if (
        linkedService.sourceType === 'GATEWAY' &&
        linkedService.apigRefConfig &&
        'mcpServerName' in linkedService.apigRefConfig
      ) {
        mcpServerName = linkedService.apigRefConfig.mcpServerName || apiProduct.name;
      } else if (linkedService.sourceType === 'GATEWAY' && linkedService.higressRefConfig) {
        mcpServerName = linkedService.higressRefConfig.mcpServerName || apiProduct.name;
      } else if (linkedService.sourceType === 'GATEWAY' && linkedService.adpAIGatewayRefConfig) {
        if ('modelApiName' in linkedService.adpAIGatewayRefConfig) {
          mcpServerName = linkedService.adpAIGatewayRefConfig.modelApiName || apiProduct.name;
        } else {
          mcpServerName = linkedService.adpAIGatewayRefConfig.mcpServerName || apiProduct.name;
        }
      } else if (linkedService.sourceType === 'GATEWAY' && linkedService.apsaraGatewayRefConfig) {
        if ('modelApiName' in linkedService.apsaraGatewayRefConfig) {
          mcpServerName = linkedService.apsaraGatewayRefConfig.modelApiName || apiProduct.name;
        } else {
          mcpServerName = linkedService.apsaraGatewayRefConfig.mcpServerName || apiProduct.name;
        }
      } else if (
        linkedService.sourceType === 'NACOS' &&
        linkedService.nacosRefConfig &&
        'mcpServerName' in linkedService.nacosRefConfig
      ) {
        mcpServerName = linkedService.nacosRefConfig.mcpServerName || apiProduct.name;
      }
    }

    const result = generateConnectionConfig(
      apiProduct.mcpConfig.mcpServerConfig.domains,
      apiProduct.mcpConfig.mcpServerConfig.path,
      mcpServerName,
      apiProduct.mcpConfig.mcpServerConfig.rawConfig,
      apiProduct.mcpConfig.protocol || apiProduct.mcpConfig.meta?.protocol,
      selectedDomainIndex,
    );
    setHttpJson(result.httpJson);
    setSseJson(result.sseJson);
    setLocalJson(result.localJson);
  }, [apiProduct, linkedService, selectedDomainIndex]);

  return { domainOptions, httpJson, localJson, sseJson };
}

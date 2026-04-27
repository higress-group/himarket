import { useEffect, useState } from 'react';

import { formatDomainWithPort } from '@/lib/utils';
import type { ApiProduct } from '@/types/api-product';
import type { LinkedService, McpMetaItem } from '@/types/api-product';

export interface DomainOption {
  domain: { domain: string; port?: number; protocol: string; networkType?: string };
  label: string;
  value: number;
}

export interface McpConnectionConfig {
  httpJson: string;
  sseJson: string;
  localJson: string;
  hotSseJson: string;
  hotHttpJson: string;
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

  if (localConfig) {
    const upperProto = (protocolType || '').toUpperCase();
    if (upperProto === 'STDIO' || upperProto === '') {
      localJson = JSON.stringify(localConfig, null, 2);
      return { httpJson, localJson, sseJson };
    }
    const cfg =
      typeof localConfig === 'object' && localConfig !== null
        ? (localConfig as Record<string, unknown>)
        : null;
    const servers = cfg?.mcpServers || cfg;
    const firstKey = servers ? Object.keys(servers)[0] : null;
    const entry = firstKey
      ? ((servers as Record<string, unknown> | null)?.[firstKey] as Record<string, unknown> | null)
      : null;
    const url = entry?.url;
    if (url) {
      if (upperProto === 'SSE') {
        sseJson = JSON.stringify({ mcpServers: { [serverName]: { type: 'sse', url } } }, null, 2);
      } else {
        httpJson = JSON.stringify(
          { mcpServers: { [serverName]: { type: 'streamable-http', url } } },
          null,
          2,
        );
      }
      return { httpJson, localJson, sseJson };
    }
    localJson = JSON.stringify(localConfig, null, 2);
    return { httpJson, localJson, sseJson };
  }

  if (domains && domains.length > 0 && path && domainIndex < domains.length) {
    const domain = domains[domainIndex];
    if (!domain) return { httpJson, localJson, sseJson };
    const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
    const baseUrl = `${domain.protocol}://${formattedDomain}`;
    const fullUrl = `${baseUrl}${path || '/'}`;

    const protoLower = (protocolType || '').toLowerCase();
    const isSSE = protoLower === 'sse';
    const isHTTP = protoLower.includes('http');

    if (isSSE) {
      sseJson = JSON.stringify(
        { mcpServers: { [serverName]: { type: 'sse', url: fullUrl } } },
        null,
        2,
      );
    } else if (isHTTP) {
      httpJson = JSON.stringify({ mcpServers: { [serverName]: { url: fullUrl } } }, null, 2);
    } else {
      sseJson = JSON.stringify(
        { mcpServers: { [serverName]: { type: 'sse', url: `${fullUrl}/sse` } } },
        null,
        2,
      );
      httpJson = JSON.stringify({ mcpServers: { [serverName]: { url: fullUrl } } }, null, 2);
    }
  }

  return { httpJson, localJson, sseJson };
}

export function useMcpConnectionConfig(
  apiProduct: ApiProduct,
  linkedService: LinkedService | null,
  mcpMetaList: McpMetaItem[],
  selectedDomainIndex: number,
): McpConnectionConfig {
  const [httpJson, setHttpJson] = useState('');
  const [sseJson, setSseJson] = useState('');
  const [localJson, setLocalJson] = useState('');
  const [hotSseJson, setHotSseJson] = useState('');
  const [hotHttpJson, setHotHttpJson] = useState('');

  const domains = apiProduct.mcpConfig?.mcpServerConfig?.domains || [];
  const domainOptions = domains.map((domain, index) => {
    const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
    return {
      domain,
      label: `${domain.protocol}://${formattedDomain}`,
      value: index,
    };
  });

  // Effect 1: 基于 mcpConfig 生成冷数据配置
  useEffect(() => {
    if (apiProduct.type !== 'MCP_SERVER' || !apiProduct.mcpConfig) {
      setHttpJson('');
      setSseJson('');
      setLocalJson('');
      return;
    }

    let mcpServerName = apiProduct.name;
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
      mcpMetaList[0]?.protocolType || apiProduct.mcpConfig.meta?.protocol,
      selectedDomainIndex,
    );
    setHttpJson(result.httpJson);
    setSseJson(result.sseJson);
    setLocalJson(result.localJson);
  }, [apiProduct, linkedService, selectedDomainIndex, mcpMetaList]);

  // Effect 2: 热数据配置
  useEffect(() => {
    if (apiProduct.type !== 'MCP_SERVER' || mcpMetaList.length === 0) {
      setHotSseJson('');
      setHotHttpJson('');
      return;
    }
    const meta = mcpMetaList[0];
    if (!meta) return;
    if (!meta.endpointUrl || meta.endpointStatus !== 'ACTIVE') {
      setHotSseJson('');
      setHotHttpJson('');
      return;
    }
    const serverName = meta.mcpName || apiProduct.name;
    const protocol = (meta.endpointProtocol || '').toLowerCase();
    const endpointUrl = meta.endpointUrl;
    if (protocol === 'sse') {
      setHotSseJson(
        JSON.stringify(
          { mcpServers: { [serverName]: { type: 'sse', url: endpointUrl } } },
          null,
          2,
        ),
      );
      setHotHttpJson('');
    } else if (protocol === 'streamablehttp' || protocol === 'http') {
      setHotHttpJson(
        JSON.stringify(
          { mcpServers: { [serverName]: { type: 'streamable-http', url: endpointUrl } } },
          null,
          2,
        ),
      );
      setHotSseJson('');
    } else {
      setHotSseJson(
        JSON.stringify(
          { mcpServers: { [serverName]: { type: 'sse', url: endpointUrl } } },
          null,
          2,
        ),
      );
      setHotHttpJson('');
    }
  }, [mcpMetaList, apiProduct]);

  // Effect 3: 冷数据 connectionConfig
  useEffect(() => {
    if (apiProduct.type !== 'MCP_SERVER') return;
    if (mcpMetaList.length === 0) return;

    const meta = mcpMetaList[0];
    if (!meta) return;
    const connCfg = meta.connectionConfig;
    if (!connCfg) {
      if (!apiProduct.mcpConfig) {
        setLocalJson('');
        setSseJson('');
        setHttpJson('');
      }
      return;
    }

    const serverName = meta.mcpName || apiProduct.name;
    const protocol = (meta.protocolType || '').toUpperCase();

    try {
      const parsed = JSON.parse(connCfg);

      if (protocol === 'STDIO' || protocol === '') {
        setLocalJson(JSON.stringify(parsed, null, 2));
        if (!apiProduct.mcpConfig) {
          setSseJson('');
          setHttpJson('');
        }
        return;
      }

      const servers = parsed?.mcpServers || parsed;
      const firstKey = servers ? Object.keys(servers)[0] : null;
      const entry = firstKey
        ? ((servers as Record<string, unknown> | null)?.[firstKey] as Record<
            string,
            unknown
          > | null)
        : null;
      const url = entry?.url;

      if (protocol === 'SSE') {
        const json = url
          ? JSON.stringify({ mcpServers: { [serverName]: { type: 'sse', url } } }, null, 2)
          : JSON.stringify(parsed, null, 2);
        setSseJson(json);
        if (!apiProduct.mcpConfig) {
          setLocalJson('');
          setHttpJson('');
        }
      } else if (protocol === 'STREAMABLEHTTP' || protocol === 'HTTP') {
        const json = url
          ? JSON.stringify(
              { mcpServers: { [serverName]: { type: 'streamable-http', url } } },
              null,
              2,
            )
          : JSON.stringify(parsed, null, 2);
        setHttpJson(json);
        if (!apiProduct.mcpConfig) {
          setLocalJson('');
          setSseJson('');
        }
      } else {
        setLocalJson(JSON.stringify(parsed, null, 2));
        if (!apiProduct.mcpConfig) {
          setSseJson('');
          setHttpJson('');
        }
      }
    } catch {
      setLocalJson(connCfg);
      if (!apiProduct.mcpConfig) {
        setSseJson('');
        setHttpJson('');
      }
    }
  }, [mcpMetaList, apiProduct]);

  return { domainOptions, hotHttpJson, hotSseJson, httpJson, localJson, sseJson };
}

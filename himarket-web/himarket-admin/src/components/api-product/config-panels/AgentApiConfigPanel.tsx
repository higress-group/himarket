import { CopyOutlined } from '@ant-design/icons';
import { Button, Card, Collapse, Select } from 'antd';
import { message } from 'antd';

import { copyToClipboard, formatDomainWithPort } from '@/lib/utils';
import type { ApiProductAgentConfig } from '@/types/api-product';

interface AgentApiConfigPanelProps {
  agentConfig: ApiProductAgentConfig;
  selectedDomainIndex: number;
  onDomainChange: (index: number) => void;
}

export function AgentApiConfigPanel({
  agentConfig,
  onDomainChange,
  selectedDomainIndex,
}: AgentApiConfigPanelProps) {
  const agentAPIConfig = agentConfig.agentAPIConfig;
  const routes = agentAPIConfig.routes || [];
  const protocols = agentAPIConfig.agentProtocols || [];
  const isA2A = protocols.includes('a2a');
  const agentCard = agentAPIConfig.agentCard;

  const getMatchTypePrefix = (matchType: string) => {
    switch (matchType) {
      case 'Exact':
        return '等于';
      case 'Prefix':
        return '前缀是';
      case 'Regex':
        return '正则是';
      default:
        return '等于';
    }
  };

  const getAllUniqueDomains = () => {
    const domainsMap = new Map<string, { domain: string; port?: number; protocol: string }>();
    routes.forEach((route) => {
      if (route.domains && route.domains.length > 0) {
        route.domains.forEach((domain) => {
          const key = `${domain.protocol}://${domain.domain}${domain.port ? `:${domain.port}` : ''}`;
          domainsMap.set(key, domain);
        });
      }
    });
    return Array.from(domainsMap.values());
  };

  const allUniqueDomains = getAllUniqueDomains();

  const agentDomainOptions = allUniqueDomains.map((domain, index) => {
    const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
    return {
      label: `${domain.protocol.toLowerCase()}://${formattedDomain}`,
      value: index,
    };
  });

  interface RouteItem {
    description?: string;
    domains?: Array<{ domain: string; port?: number; protocol: string }>;
    match?: {
      headers?: Array<{ name?: string; type?: string; value?: string }> | null;
      methods?: string[] | null;
      path?: { type?: string; value?: string };
      queryParams?: Array<{ name?: string; type?: string; value?: string }> | null;
    };
  }

  const getRouteDisplayText = (route: RouteItem, domainIndex: number = 0) => {
    if (!route.match) return 'Unknown Route';
    const path = route.match.path?.value || '/';
    const pathType = route.match.path?.type;
    let domainInfo = '';
    if (allUniqueDomains.length > 0 && allUniqueDomains.length > domainIndex) {
      const selectedDomain = allUniqueDomains[domainIndex] as {
        domain: string;
        port?: number;
        protocol: string;
      };
      if (selectedDomain) {
        const formattedDomain = formatDomainWithPort(
          selectedDomain.domain,
          selectedDomain.port,
          selectedDomain.protocol,
        );
        domainInfo = `${selectedDomain.protocol.toLowerCase()}://${formattedDomain}`;
      }
    } else if (route.domains && route.domains.length > 0) {
      const domain = route.domains[0] as { domain: string; port?: number; protocol: string };
      const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
      domainInfo = `${domain.protocol.toLowerCase()}://${formattedDomain}`;
    }
    let pathWithSuffix = path;
    if (pathType === 'Prefix') {
      pathWithSuffix = `${path}*`;
    } else if (pathType === 'Regex') {
      pathWithSuffix = `${path}~`;
    }
    let routeText = `${domainInfo}${pathWithSuffix}`;
    if (route.description && route.description.trim()) {
      routeText += ` - ${route.description.trim()}`;
    }
    return routeText;
  };

  const getFullUrl = (route: RouteItem, domainIndex: number = 0) => {
    if (allUniqueDomains.length > 0 && allUniqueDomains.length > domainIndex) {
      const selectedDomain = allUniqueDomains[domainIndex] as {
        domain: string;
        port?: number;
        protocol: string;
      };
      if (!selectedDomain) return '';
      const formattedDomain = formatDomainWithPort(
        selectedDomain.domain,
        selectedDomain.port,
        selectedDomain.protocol,
      );
      const path = route.match?.path?.value || '/';
      return `${selectedDomain.protocol.toLowerCase()}://${formattedDomain}${path}`;
    } else if (route.domains && route.domains.length > 0) {
      const domain = route.domains[0] as { domain: string; port?: number; protocol: string };
      const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
      const path = route.match?.path?.value || '/';
      return `${domain.protocol.toLowerCase()}://${formattedDomain}${path}`;
    }
    return '';
  };

  return (
    <Card title="配置详情">
      <div className="space-y-6">
        {protocols.length > 0 && (
          <div>
            <div className="text-sm text-gray-600">支持协议</div>
            <div className="font-medium">{protocols.join(', ')}</div>
          </div>
        )}

        {isA2A && agentCard && (
          <div className="border-t pt-4">
            <h3 className="text-lg font-semibold mb-4">Agent Card 信息</h3>
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <div className="text-sm text-gray-600">名称</div>
                  <div className="font-medium">{agentCard.name}</div>
                </div>
                <div>
                  <div className="text-sm text-gray-600">版本</div>
                  <div className="font-medium">{agentCard.version}</div>
                </div>
              </div>

              {agentCard.protocolVersion && (
                <div>
                  <div className="text-sm text-gray-600">协议版本</div>
                  <div className="font-mono text-sm">{agentCard.protocolVersion}</div>
                </div>
              )}

              {agentCard.description && (
                <div>
                  <div className="text-sm text-gray-600">描述</div>
                  <div>{agentCard.description}</div>
                </div>
              )}

              {agentCard.url && (
                <div>
                  <div className="text-sm text-gray-600">URL</div>
                  <div className="font-mono text-sm">{agentCard.url}</div>
                </div>
              )}

              {agentCard.preferredTransport && (
                <div>
                  <div className="text-sm text-gray-600">传输协议</div>
                  <div>{agentCard.preferredTransport}</div>
                </div>
              )}

              {agentCard.additionalInterfaces && agentCard.additionalInterfaces.length > 0 && (
                <div>
                  <div className="text-sm text-gray-600 mb-2">附加接口</div>
                  <div className="space-y-2">
                    {agentCard.additionalInterfaces.map(
                      (iface: Record<string, unknown>, idx: number) => (
                        <div className="border border-gray-200 rounded p-3 bg-gray-50" key={idx}>
                          <div className="flex items-center gap-2 mb-1">
                            <span className="text-xs bg-blue-100 text-blue-700 px-2 py-1 rounded font-medium">
                              {(iface.transport as string) || 'Unknown'}
                            </span>
                          </div>
                          <div className="font-mono text-sm text-gray-700 break-all">
                            {String(iface.url)}
                          </div>
                          {Object.keys(iface).filter((k) => k !== 'transport' && k !== 'url')
                            .length > 0 && (
                            <div className="mt-2 text-xs text-gray-500">
                              {Object.entries(iface)
                                .filter(([k]) => k !== 'transport' && k !== 'url')
                                .map(([k, v]) => (
                                  <div key={k}>
                                    <span className="font-medium">{k}:</span> {String(v)}
                                  </div>
                                ))}
                            </div>
                          )}
                        </div>
                      ),
                    )}
                  </div>
                </div>
              )}

              {agentCard.skills && agentCard.skills.length > 0 && (
                <div>
                  <div className="text-sm text-gray-600 mb-2">技能列表</div>
                  <div className="space-y-2">
                    {agentCard.skills.map((skill: Record<string, unknown>, idx: number) => (
                      <div className="border border-gray-200 rounded p-3" key={idx}>
                        <div className="font-medium">{(skill.name as string) || ''}</div>
                        {(skill.description as string) && (
                          <div className="text-sm text-gray-600 mt-1">
                            {skill.description as string}
                          </div>
                        )}
                        {(skill.tags as string[]) && (skill.tags as string[]).length > 0 && (
                          <div className="flex gap-2 mt-2">
                            {(skill.tags as string[]).map((tag: string, tagIdx: number) => (
                              <span
                                className="text-xs bg-blue-100 text-blue-700 px-2 py-1 rounded"
                                key={tagIdx}
                              >
                                {tag}
                              </span>
                            ))}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {agentCard.capabilities && (
                <div>
                  <div className="text-sm text-gray-600 mb-2">能力</div>
                  <pre className="bg-gray-50 p-3 rounded text-sm overflow-auto">
                    {JSON.stringify(agentCard.capabilities, null, 2)}
                  </pre>
                </div>
              )}
            </div>
          </div>
        )}

        {routes.length > 0 && (
          <div className={isA2A && agentCard ? 'border-t pt-4' : ''}>
            <div className="text-sm text-gray-600 mb-3">路由配置:</div>

            {agentDomainOptions.length > 1 && (
              <div className="mb-2">
                <div className="flex items-stretch border border-gray-200 rounded-md overflow-hidden">
                  <div className="bg-gray-50 px-3 py-2 text-xs text-gray-600 border-r border-gray-200 flex items-center whitespace-nowrap">
                    域名
                  </div>
                  <div className="flex-1">
                    <Select
                      bordered={false}
                      className="w-full"
                      onChange={onDomainChange}
                      placeholder="选择域名"
                      size="middle"
                      style={{
                        fontSize: '12px',
                        height: '100%',
                      }}
                      value={selectedDomainIndex}
                    >
                      {agentDomainOptions.map((option) => (
                        <Select.Option key={option.value} value={option.value}>
                          <span className="text-xs text-gray-900 font-mono">{option.label}</span>
                        </Select.Option>
                      ))}
                    </Select>
                  </div>
                </div>
              </div>
            )}

            <div className="border border-gray-200 rounded-lg overflow-hidden">
              <Collapse expandIconPosition="end" ghost>
                {routes.map((route, index) => (
                  <Collapse.Panel
                    header={
                      <div className="flex items-center justify-between py-2 px-4 hover:bg-gray-50">
                        <div className="flex-1">
                          <div className="font-mono text-sm font-medium text-blue-600">
                            {getRouteDisplayText(route, selectedDomainIndex)}
                          </div>
                        </div>
                        <Button
                          onClick={async (e) => {
                            e.stopPropagation();
                            const fullUrl = getFullUrl(route, selectedDomainIndex);
                            if (fullUrl) {
                              try {
                                await copyToClipboard(fullUrl);
                                message.success('链接已复制到剪贴板');
                              } catch (_error) {
                                message.error('复制失败');
                              }
                            }
                          }}
                          size="small"
                          type="text"
                        >
                          <CopyOutlined />
                        </Button>
                      </div>
                    }
                    key={index}
                    style={{
                      borderBottom: index < routes.length - 1 ? '1px solid #e5e7eb' : 'none',
                    }}
                  >
                    <div className="pl-4 space-y-3">
                      <div>
                        <div className="text-xs text-gray-500 mb-1">域名:</div>
                        {route.domains?.map(
                          (
                            domain: { domain: string; port?: number; protocol: string },
                            domainIndex: number,
                          ) => {
                            const formattedDomain = formatDomainWithPort(
                              domain.domain,
                              domain.port,
                              domain.protocol,
                            );
                            return (
                              <div className="text-sm" key={domainIndex}>
                                <span className="font-mono">
                                  {domain.protocol.toLowerCase()}://{formattedDomain}
                                </span>
                              </div>
                            );
                          },
                        )}
                      </div>

                      <div className="grid grid-cols-2 gap-4 text-sm">
                        <div>
                          <div className="text-xs text-gray-500">路径:</div>
                          <div className="font-mono">
                            {getMatchTypePrefix(route.match?.path?.type)} {route.match?.path?.value}
                          </div>
                        </div>
                        <div>
                          <div className="text-xs text-gray-500">方法:</div>
                          <div>{route.match?.methods ? route.match.methods.join(', ') : 'ANY'}</div>
                        </div>
                      </div>

                      {route.match?.headers && route.match.headers.length > 0 && (
                        <div>
                          <div className="text-xs text-gray-500 mb-1">请求头匹配:</div>
                          <div className="space-y-1">
                            {route.match.headers.map(
                              (
                                header: { name?: string; type?: string; value?: string },
                                headerIndex: number,
                              ) => (
                                <div className="text-sm font-mono" key={headerIndex}>
                                  {header.name} {getMatchTypePrefix(header.type || '')}{' '}
                                  {header.value}
                                </div>
                              ),
                            )}
                          </div>
                        </div>
                      )}

                      {route.match?.queryParams && route.match.queryParams.length > 0 && (
                        <div>
                          <div className="text-xs text-gray-500 mb-1">查询参数匹配:</div>
                          <div className="space-y-1">
                            {route.match.queryParams.map(
                              (
                                param: { name?: string; type?: string; value?: string },
                                paramIndex: number,
                              ) => (
                                <div className="text-sm font-mono" key={paramIndex}>
                                  {param.name} {getMatchTypePrefix(param.type || '')} {param.value}
                                </div>
                              ),
                            )}
                          </div>
                        </div>
                      )}

                      {route.description && (
                        <div>
                          <div className="text-xs text-gray-500">描述:</div>
                          <div className="text-sm">{route.description}</div>
                        </div>
                      )}
                    </div>
                  </Collapse.Panel>
                ))}
              </Collapse>
            </div>
          </div>
        )}
      </div>
    </Card>
  );
}

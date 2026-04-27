import { CopyOutlined } from '@ant-design/icons';
import { Button, Card, Collapse, Select } from 'antd';
import { message } from 'antd';

import { copyToClipboard, formatDomainWithPort } from '@/lib/utils';
import type { ApiProductModelConfig } from '@/types/api-product';

interface ModelApiConfigPanelProps {
  modelConfig: ApiProductModelConfig;
  selectedDomainIndex: number;
  onDomainChange: (index: number) => void;
}

export function ModelApiConfigPanel({
  modelConfig,
  onDomainChange,
  selectedDomainIndex,
}: ModelApiConfigPanelProps) {
  const modelAPIConfig = modelConfig.modelAPIConfig;
  const routes = modelAPIConfig.routes || [];
  const protocols = modelAPIConfig.aiProtocols || [];

  const getAllModelUniqueDomains = () => {
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

  const allModelUniqueDomains = getAllModelUniqueDomains();

  const modelDomainOptions = allModelUniqueDomains.map((domain, index) => {
    const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
    return {
      label: `${domain.protocol.toLowerCase()}://${formattedDomain}`,
      value: index,
    };
  });

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
    if (allModelUniqueDomains.length > 0 && allModelUniqueDomains.length > domainIndex) {
      const selectedDomain = allModelUniqueDomains[domainIndex] as {
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
      routeText += ` - ${route.description}`;
    }
    return routeText;
  };

  const getMethodsText = (route: RouteItem) => {
    const methods = route.match?.methods;
    if (!methods || methods.length === 0) {
      return 'ANY';
    }
    return methods.join(', ');
  };

  const getFullUrl = (route: RouteItem, domainIndex: number = 0) => {
    if (allModelUniqueDomains.length > 0 && allModelUniqueDomains.length > domainIndex) {
      const selectedDomain = allModelUniqueDomains[domainIndex] as {
        domain: string;
        port?: number;
        protocol: string;
      };
      if (!selectedDomain) return null;
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
    return null;
  };

  const getModelCategoryText = (category: string) => {
    switch (category) {
      case 'Text':
        return '文本生成';
      case 'Image':
        return '图片生成';
      case 'Video':
        return '视频生成';
      case 'Audio':
        return '语音合成';
      case 'Embedding':
        return '向量化（Embedding）';
      case 'Rerank':
        return '文本排序（Rerank）';
      case 'Others':
        return '其他';
      default:
        return category || '未知';
    }
  };

  return (
    <Card title="配置详情">
      <div className="space-y-4">
        {modelAPIConfig.modelCategory && (
          <div className="text-sm">
            <span className="text-gray-700">适用场景: </span>
            <span className="font-medium">
              {getModelCategoryText(modelAPIConfig.modelCategory)}
            </span>
          </div>
        )}
        <div className="text-sm">
          <span className="text-gray-700">协议: </span>
          <span className="font-medium">{protocols.join(', ')}</span>
        </div>
        {routes.length > 0 && (
          <div>
            <div className="text-sm text-gray-600 mb-3">路由配置:</div>
            {modelDomainOptions.length > 0 && (
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
                      {modelDomainOptions.map((option) => (
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
                      <div className="flex items-center justify-between py-3 px-4 hover:bg-gray-50">
                        <div className="flex-1">
                          <div className="font-mono text-sm font-medium text-blue-600 mb-1">
                            {getRouteDisplayText(route, selectedDomainIndex)}
                            {route.builtin && (
                              <span className="ml-2 px-2 py-0.5 text-xs bg-green-100 text-green-800 rounded-full">
                                默认
                              </span>
                            )}
                          </div>
                          <div className="text-xs text-gray-500">
                            方法:{' '}
                            <span className="font-medium text-gray-700">
                              {getMethodsText(route)}
                            </span>
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

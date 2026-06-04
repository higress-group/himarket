import { CopyOutlined } from '@ant-design/icons';
import { Button, Card, Collapse, Select } from 'antd';
import { message } from 'antd';

import { useLocale } from '@/contexts/LocaleContext';
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
  const { t } = useLocale();
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
  const selectedModelDomain = modelDomainOptions[selectedDomainIndex];

  const handleCopySelectedDomain = async () => {
    if (!selectedModelDomain?.label) return;

    try {
      await copyToClipboard(selectedModelDomain.label);
      message.success(t('common.domainCopied'));
    } catch {
      message.error(t('common.copyLinkFailed'));
    }
  };

  const getMatchTypePrefix = (matchType: string) => {
    switch (matchType) {
      case 'Exact':
        return t('product.config.matchEquals');
      case 'Prefix':
        return t('product.config.matchPrefix');
      case 'Regex':
        return t('product.config.matchRegex');
      default:
        return t('product.config.matchEquals');
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
        return t('product.config.category.text');
      case 'Image':
        return t('product.config.category.image');
      case 'Video':
        return t('product.config.category.video');
      case 'Audio':
        return t('product.config.category.audio');
      case 'Embedding':
        return t('product.config.category.embedding');
      case 'Rerank':
        return t('product.config.category.rerank');
      case 'Others':
        return t('product.config.category.others');
      default:
        return category || t('product.config.category.unknown');
    }
  };

  return (
    <Card title={t('product.linkApi.configDetail')}>
      <div className="space-y-4">
        {modelAPIConfig.modelCategory && (
          <div className="text-sm">
            <span className="text-gray-700">{t('product.config.modelCategory')}: </span>
            <span className="font-medium">
              {getModelCategoryText(modelAPIConfig.modelCategory)}
            </span>
          </div>
        )}
        <div className="text-sm">
          <span className="text-gray-700">{t('product.config.protocol')}: </span>
          <span className="font-medium">{protocols.join(', ')}</span>
        </div>
        {routes.length > 0 && (
          <div>
            <div className="text-sm text-gray-600 mb-3">{t('product.config.routeConfig')}:</div>
            {modelDomainOptions.length > 0 && (
              <div className="mb-2">
                <div className="flex items-stretch border border-gray-200 rounded-md overflow-hidden">
                  <div className="bg-gray-50 px-3 py-2 text-xs text-gray-600 border-r border-gray-200 flex items-center whitespace-nowrap">
                    {t('common.domain')}
                  </div>
                  <div className="flex-1">
                    <Select
                      bordered={false}
                      className="w-full"
                      labelRender={() => (
                        <div className="inline-flex max-w-full items-center gap-1.5">
                          <span className="min-w-0 truncate font-mono text-xs text-gray-900">
                            {selectedModelDomain?.label || t('common.selectDomain')}
                          </span>
                          <Button
                            aria-label={t('product.config.copyDomain')}
                            disabled={!selectedModelDomain?.label}
                            icon={<CopyOutlined />}
                            onClick={(event) => {
                              event.stopPropagation();
                              handleCopySelectedDomain();
                            }}
                            onMouseDown={(event) => event.stopPropagation()}
                            size="small"
                            title={t('product.config.copyDomain')}
                            type="text"
                          />
                        </div>
                      )}
                      onChange={onDomainChange}
                      optionLabelProp="label"
                      placeholder={t('common.selectDomain')}
                      size="middle"
                      style={{
                        fontSize: '12px',
                        height: '100%',
                      }}
                      value={selectedDomainIndex}
                    >
                      {modelDomainOptions.map((option) => (
                        <Select.Option key={option.value} label={option.label} value={option.value}>
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
                                {t('product.config.builtin')}
                              </span>
                            )}
                          </div>
                          <div className="text-xs text-gray-500">
                            {t('product.config.method')}:{' '}
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
                                message.success(t('product.config.copyUrlSuccess'));
                              } catch (_error) {
                                message.error(t('common.copyLinkFailed'));
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
                      <div className="grid grid-cols-2 gap-4 text-sm">
                        <div>
                          <div className="text-xs text-gray-500">{t('product.config.path')}:</div>
                          <div className="font-mono">
                            {getMatchTypePrefix(route.match?.path?.type)} {route.match?.path?.value}
                          </div>
                        </div>
                        <div>
                          <div className="text-xs text-gray-500">{t('product.config.method')}:</div>
                          <div>{route.match?.methods ? route.match.methods.join(', ') : 'ANY'}</div>
                        </div>
                      </div>
                      {route.match?.headers && route.match.headers.length > 0 && (
                        <div>
                          <div className="text-xs text-gray-500 mb-1">
                            {t('product.config.headerMatch')}:
                          </div>
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
                          <div className="text-xs text-gray-500 mb-1">
                            {t('product.config.queryMatch')}:
                          </div>
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

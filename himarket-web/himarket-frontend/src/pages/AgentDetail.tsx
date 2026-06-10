import {
  CheckCircleFilled,
  CodeOutlined,
  CopyOutlined,
  FileTextOutlined,
  InboxOutlined,
  RobotOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import { Button, message, Tabs, Collapse, Select } from 'antd';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router-dom';

import MarkdownRender from '../components/MarkdownRender';
import { ProductDetailLayout } from '../components/ProductDetailLayout';
import APIs, { type IProductDetail } from '../lib/apis';
import { copyToClipboard, formatDomainWithPort } from '../lib/utils';
import { ProductType } from '../types';

import type { IAgentConfig } from '../lib/apis/typing';

const { Panel } = Collapse;

type AgentRoute = NonNullable<IAgentConfig['agentAPIConfig']['routes']>[number];

function AgentDetail() {
  const { agentProductId } = useParams();
  const { t } = useTranslation('agentDetail');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [data, setData] = useState<IProductDetail>();
  const [agentConfig, setAgentConfig] = useState<IAgentConfig>();
  const [selectedAgentDomainIndex, setSelectedAgentDomainIndex] = useState<number>(0);
  const [rightPanelTab, setRightPanelTab] = useState<'usage' | 'request'>('usage');

  useEffect(() => {
    const fetchDetail = async () => {
      if (!agentProductId) {
        return;
      }
      setLoading(true);
      setError('');
      try {
        const response = await APIs.getProduct({ id: agentProductId });
        if (response.code === 'SUCCESS' && response.data) {
          setData(response.data);

          // 处理Agent配置
          if (response.data.type === ProductType.AGENT_API) {
            const agentProduct = response.data;

            if (agentProduct.agentConfig) {
              setAgentConfig(agentProduct.agentConfig);
            }
          }
        } else {
          setError(response.message || t('error.dataLoadFailed'));
        }
      } catch (error) {
        console.error('Failed to request API:', error);
        setError(t('error.loadFailed'));
      } finally {
        setLoading(false);
      }
    };
    fetchDetail();
  }, [agentProductId, t]);

  // 当产品切换时重置域名选择索引
  useEffect(() => {
    setSelectedAgentDomainIndex(0);
  }, [data?.productId]);

  // 获取所有唯一域名
  const getAllUniqueDomains = () => {
    if (!agentConfig?.agentAPIConfig?.routes) return [];

    const domainsMap = new Map<string, { domain: string; port?: number; protocol: string }>();

    agentConfig.agentAPIConfig.routes.forEach((route) => {
      if (route.domains && route.domains.length > 0) {
        route.domains.forEach((domain) => {
          const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
          const key = `${domain.protocol}://${formattedDomain}`;
          domainsMap.set(key, domain);
        });
      }
    });

    return Array.from(domainsMap.values());
  };

  const allUniqueDomains = getAllUniqueDomains();

  // 生成域名选择器选项
  const agentDomainOptions = allUniqueDomains.map((domain, index) => {
    const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
    return {
      label: `${domain.protocol.toLowerCase()}://${formattedDomain}`,
      value: index,
    };
  });
  const selectedAgentDomain = agentDomainOptions[selectedAgentDomainIndex];

  const handleCopySelectedAgentDomain = async () => {
    if (!selectedAgentDomain?.label) return;

    try {
      await copyToClipboard(selectedAgentDomain.label);
      message.success(t('message.domainCopied'), 1);
    } catch {
      message.error(t('message.copyFailed'));
    }
  };

  // Helper functions for route display - moved to component level
  const getMatchTypePrefix = (matchType: string) => {
    switch (matchType) {
      case 'Exact':
        return t('matchType.exact');
      case 'Prefix':
        return t('matchType.prefix');
      case 'Regex':
        return t('matchType.regex');
      default:
        return t('matchType.exact');
    }
  };

  const getRouteEndpoint = (route?: AgentRoute, domainIndex: number = 0) => {
    if (!route?.match) return '';

    const path = route.match.path?.value || '/';
    if (allUniqueDomains.length > 0 && allUniqueDomains.length > domainIndex) {
      const selectedDomain = allUniqueDomains[domainIndex];
      if (selectedDomain) {
        const formattedDomain = formatDomainWithPort(
          selectedDomain.domain,
          selectedDomain.port,
          selectedDomain.protocol,
        );
        return `${selectedDomain.protocol.toLowerCase()}://${formattedDomain}${path}`;
      }
    } else if (route.domains && route.domains.length > 0) {
      const domain = route.domains[0];
      if (domain) {
        const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
        return `${domain.protocol.toLowerCase()}://${formattedDomain}${path}`;
      }
    }

    return path;
  };

  const getRouteDisplayText = (route: AgentRoute, domainIndex: number = 0) => {
    if (!route.match) return t('route.unknown');

    const path = route.match.path?.value || '/';
    const pathType = route.match.path?.type;
    const routeEndpoint = getRouteEndpoint(route, domainIndex);

    // 构建基本路由信息（匹配符号直接加到path后面）
    let pathWithSuffix = path;
    if (pathType === 'Prefix') {
      pathWithSuffix = `${path}*`;
    } else if (pathType === 'Regex') {
      pathWithSuffix = `${path}~`;
    }

    let routeText = routeEndpoint;
    if (pathWithSuffix !== path) {
      routeText = routeEndpoint.endsWith(path)
        ? `${routeEndpoint.slice(0, -path.length)}${pathWithSuffix}`
        : `${routeEndpoint}${pathWithSuffix}`;
    }

    // 添加描述信息
    if (route.description && route.description.trim()) {
      routeText += ` - ${route.description.trim()}`;
    }

    return routeText;
  };

  const getMethodsText = (route: AgentRoute) => {
    if (!route.match?.methods || route.match.methods.length === 0) {
      return 'ANY';
    }
    return route.match.methods.join(', ');
  };

  const agentProtocols = agentConfig?.agentAPIConfig?.agentProtocols ?? [];
  const agentRoutes = agentConfig?.agentAPIConfig?.routes ?? [];
  const agentCard = agentConfig?.agentAPIConfig?.agentCard;
  const primaryAgentRoute = agentRoutes[0];
  const primaryEndpoint =
    getRouteEndpoint(primaryAgentRoute, selectedAgentDomainIndex) || agentCard?.url;

  const generateRequestExample = () => {
    if (!primaryEndpoint) {
      return '';
    }

    const firstMethod = primaryAgentRoute?.match?.methods?.find((method) => {
      const normalizedMethod = method.trim().toUpperCase();
      return normalizedMethod && normalizedMethod !== 'ANY' && normalizedMethod !== '*';
    });
    const method = firstMethod?.toUpperCase() || 'POST';

    return `curl -X ${method} \\
  '${primaryEndpoint}' \\
  --header 'Content-Type: application/json' \\
  --data '{
    "message": "Hello"
  }'`;
  };

  const leftContent = data ? (
    <div className="overflow-hidden rounded-[14px] border border-[#DDE5F0] bg-white/90 shadow-[0_18px_50px_rgba(15,23,42,0.05)] backdrop-blur-sm">
      <Tabs
        className="[&_.ant-tabs-content-holder]:px-5 [&_.ant-tabs-content-holder]:pb-5 [&_.ant-tabs-nav]:mb-5 [&_.ant-tabs-nav]:px-5 [&_.ant-tabs-tab]:py-4"
        defaultActiveKey="overview"
        items={[
          {
            children: data?.document ? (
              <div className="scrollbar-thin-soft max-h-[720px] min-h-[420px] overflow-y-auto pr-2">
                <MarkdownRender content={data.document} />
              </div>
            ) : (
              <div className="flex min-h-[420px] flex-col items-center justify-center rounded-[12px] border border-dashed border-[#DDE5F0] bg-[#FBFCFE] py-16">
                <div className="mb-2 flex h-10 w-10 items-center justify-center rounded-full bg-gray-100">
                  <InboxOutlined className="text-base text-gray-400" />
                </div>
                <div className="text-sm text-gray-500">{t('empty.overview')}</div>
              </div>
            ),
            key: 'overview',
            label: (
              <span className="flex items-center gap-1.5 font-semibold">
                <FileTextOutlined className="text-sm" />
                {t('tabs.overview')}
              </span>
            ),
          },
          {
            children: agentConfig?.agentAPIConfig ? (
              <div className="space-y-6">
                {/* 基本信息 */}
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <div className="rounded-[12px] border border-[#E8EDF5] bg-[#FBFCFE] p-4">
                    <div className="mb-1 text-sm text-gray-500">{t('field.protocol')}</div>
                    <div className="text-sm font-medium text-gray-900">
                      {agentProtocols.length > 0
                        ? agentProtocols.join(', ')
                        : t('field.noProtocol')}
                    </div>
                  </div>
                </div>

                {agentProtocols.includes('a2a') && agentCard && (
                  <div className="overflow-hidden rounded-[12px] border border-[#DDE5F0] bg-white">
                    <div className="flex items-center justify-between border-b border-[#E8EDF5] px-4 py-3">
                      <h3 className="text-sm font-semibold text-gray-950">
                        {t('agentCard.title')}
                      </h3>
                      {agentCard.protocolVersion && (
                        <span className="rounded-full bg-[#F3F6FC] px-2.5 py-1 font-mono text-xs font-medium text-gray-500">
                          {agentCard.protocolVersion}
                        </span>
                      )}
                    </div>
                    <div className="p-4">
                      <div className="space-y-4">
                        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                          <div className="rounded-[10px] border border-[#E8EDF5] bg-[#FBFCFE] p-3">
                            <div className="mb-1 text-xs text-gray-500">{t('agentCard.name')}</div>
                            <div className="font-medium text-gray-900">{agentCard.name}</div>
                          </div>
                          <div className="rounded-[10px] border border-[#E8EDF5] bg-[#FBFCFE] p-3">
                            <div className="mb-1 text-xs text-gray-500">
                              {t('agentCard.version')}
                            </div>
                            <div className="font-medium text-gray-900">{agentCard.version}</div>
                          </div>
                        </div>

                        {agentCard.protocolVersion && (
                          <div className="rounded-[10px] border border-[#E8EDF5] bg-[#FBFCFE] p-3">
                            <div className="mb-1 text-xs text-gray-500">
                              {t('agentCard.protocolVersion')}
                            </div>
                            <div className="font-mono text-sm text-gray-900">
                              {agentCard.protocolVersion}
                            </div>
                          </div>
                        )}

                        {agentCard.description && (
                          <div className="rounded-[10px] border border-[#E8EDF5] bg-[#FBFCFE] p-3">
                            <div className="mb-1 text-xs text-gray-500">
                              {t('agentCard.description')}
                            </div>
                            <div className="text-sm leading-6 text-gray-700">
                              {agentCard.description}
                            </div>
                          </div>
                        )}

                        {agentCard.url && (
                          <div className="rounded-[10px] border border-[#E8EDF5] bg-[#FBFCFE] p-3">
                            <div className="mb-1 text-xs text-gray-500">URL</div>
                            <div className="break-all font-mono text-sm text-gray-900">
                              {agentCard.url}
                            </div>
                          </div>
                        )}

                        {agentCard.preferredTransport && (
                          <div className="rounded-[10px] border border-[#E8EDF5] bg-[#FBFCFE] p-3">
                            <div className="mb-1 text-xs text-gray-500">
                              {t('agentCard.transport')}
                            </div>
                            <div className="text-sm font-medium text-gray-900">
                              {agentCard.preferredTransport}
                            </div>
                          </div>
                        )}

                        {/* Additional Interfaces */}
                        {agentCard.additionalInterfaces &&
                          agentCard.additionalInterfaces.length > 0 && (
                            <div>
                              <div className="mb-2 text-sm font-medium text-gray-700">
                                {t('agentCard.additionalInterfaces')}
                              </div>
                              <div className="space-y-2">
                                {agentCard.additionalInterfaces.map(
                                  (
                                    iface: {
                                      transport?: string;
                                      url: string;
                                      [key: string]: unknown;
                                    },
                                    idx: number,
                                  ) => (
                                    <div
                                      className="rounded-[10px] border border-[#E8EDF5] bg-[#FBFCFE] p-3"
                                      key={idx}
                                    >
                                      <div className="mb-1 flex items-center gap-2">
                                        <span className="rounded-full bg-colorPrimaryBg px-2 py-1 text-xs font-medium text-colorPrimary">
                                          {iface.transport || t('agentCard.unknown')}
                                        </span>
                                      </div>
                                      <div className="break-all font-mono text-sm text-gray-700">
                                        {iface.url}
                                      </div>
                                      {/* 显示其他附加字段 */}
                                      {Object.keys(iface).filter(
                                        (k) => k !== 'transport' && k !== 'url',
                                      ).length > 0 && (
                                        <div className="mt-2 text-xs text-gray-500">
                                          {Object.entries(iface)
                                            .filter(([k]) => k !== 'transport' && k !== 'url')
                                            .map(([k, v]) => (
                                              <div key={k}>
                                                <span className="font-medium">{k}:</span>{' '}
                                                {String(v)}
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

                        {/* Skills */}
                        {agentCard.skills && agentCard.skills.length > 0 && (
                          <div>
                            <div className="mb-2 text-sm font-medium text-gray-700">
                              {t('agentCard.skills')}
                            </div>
                            <div className="space-y-2">
                              {agentCard.skills.map(
                                (
                                  skill: {
                                    id: string;
                                    name: string;
                                    description?: string;
                                    tags?: string[];
                                  },
                                  idx: number,
                                ) => (
                                  <div
                                    className="rounded-[10px] border border-[#E8EDF5] bg-white p-3"
                                    key={idx}
                                  >
                                    <div className="font-medium text-gray-900">{skill.name}</div>
                                    {skill.description && (
                                      <div className="mt-1 text-sm leading-6 text-gray-600">
                                        {skill.description}
                                      </div>
                                    )}
                                    {skill.tags && skill.tags.length > 0 && (
                                      <div className="mt-2 flex flex-wrap gap-2">
                                        {skill.tags.map((tag: string, tagIdx: number) => (
                                          <span
                                            className="rounded-full bg-[#F3F6FC] px-2 py-1 text-xs font-medium text-gray-600"
                                            key={tagIdx}
                                          >
                                            {tag}
                                          </span>
                                        ))}
                                      </div>
                                    )}
                                  </div>
                                ),
                              )}
                            </div>
                          </div>
                        )}

                        {/* Capabilities */}
                        {agentCard.capabilities && (
                          <div>
                            <div className="mb-2 text-sm font-medium text-gray-700">
                              {t('agentCard.capabilities')}
                            </div>
                            <pre className="overflow-auto rounded-[10px] border border-[#E8EDF5] bg-[#FBFCFE] p-3 text-sm text-gray-900">
                              {JSON.stringify(agentCard.capabilities, null, 2)}
                            </pre>
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                )}

                {agentRoutes.length > 0 && (
                  <div>
                    <div className="mb-4 text-sm font-semibold text-gray-900">
                      {t('route.title')}
                    </div>

                    {agentDomainOptions.length > 0 && (
                      <div className="mb-4">
                        <div className="flex overflow-hidden rounded-[10px] border border-[#DDE5F0] bg-white">
                          <span className="flex flex-shrink-0 items-center whitespace-nowrap border-r border-[#E8EDF5] bg-[#FBFCFE] px-3 py-2 text-xs font-medium text-gray-600">
                            {t('route.domain')}:
                          </span>
                          <div className="flex-1">
                            <Select
                              className="w-full"
                              labelRender={() => (
                                <div className="inline-flex max-w-full items-center gap-1.5">
                                  <span className="min-w-0 truncate font-mono text-xs text-gray-900">
                                    {selectedAgentDomain?.label || t('route.selectDomain')}
                                  </span>
                                  <Button
                                    aria-label={t('route.copyDomain')}
                                    disabled={!selectedAgentDomain?.label}
                                    icon={<CopyOutlined />}
                                    onClick={(event) => {
                                      event.stopPropagation();
                                      handleCopySelectedAgentDomain();
                                    }}
                                    onMouseDown={(event) => event.stopPropagation()}
                                    size="small"
                                    title={t('route.copyDomain')}
                                    type="text"
                                  />
                                </div>
                              )}
                              onChange={setSelectedAgentDomainIndex}
                              optionLabelProp="label"
                              placeholder={t('route.selectDomain')}
                              size="middle"
                              value={selectedAgentDomainIndex}
                              variant="borderless"
                            >
                              {agentDomainOptions.map((option) => (
                                <Select.Option
                                  key={option.value}
                                  label={option.label}
                                  value={option.value}
                                >
                                  <span className="font-mono text-xs text-gray-900">
                                    {option.label}
                                  </span>
                                </Select.Option>
                              ))}
                            </Select>
                          </div>
                        </div>
                      </div>
                    )}

                    <div className="overflow-hidden rounded-[12px] border border-[#DDE5F0] bg-white">
                      <Collapse expandIconPosition="end" ghost>
                        {agentRoutes.map((route, index) => (
                          <Panel
                            className={
                              index < agentRoutes.length - 1 ? 'border-b border-gray-100' : ''
                            }
                            header={
                              <div className="flex items-center justify-between py-2">
                                <div className="flex-1">
                                  <div className="mb-1 font-mono text-sm font-medium text-blue-600">
                                    {getRouteDisplayText(route, selectedAgentDomainIndex)}
                                    {route.builtin && (
                                      <span className="ml-2 rounded-full bg-green-100 px-2 py-0.5 text-xs text-green-800">
                                        {t('route.default')}
                                      </span>
                                    )}
                                  </div>
                                  <div className="text-xs text-gray-500">
                                    {t('route.method')}:{' '}
                                    <span className="font-medium text-gray-700">
                                      {getMethodsText(route)}
                                    </span>
                                  </div>
                                </div>
                                <Button
                                  icon={<CopyOutlined />}
                                  onClick={async (e) => {
                                    e.stopPropagation();
                                    if (
                                      allUniqueDomains.length > 0 &&
                                      allUniqueDomains.length > selectedAgentDomainIndex
                                    ) {
                                      const selectedDomain =
                                        allUniqueDomains[selectedAgentDomainIndex];
                                      if (selectedDomain) {
                                        const fullUrl = getRouteEndpoint(
                                          route,
                                          selectedAgentDomainIndex,
                                        );
                                        copyToClipboard(fullUrl).then(() => {
                                          message.success(t('message.linkCopied'));
                                        });
                                      }
                                    } else if (route.domains && route.domains.length > 0) {
                                      const domain = route.domains[0];
                                      if (domain) {
                                        const path = route.match?.path?.value || '/';
                                        const formattedDomain = formatDomainWithPort(
                                          domain.domain,
                                          domain.port,
                                          domain.protocol,
                                        );
                                        const fullUrl = `${domain.protocol.toLowerCase()}://${formattedDomain}${path}`;
                                        copyToClipboard(fullUrl).then(() => {
                                          message.success(t('message.linkCopied'));
                                        });
                                      }
                                    }
                                  }}
                                  size="small"
                                  type="text"
                                />
                              </div>
                            }
                            key={index}
                          >
                            <div className="space-y-4 pb-4 pl-4">
                              <div className="grid grid-cols-2 gap-4">
                                <div>
                                  <div className="mb-1 text-xs text-gray-500">
                                    {t('route.path')}:
                                  </div>
                                  <div className="rounded-lg bg-gray-50 px-3 py-2 font-mono text-sm">
                                    {getMatchTypePrefix(route.match?.path?.type)}{' '}
                                    {route.match?.path?.value}
                                  </div>
                                </div>
                                <div>
                                  <div className="mb-1 text-xs text-gray-500">
                                    {t('route.method')}:
                                  </div>
                                  <div className="rounded-lg bg-gray-50 px-3 py-2 font-mono text-sm">
                                    {getMethodsText(route)}
                                  </div>
                                </div>
                              </div>

                              {route.match?.headers && route.match.headers.length > 0 && (
                                <div>
                                  <div className="mb-2 text-xs text-gray-500">
                                    {t('route.headerMatch')}:
                                  </div>
                                  <div className="space-y-1">
                                    {route.match.headers.map((header, headerIndex: number) => (
                                      <div
                                        className="rounded-lg bg-gray-50 px-3 py-2 font-mono text-sm"
                                        key={headerIndex}
                                      >
                                        {header.name} {getMatchTypePrefix(header.type)}{' '}
                                        {header.value}
                                      </div>
                                    ))}
                                  </div>
                                </div>
                              )}

                              {route.match?.queryParams && route.match.queryParams.length > 0 && (
                                <div>
                                  <div className="mb-2 text-xs text-gray-500">
                                    {t('route.queryParamMatch')}:
                                  </div>
                                  <div className="space-y-1">
                                    {route.match.queryParams.map((param, paramIndex: number) => (
                                      <div
                                        className="rounded-lg bg-gray-50 px-3 py-2 font-mono text-sm"
                                        key={paramIndex}
                                      >
                                        {param.name} {getMatchTypePrefix(param.type)} {param.value}
                                      </div>
                                    ))}
                                  </div>
                                </div>
                              )}
                            </div>
                          </Panel>
                        ))}
                      </Collapse>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <div className="flex min-h-[420px] flex-col items-center justify-center rounded-[12px] border border-dashed border-[#DDE5F0] bg-[#FBFCFE] py-16">
                <div className="mb-2 flex h-10 w-10 items-center justify-center rounded-full bg-gray-100">
                  <InboxOutlined className="text-base text-gray-400" />
                </div>
                <div className="text-sm text-gray-500">{t('empty.configuration')}</div>
              </div>
            ),
            key: 'configuration',
            label: (
              <span className="flex items-center gap-1.5 font-semibold">
                <SettingOutlined className="text-sm" />
                {agentRoutes.length > 0
                  ? t('tabs.configurationWithCount', {
                      count: agentRoutes.length,
                    })
                  : t('tabs.configuration')}
              </span>
            ),
          },
        ]}
        size="large"
      />
    </div>
  ) : null;

  const agentFeatureLabels = [
    t('usage.features.planning'),
    t('usage.features.execution'),
    t('usage.features.toolCall'),
    t('usage.features.workflow'),
  ];
  const requestExample = generateRequestExample();

  const usagePanel = (
    <div>
      <div className="rounded-[12px] border border-[#E8EDF5] bg-[#FBFCFE] p-4">
        <div className="flex items-start gap-3">
          <div className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-[12px] bg-colorPrimaryBg text-colorPrimary">
            <RobotOutlined className="text-base" />
          </div>
          <div className="min-w-0">
            <div className="text-sm font-semibold text-gray-950">{t('usage.title')}</div>
            <p className="mt-1 text-sm leading-6 text-gray-600">{t('usage.description')}</p>
          </div>
        </div>

        <div className="mt-4 grid grid-cols-2 gap-x-4 gap-y-2 border-t border-[#E8EDF5] pt-3">
          {agentFeatureLabels.map((feature) => (
            <div
              className="flex min-w-0 items-center gap-2 text-xs font-medium text-gray-600"
              key={feature}
            >
              <span className="flex h-4 w-4 flex-shrink-0 items-center justify-center rounded-full bg-colorPrimaryBg text-colorPrimary shadow-[0_0_0_3px_rgba(99,102,241,0.08)]">
                <CheckCircleFilled className="text-[9px]" />
              </span>
              <span className="min-w-0 truncate">{feature}</span>
            </div>
          ))}
        </div>
      </div>

      <Button
        block
        className="mt-4 !h-10 !rounded-[10px] !border-none !bg-gray-100 !font-semibold !text-gray-500"
        disabled
        size="large"
      >
        {t('usage.comingSoon')}
      </Button>
    </div>
  );

  const requestPanel = requestExample ? (
    <div className="relative overflow-hidden rounded-[12px] border border-[#172033] bg-[#111827]">
      <Button
        aria-label={t('usage.copyRequestExample')}
        className="absolute right-2 top-2 z-10 text-gray-400 hover:text-white"
        icon={<CopyOutlined />}
        onClick={() => {
          copyToClipboard(requestExample).then(() => {
            message.success(t('message.requestCopied'));
          });
        }}
        size="small"
        title={t('usage.copyRequestExample')}
        type="text"
      />
      <pre className="max-h-[260px] overflow-auto whitespace-pre p-4 pr-12 font-mono text-[12px] leading-5 text-gray-100">
        <code>{requestExample}</code>
      </pre>
    </div>
  ) : (
    <div className="rounded-[12px] border border-dashed border-[#DDE5F0] bg-[#FBFCFE] py-10 text-center text-sm text-gray-400">
      {t('usage.noRequestExample')}
    </div>
  );

  const rightContent = (
    <section className="rounded-[14px] border border-[#DDE5F0] bg-white/90 p-5 shadow-[0_18px_50px_rgba(15,23,42,0.05)] backdrop-blur-sm">
      <div className="mb-3 flex rounded-lg bg-gray-100 p-1">
        <button
          className={`flex flex-1 items-center justify-center gap-1.5 rounded-md py-2 text-xs transition-all ${
            rightPanelTab === 'usage'
              ? 'bg-white font-medium text-gray-800 shadow-sm'
              : 'text-gray-500 hover:text-gray-700'
          }`}
          onClick={() => setRightPanelTab('usage')}
          type="button"
        >
          <RobotOutlined className="text-[13px]" />
          {t('usage.tab')}
        </button>
        <button
          className={`flex flex-1 items-center justify-center gap-1.5 rounded-md py-2 text-xs transition-all ${
            rightPanelTab === 'request'
              ? 'bg-white font-medium text-gray-800 shadow-sm'
              : 'text-gray-500 hover:text-gray-700'
          }`}
          onClick={() => setRightPanelTab('request')}
          type="button"
        >
          <CodeOutlined className="text-[13px]" />
          {t('usage.requestTab')}
        </button>
      </div>
      {rightPanelTab === 'usage' ? usagePanel : requestPanel}
    </section>
  );

  return (
    <ProductDetailLayout
      error={error || (!data ? t('error.agentNotFound') : undefined)}
      headerProps={
        data
          ? {
              agentConfig: agentConfig,
              description: data.description,
              icon: data.icon,
              name: data.name,
              productType: 'AGENT_API',
              subscribable: data.subscribable,
              updatedAt: data.updatedAt,
            }
          : undefined
      }
      leftContent={leftContent}
      loading={loading}
      rightContent={rightContent}
    />
  );
}

export default AgentDetail;

import {
  CheckCircleFilled,
  CodeOutlined,
  CopyOutlined,
  FileTextOutlined,
  InboxOutlined,
  MessageOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import { Button, message, Tabs, Collapse, Select } from 'antd';
import { useEffect, useRef, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams, useNavigate } from 'react-router-dom';

import { LoginPrompt } from '../components/LoginPrompt';
import MarkdownRender from '../components/MarkdownRender';
import { ProductDetailLayout } from '../components/ProductDetailLayout';
import { useAuth } from '../hooks/useAuth';
import APIs from '../lib/apis';
import { resolveEndpointPath } from '../lib/modelEndpoint';
import { copyToClipboard, formatDomainWithPort } from '../lib/utils';
import { ProductType } from '../types';

import type { ProductHeaderHandle } from '../components/ProductHeader';
import type { IProductDetail } from '../lib/apis';
import type { IModelConfig, IRoute } from '../lib/apis/typing';

const { Panel } = Collapse;

function ModelDetail() {
  const { modelProductId } = useParams();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [data, setData] = useState<IProductDetail>();
  const [modelConfig, setModelConfig] = useState<IModelConfig>();
  const [selectedModelDomainIndex, setSelectedModelDomainIndex] = useState<number>(0);
  const [hasSubscription, setHasSubscription] = useState(false);
  const [rightPanelTab, setRightPanelTab] = useState<'usage' | 'curl'>('usage');
  const headerRef = useRef<ProductHeaderHandle>(null);
  const { isLoggedIn } = useAuth();
  const { t } = useTranslation('modelDetail');
  const { t: tLoginPrompt } = useTranslation('loginPrompt');
  const [loginPromptOpen, setLoginPromptOpen] = useState(false);

  const handleSubscriptionStatusChange = useCallback((subscribed: boolean) => {
    setHasSubscription(subscribed);
  }, []);

  useEffect(() => {
    const fetchDetail = async () => {
      if (!modelProductId) {
        return;
      }
      setLoading(true);
      setError('');
      try {
        const response = await APIs.getProduct({ id: modelProductId });
        if (response.code === 'SUCCESS' && response.data) {
          setData(response.data);

          if (response.data.type === ProductType.MODEL_API) {
            const modelProduct = response.data;

            if (modelProduct.modelConfig) {
              setModelConfig(modelProduct.modelConfig);
            }
          }
        } else {
          setError(response.message || t('errors.dataLoadFailed'));
        }
      } catch (error) {
        console.error('Failed to fetch model detail:', error);
        setError(t('errors.loadFailed'));
      } finally {
        setLoading(false);
      }
    };
    fetchDetail();
  }, [modelProductId, t]);

  useEffect(() => {
    setSelectedModelDomainIndex(0);
  }, [data?.productId]);

  const getAllUniqueDomains = () => {
    if (!modelConfig?.modelAPIConfig?.routes) return [];

    const domainsMap = new Map<string, { domain: string; port?: number; protocol: string }>();

    modelConfig.modelAPIConfig.routes.forEach((route) => {
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

  const modelDomainOptions = allUniqueDomains.map((domain, index) => {
    const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
    return {
      label: `${domain.protocol.toLowerCase()}://${formattedDomain}`,
      value: index,
    };
  });
  const selectedModelDomain = modelDomainOptions[selectedModelDomainIndex];

  const handleCopySelectedModelDomain = async () => {
    if (!selectedModelDomain?.label) return;

    try {
      await copyToClipboard(selectedModelDomain.label);
      message.success(t('messages.domainCopied'), 1);
    } catch {
      message.error(t('messages.copyFailed'));
    }
  };

  const getMatchTypePrefix = (type: string) => {
    switch (type) {
      case 'Exact':
        return t('match.exact');
      case 'Prefix':
        return t('match.prefix');
      case 'Regex':
        return t('match.regex');
      default:
        return t('match.exact');
    }
  };

  const getRouteDisplayText = (route: IRoute, domainIndex: number = 0) => {
    if (!route.match) return t('configuration.unknownRoute');

    const path = route.match.path?.value || '/';
    const pathType = route.match.path?.type;

    let domainInfo = '';
    if (allUniqueDomains.length > 0 && allUniqueDomains.length > domainIndex) {
      const selectedDomain = allUniqueDomains[domainIndex];
      if (selectedDomain) {
        const formattedDomain = formatDomainWithPort(
          selectedDomain.domain,
          selectedDomain.port,
          selectedDomain.protocol,
        );
        domainInfo = `${selectedDomain.protocol.toLowerCase()}://${formattedDomain}`;
      }
    } else if (route.domains && route.domains.length > 0) {
      const domain = route.domains[0];
      if (domain) {
        const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
        domainInfo = `${domain.protocol.toLowerCase()}://${formattedDomain}`;
      }
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

  const getMethodsText = (route: IRoute) => {
    const methods = route.match?.methods;
    if (!methods || methods.length === 0) {
      return 'ANY';
    }
    return methods.join(', ');
  };

  const getModelCategoryText = (category: string) => {
    switch (category) {
      case 'Text':
        return t('modelCategory.text');
      case 'Image':
        return t('modelCategory.image');
      case 'Video':
        return t('modelCategory.video');
      case 'Audio':
        return t('modelCategory.audio');
      case 'Embedding':
        return t('modelCategory.embedding');
      case 'Rerank':
        return t('modelCategory.rerank');
      case 'Others':
        return t('modelCategory.others');
      default:
        return category || t('modelCategory.unknown');
    }
  };

  const getPrimaryModelEndpointUrl = () => {
    if (!modelConfig?.modelAPIConfig?.routes || !allUniqueDomains.length) {
      return '';
    }

    const firstRoute = modelConfig.modelAPIConfig.routes[0];
    if (!firstRoute?.match?.path?.value) {
      return '';
    }

    const selectedDomain = allUniqueDomains[selectedModelDomainIndex] || allUniqueDomains[0];
    if (!selectedDomain) {
      return '';
    }

    const formattedDomain = formatDomainWithPort(
      selectedDomain.domain,
      selectedDomain.port,
      selectedDomain.protocol,
    );
    const baseUrl = `${selectedDomain.protocol.toLowerCase()}://${formattedDomain}`;
    const resolvedPath = resolveEndpointPath(
      firstRoute.match.path.value,
      firstRoute.match.path.type,
      modelConfig.modelAPIConfig.aiProtocols,
    );

    return `${baseUrl}${resolvedPath}`;
  };

  const generateCurlExample = () => {
    const fullUrl = getPrimaryModelEndpointUrl();
    if (!fullUrl) return null;

    const modelName = data?.feature?.modelFeature?.model || '{{model_name}}';

    return `curl -X POST \\
  --location '${fullUrl}' \\
  --header 'Content-Type: application/json' \\
  --data '{
    "model": "${modelName}",
    "stream": true,
    "max_tokens": 1024,
    "top_p": 0.95,
    "temperature": 1,
    "messages": [
        {
            "role": "system",
            "content": "You are a helpful assistant."
        },
        {
            "role": "user",
            "content": "Who are you?"
        }
    ]
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
                <div className="text-sm text-gray-500">{t('overview.empty')}</div>
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
            children: modelConfig?.modelAPIConfig ? (
              <div className="space-y-6">
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  {modelConfig.modelAPIConfig.modelCategory && (
                    <div className="rounded-[12px] border border-[#E8EDF5] bg-[#FBFCFE] p-4">
                      <div className="text-sm text-gray-500 mb-1">
                        {t('configuration.applicationScenario')}
                      </div>
                      <div className="text-sm font-medium text-gray-900">
                        {getModelCategoryText(modelConfig.modelAPIConfig.modelCategory)}
                      </div>
                    </div>
                  )}
                  <div className="rounded-[12px] border border-[#E8EDF5] bg-[#FBFCFE] p-4">
                    <div className="text-sm text-gray-500 mb-1">{t('configuration.protocol')}</div>
                    <div className="text-sm font-medium text-gray-900">
                      {modelConfig.modelAPIConfig.aiProtocols?.join(', ') || 'DashScope'}
                    </div>
                  </div>
                </div>

                {modelConfig.modelAPIConfig.routes &&
                  modelConfig.modelAPIConfig.routes.length > 0 && (
                    <div>
                      <div className="text-sm font-semibold text-gray-900 mb-4">
                        {t('configuration.routeConfig')}
                      </div>

                      {modelDomainOptions.length > 0 && (
                        <div className="mb-4">
                          <div className="flex overflow-hidden rounded-[10px] border border-[#DDE5F0] bg-white">
                            <span className="flex flex-shrink-0 items-center whitespace-nowrap border-r border-[#E8EDF5] bg-[#FBFCFE] px-3 py-2 text-xs font-medium text-gray-600">
                              {t('configuration.domain')}:
                            </span>
                            <div className="flex-1">
                              <Select
                                className="w-full"
                                labelRender={() => (
                                  <div className="inline-flex max-w-full items-center gap-1.5">
                                    <span className="min-w-0 truncate font-mono text-xs text-gray-900">
                                      {selectedModelDomain?.label ||
                                        t('configuration.selectDomain')}
                                    </span>
                                    <Button
                                      aria-label={t('configuration.copyDomain')}
                                      disabled={!selectedModelDomain?.label}
                                      icon={<CopyOutlined />}
                                      onClick={(event) => {
                                        event.stopPropagation();
                                        handleCopySelectedModelDomain();
                                      }}
                                      onMouseDown={(event) => event.stopPropagation()}
                                      size="small"
                                      title={t('configuration.copyDomain')}
                                      type="text"
                                    />
                                  </div>
                                )}
                                onChange={setSelectedModelDomainIndex}
                                optionLabelProp="label"
                                placeholder={t('configuration.selectDomain')}
                                size="middle"
                                value={selectedModelDomainIndex}
                                variant="borderless"
                              >
                                {modelDomainOptions.map((option) => (
                                  <Select.Option
                                    key={option.value}
                                    label={option.label}
                                    value={option.value}
                                  >
                                    <span className="text-xs text-gray-900 font-mono">
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
                          {modelConfig.modelAPIConfig.routes.map((route, index) => (
                            <Panel
                              className={
                                index < modelConfig.modelAPIConfig.routes.length - 1
                                  ? 'border-b border-gray-100'
                                  : ''
                              }
                              header={
                                <div className="flex items-center justify-between py-2">
                                  <div className="flex-1">
                                    <div className="font-mono text-sm font-medium text-blue-600 mb-1">
                                      {getRouteDisplayText(route, selectedModelDomainIndex)}
                                      {route.builtin && (
                                        <span className="ml-2 px-2 py-0.5 text-xs bg-green-100 text-green-800 rounded-full">
                                          {t('configuration.defaultRoute')}
                                        </span>
                                      )}
                                    </div>
                                    <div className="text-xs text-gray-500">
                                      {t('configuration.method')}:{' '}
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
                                        allUniqueDomains.length > selectedModelDomainIndex
                                      ) {
                                        const selectedDomain =
                                          allUniqueDomains[selectedModelDomainIndex];
                                        if (selectedDomain) {
                                          const path = resolveEndpointPath(
                                            route.match?.path?.value || '/',
                                            route.match?.path?.type,
                                            modelConfig.modelAPIConfig.aiProtocols,
                                          );
                                          const formattedDomain = formatDomainWithPort(
                                            selectedDomain.domain,
                                            selectedDomain.port,
                                            selectedDomain.protocol,
                                          );
                                          const fullUrl = `${selectedDomain.protocol.toLowerCase()}://${formattedDomain}${path}`;
                                          copyToClipboard(fullUrl)
                                            .then(() => message.success(t('messages.linkCopied')))
                                            .catch(() => message.error(t('messages.copyFailed')));
                                        }
                                      } else if (route.domains && route.domains.length > 0) {
                                        const domain = route.domains[0];
                                        if (domain) {
                                          const path = resolveEndpointPath(
                                            route.match?.path?.value || '/',
                                            route.match?.path?.type,
                                            modelConfig.modelAPIConfig.aiProtocols,
                                          );
                                          const formattedDomain = formatDomainWithPort(
                                            domain.domain,
                                            domain.port,
                                            domain.protocol,
                                          );
                                          const fullUrl = `${domain.protocol.toLowerCase()}://${formattedDomain}${path}`;
                                          copyToClipboard(fullUrl)
                                            .then(() => message.success(t('messages.linkCopied')))
                                            .catch(() => message.error(t('messages.copyFailed')));
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
                              <div className="pl-4 space-y-4 pb-4">
                                <div className="grid grid-cols-2 gap-4">
                                  <div>
                                    <div className="text-xs text-gray-500 mb-1">
                                      {t('configuration.path')}:
                                    </div>
                                    <div className="text-sm font-mono bg-gray-50 px-3 py-2 rounded-lg">
                                      {getMatchTypePrefix(route.match?.path?.type)}{' '}
                                      {route.match?.path?.value}
                                    </div>
                                  </div>
                                  <div>
                                    <div className="text-xs text-gray-500 mb-1">
                                      {t('configuration.method')}:
                                    </div>
                                    <div className="text-sm font-mono bg-gray-50 px-3 py-2 rounded-lg">
                                      {getMethodsText(route)}
                                    </div>
                                  </div>
                                </div>

                                {route.match?.headers && route.match.headers.length > 0 && (
                                  <div>
                                    <div className="text-xs text-gray-500 mb-2">
                                      {t('configuration.headerMatch')}:
                                    </div>
                                    <div className="space-y-1">
                                      {route.match.headers.map((header, headerIndex: number) => (
                                        <div
                                          className="text-sm font-mono bg-gray-50 px-3 py-2 rounded-lg"
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
                                    <div className="text-xs text-gray-500 mb-2">
                                      {t('configuration.queryParamMatch')}:
                                    </div>
                                    <div className="space-y-1">
                                      {route.match.queryParams.map((param, paramIndex: number) => (
                                        <div
                                          className="text-sm font-mono bg-gray-50 px-3 py-2 rounded-lg"
                                          key={paramIndex}
                                        >
                                          {param.name} {getMatchTypePrefix(param.type)}{' '}
                                          {param.value}
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
              <div className="flex flex-col items-center justify-center py-16">
                <div className="mb-2 flex h-10 w-10 items-center justify-center rounded-full bg-gray-100">
                  <InboxOutlined className="text-base text-gray-400" />
                </div>
                <div className="text-sm text-gray-500">{t('configuration.empty')}</div>
              </div>
            ),
            key: 'configuration',
            label: (
              <span className="flex items-center gap-1.5 font-semibold">
                <SettingOutlined className="text-sm" />
                {`${t('tabs.configuration')}${modelConfig?.modelAPIConfig?.routes ? ` (${modelConfig.modelAPIConfig.routes.length})` : ''}`}
              </span>
            ),
          },
        ]}
        size="large"
      />
    </div>
  ) : null;

  const curlExample = generateCurlExample();
  const chatFeatures = [
    t('chat.features.multiTurn'),
    t('chat.features.multimodal'),
    t('chat.features.mcpIntegration'),
    t('chat.features.modelComparison'),
  ];

  const usagePanel = (
    <div>
      <div className="rounded-[12px] border border-[#E8EDF5] bg-[#FBFCFE] p-4">
        <div className="flex items-start gap-3">
          <div className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-[12px] bg-colorPrimaryBg text-colorPrimary">
            <MessageOutlined className="text-base" />
          </div>
          <div className="min-w-0">
            <div className="text-sm font-semibold text-gray-950">{t('chat.eyebrow')}</div>
            <p className="mt-1 text-sm leading-6 text-gray-600">{t('chat.description')}</p>
          </div>
        </div>
        <div className="mt-4 grid grid-cols-2 gap-x-4 gap-y-2 border-t border-[#E8EDF5] pt-3">
          {chatFeatures.map((feature) => (
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
        className="mt-4 !h-10 !rounded-[10px] !border-none !bg-colorPrimary !font-semibold shadow-[0_10px_22px_rgba(99,102,241,0.22)]"
        onClick={() => {
          if (!isLoggedIn) {
            setLoginPromptOpen(true);
            return;
          }
          if (hasSubscription) {
            navigate('/chat', { state: { selectedProduct: data } });
          } else {
            message.warning(t('messages.subscribeFirst'));
            headerRef.current?.showManageModal();
          }
        }}
        size="large"
        type="primary"
      >
        {!isLoggedIn
          ? t('chat.loginCta')
          : hasSubscription
            ? t('chat.startCta')
            : t('chat.subscribeCta')}
      </Button>
    </div>
  );

  const curlPanel = modelConfig?.modelAPIConfig ? (
    curlExample ? (
      <div className="relative overflow-hidden rounded-[12px] border border-[#172033] bg-[#111827]">
        <Button
          aria-label={t('curl.copy')}
          className="absolute right-2 top-2 z-10 text-gray-400 hover:text-white"
          icon={<CopyOutlined />}
          onClick={async () => {
            copyToClipboard(curlExample).then(() => {
              message.success(t('messages.curlCopied'));
            });
          }}
          size="small"
          title={t('curl.copy')}
          type="text"
        />
        <pre className="max-h-[260px] overflow-auto whitespace-pre p-4 pr-12 font-mono text-[12px] leading-5 text-gray-100">
          <code>{curlExample}</code>
        </pre>
      </div>
    ) : (
      <div className="rounded-[12px] border border-dashed border-[#DDE5F0] bg-[#FBFCFE] py-8 text-center text-sm text-gray-400">
        {t('curl.noRoutes')}
      </div>
    )
  ) : (
    <div className="rounded-[12px] border border-dashed border-[#DDE5F0] bg-[#FBFCFE] py-10 text-center text-sm text-gray-400">
      {t('curl.noConfig')}
    </div>
  );

  const rightContent = (
    <>
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
            <MessageOutlined className="text-[13px]" />
            {t('chat.tab')}
          </button>
          <button
            className={`flex flex-1 items-center justify-center gap-1.5 rounded-md py-2 text-xs transition-all ${
              rightPanelTab === 'curl'
                ? 'bg-white font-medium text-gray-800 shadow-sm'
                : 'text-gray-500 hover:text-gray-700'
            }`}
            onClick={() => setRightPanelTab('curl')}
            type="button"
          >
            <CodeOutlined className="text-[13px]" />
            {t('curl.title')}
          </button>
        </div>
        {rightPanelTab === 'usage' ? usagePanel : curlPanel}
      </section>
      <LoginPrompt
        contextMessage={tLoginPrompt('contextSubscribeModel')}
        onClose={() => setLoginPromptOpen(false)}
        open={loginPromptOpen}
      />
    </>
  );

  return (
    <ProductDetailLayout
      error={error || (!data ? t('errors.notFound') : undefined)}
      headerProps={
        data
          ? {
              description: data.description,
              icon: data.icon,
              name: data.name,
              onSubscriptionStatusChange: handleSubscriptionStatusChange,
              productType: 'MODEL_API',
              ref: headerRef,
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

export default ModelDetail;

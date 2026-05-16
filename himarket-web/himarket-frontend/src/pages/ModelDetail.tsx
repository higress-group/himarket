import {
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
  const headerRef = useRef<ProductHeaderHandle>(null);
  const { isLoggedIn } = useAuth();
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

          // 处理Model配置
          if (response.data.type === ProductType.MODEL_API) {
            const modelProduct = response.data;

            if (modelProduct.modelConfig) {
              setModelConfig(modelProduct.modelConfig);
            }
          }
        } else {
          setError(response.message || '数据加载失败');
        }
      } catch (error) {
        console.error('API请求失败:', error);
        setError('加载失败，请稍后重试');
      } finally {
        setLoading(false);
      }
    };
    fetchDetail();
  }, [modelProductId]);

  // 当产品切换时重置域名选择索引
  useEffect(() => {
    setSelectedModelDomainIndex(0);
  }, [data?.productId]);

  // 获取所有唯一域名
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

  // 生成域名选择器选项
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
      message.success('域名已复制到剪贴板', 1);
    } catch {
      message.error('复制失败，请手动复制');
    }
  };

  // Helper functions for route display
  const getMatchTypePrefix = (type: string) => {
    switch (type) {
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

  const getRouteDisplayText = (route: IRoute, domainIndex: number = 0) => {
    if (!route.match) return 'Unknown Route';

    const path = route.match.path?.value || '/';
    const pathType = route.match.path?.type;

    // 拼接域名信息 - 使用选择的域名索引
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
      // 回退到路由的第一个域名
      const domain = route.domains[0];
      if (domain) {
        const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
        domainInfo = `${domain.protocol.toLowerCase()}://${formattedDomain}`;
      }
    }

    // 构建基本路由信息（匹配符号直接加到path后面）
    let pathWithSuffix = path;
    if (pathType === 'Prefix') {
      pathWithSuffix = `${path}*`;
    } else if (pathType === 'Regex') {
      pathWithSuffix = `${path}~`;
    }
    // 精确匹配不加任何符号

    let routeText = `${domainInfo}${pathWithSuffix}`;

    // 添加描述信息
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

  // 获取适用场景中文翻译
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

  // 生成curl命令示例
  const generateCurlExample = () => {
    const fullUrl = getPrimaryModelEndpointUrl();
    if (!fullUrl) return null;

    const modelName = data?.feature?.modelFeature?.model || '{{model_name}}';

    return `curl --location '${fullUrl}' \\
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
            "content": "你是谁？"
        }
    ]
}'`;
  };

  const leftContent = data ? (
    <div className="bg-white/60 backdrop-blur-sm rounded-[10px] border border-white/40 p-6 pt-0">
      <Tabs
        defaultActiveKey="overview"
        items={[
          {
            children: data?.document ? (
              <div className="min-h-[400px] px-4">
                <MarkdownRender content={data.document} />
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center py-16">
                <div className="mb-2 flex h-10 w-10 items-center justify-center rounded-full bg-gray-100">
                  <InboxOutlined className="text-base text-gray-400" />
                </div>
                <div className="text-sm text-gray-500">暂无概览信息</div>
              </div>
            ),
            key: 'overview',
            label: (
              <span className="flex items-center gap-1.5 font-semibold">
                <FileTextOutlined className="text-sm" />
                概览
              </span>
            ),
          },
          {
            children: modelConfig?.modelAPIConfig ? (
              <div className="space-y-6">
                {/* 基本信息 */}
                <div className="grid grid-cols-2 gap-4">
                  {modelConfig.modelAPIConfig.modelCategory && (
                    <div className="bg-gray-50 rounded-[10px]">
                      <div className="text-sm text-gray-500 mb-1">适用场景</div>
                      <div className="text-sm font-medium text-gray-900">
                        {getModelCategoryText(modelConfig.modelAPIConfig.modelCategory)}
                      </div>
                    </div>
                  )}
                  <div className="bg-gray-50 rounded-[10px]">
                    <div className="text-sm text-gray-500 mb-1">协议</div>
                    <div className="text-sm font-medium text-gray-900">
                      {modelConfig.modelAPIConfig.aiProtocols?.join(', ') || 'DashScope'}
                    </div>
                  </div>
                </div>

                {/* 路由配置 */}
                {modelConfig.modelAPIConfig.routes &&
                  modelConfig.modelAPIConfig.routes.length > 0 && (
                    <div>
                      <div className="text-sm font-semibold text-gray-900 mb-4">路由配置</div>

                      {/* 域名选择器 */}
                      {modelDomainOptions.length > 0 && (
                        <div className="mb-4">
                          <div className="flex border border-gray-300 rounded-md overflow-hidden">
                            <span className="flex-shrink-0 bg-gray-50 px-3 py-2 text-xs text-gray-600 border-r border-gray-300 flex items-center whitespace-nowrap">
                              域名:
                            </span>
                            <div className="flex-1">
                              <Select
                                className="w-full"
                                labelRender={() => (
                                  <div className="inline-flex max-w-full items-center gap-1.5">
                                    <span className="min-w-0 truncate font-mono text-xs text-gray-900">
                                      {selectedModelDomain?.label || '选择域名'}
                                    </span>
                                    <Button
                                      aria-label="复制域名"
                                      disabled={!selectedModelDomain?.label}
                                      icon={<CopyOutlined />}
                                      onClick={(event) => {
                                        event.stopPropagation();
                                        handleCopySelectedModelDomain();
                                      }}
                                      onMouseDown={(event) => event.stopPropagation()}
                                      size="small"
                                      title="复制域名"
                                      type="text"
                                    />
                                  </div>
                                )}
                                onChange={setSelectedModelDomainIndex}
                                optionLabelProp="label"
                                placeholder="选择域名"
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

                      {/* 路由列表 */}
                      <div className="border border-gray-200 rounded-[10px] overflow-hidden">
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
                                          copyToClipboard(fullUrl).then(() =>
                                            message.success('链接已复制到剪贴板'),
                                          );
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
                                          copyToClipboard(fullUrl).then(() =>
                                            message.success('链接已复制到剪贴板'),
                                          );
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
                                {/* 匹配规则 */}
                                <div className="grid grid-cols-2 gap-4">
                                  <div>
                                    <div className="text-xs text-gray-500 mb-1">路径:</div>
                                    <div className="text-sm font-mono bg-gray-50 px-3 py-2 rounded-lg">
                                      {getMatchTypePrefix(route.match?.path?.type)}{' '}
                                      {route.match?.path?.value}
                                    </div>
                                  </div>
                                  <div>
                                    <div className="text-xs text-gray-500 mb-1">方法:</div>
                                    <div className="text-sm font-mono bg-gray-50 px-3 py-2 rounded-lg">
                                      {getMethodsText(route)}
                                    </div>
                                  </div>
                                </div>

                                {/* 请求头匹配 */}
                                {route.match?.headers && route.match.headers.length > 0 && (
                                  <div>
                                    <div className="text-xs text-gray-500 mb-2">请求头匹配:</div>
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

                                {/* 查询参数匹配 */}
                                {route.match?.queryParams && route.match.queryParams.length > 0 && (
                                  <div>
                                    <div className="text-xs text-gray-500 mb-2">查询参数匹配:</div>
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
                <div className="text-sm text-gray-500">暂无配置信息</div>
              </div>
            ),
            key: 'configuration',
            label: (
              <span className="flex items-center gap-1.5 font-semibold">
                <SettingOutlined className="text-sm" />
                {`配置${modelConfig?.modelAPIConfig?.routes ? ` (${modelConfig.modelAPIConfig.routes.length})` : ''}`}
              </span>
            ),
          },
        ]}
        size="large"
      />
    </div>
  ) : null;

  const rightContent = (
    <>
      <div className="bg-white/60 backdrop-blur-sm rounded-[10px] border border-white/40 p-6">
        <div className="mb-4 flex items-center gap-2">
          <span className="text-xs font-medium text-gray-500">Model Chat</span>
        </div>
        <Tabs
          defaultActiveKey="chat"
          items={[
            {
              children: (
                <div className="space-y-4">
                  {/* 现代极简风格卡片 */}
                  <div className="rounded-[10px] border border-indigo-100/50 bg-indigo-50/50 p-5">
                    {/* 图标 + 标题 */}
                    <div className="mb-4 flex items-center gap-3">
                      <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-gradient-to-br from-indigo-500 to-purple-500 shadow-sm">
                        <MessageOutlined className="text-sm text-white" />
                      </div>
                      <div>
                        <div className="text-base font-semibold text-gray-900">实时对话</div>
                        <div className="text-xs text-gray-400">Interactive Chat</div>
                      </div>
                    </div>
                    {/* 描述文字 */}
                    <p className="mb-4 text-sm leading-relaxed text-gray-600">
                      在交互式环境中体验模型能力，让AI触手可及
                    </p>
                    {/* 特性标签 */}
                    <div className="flex flex-wrap gap-2">
                      <span className="rounded-full bg-gray-50 px-3 py-1.5 text-xs font-medium text-gray-600">
                        多轮对话
                      </span>
                      <span className="rounded-full bg-gray-50 px-3 py-1.5 text-xs font-medium text-gray-600">
                        多模态
                      </span>
                      <span className="rounded-full bg-gray-50 px-3 py-1.5 text-xs font-medium text-gray-600">
                        MCP集成
                      </span>
                      <span className="rounded-full bg-gray-50 px-3 py-1.5 text-xs font-medium text-gray-600">
                        多模型对比
                      </span>
                    </div>
                  </div>

                  {/* 核心操作按钮 */}
                  <Button
                    block
                    className="rounded-lg border-none bg-gradient-to-r from-indigo-500 to-purple-500"
                    icon={<MessageOutlined />}
                    onClick={() => {
                      if (!isLoggedIn) {
                        setLoginPromptOpen(true);
                        return;
                      }
                      if (hasSubscription) {
                        navigate('/chat', { state: { selectedProduct: data } });
                      } else {
                        message.warning('请先订阅该产品后再进行对话测试');
                        headerRef.current?.showManageModal();
                      }
                    }}
                    size="large"
                    type="primary"
                  >
                    {!isLoggedIn
                      ? '登录后开始对话'
                      : hasSubscription
                        ? '开始对话测试'
                        : '订阅并开始对话'}
                  </Button>
                </div>
              ),
              key: 'chat',
              label: 'Chat',
            },
            {
              children: modelConfig?.modelAPIConfig ? (
                <div className="space-y-4">
                  {generateCurlExample() ? (
                    <>
                      <div className="relative">
                        <pre className="bg-gray-900 text-gray-100 p-4 rounded-[10px] text-xs overflow-x-auto whitespace-pre-wrap border border-gray-700">
                          <code>{generateCurlExample()}</code>
                        </pre>
                        <Button
                          className="absolute top-2 right-2 text-gray-400 hover:text-white"
                          icon={<CopyOutlined />}
                          onClick={async () => {
                            const curlCommand = generateCurlExample();
                            if (curlCommand) {
                              copyToClipboard(curlCommand).then(() => {
                                message.success('Curl命令已复制到剪贴板');
                              });
                            }
                          }}
                          size="small"
                          type="text"
                        />
                      </div>
                      {!data?.feature?.modelFeature?.model && (
                        <div className="text-xs text-gray-500 bg-blue-50 px-3 py-2 rounded-lg">
                          💡 将{' '}
                          <code className="bg-white px-1.5 py-0.5 rounded text-blue-600">
                            {'{{model_name}}'}
                          </code>{' '}
                          替换为实际的模型名称
                        </div>
                      )}
                    </>
                  ) : (
                    <div className="text-gray-400 text-center py-8">当前配置中没有找到路由</div>
                  )}
                </div>
              ) : (
                <div className="text-gray-400 text-center py-16">暂无 Model API 配置信息</div>
              ),
              key: 'curl',
              label: 'cURL',
            },
          ]}
        />
      </div>
      <LoginPrompt
        contextMessage={tLoginPrompt('contextSubscribeModel')}
        onClose={() => setLoginPromptOpen(false)}
        open={loginPromptOpen}
      />
    </>
  );

  return (
    <ProductDetailLayout
      error={error || (!data ? '未找到对应的模型' : undefined)}
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

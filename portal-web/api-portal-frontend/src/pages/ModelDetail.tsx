import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Layout } from "../components/Layout";
import { ProductHeader } from "../components/ProductHeader";
import {
  Alert,
  Button,
  message,
  Tabs,
  Collapse,
  Select,
  Spin,
} from "antd";
import { CopyOutlined, ArrowLeftOutlined, MessageOutlined, ThunderboltOutlined } from "@ant-design/icons";
import { ProductType } from "../types";
import type { IProductDetail } from "../lib/apis";
import type { IModelConfig, IRoute } from "../lib/apis/typing";
import APIs from "../lib/apis";
import MarkdownRender from "../components/MarkdownRender";
import { copyToClipboard } from "../lib/utils";

const { Panel } = Collapse;

function ModelDetail() {
  const { modelProductId } = useParams();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [data, setData] = useState<IProductDetail>();
  const [modelConfig, setModelConfig] = useState<IModelConfig>();
  const [selectedModelDomainIndex, setSelectedModelDomainIndex] = useState<number>(0);


  useEffect(() => {
    const fetchDetail = async () => {
      if (!modelProductId) {
        return;
      }
      setLoading(true);
      setError("");
      try {
        const response = await APIs.getProduct({ id: modelProductId });
        if (response.code === "SUCCESS" && response.data) {
          setData(response.data);

          // 处理Model配置
          if (response.data.type === ProductType.MODEL_API) {
            const modelProduct = response.data;

            if (modelProduct.modelConfig) {
              setModelConfig(modelProduct.modelConfig);
            }
          }
        } else {
          setError(response.message || "数据加载失败");
        }
      } catch (error) {
        console.error("API请求失败:", error);
        setError("加载失败，请稍后重试");
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
    if (!modelConfig?.modelAPIConfig?.routes) return []

    const domainsMap = new Map<string, { domain: string; protocol: string }>()

    modelConfig.modelAPIConfig.routes.forEach(route => {
      if (route.domains && route.domains.length > 0) {
        route.domains.forEach((domain) => {
          const key = `${domain.protocol}://${domain.domain}`
          domainsMap.set(key, domain)
        })
      }
    })

    return Array.from(domainsMap.values())
  }

  const allUniqueDomains = getAllUniqueDomains()

  // 生成域名选择器选项
  const modelDomainOptions = allUniqueDomains.map((domain, index) => ({
    value: index,
    label: `${domain.protocol.toLowerCase()}://${domain.domain}`
  }))

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
    if (!route.match) return 'Unknown Route'

    const path = route.match.path?.value || '/'
    const pathType = route.match.path?.type

    // 拼接域名信息 - 使用选择的域名索引
    let domainInfo = ''
    if (allUniqueDomains.length > 0 && allUniqueDomains.length > domainIndex) {
      const selectedDomain = allUniqueDomains[domainIndex]
      domainInfo = `${selectedDomain.protocol.toLowerCase()}://${selectedDomain.domain}`
    } else if (route.domains && route.domains.length > 0) {
      // 回退到路由的第一个域名
      const domain = route.domains[0]
      domainInfo = `${domain.protocol.toLowerCase()}://${domain.domain}`
    }

    // 构建基本路由信息（匹配符号直接加到path后面）
    let pathWithSuffix = path
    if (pathType === 'Prefix') {
      pathWithSuffix = `${path}*`
    } else if (pathType === 'Regex') {
      pathWithSuffix = `${path}~`
    }
    // 精确匹配不加任何符号

    let routeText = `${domainInfo}${pathWithSuffix}`

    // 添加描述信息
    if (route.description && route.description.trim()) {
      routeText += ` - ${route.description}`
    }

    return routeText
  };

  const getMethodsText = (route: IRoute) => {
    const methods = route.match?.methods
    if (!methods || methods.length === 0) {
      return 'ANY'
    }
    return methods.join(', ')
  }

  // 获取适用场景中文翻译
  const getModelCategoryText = (category: string) => {
    switch (category) {
      case 'Text':
        return '文本生成'
      case 'Image':
        return '图片生成'
      case 'Video':
        return '视频生成'
      case 'Audio':
        return '语音合成'
      case 'Embedding':
        return '向量化（Embedding）'
      case 'Rerank':
        return '文本排序（Rerank）'
      case 'Others':
        return '其他'
      default:
        return category || '未知'
    }
  };

  // 生成curl命令示例
  const generateCurlExample = () => {
    if (!modelConfig?.modelAPIConfig?.routes || !allUniqueDomains.length) {
      return null;
    }

    // 直接使用第一个路由
    const firstRoute = modelConfig.modelAPIConfig.routes[0];

    if (!firstRoute?.match?.path?.value) {
      return null;
    }

    // 使用选择的域名
    const selectedDomain = allUniqueDomains[selectedModelDomainIndex] || allUniqueDomains[0];
    const baseUrl = `${selectedDomain.protocol.toLowerCase()}://${selectedDomain.domain}`;
    const fullUrl = `${baseUrl}${firstRoute.match.path.value}`;

    return `curl --location '${fullUrl}' \\
  --header 'Content-Type: application/json' \\
  --data '{
    "model": "{{model_name}}",
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

  if (loading) {
    return (
      <Layout>
        <div className="flex justify-center items-center h-screen">
          <Spin size="large" tip="加载中..." />
        </div>
      </Layout>
    );
  }

  if (error || !data) {
    return (
      <Layout>
        <div className="p-8">
          <Alert message="错误" description={error} type="error" showIcon />
        </div>
      </Layout>
    );
  }

  return (
    <Layout>
      {/* 头部 */}
      <div className="mb-8">
        {/* 返回按钮 */}
        <button
          onClick={() => navigate(-1)}
          className="
            flex items-center gap-2 mb-4 px-4 py-2 rounded-xl
            text-gray-600 hover:text-colorPrimary
            hover:bg-colorPrimaryBgHover
            transition-all duration-200
          "
        >
          <ArrowLeftOutlined />
          <span>返回</span>
        </button>

        <ProductHeader
          name={data.name}
          description={data.description}
          icon={data.icon}
          updatedAt={data.updatedAt}
          productType="MODEL_API"
        />
      </div>

      {/* 主要内容区域 */}
      <div className="flex flex-col lg:flex-row gap-6">
        {/* 左侧内容 */}
        <div className="w-full lg:w-[65%] order-2 lg:order-1">
          <div className="bg-white/60 backdrop-blur-sm rounded-2xl border border-white/40 p-6 pt-0">
            <Tabs
              size="large"
              defaultActiveKey="overview"
              items={[
                {
                  key: "overview",
                  label: "概览",
                  children: data?.document ? (
                    <div className="min-h-[400px] prose prose-lg">
                      <MarkdownRender content={data.document} />
                    </div>
                  ) : (
                    <div className="text-gray-500 text-center py-16">
                      暂无概览信息
                    </div>
                  ),
                },
                {
                  key: "configuration",
                  label: `配置${modelConfig?.modelAPIConfig?.routes ? ` (${modelConfig.modelAPIConfig.routes.length})` : ''}`,
                  children: modelConfig?.modelAPIConfig ? (
                    <div className="space-y-6">
                      {/* 基本信息 */}
                      <div className="grid grid-cols-2 gap-4">
                        {modelConfig.modelAPIConfig.modelCategory && (
                          <div className="bg-gray-50 rounded-xl">
                            <div className="text-sm text-gray-500 mb-1">适用场景</div>
                            <div className="text-sm font-medium text-gray-900">
                              {getModelCategoryText(modelConfig.modelAPIConfig.modelCategory)}
                            </div>
                          </div>
                        )}
                        <div className="bg-gray-50 rounded-xl">
                          <div className="text-sm text-gray-500 mb-1">协议</div>
                          <div className="text-sm font-medium text-gray-900">
                            {modelConfig.modelAPIConfig.aiProtocols?.join(', ') || 'DashScope'}
                          </div>
                        </div>
                      </div>

                      {/* 路由配置 */}
                      {modelConfig.modelAPIConfig.routes && modelConfig.modelAPIConfig.routes.length > 0 && (
                        <div>
                          <div className="text-sm font-semibold text-gray-900 mb-4">路由配置</div>

                          {/* 域名选择器 */}
                          {modelDomainOptions.length > 0 && (
                            <div className="mb-4">
                              <div className="flex border border-gray-300 rounded-md overflow-hidden">
                                <span className="flex-shrink-0 bg-gray-50 px-3 py-2 text-xs text-gray-600 border-r border-gray-300 flex items-center whitespace-nowrap">域名:</span>
                                <div className="flex-1">
                                  <Select
                                    value={selectedModelDomainIndex}
                                    onChange={setSelectedModelDomainIndex}
                                    className="w-full"
                                    placeholder="选择域名"
                                    size="middle"
                                    variant="borderless"
                                  >
                                    {modelDomainOptions.map((option) => (
                                      <Select.Option key={option.value} value={option.value}>
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
                          <div className="border border-gray-200 rounded-xl overflow-hidden">
                            <Collapse ghost expandIconPosition="end">
                              {modelConfig.modelAPIConfig.routes.map((route, index) => (
                                <Panel
                                  key={index}
                                  header={
                                    <div className="flex items-center justify-between py-2">
                                      <div className="flex-1">
                                        <div className="font-mono text-sm font-medium text-blue-600 mb-1">
                                          {getRouteDisplayText(route, selectedModelDomainIndex)}
                                          {route.builtin && (
                                            <span className="ml-2 px-2 py-0.5 text-xs bg-green-100 text-green-800 rounded-full">默认</span>
                                          )}
                                        </div>
                                        <div className="text-xs text-gray-500">
                                          方法: <span className="font-medium text-gray-700">{getMethodsText(route)}</span>
                                        </div>
                                      </div>
                                      <Button
                                        size="small"
                                        type="text"
                                        icon={<CopyOutlined />}
                                        onClick={async (e) => {
                                          e.stopPropagation()
                                          if (allUniqueDomains.length > 0 && allUniqueDomains.length > selectedModelDomainIndex) {
                                            const selectedDomain = allUniqueDomains[selectedModelDomainIndex]
                                            const path = route.match?.path?.value || '/'
                                            const fullUrl = `${selectedDomain.protocol.toLowerCase()}://${selectedDomain.domain}${path}`
                                            copyToClipboard(fullUrl).then(() => message.success("链接已复制到剪贴板"))
                                          } else if (route.domains && route.domains.length > 0) {
                                            const domain = route.domains[0]
                                            const path = route.match?.path?.value || '/'
                                            const fullUrl = `${domain.protocol.toLowerCase()}://${domain.domain}${path}`
                                            copyToClipboard(fullUrl).then(() => message.success("链接已复制到剪贴板"))
                                          }
                                        }}
                                      />
                                    </div>
                                  }
                                  className={index < modelConfig.modelAPIConfig.routes.length - 1 ? "border-b border-gray-100" : ""}
                                >
                                  <div className="pl-4 space-y-4 pb-4">
                                    {/* 域名信息 */}
                                    <div>
                                      <div className="text-xs text-gray-500 mb-2">域名:</div>
                                      {route.domains?.map((domain, domainIndex: number) => (
                                        <div key={domainIndex} className="text-sm font-mono bg-gray-50 px-3 py-2 rounded-lg mb-1">
                                          {domain.protocol.toLowerCase()}://{domain.domain}
                                        </div>
                                      ))}
                                    </div>

                                    {/* 匹配规则 */}
                                    <div className="grid grid-cols-2 gap-4">
                                      <div>
                                        <div className="text-xs text-gray-500 mb-1">路径:</div>
                                        <div className="text-sm font-mono bg-gray-50 px-3 py-2 rounded-lg">
                                          {getMatchTypePrefix(route.match?.path?.type)} {route.match?.path?.value}
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
                                            <div key={headerIndex} className="text-sm font-mono bg-gray-50 px-3 py-2 rounded-lg">
                                              {header.name} {getMatchTypePrefix(header.type)} {header.value}
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
                                            <div key={paramIndex} className="text-sm font-mono bg-gray-50 px-3 py-2 rounded-lg">
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
                    <div className="text-gray-500 text-center py-16">
                      暂无配置信息
                    </div>
                  ),
                },
              ]}
            />
          </div>
        </div>

        {/* 右侧内容 - Model调试 */}
        <div className="w-full lg:w-[35%] order-1 lg:order-2">
          <div className="bg-white/60 backdrop-blur-sm rounded-2xl border border-white/40 p-6">
            <h3 className="text-base font-semibold mb-2 text-gray-900">Model 调试</h3>
            <Tabs
              defaultActiveKey="chat"
              items={[
                {
                  key: "chat",
                  label: "Chat",
                  children: (
                    <div className="space-y-4">
                      {/* 功能介绍 */}
                      <div className="bg-gradient-to-br from-blue-50 to-indigo-50 rounded-xl p-4 border border-blue-100">
                        <div className="flex items-start gap-3 mb-3">
                          <MessageOutlined className="text-xl text-blue-600 mt-0.5" />
                          <div>
                            <h4 className="text-sm font-semibold text-gray-900 mb-1">实时对话测试</h4>
                            <p className="text-xs text-gray-600 leading-relaxed">
                              在交互式环境中测试模型能力，支持多轮对话、实时响应
                            </p>
                          </div>
                        </div>
                      </div>

                      {/* 功能特性 */}
                      <div className="space-y-2">
                        <div className="flex items-start gap-2 text-xs text-gray-600">
                          <ThunderboltOutlined className="text-amber-500 mt-0.5" />
                          <span>支持流式输出，实时查看生成结果</span>
                        </div>
                        <div className="flex items-start gap-2 text-xs text-gray-600">
                          <MessageOutlined className="text-blue-500 mt-0.5" />
                          <span>保存对话历史，支持多轮交互测试</span>
                        </div>
                      </div>

                      {/* 操作按钮 */}
                      <Button
                        type="primary"
                        block
                        size="large"
                        icon={<MessageOutlined />}
                        className="rounded-lg mt-4"
                        onClick={() => {
                          navigate("/chat", { state: { selectedProduct: data } });
                        }}
                      >
                        开始对话测试
                      </Button>
                    </div>
                  ),
                },
                {
                  key: "curl",
                  label: "cURL",
                  children: modelConfig?.modelAPIConfig ? (
                    <div className="space-y-4">
                      {generateCurlExample() ? (
                        <>
                          <div className="relative">
                            <pre className="bg-gray-900 text-gray-100 p-4 rounded-xl text-xs overflow-x-auto whitespace-pre-wrap border border-gray-700">
                              <code>{generateCurlExample()}</code>
                            </pre>
                            <Button
                              size="small"
                              type="text"
                              icon={<CopyOutlined />}
                              className="absolute top-2 right-2 text-gray-400 hover:text-white"
                              onClick={async () => {
                                const curlCommand = generateCurlExample();
                                if (curlCommand) {
                                  copyToClipboard(curlCommand).then(() => {
                                    message.success('Curl命令已复制到剪贴板');
                                  });
                                }
                              }}
                            />
                          </div>
                          <div className="text-xs text-gray-500 bg-blue-50 px-3 py-2 rounded-lg">
                            💡 将 <code className="bg-white px-1.5 py-0.5 rounded text-blue-600">{"{{model_name}}"}</code> 替换为实际的模型名称
                          </div>
                        </>
                      ) : (
                        <div className="text-gray-400 text-center py-8">
                          当前配置中没有找到路由
                        </div>
                      )}
                    </div>
                  ) : (
                    <div className="text-gray-400 text-center py-16">
                      暂无 Model API 配置信息
                    </div>
                  ),
                },
              ]}
            />
          </div>
        </div>
      </div>
    </Layout>
  );
}

export default ModelDetail;

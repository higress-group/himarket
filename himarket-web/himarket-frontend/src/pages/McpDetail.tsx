import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Layout } from "../components/Layout";
import {
  Spin, Tag, Button, message, Tabs, Alert, Descriptions, Tooltip,
} from "antd";
import {
  ArrowLeftOutlined, AppstoreOutlined,
  CopyOutlined, ToolOutlined, CodeOutlined,
  LinkOutlined, ThunderboltOutlined,
  RightOutlined,
} from "@ant-design/icons";
import APIs from "../lib/apis";
import type { IProductDetail, IMcpMeta } from "../lib/apis/product";
import { ProductIconRenderer } from "../components/icon/ProductIconRenderer";
import { getIconString } from "../lib/iconUtils";
import MarkdownRender from "../components/MarkdownRender";
import dayjs from "dayjs";

function McpDetail() {
  const { mcpProductId } = useParams();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [product, setProduct] = useState<IProductDetail | null>(null);
  const [meta, setMeta] = useState<IMcpMeta | null>(null);
  const [error, setError] = useState("");
  const [activeTab, setActiveTab] = useState<string>("intro");
  const [subscribing, setSubscribing] = useState(false);
  const [subscribed, setSubscribed] = useState(false);
  const [unsubscribing, setUnsubscribing] = useState(false);
  // 冷数据连接配置（从 product.mcpConfig 生成）
  const [coldLocalJson, setColdLocalJson] = useState("");
  // 统一连接配置（后端 resolvedConfig，或前端 fallback）
  const [resolvedSseJson, setResolvedSseJson] = useState("");
  const [resolvedHttpJson, setResolvedHttpJson] = useState("");

  useEffect(() => {
    const fetchDetail = async () => {
      if (!mcpProductId) return;
      setLoading(true);
      setError("");
      try {
        // 获取产品详情
        const prodRes = await APIs.getProduct({ id: mcpProductId });
        if (prodRes.code === "SUCCESS" && prodRes.data) {
          setProduct(prodRes.data);
          // 获取 MCP meta
          try {
            const metaRes = await APIs.getProductMcpMeta(mcpProductId);
            if (metaRes.code === "SUCCESS" && metaRes.data?.length > 0) {
              setMeta(metaRes.data[0]);
            }
          } catch {
            // meta 可能不存在，不影响页面展示
          }
          // 检查是否已订阅（通过产品订阅状态）
          try {
            const status = await APIs.getProductSubscriptionStatus(mcpProductId);
            if (status.hasSubscription) {
              setSubscribed(true);
            }
          } catch {
            // 未登录或获取失败不影响页面
          }
        } else {
          setError("MCP 不存在");
        }
      } catch (e: any) {
        setError(e?.message || "加载失败");
      } finally {
        setLoading(false);
      }
    };
    fetchDetail();
  }, [mcpProductId]);

  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text);
    message.success("已复制");
  };

  const handleTryNow = () => {
    if (product) {
      navigate("/chat", { state: { selectedProduct: product } });
    }
  };

  // 显示名称：优先产品名，fallback 到 meta
  const displayName = product?.name || meta?.displayName || meta?.mcpName || "";
  const description = meta?.description || product?.description || "";
  const protocolType = (() => {
    const set = new Set<string>();
    if (meta?.protocolType) {
      meta.protocolType.split(",").map(p => p.trim().toUpperCase()).filter(Boolean).forEach(p => set.add(p));
    }
    const coldProto = product?.mcpConfig?.meta?.protocol;
    if (coldProto) {
      coldProto.split(",").map((p: string) => p.trim().toUpperCase()).filter(Boolean).forEach((p: string) => set.add(p));
    }
    if (meta?.endpointProtocol) {
      meta.endpointProtocol.split(",").map(p => p.trim().toUpperCase()).filter(Boolean).forEach(p => set.add(p));
    }
    if (product?.mcpConfig?.mcpServerConfig?.rawConfig && Object.keys(product.mcpConfig.mcpServerConfig.rawConfig).length > 0) {
      // rawConfig 存在不代表一定是 stdio，根据 meta.protocolType 判断
      if (!meta?.protocolType || meta.protocolType.toLowerCase().includes("stdio")) {
        set.add("STDIO");
      }
    }
    return Array.from(set).join(",");
  })();
  const origin = meta?.origin || "";
  const repoUrl = meta?.repoUrl || "";
  const tags = meta?.tags ? meta.tags.split(",").map(t => t.trim()).filter(Boolean) : [];
  const serviceIntro = meta?.serviceIntro || "";

  // 解析 tools：优先 meta.toolsConfig，fallback 到 product.mcpConfig.tools
  const parsedTools: any[] = (() => {
    const toolsSource = meta?.toolsConfig || product?.mcpConfig?.tools;
    if (!toolsSource) return [];
    try {
      const parsed = typeof toolsSource === "string" ? JSON.parse(toolsSource) : toolsSource;
      // 兼容两种格式：{tools: [...]} 或直接数组 [...]
      if (Array.isArray(parsed)) return parsed;
      return parsed?.tools || [];
    } catch {
      return [];
    }
  })();

  // 来源标签
  const originMap: Record<string, { text: string; color: string }> = {
    GATEWAY: { text: "网关导入", color: "blue" },
    NACOS: { text: "Nacos导入", color: "cyan" },
    CUSTOM: { text: "自定义配置", color: "purple" },
  };
  const originTag = origin ? (originMap[origin] || { text: origin, color: "default" }) : null;

  // 订阅 MCP（走正常的产品订阅流程）
  const handleSubscribe = async () => {
    if (!mcpProductId) return;
    setSubscribing(true);
    try {
      const consumerRes = await APIs.getPrimaryConsumer();
      if (consumerRes.code !== "SUCCESS" || !consumerRes.data) {
        message.error("获取消费者信息失败");
        return;
      }
      await APIs.subscribeProduct(consumerRes.data.consumerId, mcpProductId);
      setSubscribed(true);
      message.success("订阅成功");
    } catch (e: any) {
      const msg = e?.response?.data?.message || e?.message || "订阅失败";
      message.error(msg);
    } finally {
      setSubscribing(false);
    }
  };

  // 取消订阅（走正常的产品取消订阅流程）
  const handleUnsubscribe = async () => {
    if (!mcpProductId) return;
    setUnsubscribing(true);
    try {
      const consumerRes = await APIs.getPrimaryConsumer();
      if (consumerRes.code !== "SUCCESS" || !consumerRes.data) {
        message.error("获取消费者信息失败");
        return;
      }
      await APIs.unsubscribeProduct(consumerRes.data.consumerId, mcpProductId);
      setSubscribed(false);
      message.success("已取消订阅");
    } catch (e: any) {
      message.error(e?.response?.data?.message || "取消订阅失败");
    } finally {
      setUnsubscribing(false);
    }
  };

  // ==================== 连接配置（优先使用后端 resolvedConfig，fallback 前端解析） ====================
  useEffect(() => {
    setColdLocalJson(""); setResolvedSseJson(""); setResolvedHttpJson("");

    // 1. 冷数据：stdio 本地配置（rawConfig），始终填充（不 return，允许热数据共存）
    const rawConfig = product?.mcpConfig?.mcpServerConfig?.rawConfig;
    if (rawConfig && Object.keys(rawConfig).length > 0) {
      setColdLocalJson(JSON.stringify(rawConfig, null, 2));
    }

    // 2. 热数据优先：使用后端 resolvedConfig（已合并冷热数据）
    if (meta?.resolvedConfig) {
      try {
        const parsed = JSON.parse(meta.resolvedConfig);
        const servers = parsed?.mcpServers;
        if (servers) {
          const firstEntry = Object.values(servers)[0] as any;
          if (firstEntry) {
            const json = JSON.stringify(parsed, null, 2);
            if (firstEntry.type === "sse") {
              setResolvedSseJson(json);
            } else if (firstEntry.type === "streamable-http") {
              setResolvedHttpJson(json);
            } else {
              // 未知 type fallback 到 HTTP
              setResolvedHttpJson(json);
            }
            return;
          }
        }
      } catch { /* fallback to manual parsing below */ }
    }

    // 3. Fallback：前端自行从冷数据解析（兼容 resolvedConfig 不存在的情况）
    if (!product?.mcpConfig?.mcpServerConfig) return;
    const mcpConfig = product.mcpConfig;
    const serverName = meta?.mcpName || product.name;
    const domains = mcpConfig.mcpServerConfig.domains;
    const path = mcpConfig.mcpServerConfig.path;
    const protocol = mcpConfig.meta?.protocol;

    if (domains?.length > 0 && path) {
      const domain = domains[0];
      const proto = domain.protocol || "https";
      let formattedDomain = domain.domain;
      if (domain.port) {
        const isDefault = (proto === "http" && domain.port === 80) || (proto === "https" && domain.port === 443);
        if (!isDefault) formattedDomain = `${domain.domain}:${domain.port}`;
      }
      const fullUrl = `${proto}://${formattedDomain}${path || "/"}`;

      if (protocol === "SSE") {
        setResolvedSseJson(JSON.stringify({ mcpServers: { [serverName]: { type: "sse", url: fullUrl } } }, null, 2));
      } else if (protocol === "StreamableHTTP") {
        setResolvedHttpJson(JSON.stringify({ mcpServers: { [serverName]: { type: "streamable-http", url: fullUrl } } }, null, 2));
      } else {
        setResolvedSseJson(JSON.stringify({ mcpServers: { [serverName]: { type: "sse", url: `${fullUrl}/sse` } } }, null, 2));
        setResolvedHttpJson(JSON.stringify({ mcpServers: { [serverName]: { type: "streamable-http", url: fullUrl } } }, null, 2));
      }
    }
  }, [product, meta]);

  // 工具卡片展开状态
  const [expandedTools, setExpandedTools] = useState<Set<number>>(new Set());
  const toggleToolExpand = (idx: number) => {
    setExpandedTools(prev => {
      const next = new Set(prev);
      next.has(idx) ? next.delete(idx) : next.add(idx);
      return next;
    });
  };

  // 解析参数类型的友好显示
  const getTypeLabel = (prop: any): string => {
    if (!prop) return "any";
    if (prop.enum) return prop.enum.join(" | ");
    if (prop.type === "array") return `${getTypeLabel(prop.items)}[]`;
    return prop.type || "any";
  };

  if (loading) {
    return (
      <Layout>
        <div className="flex justify-center items-center h-[60vh]">
          <Spin size="large" tip="加载中..." />
        </div>
      </Layout>
    );
  }

  if (error || !product) {
    return (
      <Layout>
        <div className="p-8">
          <Alert message="错误" description={error || "MCP 不存在"} type="error" showIcon />
        </div>
      </Layout>
    );
  }

  return (
    <Layout>
      <div className="max-w-6xl mx-auto px-4 py-4">
        {/* 返回按钮 */}
        <button
          onClick={() => navigate(-1)}
          className="flex items-center gap-2 mb-4 px-4 py-2 rounded-xl text-gray-600 hover:text-colorPrimary hover:bg-colorPrimaryBgHover transition-all duration-200 text-sm"
        >
          <ArrowLeftOutlined />
          <span>返回</span>
        </button>

        {/* Header - 毛玻璃卡片 */}
        <div className="bg-white/70 backdrop-blur-sm rounded-2xl border border-white/50 p-6 mb-6">
          <div className="flex items-start justify-between gap-6">
            {/* 左侧: 图标 + 信息 */}
            <div className="flex items-start gap-4 flex-1 min-w-0">
              <div className="w-14 h-14 rounded-xl bg-gradient-to-br from-purple-100 to-indigo-100 flex items-center justify-center flex-shrink-0 overflow-hidden">
                {meta?.icon || product.icon ? (
                  <ProductIconRenderer className="w-full h-full object-cover" iconType={getIconString(meta?.icon ? { type: "URL", value: meta.icon } : product.icon)} />
                ) : (
                  <AppstoreOutlined className="text-purple-500 text-2xl" />
                )}
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2.5 mb-1.5 flex-wrap">
                  <h1 className="text-xl font-bold text-gray-900">{displayName}</h1>
                  {originTag && (
                    <Tag color={originTag.color} className="border-0 m-0">{originTag.text}</Tag>
                  )}
                  {protocolType && protocolType.split(",").map(p => p.trim()).filter(Boolean).map(p => (
                    <Tag key={p} color="blue" className="border-0 m-0 bg-blue-50">{p.toUpperCase()}</Tag>
                  ))}
                </div>
                {meta?.mcpName && (
                  <div className="text-xs text-gray-400 font-mono mb-1.5">{meta.mcpName}</div>
                )}
                <p className="text-sm text-gray-500 leading-relaxed mb-3 max-w-2xl">
                  {description || "暂无描述"}
                </p>
                <div className="flex items-center gap-4 text-xs text-gray-400 flex-wrap">
                  <span className="flex items-center gap-1">
                    <ToolOutlined /> {parsedTools.length} 个工具
                  </span>
                  <span>创建于 {dayjs(product.createAt).format("YYYY-MM-DD")}</span>
                  {product.categories?.[0] && (
                    <Tag className="m-0 border-0 bg-gray-50 text-gray-500 text-xs">{product.categories[0].name}</Tag>
                  )}
                  {tags.slice(0, 3).map((t) => (
                    <Tag key={t} className="m-0 border-0 bg-gray-50 text-gray-500 text-xs">{t}</Tag>
                  ))}
                </div>
              </div>
            </div>
            {/* 右侧: 操作按钮 */}
            <div className="flex-shrink-0 pt-1">
              <Button type="primary" size="large" onClick={handleTryNow}>
                立即体验
              </Button>
            </div>
          </div>
        </div>

        {/* 主体 - 左右分栏 */}
        <div className="flex flex-col lg:flex-row gap-6">
          {/* 左侧: Tab 内容 */}
          <div className="w-full lg:w-[65%]">
            <div className="bg-white/70 backdrop-blur-sm rounded-2xl border border-white/50">
              <Tabs
                activeKey={activeTab}
                onChange={setActiveTab}
                className="px-6 pt-2"
                items={[
                  {
                    key: "intro",
                    label: "介绍",
                    children: (
                      <div className="pb-6 min-h-[300px]">
                        <div className="markdown-body text-sm" style={{ backgroundColor: 'transparent' }}>
                          <MarkdownRender content={serviceIntro || description || "暂无详细介绍"} />
                        </div>
                      </div>
                    ),
                  },
                  {
                    key: "tools",
                    label: (
                      <span className="flex items-center gap-1.5">
                        工具列表
                        <span className="text-xs text-gray-400">({parsedTools.length})</span>
                      </span>
                    ),
                    children: (
                      <div className="pb-6 min-h-[300px]">
                        {parsedTools.length > 0 ? (
                          <div className="space-y-3">
                            <div className="text-xs text-gray-400 mb-1">
                              共 {parsedTools.length} 个工具
                            </div>
                            {parsedTools.map((tool: any, idx: number) => {
                              const schema = tool.inputSchema;
                              const properties = schema?.properties || {};
                              const required: string[] = schema?.required || [];
                              const paramKeys = Object.keys(properties);
                              const isExpanded = expandedTools.has(idx);

                              return (
                                <div
                                  key={idx}
                                  className="rounded-xl border border-gray-100 bg-white/80 hover:border-indigo-200 hover:shadow-sm transition-all duration-200"
                                >
                                  {/* 工具头部 */}
                                  <div
                                    className="flex items-start gap-3 p-4 cursor-pointer select-none"
                                    onClick={() => toggleToolExpand(idx)}
                                  >
                                    <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-indigo-50 to-purple-50 flex items-center justify-center flex-shrink-0 mt-0.5">
                                      <CodeOutlined className="text-indigo-400 text-sm" />
                                    </div>
                                    <div className="flex-1 min-w-0">
                                      <div className="flex items-center gap-2 mb-1">
                                        <span className="font-mono text-sm font-semibold text-gray-800">{tool.name}</span>
                                        {paramKeys.length > 0 && (
                                          <span className="text-[10px] text-gray-400 bg-gray-50 px-1.5 py-0.5 rounded">
                                            {paramKeys.length} 个参数
                                          </span>
                                        )}
                                      </div>
                                      <p className="text-xs text-gray-500 leading-relaxed line-clamp-2">
                                        {tool.description || "暂无描述"}
                                      </p>
                                      {/* 参数预览 */}
                                      {paramKeys.length > 0 && (
                                        <div className="flex flex-wrap gap-1.5 mt-2">
                                          {paramKeys.slice(0, 6).map(key => (
                                            <Tooltip
                                              key={key}
                                              title={properties[key]?.description || key}
                                              placement="top"
                                            >
                                              <span
                                                className={`inline-flex items-center gap-1 text-[11px] px-2 py-0.5 rounded-md ${
                                                  required.includes(key)
                                                    ? "bg-indigo-50 text-indigo-600 border border-indigo-100"
                                                    : "bg-gray-50 text-gray-500 border border-gray-100"
                                                }`}
                                              >
                                                {key}
                                                <span className="text-[10px] opacity-60">{getTypeLabel(properties[key])}</span>
                                                {required.includes(key) && <span className="text-indigo-400">*</span>}
                                              </span>
                                            </Tooltip>
                                          ))}
                                          {paramKeys.length > 6 && (
                                            <span className="text-[11px] text-gray-400 px-1.5 py-0.5">
                                              +{paramKeys.length - 6} 更多
                                            </span>
                                          )}
                                        </div>
                                      )}
                                    </div>
                                    <RightOutlined
                                      className={`text-gray-300 text-xs mt-2 transition-transform duration-200 ${isExpanded ? "rotate-90" : ""}`}
                                    />
                                  </div>

                                  {/* 展开的参数详情 */}
                                  {isExpanded && paramKeys.length > 0 && (
                                    <div className="px-4 pb-4 pt-0">
                                      <div className="rounded-lg bg-gray-50/80 border border-gray-100 overflow-hidden">
                                        <div className="px-3 py-2 bg-gray-50 border-b border-gray-100">
                                          <span className="text-[11px] font-medium text-gray-500">参数详情</span>
                                        </div>
                                        <div className="divide-y divide-gray-100">
                                          {paramKeys.map(key => {
                                            const prop = properties[key];
                                            const isRequired = required.includes(key);
                                            return (
                                              <div key={key} className="px-3 py-2.5 flex items-start gap-3">
                                                <div className="flex items-center gap-1.5 min-w-0 flex-shrink-0" style={{ width: 140 }}>
                                                  <span className="font-mono text-xs text-gray-700 truncate">{key}</span>
                                                  {isRequired && (
                                                    <Tag color="blue" className="m-0 border-0 text-[10px] leading-4 px-1">必填</Tag>
                                                  )}
                                                </div>
                                                <div className="flex-1 min-w-0">
                                                  <div className="flex items-center gap-2 mb-0.5">
                                                    <Tag className="m-0 border-0 bg-purple-50 text-purple-500 text-[10px] leading-4 px-1.5">
                                                      {getTypeLabel(prop)}
                                                    </Tag>
                                                    {prop?.default !== undefined && prop?.default !== "" && (
                                                      <span className="text-[10px] text-gray-400">
                                                        默认: {JSON.stringify(prop.default)}
                                                      </span>
                                                    )}
                                                  </div>
                                                  {prop?.description && (
                                                    <p className="text-[11px] text-gray-500 leading-relaxed mt-0.5">{prop.description}</p>
                                                  )}
                                                </div>
                                              </div>
                                            );
                                          })}
                                        </div>
                                      </div>
                                    </div>
                                  )}

                                  {/* 无参数时展开提示 */}
                                  {isExpanded && paramKeys.length === 0 && (
                                    <div className="px-4 pb-4 pt-0">
                                      <div className="text-xs text-gray-400 bg-gray-50 rounded-lg p-3 text-center">
                                        此工具无需参数
                                      </div>
                                    </div>
                                  )}
                                </div>
                              );
                            })}
                          </div>
                        ) : (
                          <div className="text-gray-400 text-center py-12">
                            <ToolOutlined className="text-3xl mb-2 block" />
                            暂无工具信息
                          </div>
                        )}
                      </div>
                    ),
                  },
                ]}
              />
            </div>
          </div>

          {/* 右侧: 连接配置 + 基本信息 */}
          <div className="w-full lg:w-[35%]">
            <div className="lg:sticky lg:top-4 space-y-4">
              {/* 连接配置 */}
              <div className="bg-white/70 backdrop-blur-sm rounded-2xl border border-white/50 p-5">
                <h3 className="text-sm font-semibold text-gray-900 mb-3 flex items-center gap-2">
                  <LinkOutlined className="text-green-500" />
                  连接配置
                </h3>
                {(() => {
                  const hasCold = !!(resolvedSseJson || resolvedHttpJson || coldLocalJson);

                  if (!hasCold) {
                    return (
                      <div className="text-xs text-gray-400 text-center py-4">
                        暂无连接配置信息
                      </div>
                    );
                  }

                  const tabItems: { key: string; label: React.ReactNode; children: React.ReactNode }[] = [];

                  // 判断冷热数据协议是否重叠：重叠时只显示热数据
                  const coldProto = (meta?.protocolType?.toLowerCase() || "").trim();
                  const coldIsSse = coldProto === "sse";
                  const coldIsHttp = coldProto.includes("http");
                  const coldOverlapsHot = (coldIsSse && resolvedSseJson) || (coldIsHttp && resolvedHttpJson);

                  // SSE（热数据）
                  if (resolvedSseJson) {
                    tabItems.push({
                      key: "sse",
                      label: "SSE",
                      children: renderConfigJsonBlock(resolvedSseJson),
                    });
                  }

                  // Streamable HTTP（热数据）
                  if (resolvedHttpJson) {
                    tabItems.push({
                      key: "http",
                      label: "Streamable HTTP",
                      children: renderConfigJsonBlock(resolvedHttpJson),
                    });
                  }

                  // 冷数据（本地配置）：协议与热数据重叠时不显示
                  if (coldLocalJson && !coldOverlapsHot) {
                    const coldLabel = (() => {
                      if (coldIsHttp) return "Streamable HTTP";
                      if (coldIsSse) return "SSE";
                      return "Stdio";
                    })();
                    tabItems.push({
                      key: "local",
                      label: coldLabel,
                      children: renderConfigJsonBlock(coldLocalJson),
                    });
                  }

                  const defaultKey = tabItems[0]?.key || "sse";

                  return (
                    <div>
                      {subscribed && (
                        <div className="mb-3">
                          <Tag color="green" className="m-0 border-0">已订阅</Tag>
                        </div>
                      )}
                      <Tabs size="small" defaultActiveKey={defaultKey} items={tabItems} />
                      {/* 订阅/取消订阅 */}
                      <div className="mt-3">
                        {!subscribed ? (
                          <Button
                            type="primary"
                            size="small"
                            icon={<ThunderboltOutlined />}
                            loading={subscribing}
                            onClick={handleSubscribe}
                            block
                          >
                            订阅
                          </Button>
                        ) : (
                          <Button
                            size="small"
                            danger
                            loading={unsubscribing}
                            onClick={handleUnsubscribe}
                            block
                          >
                            取消订阅
                          </Button>
                        )}
                      </div>
                    </div>
                  );
                })()}
              </div>

              {/* 基本信息 */}
              <div className="bg-white/70 backdrop-blur-sm rounded-2xl border border-white/50 p-5">
                <h3 className="text-sm font-semibold text-gray-900 mb-3">基本信息</h3>
                <Descriptions column={1} size="small">
                  {originTag && (
                    <Descriptions.Item label="来源">
                      <Tag color={originTag.color} className="m-0 border-0">{originTag.text}</Tag>
                    </Descriptions.Item>
                  )}
                  {protocolType && (
                    <Descriptions.Item label="协议">
                      <div className="flex flex-wrap gap-1">
                        {protocolType.split(",").map(p => p.trim()).filter(Boolean).map(p => (
                          <Tag key={p} color="blue" className="m-0 border-0 bg-blue-50">{p.toUpperCase()}</Tag>
                        ))}
                      </div>
                    </Descriptions.Item>
                  )}
                  {repoUrl && (
                    <Descriptions.Item label="仓库地址">
                      <div className="flex items-center gap-1 max-w-full">
                        <a href={repoUrl} target="_blank" rel="noopener noreferrer" className="text-xs text-blue-500 hover:underline truncate">
                          {repoUrl}
                        </a>
                        <CopyOutlined className="text-gray-400 hover:text-gray-600 cursor-pointer flex-shrink-0" onClick={() => handleCopy(repoUrl)} />
                      </div>
                    </Descriptions.Item>
                  )}
                  <Descriptions.Item label="工具数">
                    {parsedTools.length} 个
                  </Descriptions.Item>
                  <Descriptions.Item label="创建时间">
                    {dayjs(product.createAt).format("YYYY-MM-DD HH:mm")}
                  </Descriptions.Item>
                  {product.categories?.length > 0 && (
                    <Descriptions.Item label="分类">
                      {product.categories.map(c => (
                        <Tag key={c.categoryId} className="m-0 border-0 bg-gray-50 text-gray-600 text-xs">{c.name}</Tag>
                      ))}
                    </Descriptions.Item>
                  )}
                  {tags.length > 0 && (
                    <Descriptions.Item label="标签">
                      <div className="flex flex-wrap gap-1">
                        {tags.map(t => (
                          <Tag key={t} className="m-0 border-0 bg-purple-50 text-purple-600 text-xs">{t}</Tag>
                        ))}
                      </div>
                    </Descriptions.Item>
                  )}
                </Descriptions>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Layout>
  );

  // JSON 语法高亮（浅色主题）
  function highlightJson(json: string) {
    return json.replace(
      /("(?:\\.|[^"\\])*")\s*:/g,
      '<span style="color:#6366f1">$1</span>:'
    ).replace(
      /:\s*("(?:\\.|[^"\\])*")/g,
      ': <span style="color:#059669">$1</span>'
    ).replace(
      /:\s*(\d+)/g,
      ': <span style="color:#d97706">$1</span>'
    ).replace(
      /:\s*(true|false|null)/g,
      ': <span style="color:#dc2626">$1</span>'
    );
  }

  // 统一的配置 JSON 展示块
  function renderConfigJsonBlock(json: string) {
    if (!json) {
      return <div className="text-xs text-gray-400 text-center py-4">已订阅，但暂无可用链接</div>;
    }
    return (
      <div className="relative group/json">
        <div className="rounded-lg p-3 overflow-x-auto border border-purple-100 bg-purple-50/30">
          <Button
            type="text"
            size="small"
            icon={<CopyOutlined />}
            className="absolute top-1.5 right-1.5 text-gray-300 hover:text-gray-500 opacity-0 group-hover/json:opacity-100 transition-opacity z-10"
            onClick={() => handleCopy(json)}
          />
          <pre
            className="text-xs font-mono whitespace-pre leading-relaxed text-gray-500"
            dangerouslySetInnerHTML={{ __html: highlightJson(json) }}
          />
        </div>
      </div>
    );
  }

}

export default McpDetail;

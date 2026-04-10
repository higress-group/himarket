import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Layout } from "../components/Layout";
import {
  Spin, Tag, Button, message, Tabs, Alert, Descriptions, Tooltip, Modal, Select, Popconfirm,
} from "antd";
import {
  ArrowLeftOutlined, AppstoreOutlined,
  CopyOutlined, ToolOutlined, CodeOutlined,
  LinkOutlined, ThunderboltOutlined,
  RightOutlined, PlusOutlined,
  CheckCircleFilled, ClockCircleFilled, ExclamationCircleFilled,
} from "@ant-design/icons";
import APIs from "../lib/apis";
import type { IProductDetail, IMcpMeta } from "../lib/apis/product";
import { getProductMcpMetaPublic } from "../lib/apis/product";
import { getConsumers, subscribeProduct, unsubscribeProduct, getProductSubscriptions } from "../lib/apis/consumer";
import type { Consumer } from "../types/consumer";
import type { ISubscription } from "../lib/apis/consumer";
import { ProductIconRenderer } from "../components/icon/ProductIconRenderer";
import { copyToClipboard } from "../lib/utils";
import { getIconString, parseMetaIcon } from "../lib/iconUtils";
import MarkdownRender from "../components/MarkdownRender";
import { useAuth } from "../hooks/useAuth";
import { DetailSkeleton } from "../components/loading";
import { hasAvailableEndpoint } from "../lib/utils/mcpUtils";
import dayjs from "dayjs";

function McpDetail() {
  const { mcpProductId } = useParams();
  const navigate = useNavigate();
  const { isLoggedIn, login } = useAuth();
  const [loading, setLoading] = useState(true);
  const [product, setProduct] = useState<IProductDetail | null>(null);
  const [meta, setMeta] = useState<IMcpMeta | null>(null);
  const [error, setError] = useState("");
  const [activeTab, setActiveTab] = useState<string>("intro");
  const [subscribing, setSubscribing] = useState(false);
  const [subscribed, setSubscribed] = useState(false);
  // 订阅管理弹窗
  const [subscribeModalOpen, setSubscribeModalOpen] = useState(false);
  const [consumers, setConsumers] = useState<Consumer[]>([]);
  const [consumersLoading, setConsumersLoading] = useState(false);
  const [selectedConsumerId, setSelectedConsumerId] = useState<string>("");
  const [isApplyingSubscription, setIsApplyingSubscription] = useState(false);
  // 订阅列表
  const [subscriptionList, setSubscriptionList] = useState<ISubscription[]>([]);
  const [subscriptionListLoading, setSubscriptionListLoading] = useState(false);
  // 连接配置 tabs（由 useEffect 统一计算）
  const [configTabs, setConfigTabs] = useState<{ key: string; label: string; json: string }[]>([]);

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
            const metaRes = isLoggedIn
              ? await APIs.getProductMcpMeta(mcpProductId)
              : await getProductMcpMetaPublic(mcpProductId);
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
  }, [mcpProductId, isLoggedIn]);

  const handleCopy = (text: string) => {
    copyToClipboard(text);
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
  const tags = (() => {
    if (!meta?.tags) return [];
    try {
      const parsed = JSON.parse(meta.tags);
      return Array.isArray(parsed) ? parsed : meta.tags.split(",").map((t: string) => t.trim()).filter(Boolean);
    } catch {
      return meta.tags.split(",").map((t: string) => t.trim()).filter(Boolean);
    }
  })();
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
    ADMIN: { text: "管理员发布", color: "green" },
    OPEN_API: { text: "API注册", color: "geekblue" },
    AGENTRUNTIME: { text: "AgentRuntime", color: "orange" },
    USER: { text: "开发者发布", color: "lime" },
    VENDOR_IMPORT: { text: "三方导入", color: "magenta" },
  };
  const originTag = origin ? (originMap[origin] || { text: origin, color: "default" }) : null;

  // 获取订阅列表
  const fetchSubscriptionList = async () => {
    if (!mcpProductId) return;
    setSubscriptionListLoading(true);
    try {
      const res = await getProductSubscriptions(mcpProductId, { size: 100 });
      const list = res.data?.content || [];
      setSubscriptionList(list);
      setSubscribed(list.some((s: ISubscription) => s.status === "APPROVED"));
    } catch {
      // ignore
    } finally {
      setSubscriptionListLoading(false);
    }
  };

  // 打开管理订阅弹窗
  const openSubscribeModal = async () => {
    setSubscribeModalOpen(true);
    setIsApplyingSubscription(false);
    setSelectedConsumerId("");
    await fetchSubscriptionList();
  };

  // 开始新增订阅流程
  const startApplyingSubscription = async () => {
    setIsApplyingSubscription(true);
    setSelectedConsumerId("");
    setConsumersLoading(true);
    try {
      const res = await getConsumers({ page: 1, size: 100 });
      if (res.data) {
        setConsumers(res.data.content || res.data);
      }
    } catch {
      message.error("获取消费者列表失败");
    } finally {
      setConsumersLoading(false);
    }
  };

  // 确认订阅
  const handleSubscribe = async () => {
    if (!mcpProductId || !selectedConsumerId) return;
    setSubscribing(true);
    try {
      await subscribeProduct(selectedConsumerId, mcpProductId);
      message.success("订阅成功");
      setIsApplyingSubscription(false);
      setSelectedConsumerId("");
      await fetchSubscriptionList();
    } catch (e: any) {
      const msg = e?.response?.data?.message || e?.message || "订阅失败";
      message.error(msg);
    } finally {
      setSubscribing(false);
    }
  };

  // 取消某个消费者的订阅
  const handleUnsubscribeConsumer = async (consumerId: string) => {
    if (!mcpProductId) return;
    try {
      await unsubscribeProduct(consumerId, mcpProductId);
      message.success("已取消订阅");
      await fetchSubscriptionList();
    } catch (e: any) {
      message.error(e?.response?.data?.message || "取消订阅失败");
    }
  };

  // ==================== 连接配置（统一解析为 configTabs） ====================
  useEffect(() => {
    const tabs: { key: string; label: string; json: string }[] = [];

    // 辅助：检测协议类型，优先用 meta.protocolType，fallback 到 JSON 内容推断
    const detectProtoFromEntry = (entry: any): "stdio" | "sse" | "http" => {
      if (entry?.command) return "stdio";
      if (entry?.type === "sse") return "sse";
      if (entry?.type === "streamable-http") return "http";
      if (entry?.url) return "http";
      return "stdio";
    };
    const normalizeProto = (raw: string): "stdio" | "sse" | "http" | null => {
      const lower = raw.trim().toLowerCase();
      if (lower === "stdio") return "stdio";
      if (lower === "sse") return "sse";
      if (lower.includes("http")) return "http";
      return null;
    };
    const protoLabel: Record<string, string> = { stdio: "Stdio", sse: "SSE", http: "Streamable HTTP" };

    // 1. 热数据：resolvedConfig
    let hotProto: string | null = null;
    if (meta?.resolvedConfig) {
      try {
        const parsed = JSON.parse(meta.resolvedConfig);
        const firstEntry = Object.values(parsed?.mcpServers || {})[0] as any;
        if (firstEntry) {
          const proto = normalizeProto(meta?.endpointProtocol || "") || detectProtoFromEntry(firstEntry);
          hotProto = proto;
          tabs.push({ key: proto, label: protoLabel[proto], json: JSON.stringify(parsed, null, 2) });
        }
      } catch { /* fallback below */ }
    }

    // 2. 冷数据：meta.connectionConfig（原始配置）
    //    协议与热数据不同时追加；协议相同时热数据已覆盖，不重复展示
    if (meta?.connectionConfig) {
      try {
        const serverName = meta.mcpName || product?.name || "mcp-server";
        const parsed = JSON.parse(meta.connectionConfig);
        // 优先用 meta.protocolType 判断协议，fallback 到 JSON 内容推断
        const servers = parsed?.mcpServers || parsed;
        const firstKey = servers ? Object.keys(servers)[0] : null;
        const entry = firstKey ? servers[firstKey] : null;
        const coldProto = normalizeProto(meta.protocolType || "")
          || (entry ? detectProtoFromEntry(entry) : (parsed?.command ? "stdio" : null));

        if (coldProto && coldProto !== hotProto) {
          // 标准化为 mcpServers 格式
          let coldJson: string;
          if (parsed?.mcpServers) {
            coldJson = JSON.stringify(parsed, null, 2);
          } else if (parsed?.command) {
            // 单 server 格式 → 包装为 mcpServers
            const { env: _env, ...rest } = parsed;
            coldJson = JSON.stringify({ mcpServers: { [serverName]: rest } }, null, 2);
          } else if (firstKey && entry) {
            // { serverName: { ... } } 格式 → 包装
            const { env: _env, ...rest } = entry;
            coldJson = JSON.stringify({ mcpServers: { [firstKey]: rest } }, null, 2);
          } else {
            coldJson = JSON.stringify(parsed, null, 2);
          }
          tabs.push({ key: coldProto, label: protoLabel[coldProto], json: coldJson });
        }
      } catch { /* ignore */ }
    }

    // 3. Fallback：rawConfig（仅在无任何数据时）
    if (tabs.length === 0) {
      const rawConfig = product?.mcpConfig?.mcpServerConfig?.rawConfig;
      if (rawConfig && Object.keys(rawConfig).length > 0) {
        tabs.push({ key: "stdio", label: "Stdio", json: JSON.stringify(rawConfig, null, 2) });
      }
    }

    // 4. Fallback：domains + path（旧网关导入兼容）
    if (tabs.length === 0 && product?.mcpConfig?.mcpServerConfig) {
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
          tabs.push({ key: "sse", label: "SSE", json: JSON.stringify({ mcpServers: { [serverName]: { type: "sse", url: fullUrl } } }, null, 2) });
        } else if (protocol === "StreamableHTTP") {
          tabs.push({ key: "http", label: "Streamable HTTP", json: JSON.stringify({ mcpServers: { [serverName]: { type: "streamable-http", url: fullUrl } } }, null, 2) });
        } else {
          tabs.push({ key: "sse", label: "SSE", json: JSON.stringify({ mcpServers: { [serverName]: { type: "sse", url: `${fullUrl}/sse` } } }, null, 2) });
          tabs.push({ key: "http", label: "Streamable HTTP", json: JSON.stringify({ mcpServers: { [serverName]: { type: "streamable-http", url: fullUrl } } }, null, 2) });
        }
      }
    }

    setConfigTabs(tabs);
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
        <div className="max-w-6xl mx-auto px-4 py-6">
          <DetailSkeleton />
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
                  <ProductIconRenderer className="w-full h-full object-cover" iconType={getIconString(parseMetaIcon(meta?.icon) || product.icon)} />
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
                {!isLoggedIn ? (
                  <div className="text-center py-6">
                    <div className="text-sm text-gray-400 mb-3">请登录后查看连接信息及订阅</div>
                    <Button type="primary" size="small" onClick={() => login()}>
                      去登录
                    </Button>
                  </div>
                ) : (() => {
                  if (configTabs.length === 0) {
                    return (
                      <div className="text-xs text-gray-400 text-center py-4">
                        暂无连接配置信息
                      </div>
                    );
                  }

                  const tabItems = configTabs.map(tab => ({
                    key: tab.key,
                    label: tab.label,
                    children: renderConfigJsonBlock(tab.json),
                  }));

                  return (
                    <div>
                      {subscribed && (
                        <div className="mb-3">
                          <Tag color="green" className="m-0 border-0">已订阅</Tag>
                        </div>
                      )}
                      <Tabs size="small" defaultActiveKey={tabItems[0]?.key} items={tabItems} />
                      {/* 订阅/管理订阅 */}
                      <div className="mt-3">
                        {(hasAvailableEndpoint(meta) || subscribed) && (
                          <Button
                            type="primary"
                            size="small"
                            icon={<ThunderboltOutlined />}
                            onClick={openSubscribeModal}
                            block
                          >
                            管理订阅
                          </Button>
                        )}
                      </div>
                    </div>
                  );
                })()}
              </div>
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

      {/* 订阅管理弹窗 */}
      <Modal
        title="订阅管理"
        open={subscribeModalOpen}
        onCancel={() => { setSubscribeModalOpen(false); setIsApplyingSubscription(false); }}
        footer={null}
        width={520}
      >
        <div className="py-2">
          {/* 订阅列表 */}
          <div className="border border-gray-200 rounded overflow-hidden mb-4">
            {subscriptionListLoading ? (
              <div className="p-6 text-center"><Spin /></div>
            ) : subscriptionList.length > 0 ? (
              <div className="divide-y divide-gray-100">
                {subscriptionList.map((item) => (
                  <div key={item.consumerId} className="flex items-center px-4 py-3 hover:bg-gray-50">
                    <div className="flex-1 min-w-0 pr-4">
                      <span className="text-sm text-gray-700 truncate block">{item.consumerName}</span>
                    </div>
                    <div className="w-20 flex items-center pr-4">
                      {item.status === "APPROVED" ? (
                        <><CheckCircleFilled className="text-green-500 mr-1" style={{ fontSize: 10 }} /><span className="text-xs text-gray-700">已通过</span></>
                      ) : item.status === "PENDING" ? (
                        <><ClockCircleFilled className="text-blue-500 mr-1" style={{ fontSize: 10 }} /><span className="text-xs text-gray-700">审核中</span></>
                      ) : (
                        <><ExclamationCircleFilled className="text-red-500 mr-1" style={{ fontSize: 10 }} /><span className="text-xs text-gray-700">已拒绝</span></>
                      )}
                    </div>
                    <div className="w-20">
                      <Popconfirm title="确定要取消订阅吗？" onConfirm={() => handleUnsubscribeConsumer(item.consumerId)} okText="确认" cancelText="取消">
                        <Button type="link" danger size="small" className="p-0">取消订阅</Button>
                      </Popconfirm>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="p-6 text-center text-gray-400 text-sm">暂无订阅记录</div>
            )}
          </div>

          {/* 新增订阅 */}
          <div className="border-t pt-3">
            <div className="flex justify-end">
              {!isApplyingSubscription ? (
                <Button type="primary" icon={<PlusOutlined />} onClick={startApplyingSubscription}>
                  订阅
                </Button>
              ) : (
                <div className="w-full">
                  <div className="bg-gray-50 p-4 rounded-xl">
                    <div className="mb-3">
                      <label className="block text-sm font-medium text-gray-700 mb-2">选择消费者</label>
                      <Select
                        placeholder="搜索或选择消费者"
                        style={{ width: "100%" }}
                        value={selectedConsumerId || undefined}
                        onChange={setSelectedConsumerId}
                        showSearch
                        loading={consumersLoading}
                        filterOption={(input, option) =>
                          (option?.children as unknown as string)?.toLowerCase().includes(input.toLowerCase())
                        }
                        notFoundContent={consumersLoading ? "加载中..." : "暂无消费者"}
                      >
                        {consumers
                          .filter((c) => !subscriptionList.some((s) => s.consumerId === c.consumerId))
                          .map((c) => (
                            <Select.Option key={c.consumerId} value={c.consumerId}>
                              {c.name}
                            </Select.Option>
                          ))}
                      </Select>
                    </div>
                    <div className="flex justify-end gap-2">
                      <Button onClick={() => setIsApplyingSubscription(false)}>取消</Button>
                      <Button type="primary" loading={subscribing} disabled={!selectedConsumerId} onClick={handleSubscribe}>
                        确认申请
                      </Button>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </Modal>
    </Layout>
  );

  // JSON 语法高亮（浅色主题）
  function highlightJson(json: string) {
    // Sanitize: escape HTML entities to prevent XSS
    const escaped = json
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
    // 1. key 高亮：匹配 JSON key（冒号前的字符串）
    let result = escaped.replace(
      /(&quot;|")((?:\\.|[^"\\])*)(&quot;|")\s*:/g,
      '<span style="color:#6366f1">"$2"</span>:'
    );
    // 2. 字符串值高亮：仅匹配 key 后的 value 部分
    result = result.replace(
      /(<\/span>:\s*)(&quot;|")((?:\\.|[^"\\])*)(&quot;|")/g,
      '$1<span style="color:#059669">"$3"</span>'
    );
    // 3. 数字值高亮
    result = result.replace(
      /(<\/span>:\s*)(\d+)/g,
      '$1<span style="color:#d97706">$2</span>'
    );
    // 4. 布尔/null 值高亮
    result = result.replace(
      /(<\/span>:\s*)(true|false|null)/g,
      '$1<span style="color:#dc2626">$2</span>'
    );
    return result;
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

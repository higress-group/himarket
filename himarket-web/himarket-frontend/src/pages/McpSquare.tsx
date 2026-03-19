import { useState, useEffect, useRef, useCallback } from "react";
import {
  SearchOutlined, ToolOutlined, AppstoreOutlined,
  CloudServerOutlined, StarOutlined, PlusOutlined,
} from "@ant-design/icons";
import { Input, Spin, message, Button } from "antd";
import { useNavigate } from "react-router-dom";
import { Layout } from "../components/Layout";
import { CategoryMenu } from "../components/square/CategoryMenu";
import APIs, { type ICategory } from "../lib/apis";
import { getIconString } from "../lib/iconUtils";
import type { IProductDetail, IMcpMeta } from "../lib/apis/product";
import { getProductMcpMetaBatch } from "../lib/apis/product";
import { ProductIconRenderer } from "../components/icon/ProductIconRenderer";
import dayjs from "dayjs";
import BackToTopButton from "../components/scroll-to-top";

interface McpProductItem {
  product: IProductDetail;
  meta: IMcpMeta | null;
}

function McpSquare() {
  const navigate = useNavigate();
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const [activeTab, setActiveTab] = useState<"market" | "my">("market");

  const [activeCategory, setActiveCategory] = useState("all");
  const [searchQuery, setSearchQuery] = useState("");
  const [mcpItems, setMcpItems] = useState<McpProductItem[]>([]);
  const [categories, setCategories] = useState<Array<{ id: string; name: string; count: number }>>([]);
  const [loading, setLoading] = useState(false);
  const [currentPage, setCurrentPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const PAGE_SIZE = 30;

  const [myMcpItems, setMyMcpItems] = useState<McpProductItem[]>([]);
  const [myMcpsLoading, setMyMcpsLoading] = useState(false);
  const [subscribedProductIds, setSubscribedProductIds] = useState<Set<string>>(new Set());

  useEffect(() => {
    const fetchCategories = async () => {
      try {
        const response = await APIs.getCategoriesByProductType({ productType: "MCP_SERVER" });
        if (response.code === "SUCCESS" && response.data?.content) {
          const list = response.data.content.map((cat: ICategory) => ({
            id: cat.categoryId, name: cat.name, count: 0,
          }));
          setCategories(list.length > 0 ? [{ id: "all", name: "全部", count: 0 }, ...list] : []);
        }
      } catch (error) {
        console.error("Failed to fetch categories:", error);
      }
    };
    fetchCategories();
  }, []);

  const fetchMetaForProducts = async (products: IProductDetail[]): Promise<McpProductItem[]> => {
    if (products.length === 0) return [];
    const productIds = products.map(p => p.productId);
    try {
      const res = await getProductMcpMetaBatch(productIds);
      const metaList = res.code === "SUCCESS" ? (res.data || []) : [];
      // 按 productId 分组，取每个产品的第一条 meta
      const metaByProduct = new Map<string, IMcpMeta>();
      for (const meta of metaList) {
        if (!metaByProduct.has(meta.productId)) {
          metaByProduct.set(meta.productId, meta);
        }
      }
      return products.map(product => ({
        product,
        meta: metaByProduct.get(product.productId) || null,
      }));
    } catch {
      return products.map(product => ({ product, meta: null }));
    }
  };

  useEffect(() => {
    if (activeTab !== "market") return;
    const fetchProducts = async () => {
      setLoading(true);
      setMcpItems([]);
      setCurrentPage(0);
      setHasMore(true);
      try {
        const categoryIds = activeCategory === "all" ? undefined : [activeCategory];
        const response = await APIs.getProducts({ type: "MCP_SERVER", categoryIds, page: 0, size: PAGE_SIZE });
        if (response.code === "SUCCESS" && response.data?.content) {
          const prods = response.data.content;
          setHasMore(response.data.totalElements > prods.length);
          setMcpItems(await fetchMetaForProducts(prods));
        }
      } catch (error) {
        console.error("Failed to fetch products:", error);
        message.error("获取MCP Server列表失败");
      } finally {
        setLoading(false);
      }
    };
    fetchProducts();
  }, [activeCategory, activeTab]);

  // 获取我的 MCP（基于 product_subscription）
  const fetchMyMcps = useCallback(async () => {
    setMyMcpsLoading(true);
    try {
      const consumerRes = await APIs.getPrimaryConsumer();
      if (consumerRes.code !== "SUCCESS" || !consumerRes.data) return;
      const subRes = await APIs.getConsumerSubscriptions(consumerRes.data.consumerId, { size: 200 });
      if (subRes.code !== "SUCCESS" || !subRes.data?.content) return;
      const subs = subRes.data.content;
      const productIds = new Set(subs.map(s => s.productId));
      setSubscribedProductIds(productIds);
      // 批量获取产品详情（一次请求）
      const prodRes = await APIs.getProducts({ type: "MCP_SERVER", size: 200 });
      const allProducts = prodRes.code === "SUCCESS" ? (prodRes.data?.content || []) : [];
      const productMap = new Map<string, IProductDetail>();
      for (const p of allProducts) {
        productMap.set(p.productId, p);
      }
      // 构建已订阅的产品列表
      const subscribedProducts = subs
        .filter(s => productMap.has(s.productId))
        .map(s => productMap.get(s.productId)!)
        .filter(p => p.type === "MCP_SERVER");
      // 批量获取 meta（一次请求）
      const items = await fetchMetaForProducts(subscribedProducts);
      setMyMcpItems(items);
    } catch {
      // 未登录或获取失败
    } finally {
      setMyMcpsLoading(false);
    }
  }, []);

  // 获取订阅状态（基于 product_subscription）
  const fetchSubscribedProducts = useCallback(async () => {
    try {
      const consumerRes = await APIs.getPrimaryConsumer();
      if (consumerRes.code === "SUCCESS" && consumerRes.data) {
        const subRes = await APIs.getConsumerSubscriptions(consumerRes.data.consumerId, { size: 200 });
        if (subRes.code === "SUCCESS" && subRes.data?.content) {
          setSubscribedProductIds(new Set(subRes.data.content.map(s => s.productId)));
        }
      }
    } catch {
      // 未登录或获取失败不影响页面
    }
  }, []);

  // 页面初始化时获取订阅状态（用于广场"已订阅"标记）+ 我的MCP数据（用于数量显示）
  useEffect(() => {
    fetchSubscribedProducts();
    fetchMyMcps();
  }, [fetchSubscribedProducts, fetchMyMcps]);

  useEffect(() => {
    if (activeTab === "my") fetchMyMcps();
  }, [activeTab, fetchMyMcps]);

  const loadMoreProducts = useCallback(async () => {
    if (loadingMore || !hasMore) return;
    setLoadingMore(true);
    try {
      const categoryIds = activeCategory === "all" ? undefined : [activeCategory];
      const nextPage = currentPage + 1;
      const response = await APIs.getProducts({ type: "MCP_SERVER", categoryIds, page: nextPage, size: PAGE_SIZE });
      if (response.code === "SUCCESS" && response.data?.content) {
        const newItems = await fetchMetaForProducts(response.data.content);
        setMcpItems(prev => [...prev, ...newItems]);
        setCurrentPage(nextPage);
        setHasMore(response.data.totalElements > mcpItems.length + response.data.content.length);
      }
    } catch (error) {
      console.error("Failed to load more:", error);
    } finally {
      setLoadingMore(false);
    }
  }, [activeCategory, currentPage, hasMore, loadingMore, mcpItems]);

  useEffect(() => {
    const el = scrollContainerRef.current;
    if (!el) return;
    const handleScroll = () => {
      if (el.scrollHeight - el.scrollTop - el.clientHeight < 200) loadMoreProducts();
    };
    el.addEventListener("scroll", handleScroll);
    return () => el.removeEventListener("scroll", handleScroll);
  }, [loadMoreProducts]);

  const filteredItems = mcpItems.filter((item) => {
    if (!searchQuery) return true;
    const q = searchQuery.toLowerCase();
    const name = item.meta?.displayName || item.meta?.mcpName || item.product.name;
    const desc = item.meta?.description || item.product.description;
    return name.toLowerCase().includes(q) || desc?.toLowerCase().includes(q);
  });

  const handleSubscribe = async (productId: string) => {
    try {
      const consumerRes = await APIs.getPrimaryConsumer();
      if (consumerRes.code !== "SUCCESS" || !consumerRes.data) {
        message.error("获取消费者信息失败");
        return;
      }
      await APIs.subscribeProduct(consumerRes.data.consumerId, productId);
      message.success("订阅成功");
      fetchSubscribedProducts();
      fetchMyMcps();
    } catch (error: any) {
      message.error(error?.response?.data?.message || error?.message || "订阅失败");
    }
  };

  const handleUnsubscribe = async (productId: string) => {
    try {
      const consumerRes = await APIs.getPrimaryConsumer();
      if (consumerRes.code !== "SUCCESS" || !consumerRes.data) {
        message.error("获取消费者信息失败");
        return;
      }
      await APIs.unsubscribeProduct(consumerRes.data.consumerId, productId);
      message.success("已取消订阅");
      fetchSubscribedProducts();
      fetchMyMcps();
    } catch (error: any) {
      message.error(error?.response?.data?.message || error?.message || "取消订阅失败");
    }
  };

  return (
    <Layout>
      <div className="flex h-[calc(100vh-96px)]">
        {/* 左侧分类菜单 - 仅广场 tab 且有分类时显示 */}
        {activeTab === "market" && categories.length > 0 && (
          <CategoryMenu
            categories={categories}
            activeCategory={activeCategory}
            onSelectCategory={setActiveCategory}
          />
        )}

        {/* 右侧内容区域 */}
        <div className="flex-1 flex flex-col relative">
          {/* 顶部：Tab 切换 + 搜索框 */}
          <div className="flex items-center justify-between mb-2 pl-4">
            <div className="flex items-center gap-1 bg-gray-100/80 rounded-xl p-1">
              <button
                onClick={() => setActiveTab("market")}
                className={`px-4 py-1.5 rounded-lg text-sm font-medium transition-all duration-200 ${
                  activeTab === "market"
                    ? "bg-white text-gray-900 shadow-sm"
                    : "text-gray-500 hover:text-gray-700"
                }`}
              >
                <AppstoreOutlined className="mr-1.5" />MCP 广场
              </button>
              <button
                onClick={() => setActiveTab("my")}
                className={`px-4 py-1.5 rounded-lg text-sm font-medium transition-all duration-200 flex items-center gap-1.5 ${
                  activeTab === "my"
                    ? "bg-white text-gray-900 shadow-sm"
                    : "text-gray-500 hover:text-gray-700"
                }`}
              >
                <StarOutlined className="mr-0.5" />我的 MCP
                {myMcpItems.length > 0 && (
                  <span className="text-[10px] font-medium px-1.5 py-0.5 rounded-full bg-colorPrimary/10 text-colorPrimary">
                    {myMcpItems.length}
                  </span>
                )}
              </button>
            </div>
            <div className="flex items-center gap-3">
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => navigate("/mcp/create")}
                className="rounded-xl"
              >
                创建 MCP
              </Button>
              <Input
              placeholder="搜索 MCP Server..."
              prefix={<SearchOutlined className="text-gray-400" />}
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-80 rounded-xl"
              style={{
                backgroundColor: "rgba(255, 255, 255, 0.6)",
                backdropFilter: "blur(10px)",
              }}
              allowClear
            />
            </div>
          </div>

          {/* 内容区域 */}
          <div className="flex-1 relative overflow-auto" ref={scrollContainerRef}>
            <div className="h-full p-4">
              {activeTab === "market" ? (
                <MarketContent
                  loading={loading}
                  loadingMore={loadingMore}
                  items={filteredItems}
                  subscribedProductIds={subscribedProductIds}
                  onViewDetail={(pid) => navigate(`/mcp/${pid}`)}
                  onSubscribe={handleSubscribe}
                  onUnsubscribe={handleUnsubscribe}
                />
              ) : (
                <MyMcpContent
                  items={myMcpItems}
                  loading={myMcpsLoading}
                  onDisconnect={handleUnsubscribe}
                  onViewDetail={(pid) => navigate(`/mcp/${pid}`)}
                />
              )}
            </div>
          </div>
        </div>
      </div>
      <BackToTopButton container={scrollContainerRef.current!} />
    </Layout>
  );
}

/* ==================== 广场内容 ==================== */
function MarketContent({ loading, loadingMore, items, subscribedProductIds, onViewDetail, onSubscribe, onUnsubscribe }: {
  loading: boolean;
  loadingMore: boolean;
  items: McpProductItem[];
  subscribedProductIds: Set<string>;
  onViewDetail: (productId: string) => void;
  onSubscribe: (productId: string) => Promise<void>;
  onUnsubscribe: (productId: string) => Promise<void>;
}) {
  if (loading) {
    return <div className="flex items-center justify-center h-full"><Spin size="large" tip="加载中..." /></div>;
  }

  if (items.length === 0) {
    return <div className="col-span-full flex items-center justify-center py-20 text-gray-400">暂无数据</div>;
  }

  return (
    <>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {items.map((item) => (
          <McpCard
            key={item.product.productId}
            item={item}
            subscribed={subscribedProductIds.has(item.product.productId)}
            onViewDetail={() => onViewDetail(item.product.productId)}
            onSubscribe={() => onSubscribe(item.product.productId)}
            onUnsubscribe={() => onUnsubscribe(item.product.productId)}
          />
        ))}
      </div>
      {loadingMore && (
        <div className="flex items-center justify-center py-8"><Spin tip="加载更多..." /></div>
      )}
    </>
  );
}

/* ==================== MCP 卡片（匹配 ModelCard 风格） ==================== */
function McpCard({ item, subscribed, onViewDetail, onSubscribe, onUnsubscribe }: {
  item: McpProductItem;
  subscribed: boolean;
  onViewDetail: () => void;
  onSubscribe: () => Promise<void>;
  onUnsubscribe: () => Promise<void>;
}) {
  const { product, meta } = item;
  const displayName = meta?.displayName || meta?.mcpName || product.name;
  const description = meta?.description || product.description;
  const [actionLoading, setActionLoading] = useState(false);

  // 收集所有可用的连接类型（去重、统一大写）
  const allProtocols = (() => {
    const set = new Set<string>();
    // 1. meta.protocolType（MCP 元信息上的协议）
    if (meta?.protocolType) {
      meta.protocolType.split(",").map(p => p.trim().toUpperCase()).filter(Boolean).forEach(p => set.add(p));
    }
    // 2. 冷数据：product.mcpConfig.meta.protocol
    const coldProto = product.mcpConfig?.meta?.protocol;
    if (coldProto) {
      coldProto.split(",").map((p: string) => p.trim().toUpperCase()).filter(Boolean).forEach((p: string) => set.add(p));
    }
    // 3. 热数据：endpoint 协议
    if (meta?.endpointProtocol) {
      meta.endpointProtocol.split(",").map(p => p.trim().toUpperCase()).filter(Boolean).forEach(p => set.add(p));
    }
    // 4. rawConfig 存在说明支持 Stdio
    if (product.mcpConfig?.mcpServerConfig?.rawConfig && Object.keys(product.mcpConfig.mcpServerConfig.rawConfig).length > 0) {
      set.add("STDIO");
    }
    return Array.from(set);
  })();

  const toolCount = (() => {
    const src = meta?.toolsConfig || product.mcpConfig?.tools;
    if (!src) return 0;
    try {
      const parsed = typeof src === "string" ? JSON.parse(src) : src;
      if (Array.isArray(parsed)) return parsed.length;
      return parsed?.tools?.length || 0;
    } catch { return 0; }
  })();

  const tagList: string[] = (() => {
    if (!meta?.tags) return [];
    try {
      const parsed = JSON.parse(meta.tags);
      return Array.isArray(parsed) ? parsed : meta.tags.split(",").map((t: string) => t.trim()).filter(Boolean);
    } catch {
      return meta.tags.split(",").map((t: string) => t.trim()).filter(Boolean);
    }
  })();

  const handleSubscribeClick = async (e: React.MouseEvent) => {
    e.stopPropagation();
    setActionLoading(true);
    try { await onSubscribe(); } finally { setActionLoading(false); }
  };

  const handleUnsubscribeClick = async (e: React.MouseEvent) => {
    e.stopPropagation();
    setActionLoading(true);
    try { await onUnsubscribe(); } finally { setActionLoading(false); }
  };

  return (
    <div
      onClick={onViewDetail}
      className="
        bg-white/60 backdrop-blur-sm rounded-2xl p-5
        border border-white/40
        cursor-pointer
        transition-all duration-300 ease-in-out
        hover:bg-white hover:shadow-md hover:scale-[1.02] hover:border-colorPrimary/30
        active:scale-[0.98]
        relative overflow-hidden group
        h-[200px] flex flex-col
      "
    >
      {/* 已订阅角标 */}
      {subscribed && (
        <div className="absolute top-3 right-3 z-10">
          <span className="text-[10px] font-medium px-1.5 py-0.5 rounded-full bg-green-50 text-green-600 border border-green-100">
            已订阅
          </span>
        </div>
      )}
      {/* 头部：icon + 名称 + 标签 */}
      <div className="flex items-center gap-3 mb-3">
        <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-colorPrimary/10 to-colorPrimary/5 flex items-center justify-center flex-shrink-0 overflow-hidden">
          {meta?.icon || product.icon ? (
            <ProductIconRenderer className="w-full h-full object-cover" iconType={getIconString(meta?.icon ? { type: "URL", value: meta.icon } : product.icon)} />
          ) : (
            <AppstoreOutlined className="text-colorPrimary text-lg" />
          )}
        </div>
        <div className="flex-1 min-w-0">
          <h3 className="text-base font-semibold text-gray-900 truncate">{displayName}</h3>
          {meta?.mcpName && (
            <div className="text-[10px] text-gray-400 font-mono truncate mt-0.5">{meta.mcpName}</div>
          )}
          <div className="flex items-center gap-1.5 mt-1 flex-wrap">
            {allProtocols.map(p => (
              <span key={p} className="text-[10px] font-medium px-1.5 py-0.5 rounded bg-colorPrimary/10 text-colorPrimary">
                {p}
              </span>
            ))}
            {toolCount > 0 && (
              <span className="text-[10px] font-medium px-1.5 py-0.5 rounded bg-colorPrimary/5 text-colorPrimary/80">
                <ToolOutlined className="mr-0.5" />{toolCount}
              </span>
            )}
          </div>
        </div>
      </div>

      {/* 描述 */}
      <p className="max-h-12 text-sm mb-4 line-clamp-2 leading-relaxed flex-1 text-[#a3a3a3]">
        {description || "暂无描述"}
      </p>

      {/* 底部：标签 + 日期 - hover 时淡出 */}
      <div className="h-10 flex items-center justify-between text-xs transition-opacity duration-300 group-hover:opacity-0">
        <div className="flex items-center gap-1.5 flex-wrap flex-1 min-w-0">
          {tagList.slice(0, 2).map((t) => (
            <span key={t} className="text-[10px] px-2 py-0.5 rounded-full bg-gray-50 text-gray-500 border border-gray-100 truncate max-w-[80px]">
              {t}
            </span>
          ))}
          {!tagList.length && product.categories?.[0]?.name && (
            <span className="text-[10px] px-2 py-0.5 rounded-full bg-gray-50 text-gray-500 border border-gray-100">
              {product.categories[0].name}
            </span>
          )}
        </div>
        <span className="flex-shrink-0 text-[#a3a3a3]">{dayjs(product.createAt).format("YYYY-MM-DD")}</span>
      </div>

      {/* Hover 操作按钮 */}
      <div className="
        absolute bottom-0 left-0 right-0 p-5
        opacity-0 translate-y-2
        group-hover:opacity-100 group-hover:translate-y-0
        transition-all duration-300 ease-out
        pointer-events-none group-hover:pointer-events-auto
      ">
        <div className="flex gap-3">
          <button
            onClick={(e) => { e.stopPropagation(); onViewDetail(); }}
            className="flex-1 px-4 py-2.5 rounded-xl border border-gray-300 text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 hover:border-gray-400 transition-all duration-200 shadow-sm"
          >
            查看详情
          </button>
          {subscribed ? (
            <button
              disabled={actionLoading}
              onClick={handleUnsubscribeClick}
              className="flex-1 px-4 py-2.5 rounded-xl text-sm font-medium text-red-600 bg-white border border-red-300 hover:bg-red-50 hover:border-red-400 transition-all duration-200 shadow-sm disabled:opacity-50"
            >
              {actionLoading ? "处理中..." : "取消订阅"}
            </button>
          ) : (
            <button
              disabled={actionLoading}
              onClick={handleSubscribeClick}
              className="flex-1 px-4 py-2.5 rounded-xl text-sm font-medium text-white bg-colorPrimary hover:opacity-90 transition-all duration-200 shadow-sm disabled:opacity-50"
            >
              {actionLoading ? "处理中..." : "立即订阅"}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

/* ==================== 我的 MCP 内容 ==================== */
function MyMcpContent({ items, loading, onDisconnect, onViewDetail }: {
  items: McpProductItem[];
  loading: boolean;
  onDisconnect: (productId: string) => void;
  onViewDetail: (productId: string) => void;
}) {
  if (loading) {
    return <div className="flex items-center justify-center h-full"><Spin size="large" tip="加载中..." /></div>;
  }

  if (items.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-24 text-gray-400">
        <CloudServerOutlined className="text-5xl mb-4 text-gray-300" />
        <span className="text-sm">暂无已订阅的 MCP</span>
        <span className="text-xs mt-1 text-gray-300">去 MCP 广场浏览并订阅</span>
      </div>
    );
  }

  return (
    <>
      <div className="text-xs text-gray-400 mb-3">共 {items.length} 个已订阅</div>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {items.map((item) => (
          <MyMcpCard
            key={item.product.productId}
            item={item}
            onDisconnect={() => onDisconnect(item.product.productId)}
            onViewDetail={() => onViewDetail(item.product.productId)}
          />
        ))}
      </div>
    </>
  );
}

/* ==================== 我的 MCP 卡片 ==================== */
function MyMcpCard({ item, onDisconnect, onViewDetail }: {
  item: McpProductItem;
  onDisconnect: () => void;
  onViewDetail: () => void;
}) {
  const { product, meta } = item;
  const displayName = meta?.displayName || meta?.mcpName || product.name;
  const description = meta?.description || product.description;

  // 收集所有可用的连接类型（去重、统一大写）
  const allProtocols = (() => {
    const set = new Set<string>();
    if (meta?.protocolType) {
      meta.protocolType.split(",").map(p => p.trim().toUpperCase()).filter(Boolean).forEach(p => set.add(p));
    }
    const coldProto = product.mcpConfig?.meta?.protocol;
    if (coldProto) {
      coldProto.split(",").map((p: string) => p.trim().toUpperCase()).filter(Boolean).forEach((p: string) => set.add(p));
    }
    if (meta?.endpointProtocol) {
      meta.endpointProtocol.split(",").map(p => p.trim().toUpperCase()).filter(Boolean).forEach(p => set.add(p));
    }
    if (product.mcpConfig?.mcpServerConfig?.rawConfig && Object.keys(product.mcpConfig.mcpServerConfig.rawConfig).length > 0) {
      set.add("STDIO");
    }
    return Array.from(set);
  })();

  const toolCount = (() => {
    const src = meta?.toolsConfig || product.mcpConfig?.tools;
    if (!src) return 0;
    try {
      const parsed = typeof src === "string" ? JSON.parse(src) : src;
      if (Array.isArray(parsed)) return parsed.length;
      return parsed?.tools?.length || 0;
    } catch { return 0; }
  })();

  return (
    <div
      onClick={onViewDetail}
      className="
        bg-white/60 backdrop-blur-sm rounded-2xl p-5
        border border-white/40
        cursor-pointer
        transition-all duration-300 ease-in-out
        hover:bg-white hover:shadow-md hover:scale-[1.02] hover:border-colorPrimary/30
        active:scale-[0.98]
        relative overflow-hidden group
        h-[200px] flex flex-col
      "
    >
      {/* 头部：icon + 名称 + 标签 */}
      <div className="flex items-center gap-3 mb-3">
        <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-colorPrimary/10 to-colorPrimary/5 flex items-center justify-center flex-shrink-0 overflow-hidden">
          {meta?.icon || product.icon ? (
            <ProductIconRenderer className="w-full h-full object-cover" iconType={getIconString(meta?.icon ? { type: "URL", value: meta.icon } : product.icon)} />
          ) : (
            <AppstoreOutlined className="text-colorPrimary text-lg" />
          )}
        </div>
        <div className="flex-1 min-w-0">
          <h3 className="text-base font-semibold text-gray-900 truncate">{displayName}</h3>
          {meta?.mcpName && (
            <div className="text-[10px] text-gray-400 font-mono truncate mt-0.5">{meta.mcpName}</div>
          )}
          <div className="flex items-center gap-1.5 mt-1 flex-wrap">
            {allProtocols.map(p => (
              <span key={p} className="text-[10px] font-medium px-1.5 py-0.5 rounded bg-colorPrimary/10 text-colorPrimary">
                {p}
              </span>
            ))}
            {toolCount > 0 && (
              <span className="text-[10px] font-medium px-1.5 py-0.5 rounded bg-colorPrimary/5 text-colorPrimary/80">
                <ToolOutlined className="mr-0.5" />{toolCount}
              </span>
            )}
          </div>
        </div>
      </div>

      {/* 描述 */}
      <p className="max-h-12 text-sm mb-4 line-clamp-2 leading-relaxed flex-1 text-[#a3a3a3]">
        {description || "暂无描述"}
      </p>

      {/* 底部 - hover 时淡出 */}
      <div className="h-10 flex items-center justify-between text-xs transition-opacity duration-300 group-hover:opacity-0">
        <span className="text-[10px] font-medium px-1.5 py-0.5 rounded-full bg-green-50 text-green-600 border border-green-100">
          已订阅
        </span>
        <span className="flex-shrink-0 text-[#a3a3a3]">{dayjs(product.createAt).format("YYYY-MM-DD")}</span>
      </div>

      {/* Hover 操作按钮 */}
      <div className="
        absolute bottom-0 left-0 right-0 p-5
        opacity-0 translate-y-2
        group-hover:opacity-100 group-hover:translate-y-0
        transition-all duration-300 ease-out
        pointer-events-none group-hover:pointer-events-auto
      ">
        <div className="flex gap-3">
          <button
            onClick={(e) => { e.stopPropagation(); onViewDetail(); }}
            className="flex-1 px-4 py-2.5 rounded-xl border border-gray-300 text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 hover:border-gray-400 transition-all duration-200 shadow-sm"
          >
            查看详情
          </button>
          <button
            onClick={(e) => { e.stopPropagation(); onDisconnect(); }}
            className="flex-1 px-4 py-2.5 rounded-xl text-sm font-medium text-red-600 bg-white border border-red-300 hover:bg-red-50 hover:border-red-400 transition-all duration-200 shadow-sm"
          >
            取消订阅
          </button>
        </div>
      </div>
    </div>
  );
}

export default McpSquare;

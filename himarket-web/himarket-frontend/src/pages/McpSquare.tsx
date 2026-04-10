import { useState, useEffect, useRef, useCallback } from "react";
import {
  SearchOutlined, ToolOutlined, AppstoreOutlined,
  CloudServerOutlined, StarOutlined, PlusOutlined,
} from "@ant-design/icons";
import { Input, Spin, message, Pagination } from "antd";
import { useNavigate } from "react-router-dom";
import { Layout } from "../components/Layout";
import { CategoryMenu } from "../components/square/CategoryMenu";
import { EmptyState } from "../components/EmptyState";
import { useAuth } from "../hooks/useAuth";
import { useDebounce } from "../hooks/useDebounce";
import APIs, { type ICategory } from "../lib/apis";
import { getIconString, parseMetaIcon } from "../lib/iconUtils";
import type { IProductDetail, IMcpMeta } from "../lib/apis/product";
import { getProductMcpMetaBatch, getProductMcpMetaBatchPublic } from "../lib/apis/product";
import { ProductIconRenderer } from "../components/icon/ProductIconRenderer";
import dayjs from "dayjs";
import BackToTopButton from "../components/scroll-to-top";
import { CardGridSkeleton } from "../components/loading";

interface McpProductItem {
  product: IProductDetail;
  meta: IMcpMeta | null;
}

function McpSquare() {
  const navigate = useNavigate();
  const { isLoggedIn } = useAuth();
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const sentinelRef = useRef<HTMLDivElement>(null);
  const [activeTab, _setActiveTab] = useState<"market" | "my">("market");
  const [isStuck, setIsStuck] = useState(false);

  const [activeCategory, setActiveCategory] = useState("all");
  const [searchQuery, setSearchQuery] = useState("");
  const [committedSearch, setCommittedSearch] = useState("");
  const [mcpItems, setMcpItems] = useState<McpProductItem[]>([]);
  const [categories, setCategories] = useState<Array<{ id: string; name: string; count: number }>>([]);
  const [loading, setLoading] = useState(false);
  const [currentPage, setCurrentPage] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const PAGE_SIZE = 12;

  const [myMcpItems, setMyMcpItems] = useState<McpProductItem[]>([]);
  const [myMcpsLoading, setMyMcpsLoading] = useState(false);
  const [subscribedProductIds, setSubscribedProductIds] = useState<Set<string>>(new Set());

  // IntersectionObserver 检测 sticky 状态
  useEffect(() => {
    const sentinel = sentinelRef.current;
    if (!sentinel) return;
    const observer = new IntersectionObserver(
      ([entry]) => setIsStuck(!entry.isIntersecting),
      { threshold: 0 }
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, []);

  // Debounce 自动搜索：输入停顿 300ms 后自动触发
  useDebounce(searchQuery, 300, (debouncedValue) => {
    setCommittedSearch(debouncedValue);
    setCurrentPage(1);
  });

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
      const fetchFn = isLoggedIn ? getProductMcpMetaBatch : getProductMcpMetaBatchPublic;
      const res = await fetchFn(productIds);
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
      try {
        const categoryIds = activeCategory === "all" ? undefined : [activeCategory];
        const response = await APIs.getProducts({ type: "MCP_SERVER", categoryIds, name: committedSearch || undefined, page: currentPage - 1, size: PAGE_SIZE });
        if (response.code === "SUCCESS" && response.data?.content) {
          setTotalElements(response.data.totalElements);
          setMcpItems(await fetchMetaForProducts(response.data.content));
        }
      } catch (error) {
        console.error("Failed to fetch products:", error);
        message.error("获取MCP Server列表失败");
      } finally {
        setLoading(false);
      }
    };
    fetchProducts();
  }, [activeCategory, activeTab, committedSearch, isLoggedIn, currentPage]);

  // Unified consumer data loader: fetches subscriptions once and derives both
  // subscribedProductIds (for market tab badges) and myMcpItems (for "My MCP" tab)
  const consumerIdRef = useRef<string | null>(null);

  const fetchConsumerData = useCallback(async () => {
    setMyMcpsLoading(true);
    try {
      const consumerRes = await APIs.getPrimaryConsumer();
      if (consumerRes.code !== "SUCCESS" || !consumerRes.data) {
        setSubscribedProductIds(new Set());
        setMyMcpItems([]);
        return;
      }
      consumerIdRef.current = consumerRes.data.consumerId;

      const subRes = await APIs.getConsumerSubscriptions(consumerRes.data.consumerId, { size: 200 });
      if (subRes.code !== "SUCCESS" || !subRes.data?.content) {
        setSubscribedProductIds(new Set());
        setMyMcpItems([]);
        return;
      }

      const approvedSubs = subRes.data.content.filter(s => s.status === 'APPROVED');
      setSubscribedProductIds(new Set(approvedSubs.map(s => s.productId)));

      // Build "My MCP" items from MCP_SERVER subscriptions
      const mcpSubs = approvedSubs.filter(s => s.productType === 'MCP_SERVER');
      const productIds = mcpSubs.map(s => s.productId);

      if (productIds.length === 0) {
        setMyMcpItems([]);
        return;
      }

      const fetchFn = isLoggedIn ? getProductMcpMetaBatch : getProductMcpMetaBatchPublic;
      const metaRes = await fetchFn(productIds);
      const metaList = metaRes.code === "SUCCESS" ? (metaRes.data || []) : [];
      const metaByProduct = new Map<string, IMcpMeta>();
      for (const meta of metaList) {
        if (!metaByProduct.has(meta.productId)) {
          metaByProduct.set(meta.productId, meta);
        }
      }

      const items: McpProductItem[] = mcpSubs.map(sub => {
        const meta = metaByProduct.get(sub.productId);
        const product: IProductDetail = {
          productId: sub.productId,
          name: sub.productName || meta?.displayName || meta?.mcpName || "",
          description: meta?.description || "",
          type: "MCP_SERVER",
          icon: meta?.icon ? (() => { try { return JSON.parse(meta.icon); } catch { return undefined; } })() : undefined,
          createAt: sub.createAt || "",
        } as IProductDetail;
        return { product, meta: meta || null };
      });
      setMyMcpItems(items);
    } catch {
      // Not logged in or request failed
    } finally {
      setMyMcpsLoading(false);
    }
  }, [isLoggedIn]);

  // Load consumer data once on login
  useEffect(() => {
    if (!isLoggedIn) return;
    fetchConsumerData();
  }, [fetchConsumerData, isLoggedIn]);

  // Refresh when switching to "my" tab
  useEffect(() => {
    if (activeTab === "my" && isLoggedIn) fetchConsumerData();
  }, [activeTab, fetchConsumerData, isLoggedIn]);

  const handlePageChange = (page: number) => {
    setCurrentPage(page);
  };

  const handleCategoryChange = (categoryId: string) => {
    setActiveCategory(categoryId);
    setCurrentPage(1);
  };

  const handleSearchCommit = () => {
    setCommittedSearch(searchQuery);
    setCurrentPage(1);
  };

  return (
    <Layout>
      <div className="flex flex-col h-[calc(100vh-96px)] overflow-auto scrollbar-hide" ref={scrollContainerRef}>
        {/* IntersectionObserver 哨兵元素 */}
        <div ref={sentinelRef} className="h-0 flex-shrink-0" />

        {/* 搜索区域 - sticky */}
        <div className={`sticky top-0 z-50 backdrop-blur-md transition-shadow duration-200 flex-shrink-0 ${isStuck ? 'shadow-sm bg-white/80' : ''}`}>
          <div className="flex flex-col gap-4 px-6 py-4">
            {/* 第一行：统计信息 */}
            <div className="flex items-center justify-center gap-4 text-sm text-gray-600">
              <div className="flex items-center gap-1.5">
                <div className="w-2 h-2 rounded-full bg-green-500"></div>
                <span className="font-medium">{totalElements.toLocaleString()}</span>
                <span>MCP Servers</span>
              </div>
            </div>

            {/* 第二行：创建按钮 + 搜索框居中，我的MCP贴最右 */}
            <div className="relative flex items-center justify-center gap-3">
              {isLoggedIn && (
                <button
                  onClick={() => navigate("/mcp/create")}
                  className="group flex items-center gap-0 h-10 px-3 rounded-xl bg-black text-white hover:shadow-md transition-all duration-300 flex-shrink-0 overflow-hidden"
                >
                  <PlusOutlined className="text-base" />
                  <span className="max-w-0 overflow-hidden group-hover:max-w-[60px] transition-all duration-300 whitespace-nowrap group-hover:ml-1.5">
                    创建
                  </span>
                </button>
              )}
              <div className="w-full max-w-xl">
                <Input
                  placeholder="搜索 MCP Server..."
                  value={searchQuery}
                  onChange={(e) => {
                    setSearchQuery(e.target.value);
                    if (!e.target.value) setCommittedSearch("");
                  }}
                  onPressEnter={handleSearchCommit}
                  size="large"
                  suffix={
                    <button
                      onClick={handleSearchCommit}
                      className="bg-black hover:bg-gray-800 text-white rounded-lg p-2 transition-colors"
                      type="button"
                    >
                      <SearchOutlined className="text-lg" />
                    </button>
                  }
                  className="rounded-xl text-base"
                  style={{
                    backgroundColor: "rgba(255, 255, 255, 0.95)",
                    boxShadow: "0 2px 8px rgba(0, 0, 0, 0.08)",
                  }}
                  allowClear
                />
              </div>
              {isLoggedIn && (
                <button
                  onClick={() => navigate("/mcp/my")}
                  className="absolute right-0 flex items-center gap-1.5 h-10 px-4 rounded-xl border border-gray-200 bg-white text-gray-700 text-sm font-medium transition-all duration-200 flex-shrink-0 hover:bg-black hover:text-white hover:border-black hover:shadow-md"
                >
                  <StarOutlined className="text-sm" />
                  我的 MCP
                  {myMcpItems.length > 0 && (
                    <span className="text-[10px] bg-gray-100 text-gray-600 px-1.5 py-0.5 rounded-full leading-none font-semibold">
                      {myMcpItems.length}
                    </span>
                  )}
                </button>
              )}
            </div>

            {/* 第三行：分类标签 - 仅广场 tab 显示 */}
            {activeTab === "market" && categories.length > 0 && (
              <div className="flex-1 min-w-0">
                <CategoryMenu
                  categories={categories}
                  activeCategory={activeCategory}
                  onSelectCategory={handleCategoryChange}
                />
              </div>
            )}
          </div>
        </div>

        {/* 内容区域 */}
        <div className="flex-1 px-4 pt-2 pb-4">
          <div className="pb-4">
            {activeTab === "market" ? (
              <MarketContent
                loading={loading}
                items={mcpItems}
                subscribedProductIds={subscribedProductIds}
                isLoggedIn={isLoggedIn}
                onViewDetail={(pid) => navigate(`/mcp/${pid}`)}
                currentPage={currentPage}
                totalElements={totalElements}
                pageSize={PAGE_SIZE}
                onPageChange={handlePageChange}
              />
            ) : (
              <MyMcpContent
                items={myMcpItems}
                loading={myMcpsLoading}
                onViewDetail={(pid) => navigate(`/mcp/${pid}`)}
              />
            )}
          </div>
        </div>
      </div>
      <BackToTopButton container={scrollContainerRef.current!} />
    </Layout>
  );
}

/* ==================== 广场内容 ==================== */
function MarketContent({ loading, items, subscribedProductIds, isLoggedIn, onViewDetail, currentPage, totalElements, pageSize, onPageChange }: {
  loading: boolean;
  items: McpProductItem[];
  subscribedProductIds: Set<string>;
  isLoggedIn: boolean;
  onViewDetail: (productId: string) => void;
  currentPage: number;
  totalElements: number;
  pageSize: number;
  onPageChange: (page: number) => void;
}) {
  if (loading) {
    return <CardGridSkeleton count={8} columns={{ sm: 1, md: 2, lg: 4 }} />;
  }

  if (items.length === 0) {
    return <EmptyState productType="MCP_SERVER" />;
  }

  return (
    <>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
        {items.map((item) => (
          <McpCard
            key={item.product.productId}
            item={item}
            subscribed={subscribedProductIds.has(item.product.productId)}
            isLoggedIn={isLoggedIn}
            onViewDetail={() => onViewDetail(item.product.productId)}
          />
        ))}
      </div>
      {totalElements > pageSize && (
        <div className="flex justify-center mt-8 mb-4">
          <Pagination
            current={currentPage}
            pageSize={pageSize}
            total={totalElements}
            onChange={onPageChange}
            showSizeChanger={false}
            showQuickJumper
          />
        </div>
      )}
    </>
  );
}

/* ==================== MCP 卡片（匹配 ModelCard 风格） ==================== */
function McpCard({ item, subscribed, isLoggedIn, onViewDetail }: {
  item: McpProductItem;
  subscribed: boolean;
  isLoggedIn: boolean;
  onViewDetail: () => void;
}) {
  const { product, meta } = item;
  const displayName = meta?.displayName || meta?.mcpName || product.name;
  const description = meta?.description || product.description;

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

  return (
    <div
      onClick={onViewDetail}
      className="
        bg-white/60 backdrop-blur-sm rounded-xl p-4
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
      {isLoggedIn && subscribed && (
        <div className="absolute top-3 right-3 z-10">
          <span className="text-[10px] font-medium px-1.5 py-0.5 rounded-full bg-green-50 text-green-600 border border-green-100">
            已订阅
          </span>
        </div>
      )}
      {/* 头部：icon + 名称 + 标签 */}
      <div className="flex items-center gap-3 mb-3">
        <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-colorPrimary/10 to-colorPrimary/5 flex items-center justify-center flex-shrink-0 overflow-hidden">
          {meta?.icon || product.icon ? (
            <ProductIconRenderer className="w-full h-full object-cover" iconType={getIconString(parseMetaIcon(meta?.icon) || product.icon)} />
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
      <p className="text-sm mb-2 line-clamp-2 leading-relaxed flex-1 text-[#a3a3a3]">
        {description || "暂无描述"}
      </p>

      {/* 底部：标签 + 日期 */}
      <div className="h-10 flex items-center justify-between text-xs transition-opacity duration-300">
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
    </div>
  );
}

/* ==================== 我的 MCP 内容 ==================== */
function MyMcpContent({ items, loading, onViewDetail }: {
  items: McpProductItem[];
  loading: boolean;
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
            onViewDetail={() => onViewDetail(item.product.productId)}
          />
        ))}
      </div>
    </>
  );
}

/* ==================== 我的 MCP 卡片 ==================== */
function MyMcpCard({ item, onViewDetail }: {
  item: McpProductItem;
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
        bg-white/60 backdrop-blur-sm rounded-xl p-4
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
        <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-colorPrimary/10 to-colorPrimary/5 flex items-center justify-center flex-shrink-0 overflow-hidden">
          {meta?.icon || product.icon ? (
            <ProductIconRenderer className="w-full h-full object-cover" iconType={getIconString(parseMetaIcon(meta?.icon) || product.icon)} />
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
      <p className="text-sm mb-2 line-clamp-2 leading-relaxed flex-1 text-[#a3a3a3]">
        {description || "暂无描述"}
      </p>

      {/* 底部 */}
      <div className="h-10 flex items-center justify-between text-xs transition-opacity duration-300">
        <span className="text-[10px] font-medium px-1.5 py-0.5 rounded-full bg-green-50 text-green-600 border border-green-100">
          已订阅
        </span>
        <span className="flex-shrink-0 text-[#a3a3a3]">{dayjs(product.createAt).format("YYYY-MM-DD")}</span>
      </div>
    </div>
  );
}

export default McpSquare;

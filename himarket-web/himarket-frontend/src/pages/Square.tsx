import { useState, useEffect, useRef, useCallback, useLayoutEffect } from "react";
import { SearchOutlined, DownloadOutlined, ClockCircleOutlined } from "@ant-design/icons";
import { Input, message, Pagination, Segmented } from "antd";
import { useNavigate } from "react-router-dom";
import { Layout } from "../components/Layout";
import { CategoryMenu } from "../components/square/CategoryMenu";
import { ModelCard } from "../components/square/ModelCard";
import { SkillCard } from "../components/square/SkillCard";
import { EmptyState } from "../components/EmptyState";
import { LoginPrompt } from "../components/LoginPrompt";
import { useAuth } from "../hooks/useAuth";
import { WorkerCard } from "../components/square/WorkerCard";
import APIs, { type ICategory } from "../lib/apis";
import { getIconString } from "../lib/iconUtils";
import type { IProductDetail } from "../lib/apis/product";
import dayjs from "dayjs";
import BackToTopButton from "../components/scroll-to-top";
import { CardGridSkeleton } from "../components/loading";

// 滚动阈值：当滚动超过此值时显示粘性搜索框
const STICKY_SEARCH_SCROLL_THRESHOLD = 64;

function Square(props: { activeType: string }) {
  const { activeType } = props;
  const navigate = useNavigate();
  const { isLoggedIn } = useAuth();
  const [loginPromptOpen, setLoginPromptOpen] = useState(false);

  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const [activeCategory, setActiveCategory] = useState("all");
  const [searchQuery, setSearchQuery] = useState("");
  const [showStickySearch, setShowStickySearch] = useState(false);
  const [products, setProducts] = useState<IProductDetail[]>([]);
  const [categories, setCategories] = useState<Array<{ id: string; name: string; count: number }>>([]);
  const [loading, setLoading] = useState(false);
  const [categoriesLoading, setCategoriesLoading] = useState(false);
  const [sortBy, setSortBy] = useState<string>("DOWNLOAD_COUNT");

  const showSortControl = activeType === 'AGENT_SKILL' || activeType === 'WORKER';

  // 分页相关状态
  const [currentPage, setCurrentPage] = useState(1); // 从1开始，用于分页组件
  const [totalElements, setTotalElements] = useState(0);
  const PAGE_SIZE = 12;


  // 获取分类列表
  useEffect(() => {
    // Reset sort when switching product types
    setSortBy("DOWNLOAD_COUNT");

    const fetchCategories = async () => {
      setCategoriesLoading(true);
      try {
        const productType = activeType
        const response = await APIs.getCategoriesByProductType({ productType });

        if (response.code === "SUCCESS" && response.data?.content) {
          const categoryList = response.data.content.map((cat: ICategory) => ({
            id: cat.categoryId,
            name: cat.name,
            count: 0, // 后端没有返回数量，先设为 0
          }));

          if (categoryList.length > 0) {
            // 添加"全部"选项
            setCategories([
              { id: "all", name: "全部", count: 0 },
              ...categoryList
            ]);
            // 重置选中的分类为"全部"
            setActiveCategory("all");
          } else {
            setCategories([])
            // 没有分类时，不选中任何分类
            setActiveCategory("");
          }
        }
      } catch (error) {
        console.error("Failed to fetch categories:", error);
        message.error("获取分类列表失败");
      } finally {
        setCategoriesLoading(false);
      }
    };

    fetchCategories();
  }, [activeType]);

  // 监听滚动，控制粘性搜索框显示/隐藏
  useLayoutEffect(() => {
    const container = scrollContainerRef.current;
    
    if (!container) {
      return;
    }

    const handleScroll = () => {
      // 当滚动超过阈值时，显示粘性搜索框
      setShowStickySearch(container.scrollTop > STICKY_SEARCH_SCROLL_THRESHOLD);
    };

    // 立即绑定监听器
    container.addEventListener('scroll', handleScroll);
    console.log("Scroll listener attached");
    
    return () => {
      console.log("Scroll listener removed");
      container.removeEventListener('scroll', handleScroll);
    };
  }, []);

  // 获取产品列表
  const fetchProducts = useCallback(async (searchText?: string, page?: number) => {
    setLoading(true);
    try {
      const productType = activeType;
      const categoryIds = activeCategory === "all" ? undefined : [activeCategory];
      const name = (searchText ?? "").trim() || undefined;
      // page 从 0 开始，currentPage 从 1 开始
      const pageIndex = (page ?? currentPage) - 1;

      const response = await APIs.getProducts({
        type: productType,
        categoryIds,
        name,
        page: pageIndex,
        size: PAGE_SIZE,
        sortBy: showSortControl ? sortBy : undefined,
      });
      if (response.code === "SUCCESS" && response.data?.content) {
        setProducts(response.data.content);
        setTotalElements(response.data.totalElements);
      }
    } catch (error) {
      console.error("Failed to fetch products:", error);
      message.error("获取产品列表失败");
    } finally {
      setLoading(false);
    }
  }, [activeType, activeCategory, currentPage, sortBy, showSortControl]);

  useEffect(() => {
    fetchProducts(searchQuery);
  }, [activeType, activeCategory, currentPage, sortBy]);

  // 分页变化时重新获取数据
  const handlePageChange = (page: number) => {
    setCurrentPage(page);
  };

  // 输入框变化时只更新 state，不发起请求
  const handleSearchChange = (value: string) => {
    setSearchQuery(value);
  };

  // 点击搜索按钮时发起请求
  const handleSearch = () => {
    setCurrentPage(1);
    fetchProducts(searchQuery);
  };

  // 直接使用后端搜索结果，不再做前端过滤
  const filteredModels = products;

  // 根据产品类型获取对应的统计文案
  const getStatLabel = () => {
    switch (activeType) {
      case 'MODEL_API':
        return 'Models';
      case 'MCP_SERVER':
        return 'MCP Servers';
      case 'AGENT_API':
        return 'Agents';
      case 'REST_API':
        return 'APIs';
      case 'AGENT_SKILL':
        return 'Skills';
      case 'WORKER':
        return 'Workers';
      default:
        return 'Items';
    }
  };

  const handleTryNow = (product: IProductDetail) => {
    if (!isLoggedIn) {
      setLoginPromptOpen(true);
      return;
    }
    // 跳转到 Chat 页面并传递选中的模型 ID
    navigate("/chat", { state: { selectedProduct: product } });
  };

  const handleViewDetail = (product: IProductDetail) => {
    // 根据产品类型跳转到对应的详情页面
    switch (product.type) {
      case "MODEL_API":
        navigate(`/models/${product.productId}`);
        break;
      case "MCP_SERVER":
        navigate(`/mcp/${product.productId}`);
        break;
      case "AGENT_API":
        navigate(`/agents/${product.productId}`);
        break;
      case "REST_API":
        navigate(`/apis/${product.productId}`);
        break;
      case "AGENT_SKILL":
        navigate(`/skills/${product.productId}`);
        break;
      case "WORKER":
        navigate(`/workers/${product.productId}`);
        break;
      default:
        console.log("未知的产品类型", product.type);
    }
  };


  return (
    <>
      {/* 粘性搜索框（滚动时固定显示） */}
      {showStickySearch && (
        <div className="fixed top-0 left-0 right-0 z-[1000] bg-white border-b border-gray-200 shadow-sm py-2">
          <div className="flex flex-col items-center justify-center px-6 py-2 gap-2">
            {/* 搜索框 */}
            <div className="w-full max-w-5xl">
              <Input
                placeholder="搜索名称..."
                value={searchQuery}
                onChange={(e) => handleSearchChange(e.target.value)}
                onPressEnter={handleSearch}
                size="middle"
                suffix={
                  <button
                    onClick={handleSearch}
                    className="bg-black hover:bg-gray-800 text-white rounded-lg p-1.5 transition-colors"
                    type="button"
                  >
                    <SearchOutlined className="text-base" />
                  </button>
                }
                className="rounded-lg text-sm"
                style={{
                  backgroundColor: "rgba(243, 244, 246, 0.5)",
                }}
              />
            </div>
            {/* 排序控件 + 分类菜单 */}
            <div className="w-full flex items-center gap-3">
              <div className="flex-1 min-w-0">
                <CategoryMenu
                  categories={categories}
                  activeCategory={activeCategory}
                  onSelectCategory={setActiveCategory}
                  loading={categoriesLoading}
                />
              </div>
              {showSortControl && (
                <Segmented
                  size="small"
                  value={sortBy}
                  onChange={(value) => {
                    setSortBy(value as string);
                    setCurrentPage(1);
                  }}
                  options={[
                    { label: <span><DownloadOutlined /> 最多下载</span>, value: 'DOWNLOAD_COUNT' },
                    { label: <span><ClockCircleOutlined /> 最近更新</span>, value: 'UPDATED_AT' },
                  ]}
                />
              )}
            </div>
          </div>
        </div>
      )}
      <Layout>
        <div className="flex flex-col h-[calc(100vh-96px)] overflow-auto scrollbar-hide" ref={scrollContainerRef}>
          {/* 顶部区域：统计 + 搜索框 + 分类 */}
          <div className="flex flex-col gap-4 px-6 py-6 from-blue-50 to-purple-50 flex-shrink-0">
            {/* 统计信息 + 排序 */}
            <div className="flex items-center justify-center gap-4 text-sm text-gray-600">
              <div className="flex items-center gap-1.5">
                <div className="w-2 h-2 rounded-full bg-green-500"></div>
                <span className="font-medium">{totalElements.toLocaleString()}</span>
                <span>{getStatLabel()}</span>
              </div>
              {showSortControl && (
                <Segmented
                  size="small"
                  value={sortBy}
                  onChange={(value) => {
                    setSortBy(value as string);
                    setCurrentPage(1);
                  }}
                  options={[
                    { label: <span><DownloadOutlined /> 最多下载</span>, value: 'DOWNLOAD_COUNT' },
                    { label: <span><ClockCircleOutlined /> 最近更新</span>, value: 'UPDATED_AT' },
                  ]}
                />
              )}
            </div>

            {/* 搜索框 */}
            <div className="flex items-center justify-center">
              <div className="w-full max-w-3xl">
                <Input
                  placeholder="搜索名称..."
                  value={searchQuery}
                  onChange={(e) => handleSearchChange(e.target.value)}
                  onPressEnter={handleSearch}
                  size="large"
                  suffix={
                    <button
                      onClick={handleSearch}
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
                />
              </div>
            </div>

            {/* 分类菜单 */}
            <div className="flex-1 min-w-0">
              <CategoryMenu
                categories={categories}
                activeCategory={activeCategory}
                onSelectCategory={setActiveCategory}
                loading={categoriesLoading}
              />
            </div>
          </div>

          {/* 空白区域 */}
          <div className="h-10 border-t border-gray-200 border-width-3 flex-shrink-0" />
          {/* 内容区域：Grid 卡片展示 */}
          <div className="flex-1 px-4 pb-4 flex-shrink-0">
            <div className="pb-4">
              {loading ? (
                <CardGridSkeleton count={8} columns={{ sm: 1, md: 2, lg: 3 }} />
              ) : (
                <>
                  <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6 max-w-[1600px] mx-auto">
                    {filteredModels.map((product) => (
                      product.type === 'AGENT_SKILL' ? (
                        <SkillCard
                          key={product.productId}
                          name={product.name}
                          description={product.description}
                          releaseDate={dayjs(product.createAt).format("YYYY-MM-DD HH:mm:ss")}
                          skillTags={product.skillConfig?.skillTags}
                          downloadCount={product.skillConfig?.downloadCount}
                          icon={getIconString(product.icon, product.name)}
                          onClick={() => handleViewDetail(product)}
                        />
                      ) : product.type === 'WORKER' ? (
                        <WorkerCard
                          key={product.productId}
                          name={product.name}
                          description={product.description}
                          icon={getIconString(product.icon, product.name)}
                          releaseDate={dayjs(product.createAt).format("YYYY-MM-DD HH:mm:ss")}
                          workerTags={product.workerConfig?.tags}
                          downloadCount={product.workerConfig?.downloadCount}
                          onClick={() => handleViewDetail(product)}
                        />
                      ) : (
                        <ModelCard
                          key={product.productId}
                          icon={getIconString(product.icon, product.name)}
                          name={product.name}
                          description={product.description}
                          releaseDate={dayjs(product.createAt).format("YYYY-MM-DD HH:mm:ss")}
                          onClick={() => handleViewDetail(product)}
                          onTryNow={activeType === "MODEL_API" ? () => handleTryNow(product) : undefined}
                        />
                      )
                    ))}
                    {!loading && filteredModels.length === 0 && (
                      <EmptyState productType={activeType} />
                    )}
                  </div>

                  {/* 分页组件 */}
                  {!loading && totalElements > PAGE_SIZE && (
                    <div className="flex justify-center mt-8 mb-4">
                      <Pagination
                        current={currentPage}
                        pageSize={PAGE_SIZE}
                        total={totalElements}
                        onChange={handlePageChange}
                        showSizeChanger={false}
                        showQuickJumper
                      />
                    </div>
                  )}
                </>
              )}
            </div>
          </div>
        </div>
        <BackToTopButton container={scrollContainerRef.current!} />
        <LoginPrompt
          open={loginPromptOpen}
          onClose={() => setLoginPromptOpen(false)}
          contextMessage="登录后即可试用 AI 模型，体验智能对话能力"
        />
    </Layout>
    </>
  );
}

export default Square;

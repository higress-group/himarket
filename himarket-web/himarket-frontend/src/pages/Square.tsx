import { useState, useEffect, useRef, useCallback } from "react";
import { SearchOutlined } from "@ant-design/icons";
import { Input, message, Pagination } from "antd";
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

function Square(props: { activeType: string }) {
  const { activeType } = props;
  const navigate = useNavigate();
  const { isLoggedIn } = useAuth();
  const [loginPromptOpen, setLoginPromptOpen] = useState(false);

  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const [activeCategory, setActiveCategory] = useState("all");
  const [searchQuery, setSearchQuery] = useState("");
  const [products, setProducts] = useState<IProductDetail[]>([]);
  const [categories, setCategories] = useState<Array<{ id: string; name: string; count: number }>>([]);
  const [loading, setLoading] = useState(false);
  const [categoriesLoading, setCategoriesLoading] = useState(false);

  // 分页相关状态
  const [currentPage, setCurrentPage] = useState(1); // 从1开始，用于分页组件
  const [totalElements, setTotalElements] = useState(0);
  const PAGE_SIZE = 12;


  // 获取分类列表
  useEffect(() => {
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
          } else {
            setCategories([])
          }

          // 重置选中的分类为"全部"
          setActiveCategory("all");
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
  }, [activeType, activeCategory, currentPage]);

  useEffect(() => {
    fetchProducts(searchQuery);
  }, [activeType, activeCategory, currentPage]);

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
    <Layout>
      <div className="flex flex-col h-[calc(100vh-96px)]">
        {/* 顶部区域：分类 + 搜索框 */}
        <div className="flex items-center justify-between gap-4 px-6 py-3 border-b border-gray-200">
          {/* 分类菜单 */}
          <div className="flex-1 min-w-0">
            <CategoryMenu
              categories={categories}
              activeCategory={activeCategory}
              onSelectCategory={setActiveCategory}
              loading={categoriesLoading}
            />
          </div>

          {/* 搜索框 */}
          <div className="flex-shrink-0">
            <Input
              placeholder="搜索名称..."
              value={searchQuery}
              onChange={(e) => handleSearchChange(e.target.value)}
              onPressEnter={handleSearch}
              suffix={
                <button
                  onClick={handleSearch}
                  className="text-gray-400 hover:text-gray-600 transition-colors"
                  type="button"
                >
                  <SearchOutlined />
                </button>
              }
              className="w-64 rounded-lg"
              style={{
                backgroundColor: "rgba(243, 244, 246, 0.5)",
              }}
            />
          </div>
        </div>

        {/* 空白区域 */}
        <div className="h-10" />
        {/* 内容区域：Grid 卡片展示 */}
        <div className="flex-1 overflow-hidden px-4 pb-4">
          <div className="h-full overflow-auto scrollbar-hide pb-4"
            ref={scrollContainerRef}
          >
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
  );
}

export default Square;

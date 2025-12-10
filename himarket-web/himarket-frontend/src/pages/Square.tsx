import { useState, useEffect, useRef, useCallback } from "react";
import { SearchOutlined } from "@ant-design/icons";
import { Input, Spin, message } from "antd";
import { useNavigate } from "react-router-dom";
import { Layout } from "../components/Layout";
import { CategoryMenu } from "../components/square/CategoryMenu";
import { ModelCard } from "../components/square/ModelCard";
import APIs, { type ICategory } from "../lib/apis";
import { getIconString } from "../lib/iconUtils";
import type { IProductDetail } from "../lib/apis/product";
import dayjs from "dayjs";
import BackToTopButton from "../components/scroll-to-top";

function Square(props: { activeType: string }) {
  const { activeType } = props;
  const navigate = useNavigate();

  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const [activeCategory, setActiveCategory] = useState("all");
  const [searchQuery, setSearchQuery] = useState("");
  const [products, setProducts] = useState<IProductDetail[]>([]);
  const [categories, setCategories] = useState<Array<{ id: string; name: string; count: number }>>([]);
  const [loading, setLoading] = useState(false);
  const [categoriesLoading, setCategoriesLoading] = useState(false);

  // 分页相关状态
  const [currentPage, setCurrentPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const PAGE_SIZE = 30;


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
  useEffect(() => {
    const fetchProducts = async () => {
      setLoading(true);
      setProducts([]); // 重置产品列表
      setCurrentPage(0); // 重置页码
      setHasMore(true); // 重置加载更多状态
      try {
        const productType = activeType;
        const categoryIds = activeCategory === "all" ? undefined : [activeCategory];

        const response = await APIs.getProducts({
          type: productType,
          categoryIds,
          page: 0,
          size: PAGE_SIZE,
        });
        if (response.code === "SUCCESS" && response.data?.content) {
          const prods = response.data.content;
          setProducts(prods);
          setHasMore(response.data.totalElements > prods.length);
        }
      } catch (error) {
        console.error("Failed to fetch products:", error);
        message.error("获取产品列表失败");
      } finally {
        setLoading(false);
      }
    };

    fetchProducts();
  }, [activeType, activeCategory]);

  // 加载更多产品
  const loadMoreProducts = useCallback(async () => {
    if (loadingMore || !hasMore) return;

    setLoadingMore(true);
    try {
      const productType = activeType;
      const categoryIds = activeCategory === "all" ? undefined : [activeCategory];
      const nextPage = currentPage + 1;

      const response = await APIs.getProducts({
        type: productType,
        categoryIds,
        page: nextPage,
        size: PAGE_SIZE,
      });

      if (response.code === "SUCCESS" && response.data?.content) {
        const prods = [...products, ...response.data.content];
        setProducts(prods);
        setCurrentPage(nextPage);
        setHasMore(response.data.totalElements > prods.length);
      }
    } catch (error) {
      console.error("Failed to load more products:", error);
      message.error("加载更多失败");
    } finally {
      setLoadingMore(false);
    }
  }, [activeType, activeCategory, currentPage, hasMore, loadingMore, PAGE_SIZE]);

  // 监听滚动事件
  useEffect(() => {
    const scrollContainer = scrollContainerRef.current;
    if (!scrollContainer) return;

    const handleScroll = () => {
      const { scrollTop, scrollHeight, clientHeight } = scrollContainer;

      // 滚动到底部时加载更多
      if (scrollHeight - scrollTop - clientHeight < 200) {
        loadMoreProducts();
      }
    };

    scrollContainer.addEventListener("scroll", handleScroll);
    return () => scrollContainer.removeEventListener("scroll", handleScroll);
  }, [loadMoreProducts]);

  const filteredModels = products.filter((product) => {
    const matchesSearch =
      searchQuery === "" ||
      product.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      product.description.toLowerCase().includes(searchQuery.toLowerCase());

    return matchesSearch;
  });

  const handleTryNow = (product: IProductDetail) => {
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
      default:
        console.log("未知的产品类型", product.type);
    }
  };


  return (
    <Layout>
      <div className="flex h-[calc(100vh-96px)]">
        {/* 左侧类型列表 */}
        {
          categories.length > 0 && (
            <CategoryMenu
              categories={categories}
              activeCategory={activeCategory}
              onSelectCategory={setActiveCategory}
              loading={categoriesLoading}
            />
          )
        }

        {/* 右侧内容区域 */}
        <div className="flex-1 flex flex-col relative">
          {/* 上半部分：Tab + 搜索框 */}
          <div className="flex items-center justify-end mb-2 pl-4">
            {/* 搜索框 */}
            <Input
              placeholder="搜索..."
              prefix={<SearchOutlined className="text-gray-400" />}
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-80 rounded-xl"
              style={{
                backgroundColor: "rgba(255, 255, 255, 0.6)",
                backdropFilter: "blur(10px)",
              }}
            />
          </div>

          {/* 下半部分：Grid 卡片展示 */}
          <div className="flex-1 relative overflow-auto"
            ref={scrollContainerRef}
          >
            <div
              className="h-full p-4"
            >
              {loading ? (
                <div className="flex items-center justify-center h-full">
                  <Spin size="large" tip="加载中..." />
                </div>
              ) : (
                <>
                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                    {filteredModels.map((product) => (
                      <ModelCard
                        key={product.productId}
                        icon={getIconString(product.icon)}
                        name={product.name}
                        description={product.description}
                        releaseDate={dayjs(product.createAt).format("YYYY-MM-DD HH:mm:ss")}
                        onClick={() => handleViewDetail(product)}
                        onTryNow={activeType === "MODEL_API" ? () => handleTryNow(product) : undefined}
                      />
                    ))}
                    {!loading && filteredModels.length === 0 && (
                      <div className="col-span-full flex items-center justify-center py-20 text-gray-400">
                        暂无数据
                      </div>
                    )}
                  </div>

                  {/* 加载更多指示器 */}
                  {loadingMore && (
                    <div className="flex items-center justify-center py-8">
                      <Spin tip="加载更多..." />
                    </div>
                  )}

                  {/* 没有更多数据提示 */}
                  {/* {!hasMore && filteredModels.length > 0 && (
                    <div className="flex items-center justify-center py-8 text-gray-400 text-sm">
                      已加载全部内容
                    </div>
                  )} */}
                </>
              )}
            </div>
          </div>

        </div>
      </div>
      <BackToTopButton container={scrollContainerRef.current!} />
    </Layout>
  );
}

export default Square;

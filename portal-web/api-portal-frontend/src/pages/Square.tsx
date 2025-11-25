import { useState, useEffect } from "react";
import { SearchOutlined } from "@ant-design/icons";
import { Input, Spin, message } from "antd";
import { useNavigate } from "react-router-dom";
import { Layout } from "../components/Layout";
import { CategoryMenu } from "../components/square/CategoryMenu";
import { ModelCard } from "../components/square/ModelCard";
import { getProducts, type Product, categoryApi, type Category } from "../lib/api";
import { getIconString } from "../lib/iconUtils";

function Square() {
  const navigate = useNavigate();
  const tabs = [
    { label: "模型", value: "Model", key: "MODEL_API" },
    { label: "MCP", value: "MCP", key: "MCP_SERVER" },
    { label: "智能体", value: "Agent", key: "AGENT_API" },
    { label: "API", value: "APIs", key: "REST_API" },
  ];

  const [activeTab, setActiveTab] = useState(tabs[0]);
  const [activeCategory, setActiveCategory] = useState("all");
  const [searchQuery, setSearchQuery] = useState("");
  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<Array<{ id: string; name: string; count: number }>>([]);
  const [loading, setLoading] = useState(false);
  const [categoriesLoading, setCategoriesLoading] = useState(false);

  // 获取分类列表
  useEffect(() => {
    const fetchCategories = async () => {
      setCategoriesLoading(true);
      try {
        const productType = activeTab.key;
        const response: any = await categoryApi.getCategoriesByProductType(productType);

        if (response.code === "SUCCESS" && response.data?.content) {
          const categoryList = response.data.content.map((cat: Category) => ({
            id: cat.categoryId,
            name: cat.name,
            count: 0, // 后端没有返回数量，先设为 0
          }));

          // 添加"全部"选项
          setCategories([
            { id: "all", name: "全部", count: 0 },
            ...categoryList
          ]);

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
  }, [activeTab.value]);

  // 获取产品列表
  useEffect(() => {
    const fetchProducts = async () => {
      setLoading(true);
      try {
        const productType = activeTab.key;
        const categoryIds = activeCategory === "all" ? undefined : [activeCategory];

        const response: any = await getProducts({
          type: productType,
          categoryIds,
          page: 0,
          size: 100,
        });

        if (response.code === "SUCCESS" && response.data?.content) {
          setProducts(response.data.content);
        }
      } catch (error) {
        console.error("Failed to fetch products:", error);
        message.error("获取产品列表失败");
      } finally {
        setLoading(false);
      }
    };

    fetchProducts();
  }, [activeTab.key, activeCategory]);

  const filteredModels = products.filter((product) => {
    const matchesSearch =
      searchQuery === "" ||
      product.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      product.description.toLowerCase().includes(searchQuery.toLowerCase());

    return matchesSearch;
  });

  const handleTryNow = (product: Product) => {
    // 跳转到 Chat 页面并传递选中的模型 ID
    navigate("/chat", { state: { selectedProduct: product } });
  };

  const handleViewDetail = (product: Product) => {
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
        <CategoryMenu
          categories={categories}
          activeCategory={activeCategory}
          onSelectCategory={setActiveCategory}
          loading={categoriesLoading}
        />

        {/* 右侧内容区域 */}
        <div className="flex-1 flex flex-col">
          {/* 上半部分：Tab + 搜索框 */}
          <div className="flex items-center justify-between mb-6">
            {/* Tab 区域 */}
            <div className="flex items-center gap-6 pl-4">
              {tabs.map((tab) => {
                const isActive = tab.value === activeTab.value;
                return (
                  <div
                    key={tab.value}
                    onClick={() => setActiveTab(tab)}
                    className={`
                      text-xl font-medium cursor-pointer
                      transition-all duration-300 ease-in-out
                      origin-left
                      ${isActive
                        ? "text-black scale-110"
                        : "text-gray-400 hover:text-gray-600 hover:scale-105"
                      }
                    `}
                  >
                    {tab.label}
                  </div>
                );
              })}
            </div>

            {/* 搜索框 */}
            <Input
              placeholder="搜索模型..."
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
          <div className="flex-1 overflow-y-auto p-4">
            {loading ? (
              <div className="flex items-center justify-center h-full">
                <Spin size="large" tip="加载中..." />
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                {filteredModels.map((product) => (
                  <ModelCard
                    key={product.productId}
                    icon={getIconString(product.icon)}
                    name={product.name}
                    description={product.description}
                    company={product.modelConfig?.modelAPIConfig?.aiProtocols?.[0] || "AI Model"}
                    releaseDate={new Date(product.createAt).toLocaleDateString('zh-CN')}
                    onClick={() => handleViewDetail(product)}
                    onTryNow={activeTab.value === "Model" ? () => handleTryNow(product) : undefined}
                  />
                ))}
                {!loading && filteredModels.length === 0 && (
                  <div className="col-span-full flex items-center justify-center py-20 text-gray-400">
                    暂无数据
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </Layout>
  );
}

export default Square;

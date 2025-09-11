import { useEffect, useState } from "react";
import { Card, Tag, Typography, Input, Avatar } from "antd";
import { Link } from "react-router-dom";
import { Layout } from "../components/Layout";
import api from "../lib/api";
import { ProductType, ProductStatus } from "../types";
import type { Product, ApiResponse, PaginatedResponse } from "../types";

const { Title, Paragraph } = Typography;
const { Search } = Input;

interface ModelProductItem {
  key: string;
  name: string;
  description: string;
  status: string;
  category: string;
  icon?: string;
  updatedAt: string;
}

export default function Models() {
  const [loading, setLoading] = useState(false);
  const [products, setProducts] = useState<ModelProductItem[]>([]);
  const [searchText, setSearchText] = useState("");

  useEffect(() => {
    fetchProducts();
  }, []);

  const fetchProducts = async () => {
    setLoading(true);
    try {
      const response: ApiResponse<PaginatedResponse<Product>> = await api.get(
        "/products?type=MODEL_API&page=0&size=100"
      );
      if (response.code === "SUCCESS" && response.data) {
        const mapped = response.data.content
          .filter((item: Product) => item.type === ProductType.MODEL_API)
          .map((item: Product) => ({
            key: item.productId,
            name: item.name,
            description: item.description,
            status: item.status === ProductStatus.ENABLE ? "active" : "inactive",
            category: item.category,
            icon: item.icon || undefined,
            updatedAt: item.updatedAt?.slice(0, 10) || "",
          }));
        setProducts(mapped);
      }
    } catch (e) {
      console.error("获取Model API产品列表失败:", e);
    } finally {
      setLoading(false);
    }
  };

  const filtered = products.filter((p) =>
    [p.name, p.description, p.category]
      .filter(Boolean)
      .some((t) => (t || "").toLowerCase().includes(searchText.toLowerCase()))
  );

  const getIconText = (name: string) => {
    const words = name.split(" ");
    if (words.length >= 2) {
      return words[0][0] + words[1][0];
    }
    return name.substring(0, 2).toUpperCase();
  };

  const getColor = (name: string) => {
    const colors = ["#1890ff", "#52c41a", "#faad14", "#f5222d", "#722ed1", "#13c2c2"];
    const index = name.charCodeAt(0) % colors.length;
    return colors[index];
  };

  return (
    <Layout loading={loading}>
      <div className="text-center mb-8">
        <Title level={1} className="mb-4">
          Model 市场
        </Title>
        <Paragraph className="text-gray-600 text-lg max-w-4xl mx-auto">
          展示可用的模型 API 产品
        </Paragraph>
      </div>

      <div className="flex justify-center mb-8">
        <div className="relative w-full max-w-2xl">
          <Search
            placeholder="请输入内容"
            size="large"
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            className="rounded-lg shadow-lg"
          />
        </div>
      </div>

      <div className="mb-6">
        <Title level={3} className="mb-4">
          热门/推荐 Model APIs: {filtered.length}
        </Title>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mb-8">
        {filtered.map((product) => (
          <Link key={product.key} to={`/models/${product.key}`} className="block">
            <Card
              hoverable
              className="h-full transition-all duration-200 hover:shadow-lg cursor-pointer rounded-lg shadow-lg"
            >
              <div className="flex items-start space-x-4">
                <Avatar
                  size={48}
                  style={{ backgroundColor: getColor(product.name), fontSize: 18, fontWeight: "bold" }}
                >
                  {getIconText(product.name)}
                </Avatar>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between mb-2">
                    <Title level={5} className="mb-0 truncate">
                      {product.name}
                    </Title>
                    <Tag color="purple" className="text-xs">
                      MODEL
                    </Tag>
                  </div>
                  <Paragraph className="text-sm text-gray-600 mb-3 line-clamp-2">
                    {product.description}
                  </Paragraph>
                  <div className="text-xs text-gray-400">更新 {product.updatedAt}</div>
                </div>
              </div>
            </Card>
          </Link>
        ))}
      </div>

      {filtered.length === 0 && (
        <div className="text-center py-8">
          <div className="text-gray-500">暂无Model API产品</div>
        </div>
      )}
    </Layout>
  );
}

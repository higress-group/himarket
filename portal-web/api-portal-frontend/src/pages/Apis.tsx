import { useEffect, useState } from "react";
import { Card, Tag, Typography, Input, Avatar } from "antd";
import { Link } from "react-router-dom";
import { Layout } from "../components/Layout";
import api from "../lib/api";
import { ProductType, ProductStatus } from "../types";
import type { Product, ApiResponse, PaginatedResponse } from "../types";
// import { getCategoryText, getCategoryColor } from "../lib/statusUtils";
import './Test.css';

const { Title, Paragraph } = Typography;
const { Search } = Input;



interface ApiProduct {
  key: string;
  name: string;
  description: string;
  status: string;
  version: string;
  endpoints: number;
  category: string;
  creator: string;
  icon?: string;
  updatedAt: string;
}

function APIsPage() {
  const [loading, setLoading] = useState(false);
  const [apiProducts, setApiProducts] = useState<ApiProduct[]>([]);
  const [searchText, setSearchText] = useState('');

  useEffect(() => {
    fetchApiProducts();
  }, []);

  const revertIcon = (icon: string) => {
    const startIndex = icon.indexOf("value=") + 6;
    const endIndex = icon.length - 1;
    const URL = icon.substring(startIndex, endIndex).trim();
    return URL;
  }
  const fetchApiProducts = async () => {
    setLoading(true);
    try {
      const response: ApiResponse<PaginatedResponse<Product>> = await api.get("/products?type=REST_API&page=0&size=100");
      if (response.code === "SUCCESS" && response.data) {
        const mapped = response.data.content
          .filter((item: Product) => item.type === ProductType.REST_API)
          .map((item: Product) => {
            return {
              key: item.productId,
              name: item.name,
              description: item.description,
              status: item.status === ProductStatus.ENABLE ? 'active' : 'inactive',
              version: 'v1.0.0',
              endpoints: 0,
              category: item.category,
              creator: 'Unknown', // Product类型中没有creator属性，使用默认值
              icon: item.icon || undefined,
              updatedAt: item.updatedAt?.slice(0, 10) || ''
            };
          });
        setApiProducts(mapped);
      }
    } catch (error) {
      console.error('获取API产品列表失败:', error);
    } finally {
      setLoading(false);
    }
  };



  const filteredApiProducts = apiProducts.filter(product => {
    return product.name.toLowerCase().includes(searchText.toLowerCase()) ||
           product.description.toLowerCase().includes(searchText.toLowerCase()) ||
           product.creator.toLowerCase().includes(searchText.toLowerCase());
  });

  const getApiIcon = (name: string) => {
    // Generate initials for API icon
    const words = name.split(' ');
    if (words.length >= 2) {
      return words[0][0] + words[1][0];
    }
    return name.substring(0, 2).toUpperCase();
  };

  const getApiIconColor = (name: string) => {
    const colors = ['#1890ff', '#52c41a', '#faad14', '#f5222d', '#722ed1', '#13c2c2'];
    const index = name.charCodeAt(0) % colors.length;
    return colors[index];
  };

  return (
    <Layout loading={loading}>
      {/* Header Section */}
      <div className="text-center mb-8">
        <Title level={1} className="mb-4">
          API 市场
        </Title>
        <Paragraph className="text-gray-600 text-lg max-w-4xl mx-auto text-flow text-flow-grey slow">
          支持私有化部署，具备更多管理能力，支持自动注册、智能路由的API市场
        </Paragraph>
      </div>

      {/* Search Section */}
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

      {/* APIs Section */}
      <div className="mb-6">
        <Title level={3} className="mb-4">
          热门/推荐 APIs: {filteredApiProducts.length}
        </Title>
      </div>

      {/* APIs Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mb-8">
        {filteredApiProducts.map((product) => (
          <Link key={product.key} to={`/apis/${product.key}`} className="block">
            <Card
              hoverable
              className="h-full transition-all duration-200 hover:shadow-lg cursor-pointer rounded-lg shadow-lg"
            >
              <div className="flex items-start space-x-4">
                {/* API Icon */}
                <Avatar
                  size={48}
                  src={product.icon ? revertIcon(product.icon) : undefined}
                  style={{ 
                    backgroundColor: getApiIconColor(product.name),
                    fontSize: '18px',
                    fontWeight: 'bold'
                  }}
                >
                  {revertIcon(product.icon || '') || getApiIcon(product.name)}
                </Avatar>

                {/* API Info */}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between mb-2">
                    <Title level={5} className="mb-0 truncate">
                      {product.name}
                    </Title>
                    <Tag color="green" className="text-xs">
                      REST
                    </Tag>
                  </div>

                  {/* <div className="text-sm text-gray-500 mb-2">
                    创建者: {product.creator}
                  </div> */}

                  <Paragraph className="text-sm text-gray-600 mb-3 line-clamp-2">
                    {product.description}
                  </Paragraph>

                  <div className="flex items-center justify-between">
                    {/* <Tag color={getCategoryColor(product.category)} className="">
                      {getCategoryText(product.category)}
                    </Tag> */}
                    <div className="text-xs text-gray-400">
                      更新 {product.updatedAt}
                    </div>
                  </div>
                </div>
              </div>
            </Card>
          </Link>
        ))}
      </div>

      {/* Empty State */}
      {filteredApiProducts.length === 0 && (
        <div className="text-center py-8">
          <div className="text-gray-500">暂无API产品</div>
        </div>
      )}
    </Layout>
  );
}

export default APIsPage; 
import { useEffect, useState } from "react";
import { Card, Tag, Typography, Input, Avatar, Skeleton } from "antd";
import { Link } from "react-router-dom";
import { Layout } from "../components/Layout";
import api from "../lib/api";
import { ProductStatus } from "../types";
import type { Product, ApiResponse, PaginatedResponse, ProductIcon } from "../types";
// import { getCategoryText, getCategoryColor } from "../lib/statusUtils";
import './Test.css';

const { Title, Paragraph } = Typography;
const { Search } = Input;



interface ApiProductListItem {
  key: string;
  name: string;
  description: string;
  status: string;
  version: string;
  endpoints: number;
  creator: string;
  icon?: ProductIcon;
  updatedAt: string;
}

function APIsPage() {
  const [loading, setLoading] = useState(false);
  const [apiProducts, setApiProducts] = useState<ApiProductListItem[]>([]);
  const [searchText, setSearchText] = useState('');

  useEffect(() => {
    fetchApiProducts();
  }, []);

  // 处理产品图标的函数
  const getIconUrl = (icon?: ProductIcon | null): string => {
    const fallback = "/logo.svg";
    
    if (!icon) {
      return fallback;
    }
    
    switch (icon.type) {
      case "URL":
        return icon.value || fallback;
      case "BASE64":
        // 如果value已经包含data URL前缀，直接使用；否则添加前缀
        return icon.value ? (icon.value.startsWith('data:') ? icon.value : `data:image/png;base64,${icon.value}`) : fallback;
      default:
        return fallback;
    }
  };
  const fetchApiProducts = async () => {
    setLoading(true);
    try {
      const response: ApiResponse<PaginatedResponse<Product>> = await api.get("/products?type=REST_API&page=0&size=100");
      if (response.code === "SUCCESS" && response.data) {
        // 移除重复过滤，简化数据映射
        const mapped = response.data.content.map((item: Product) => ({
          key: item.productId,
          name: item.name,
          description: item.description,
          status: item.status === ProductStatus.ENABLE ? 'active' : 'inactive',
          version: 'v1.0.0',
          endpoints: 0,
          creator: 'Unknown',
          icon: item.icon || undefined,
          updatedAt: item.updatedAt?.slice(0, 10) || ''
        }));
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
    <Layout>
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
      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mb-8">
          {Array.from({ length: 6 }).map((_, index) => (
            <Card key={index} className="h-full rounded-lg shadow-lg">
              <Skeleton loading active>
                <div className="flex items-start space-x-4">
                  <Skeleton.Avatar size={48} active />
                  <div className="flex-1 min-w-0">
                    <Skeleton.Input active size="small" style={{ width: '80%', marginBottom: 8 }} />
                    <Skeleton.Input active size="small" style={{ width: '100%', marginBottom: 12 }} />
                    <Skeleton.Input active size="small" style={{ width: '60%' }} />
                  </div>
                </div>
              </Skeleton>
            </Card>
          ))}
        </div>
      ) : (
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
                  src={product.icon ? getIconUrl(product.icon) : undefined}
                  style={{ 
                    backgroundColor: getApiIconColor(product.name),
                    fontSize: '18px',
                    fontWeight: 'bold'
                  }}
                >
                  {!product.icon && getApiIcon(product.name)}
                </Avatar>

                {/* API Info */}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between mb-2">
                    <Title level={5} className="mb-0 truncate">
                      {product.name}
                    </Title>
                    <Tag className="text-xs text-green-700 border-0 bg-transparent px-0">
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
      )}

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
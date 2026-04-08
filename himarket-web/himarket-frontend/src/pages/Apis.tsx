import { FolderFilled, FolderOpenFilled } from '@ant-design/icons';
import { Card, Tag, Typography, Input, Avatar, Skeleton } from 'antd';
import { useEffect, useState } from 'react';
const { Paragraph, Title } = Typography;
import { Link } from 'react-router-dom';

import { Layout } from '../components/Layout';
import APIs, { type ICategory } from '../lib/apis';
import { ProductStatus } from '../types';
import './Test.css';

import type { IProductIcon } from '../lib/apis/typing';

interface ApiProductListItem {
  key: string;
  name: string;
  description: string;
  status: string;
  version: string;
  endpoints: number;
  creator: string;
  icon?: IProductIcon;
  categories: ICategory[];
  updatedAt: string;
}

function APIsPage() {
  const [loading, setLoading] = useState(false);
  const [apiProducts, setApiProducts] = useState<ApiProductListItem[]>([]);
  const [allProducts, setAllProducts] = useState<ApiProductListItem[]>([]);
  const [searchText, setSearchText] = useState('');
  const [categories, setCategories] = useState<ICategory[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string>('all');

  useEffect(() => {
    fetchApiProducts();
    fetchCategories();
  }, []);

  // 处理产品图标的函数
  const getIconUrl = (icon?: IProductIcon): string => {
    const fallback = '/logo.svg';

    if (!icon) {
      return fallback;
    }

    switch (icon.type) {
      case 'URL':
        return icon.value || fallback;
      case 'BASE64':
        // 如果value已经包含data URL前缀，直接使用；否则添加前缀
        return icon.value
          ? icon.value.startsWith('data:')
            ? icon.value
            : `data:image/png;base64,${icon.value}`
          : fallback;
      default:
        return fallback;
    }
  };
  // 获取类别列表
  const fetchCategories = async () => {
    try {
      const response = await APIs.getCategoriesByProductType({ productType: 'REST_API' });
      if (response.code === 'SUCCESS' && response.data) {
        const categoriesData = response.data.content || [];
        setCategories(categoriesData);
      }
    } catch (error) {
      console.error('获取类别列表失败:', error);
    }
  };

  const fetchApiProducts = async () => {
    setLoading(true);
    try {
      const response = await APIs.getProducts({ page: 0, size: 100, type: 'REST_API' });
      if (response.code === 'SUCCESS' && response.data) {
        // 移除重复过滤，简化数据映射
        const mapped: ApiProductListItem[] = response.data.content.map((item) => ({
          categories: item.categories || [],
          creator: 'Unknown',
          description: item.description,
          endpoints: 0,
          icon: item.icon,
          key: item.productId,
          name: item.name,
          status: item.status === ProductStatus.ENABLE ? 'active' : 'inactive',
          updatedAt: item.updatedAt?.slice(0, 10) || '',
          version: 'v1.0.0',
        })) as ApiProductListItem[];
        setAllProducts(mapped);
        setApiProducts(mapped);
      }
    } catch (error) {
      console.error('获取API产品列表失败:', error);
    } finally {
      setLoading(false);
    }
  };

  // 处理类别筛选
  const handleCategoryChange = (categoryId: string) => {
    setSelectedCategory(categoryId);
    if (categoryId === 'all') {
      setApiProducts(allProducts);
    } else {
      const filtered = allProducts.filter((product) =>
        product.categories.some((cat) => cat.categoryId === categoryId),
      );
      setApiProducts(filtered);
    }
  };

  // 获取类别图标
  const getCategoryIcon = (icon?: IProductIcon, _isSelected?: boolean, isAll?: boolean) => {
    if (!icon || !icon.value) {
      // "全部"使用打开的文件夹图标，其他使用普通文件夹图标
      const IconComponent = isAll ? FolderOpenFilled : FolderFilled;
      return <IconComponent style={{ color: '#D1D5DB', fontSize: '18px' }} />;
    }

    let iconUrl = '';
    if (icon.type === 'URL') {
      iconUrl = icon.value;
    } else if (icon.type === 'BASE64') {
      // 处理BASE64数据，确保有正确的前缀
      iconUrl = icon.value.startsWith('data:') ? icon.value : `data:image/png;base64,${icon.value}`;
    }

    return (
      <img
        alt=""
        onError={(e) => {
          // 如果图标加载失败，显示默认图标
          e.currentTarget.style.display = 'none';
        }}
        src={iconUrl}
        style={{ height: '18px', width: '18px' }}
      />
    );
  };

  const filteredApiProducts = apiProducts.filter((product) => {
    return (
      product.name.toLowerCase().includes(searchText.toLowerCase()) ||
      product.description.toLowerCase().includes(searchText.toLowerCase()) ||
      product.creator.toLowerCase().includes(searchText.toLowerCase())
    );
  });

  const getApiIcon = (name: string) => {
    // Generate initials for API icon
    const words = name.split(' ');
    if (words.length >= 2) {
      const firstWord = words[0];
      const secondWord = words[1];
      const firstChar = firstWord?.[0] ?? '';
      const secondChar = secondWord?.[0] ?? '';
      return firstChar + secondChar;
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
        <Title className="mb-4" level={1}>
          API 市场
        </Title>
        <Paragraph className="text-gray-600 text-lg max-w-4xl mx-auto text-flow text-flow-grey slow">
          支持私有化部署，具备更多管理能力，支持自动注册、智能路由的API市场
        </Paragraph>
      </div>

      {/* Search Section */}
      <div className="flex justify-center mb-8">
        <div className="relative w-full max-w-lg">
          <div
            className="border border-gray-300 rounded-md overflow-hidden hover:border-blue-500 focus-within:border-blue-500 focus-within:shadow-sm"
            style={{ maxWidth: '500px', width: '100%' }}
          >
            <Input.Search
              className="border-0 rounded-none"
              onChange={(e) => setSearchText(e.target.value)}
              placeholder="请输入内容"
              size="large"
              value={searchText}
              variant="borderless"
            />
          </div>
        </div>
      </div>

      {/* Category Tags Section */}
      <div className="mb-2">
        <div className="py-3 px-4 border border-gray-200 rounded-lg bg-[#f4f4f6]">
          <div className="flex flex-wrap items-center gap-4">
            <button
              className={`cursor-pointer transition-all duration-200 px-3 py-1.5 rounded-md flex items-center gap-2 text-sm border ${
                selectedCategory === 'all'
                  ? 'bg-white shadow-sm text-blue-600 border-blue-200'
                  : 'text-gray-600 border-transparent hover:bg-white hover:shadow-sm hover:border-gray-200'
              }`}
              onClick={() => handleCategoryChange('all')}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  handleCategoryChange('all');
                }
              }}
              type="button"
            >
              <div
                className={`w-4 h-4 rounded border flex items-center justify-center ${
                  selectedCategory === 'all'
                    ? 'border-blue-500 bg-blue-500'
                    : 'border-gray-300 bg-white'
                }`}
              >
                {selectedCategory === 'all' && (
                  <svg className="w-2.5 h-2.5 text-white" fill="currentColor" viewBox="0 0 20 20">
                    <path
                      clipRule="evenodd"
                      d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                      fillRule="evenodd"
                    />
                  </svg>
                )}
              </div>
              {getCategoryIcon(undefined, selectedCategory === 'all', true)}
              <span>全部</span>
            </button>
            {categories.map((category) => (
              <button
                className={`cursor-pointer transition-all duration-200 px-3 py-1.5 rounded-md flex items-center gap-2 text-sm border ${
                  selectedCategory === category.categoryId
                    ? 'bg-white shadow-sm text-blue-600 border-blue-200'
                    : 'text-gray-600 border-transparent hover:bg-white hover:shadow-sm hover:border-gray-200'
                }`}
                key={category.categoryId}
                onClick={() => handleCategoryChange(category.categoryId)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    handleCategoryChange(category.categoryId);
                  }
                }}
                type="button"
              >
                <div
                  className={`w-4 h-4 rounded border flex items-center justify-center ${
                    selectedCategory === category.categoryId
                      ? 'border-blue-500 bg-blue-500'
                      : 'border-gray-300 bg-white'
                  }`}
                >
                  {selectedCategory === category.categoryId && (
                    <svg className="w-2.5 h-2.5 text-white" fill="currentColor" viewBox="0 0 20 20">
                      <path
                        clipRule="evenodd"
                        d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                        fillRule="evenodd"
                      />
                    </svg>
                  )}
                </div>
                {getCategoryIcon(category.icon, selectedCategory === category.categoryId)}
                <span>{category.name}</span>
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* APIs Grid */}
      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mb-8">
          {Array.from({ length: 6 }).map((_, index) => (
            <Card className="h-full rounded-lg shadow-lg" key={index}>
              <Skeleton active loading>
                <div className="flex items-start space-x-4">
                  <Skeleton.Avatar active size={48} />
                  <div className="flex-1 min-w-0">
                    <Skeleton.Input active size="small" style={{ marginBottom: 8, width: '80%' }} />
                    <Skeleton.Input
                      active
                      size="small"
                      style={{ marginBottom: 12, width: '100%' }}
                    />
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
            <Link className="block" key={product.key} to={`/apis/${product.key}`}>
              <Card
                className="h-full transition-all duration-200 hover:shadow-lg cursor-pointer rounded-lg shadow-lg"
                hoverable
              >
                <div className="flex items-start space-x-4">
                  {/* API Icon */}
                  {product.icon ? (
                    <Avatar size={48} src={getIconUrl(product.icon)} />
                  ) : (
                    <Avatar
                      size={48}
                      style={{
                        backgroundColor: getApiIconColor(product.name),
                        fontSize: '18px',
                        fontWeight: 'bold',
                      }}
                    >
                      {getApiIcon(product.name)}
                    </Avatar>
                  )}

                  {/* API Info */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between mb-2">
                      <Title className="mb-0 truncate" level={5}>
                        {product.name}
                      </Title>
                      <Tag className="text-xs text-gray-500 border-0 bg-transparent px-0">REST</Tag>
                    </div>

                    <Paragraph className="text-sm text-gray-600 mb-3 line-clamp-2">
                      {product.description}
                    </Paragraph>

                    <div className="flex items-center justify-between">
                      <div className="text-xs text-gray-400">更新 {product.updatedAt}</div>
                    </div>
                  </div>
                </div>
              </Card>
            </Link>
          ))}
        </div>
      )}

      {/* Empty State */}
      {!loading && filteredApiProducts.length === 0 && (
        <div className="text-center py-8">
          <div className="text-gray-500">暂无API服务</div>
        </div>
      )}
    </Layout>
  );
}

export default APIsPage;

import { useEffect, useState } from "react";
import { Card, Tag, Typography, Input, Avatar } from "antd";
import { Link } from "react-router-dom";
import { Layout } from "../components/Layout";
import api from "../lib/api";
import { ProductType, ProductStatus } from "../types";
import type { Product, ApiResponse, PaginatedResponse } from "../types";
// import { getCategoryText, getCategoryColor } from "../lib/statusUtils";

const { Title, Paragraph } = Typography;
const { Search } = Input;

interface McpServer {
  key: string;
  name: string;
  description: string;
  status: string;
  version: string;
  endpoints: number;
  category: string;
  creator: string;
  icon?: string;
  mcpConfig?: any;
  updatedAt: string;
}

function McpPage() {
  const [loading, setLoading] = useState(false);
  const [mcpServers, setMcpServers] = useState<McpServer[]>([]);
  const [searchText, setSearchText] = useState('');

  useEffect(() => {
    fetchMcpServers();
  }, []);
  const revertIcon = (icon: string) => {
    const startIndex = icon.indexOf("value=") + 6;
    const endIndex = icon.length - 1;
    const URL = icon.substring(startIndex, endIndex).trim();
    return URL;
  }
  const fetchMcpServers = async () => {
    setLoading(true);
    try {
      const response: ApiResponse<PaginatedResponse<Product>> = await api.get("/products?type=MCP_SERVER&page=0&size=100");
      if (response.code === "SUCCESS" && response.data) {
        const mapped = response.data.content
          .filter((item: Product) => item.type === ProductType.MCP_SERVER)
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
              mcpConfig: item.mcpConfig,
              updatedAt: item.updatedAt?.slice(0, 10) || ''
            };
          });
        setMcpServers(mapped);
      }
    } catch (error) {
      console.error('获取MCP服务器列表失败:', error);
    } finally {
      setLoading(false);
    }
  };



  const filteredMcpServers = mcpServers.filter(server => {
    return server.name.toLowerCase().includes(searchText.toLowerCase()) ||
           server.description.toLowerCase().includes(searchText.toLowerCase()) ||
           server.creator.toLowerCase().includes(searchText.toLowerCase());
  });

  return (
    <Layout loading={loading}>
      {/* Header Section */}
      <div className="text-center mb-8">
        <Title level={1} className="mb-4">
          MCP 市场
        </Title>
        <Paragraph className="text-gray-600 text-lg max-w-4xl mx-auto text-flow text-flow-grey slow">
          支持私有化部署，共建和兼容MCP市场官方协议，具备更多管理能力，支持自动注册、智能路由的MCP市场
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

      {/* Servers Section */}
      <div className="mb-6">
        <Title level={3} className="mb-4">
          热门/推荐 MCP Servers: {filteredMcpServers.length}
        </Title>
      </div>

      {/* Servers Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mb-8">
        {filteredMcpServers.map((server) => (
          <Link key={server.key} to={`/mcp/${server.key}`} className="block">
            <Card
              hoverable
              className="h-full transition-all duration-200 hover:shadow-lg cursor-pointer rounded-lg shadow-lg"
            >
              <div className="flex items-start space-x-4 mb-2">
                {/* Server Icon */}
                {server.icon ? (
                  <Avatar
                    size={48}
                    src={revertIcon(server.icon)}
                  />
                ) : (
                  <Avatar
                    size={48}
                    className="bg-gradient-to-br from-blue-500 to-blue-600 shadow-lg"
                    style={{ fontSize: "18px", fontWeight: "600" }}
                  >
                    {server.name[0]}
                  </Avatar>
                )}

                {/* Server Info */}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between mb-2">
                    <Title level={5} className="mb-0 truncate">
                      {server.name}
                    </Title>
                    <Tag color="green" className="text-xs">
                      {server.mcpConfig?.mcpServerConfig?.transportMode || 'REMOTE'}
                    </Tag>
                  </div>
                  </div>
                </div>
                  <Paragraph className="text-sm text-gray-600 mb-3 line-clamp-2">
                    {server.description}
                  </Paragraph>

                  <div className="flex items-center justify-between">
                    {/* <Tag color={getCategoryColor(server.category || 'OFFICIAL')} className="">
                      {getCategoryText(server.category || 'OFFICIAL')}
                    </Tag> */}
                    <div className="text-xs text-gray-400">
                      更新 {server.updatedAt}
                    </div>
                  </div>
            </Card>
          </Link>
        ))}
      </div>

      {/* Empty State */}
      {filteredMcpServers.length === 0 && (
        <div className="text-center py-8">
          <div className="text-gray-500">暂无MCP服务器</div>
        </div>
      )}
    </Layout>
  );
}

export default McpPage; 
import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Card,
  message,
  Input,
  Select,
  Tag,
  Space,
  Empty,
  Skeleton,
  Dropdown
} from 'antd';
import type { MenuProps } from 'antd';
import {
  ApiOutlined,
  PlusOutlined,
  SearchOutlined,
  RobotOutlined,
  BulbOutlined,
  ClockCircleFilled,
  CheckCircleFilled,
  ExclamationCircleFilled,
  MoreOutlined,
  EditOutlined,
  DeleteOutlined,
  ExclamationCircleOutlined
} from '@ant-design/icons';
import { Modal } from 'antd';
import McpServerIcon from '@/components/icons/McpServerIcon';
import { apiDefinitionApi } from '@/lib/api';

// API 类型映射
const API_TYPE_MAP = {
  REST_API: { label: 'REST API', icon: ApiOutlined, color: 'blue' },
  MCP_SERVER: { label: 'MCP Server', icon: McpServerIcon, color: 'purple' },
  AGENT_API: { label: 'Agent API', icon: RobotOutlined, color: 'green' },
  MODEL_API: { label: 'Model API', icon: BulbOutlined, color: 'orange' }
};

// API 状态映射
const API_STATUS_MAP = {
  DRAFT: { label: '草稿', color: 'default', icon: ClockCircleFilled },
  PUBLISHING: { label: '发布中', color: 'processing', icon: ClockCircleFilled },
  PUBLISHED: { label: '已发布', color: 'success', icon: CheckCircleFilled },
  DEPRECATED: { label: '已弃用', color: 'warning', icon: ExclamationCircleFilled },
  ARCHIVED: { label: '已归档', color: 'default', icon: ExclamationCircleFilled }
};

interface ApiDefinition {
  apiDefinitionId: string;
  name: string;
  description: string;
  type: keyof typeof API_TYPE_MAP;
  status: keyof typeof API_STATUS_MAP;
  version: string;
  properties?: Array<Record<string, any>>;
  createdAt: string;
  updatedAt: string;
}

export default function ApiDefinitions() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [apis, setApis] = useState<ApiDefinition[]>([]);

  // 搜索状态
  const [searchValue, setSearchValue] = useState('');
  const [searchType, setSearchType] = useState<'name' | 'type' | 'status'>('name');
  const [activeFilters, setActiveFilters] = useState<Array<{ type: string; value: string; label: string }>>([]);

  useEffect(() => {
    fetchApiDefinitions();
  }, []);

  const fetchApiDefinitions = useCallback(async (filters?: { type?: string; status?: string; keyword?: string }) => {
    setLoading(true);
    try {
      const response: any = await apiDefinitionApi.getApiDefinitions(filters || {});

      // 后端返回的数据结构: { code, message, data: { content, number, size, totalElements } }
      if (response && response.data) {
        // PageResult 结构
        if (response.data.content && Array.isArray(response.data.content)) {
          setApis(response.data.content);
        } else if (Array.isArray(response.data)) {
          // 直接是数组
          setApis(response.data);
        } else {
          setApis([]);
        }
      } else if (response && response.content) {
        // 兼容旧的数据结构
        setApis(response.content);
      } else if (Array.isArray(response)) {
        setApis(response);
      } else {
        setApis([]);
      }
    } catch (error) {
      message.error('获取 API 列表失败');
      setApis([]);
    } finally {
      setLoading(false);
    }
  }, []);

  // 搜索类型选项
  const searchTypeOptions = [
    { label: 'API 名称', value: 'name' as const },
    { label: 'API 类型', value: 'type' as const },
    { label: '状态', value: 'status' as const },
  ];

  // 搜索处理函数
  const handleSearch = () => {
    if (searchValue.trim()) {
      let labelText = '';
      const filterValue = searchValue.trim();

      if (searchType === 'name') {
        labelText = `API 名称：${searchValue.trim()}`;
      } else if (searchType === 'type') {
        const typeLabel = API_TYPE_MAP[filterValue as keyof typeof API_TYPE_MAP]?.label || searchValue.trim();
        labelText = `API 类型：${typeLabel}`;
      } else if (searchType === 'status') {
        const statusLabel = API_STATUS_MAP[filterValue as keyof typeof API_STATUS_MAP]?.label || searchValue.trim();
        labelText = `状态：${statusLabel}`;
      }

      const newFilter = { type: searchType, value: filterValue, label: labelText };
      const updatedFilters = activeFilters.filter(f => f.type !== searchType);
      updatedFilters.push(newFilter);
      setActiveFilters(updatedFilters);

      const filters: { type?: string; status?: string; keyword?: string } = {};
      updatedFilters.forEach(filter => {
        if (filter.type === 'type') {
          filters.type = filter.value;
        } else if (filter.type === 'status') {
          filters.status = filter.value;
        } else if (filter.type === 'name') {
          filters.keyword = filter.value;
        }
      });

      fetchApiDefinitions(filters);
      setSearchValue('');
    }
  };

  // 移除单个筛选条件
  const removeFilter = (filterType: string) => {
    const updatedFilters = activeFilters.filter(f => f.type !== filterType);
    setActiveFilters(updatedFilters);

    const filters: { type?: string; status?: string; keyword?: string } = {};
    updatedFilters.forEach(filter => {
      if (filter.type === 'type') {
        filters.type = filter.value;
      } else if (filter.type === 'status') {
        filters.status = filter.value;
      } else if (filter.type === 'name') {
        filters.keyword = filter.value;
      }
    });

    fetchApiDefinitions(filters);
  };

  // 清空所有筛选条件
  const clearAllFilters = () => {
    setActiveFilters([]);
    fetchApiDefinitions({});
  };

  const handleNavigateToDetail = useCallback((apiDefinitionId: string) => {
    navigate(`/api-definitions/detail?id=${apiDefinitionId}`);
  }, [navigate]);

  const handleCreateNew = () => {
    navigate('/api-definitions/create');
  };

  const handleEdit = (api: ApiDefinition, e?: any) => {
    if (e) e.stopPropagation();
    navigate(`/api-definitions/edit?id=${api.apiDefinitionId}`);
  };

  const handleDelete = (api: ApiDefinition, e?: any) => {
    if (e) e.stopPropagation();
    Modal.confirm({
      title: '确认删除',
      icon: <ExclamationCircleOutlined />,
      content: `确定要删除 API "${api.name}" 吗？此操作不可恢复。`,
      okText: '确认删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await apiDefinitionApi.deleteApiDefinition(api.apiDefinitionId);
          message.success('API 删除成功');
          // 重新获取列表
          const filters: { type?: string; status?: string; keyword?: string } = {};
          activeFilters.forEach(filter => {
            if (filter.type === 'type') filters.type = filter.value;
            else if (filter.type === 'status') filters.status = filter.value;
            else if (filter.type === 'name') filters.keyword = filter.value;
          });
          fetchApiDefinitions(filters);
        } catch (error) {
          message.error('删除失败，请重试');
        }
      },
    });
  };

  const renderApiCard = (api: ApiDefinition) => {
    const typeInfo = API_TYPE_MAP[api.type];
    const statusInfo = API_STATUS_MAP[api.status];
    const TypeIcon = typeInfo.icon;
    const StatusIcon = statusInfo.icon;

    const dropdownItems: MenuProps['items'] = [
      {
        key: 'edit',
        label: '编辑',
        icon: <EditOutlined />,
        onClick: ({ domEvent }) => handleEdit(api, domEvent),
      },
      {
        type: 'divider',
      },
      {
        key: 'delete',
        label: '删除',
        icon: <DeleteOutlined />,
        danger: true,
        onClick: ({ domEvent }) => handleDelete(api, domEvent),
      },
    ];

    return (
      <Card
        key={api.apiDefinitionId}
        className="hover:shadow-lg transition-shadow cursor-pointer rounded-xl border border-gray-200 shadow-sm hover:border-blue-300"
        onClick={() => handleNavigateToDetail(api.apiDefinitionId)}
        bodyStyle={{ padding: '16px' }}
      >
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center space-x-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-100">
              <TypeIcon style={{ fontSize: '20px' }} />
            </div>
            <div>
              <h3 className="text-lg font-semibold">{api.name}</h3>
              <div className="flex items-center gap-3 mt-1 flex-wrap">
                <div className="flex items-center">
                  <TypeIcon className="mr-1" style={{fontSize: '12px', width: '12px', height: '12px'}} />
                  <span className="text-xs text-gray-700">
                    {typeInfo.label}
                  </span>
                </div>
                <div className="flex items-center">
                  <StatusIcon className={`mr-1 ${
                    api.status === 'PUBLISHED' ? 'text-green-500' : 
                    api.status === 'DRAFT' ? 'text-blue-500' : 
                    api.status === 'DEPRECATED' ? 'text-yellow-500' : 'text-gray-500'
                  }`} style={{fontSize: '12px', width: '12px', height: '12px'}} />
                  <span className="text-xs text-gray-700">
                    {statusInfo.label}
                  </span>
                </div>
              </div>
            </div>
          </div>
          <Dropdown menu={{ items: dropdownItems }} trigger={['click']}>
            <Button
              type="text"
              icon={<MoreOutlined />}
              onClick={(e) => e.stopPropagation()}
            />
          </Dropdown>
        </div>

        <div className="space-y-2">
          {api.description && (
            <p className="text-sm text-gray-600 line-clamp-2">{api.description}</p>
          )}
          <div className="flex items-center text-xs text-gray-400 space-x-3">
            <span>v{api.version}</span>
            <span>•</span>
            <span>{api.createdAt}</span>
          </div>
        </div>
      </Card>
    );
  };

  return (
    <div className="space-y-6">
      {/* 页面标题和操作栏 */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">API 管理</h1>
          <p className="text-gray-500 mt-2">
            管理和维护 Himarket 托管的 API Definition
          </p>
        </div>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={handleCreateNew}
        >
          创建 API
        </Button>
      </div>

      {/* 搜索和筛选 */}
      <div className="space-y-4">
        {/* 搜索框 */}
        <div className="flex max-w-xl border border-gray-300 rounded-md overflow-hidden hover:border-blue-500 focus-within:border-blue-500 focus-within:shadow-sm">
          {/* 左侧：搜索类型选择器 */}
          <Select
            value={searchType}
            onChange={setSearchType}
            style={{
              width: 120,
              backgroundColor: '#f5f5f5',
            }}
            className="h-10 border-0 rounded-none"
            size="large"
            variant="borderless"
          >
            {searchTypeOptions.map(option => (
              <Select.Option key={option.value} value={option.value}>
                {option.label}
              </Select.Option>
            ))}
          </Select>

          {/* 分隔线 */}
          <div className="w-px bg-gray-300 self-stretch"></div>

          {/* 中间：搜索值输入框或选择框 */}
          {searchType === 'type' ? (
            <Select
              placeholder="请选择 API 类型"
              value={searchValue}
              onChange={(value) => {
                setSearchValue(value);
                // 立即执行搜索
                if (value) {
                  const typeLabel = API_TYPE_MAP[value as keyof typeof API_TYPE_MAP]?.label || value;
                  const labelText = `API 类型：${typeLabel}`;
                  const newFilter = { type: 'type', value, label: labelText };
                  const updatedFilters = activeFilters.filter(f => f.type !== 'type');
                  updatedFilters.push(newFilter);
                  setActiveFilters(updatedFilters);

                  const filters: { type?: string; status?: string; keyword?: string } = {};
                  updatedFilters.forEach(filter => {
                    if (filter.type === 'type') filters.type = filter.value;
                    else if (filter.type === 'status') filters.status = filter.value;
                    else if (filter.type === 'name') filters.keyword = filter.value;
                  });

                  fetchApiDefinitions(filters);
                  setSearchValue('');
                }
              }}
              style={{ flex: 1 }}
              allowClear
              onClear={clearAllFilters}
              className="h-10 border-0 rounded-none"
              size="large"
              variant="borderless"
            >
              {Object.entries(API_TYPE_MAP).map(([key, value]) => (
                <Select.Option key={key} value={key}>
                  {value.label}
                </Select.Option>
              ))}
            </Select>
          ) : searchType === 'status' ? (
            <Select
              placeholder="请选择状态"
              value={searchValue}
              onChange={(value) => {
                setSearchValue(value);
                // 立即执行搜索
                if (value) {
                  const statusLabel = API_STATUS_MAP[value as keyof typeof API_STATUS_MAP]?.label || value;
                  const labelText = `状态：${statusLabel}`;
                  const newFilter = { type: 'status', value, label: labelText };
                  const updatedFilters = activeFilters.filter(f => f.type !== 'status');
                  updatedFilters.push(newFilter);
                  setActiveFilters(updatedFilters);

                  const filters: { type?: string; status?: string; keyword?: string } = {};
                  updatedFilters.forEach(filter => {
                    if (filter.type === 'type') filters.type = filter.value;
                    else if (filter.type === 'status') filters.status = filter.value;
                    else if (filter.type === 'name') filters.keyword = filter.value;
                  });

                  fetchApiDefinitions(filters);
                  setSearchValue('');
                }
              }}
              style={{ flex: 1 }}
              allowClear
              onClear={clearAllFilters}
              className="h-10 border-0 rounded-none"
              size="large"
              variant="borderless"
            >
              {Object.entries(API_STATUS_MAP).map(([key, value]) => (
                <Select.Option key={key} value={key}>
                  {value.label}
                </Select.Option>
              ))}
            </Select>
          ) : (
            <Input
              placeholder="请输入要检索的 API 名称"
              value={searchValue}
              onChange={(e) => setSearchValue(e.target.value)}
              style={{ flex: 1 }}
              onPressEnter={handleSearch}
              allowClear
              onClear={() => setSearchValue('')}
              size="large"
              className="h-10 border-0 rounded-none"
              variant="borderless"
            />
          )}

          {/* 分隔线 */}
          <div className="w-px bg-gray-300 self-stretch"></div>

          {/* 右侧：搜索按钮 */}
          <Button
            icon={<SearchOutlined />}
            onClick={handleSearch}
            style={{ width: 48 }}
            className="h-10 border-0 rounded-none"
            size="large"
            type="text"
          />
        </div>

        {/* 筛选条件标签 */}
        {activeFilters.length > 0 && (
          <div className="flex items-center gap-2">
            <span className="text-sm text-gray-500">筛选条件：</span>
            <Space wrap>
              {activeFilters.map(filter => (
                <Tag
                  key={filter.type}
                  closable
                  onClose={() => removeFilter(filter.type)}
                  style={{
                    backgroundColor: '#f5f5f5',
                    border: '1px solid #d9d9d9',
                    borderRadius: '16px',
                    color: '#666',
                    fontSize: '12px',
                    padding: '4px 12px',
                  }}
                >
                  {filter.label}
                </Tag>
              ))}
            </Space>
            <Button
              type="link"
              size="small"
              onClick={clearAllFilters}
              className="text-blue-500 hover:text-blue-600 text-sm"
            >
              清除筛选条件
            </Button>
          </div>
        )}
      </div>

      {/* API 列表 */}
      {loading ? (
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, index) => (
            <div key={index} className="h-full rounded-lg shadow-lg bg-white p-4">
              <div className="flex items-start space-x-4">
                <Skeleton.Avatar size={48} active />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between mb-2">
                    <Skeleton.Input active size="small" style={{ width: 120 }} />
                    <Skeleton.Input active size="small" style={{ width: 60 }} />
                  </div>
                  <Skeleton.Input active size="small" style={{ width: '100%', marginBottom: 12 }} />
                  <Skeleton.Input active size="small" style={{ width: '80%', marginBottom: 8 }} />
                  <div className="flex items-center justify-between">
                    <Skeleton.Input active size="small" style={{ width: 60 }} />
                    <Skeleton.Input active size="small" style={{ width: 80 }} />
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      ) : apis.length === 0 ? (
        <div className="text-center py-12">
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="暂无 API Definition"
          />
        </div>
      ) : (
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          {apis.map(renderApiCard)}
        </div>
      )}
    </div>
  );
}

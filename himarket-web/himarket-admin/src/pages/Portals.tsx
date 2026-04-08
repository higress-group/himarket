import { PlusOutlined, MoreOutlined, LinkOutlined } from '@ant-design/icons';
import {
  Button,
  Card,
  Avatar,
  Dropdown,
  Modal,
  Form,
  Input,
  message,
  Tooltip,
  Pagination,
  Skeleton,
} from 'antd';
import { useState, useCallback, memo, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

import type { ApiResponse, PaginatedResponse, Portal } from '@/types';

import { portalApi } from '../lib/api';

import type { MenuProps } from 'antd';

// 优化的Portal卡片组件
const PortalCard = memo(
  ({
    fetchPortals,
    onNavigate,
    portal,
  }: {
    portal: Portal;
    onNavigate: (id: string) => void;
    fetchPortals: () => void;
  }) => {
    const handleCardClick = useCallback(() => {
      onNavigate(portal.portalId);
    }, [portal.portalId, onNavigate]);

    const handleLinkClick = useCallback((e: React.MouseEvent) => {
      e.stopPropagation();
    }, []);

    const dropdownItems: MenuProps['items'] = [
      {
        danger: true,
        key: 'delete',
        label: '删除',
        onClick: (e) => {
          e?.domEvent?.stopPropagation(); // 阻止事件冒泡
          Modal.confirm({
            content: '确定要删除该Portal吗？',
            onOk: () => {
              return handleDeletePortal(portal.portalId);
            },
            title: '删除Portal',
          });
        },
      },
    ];

    const handleDeletePortal = useCallback(
      (portalId: string) => {
        return portalApi
          .deletePortal(portalId)
          .then(() => {
            message.success('Portal删除成功');
            fetchPortals();
          })
          .catch((error) => {
            message.error(error?.response?.data?.message || '删除失败，请稍后重试');
            throw error;
          });
      },
      [fetchPortals],
    );

    return (
      <Card
        className="
          bg-white/60 backdrop-blur-sm rounded-2xl
          border cursor-pointer
          transition-all duration-300 ease-in-out
          hover:bg-white hover:shadow-md hover:scale-[1.02] hover:border-colorPrimary/50
          border-colorPrimary/30 active:scale-[0.98]
          relative overflow-hidden group
        "
        hoverable
        onClick={handleCardClick}
      >
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center space-x-4">
            <div className="relative">
              <Avatar
                className="bg-gradient-to-br from-colorPrimary to-colorPrimaryHover text-lg font-semibold border-none"
                size={48}
              >
                {portal.title.charAt(0).toUpperCase()}
              </Avatar>
              <div className="absolute -bottom-1 -right-1 w-4 h-4 bg-green-400 rounded-full border-2 border-white"></div>
            </div>
            <div>
              <h3 className="text-xl font-bold text-gray-800 mb-1">{portal.title}</h3>
              <p className="text-sm text-gray-500">{portal.description}</p>
            </div>
          </div>
          <Dropdown menu={{ items: dropdownItems }} trigger={['click']}>
            <Button
              className="hover:bg-gray-100 rounded-full"
              icon={<MoreOutlined />}
              onClick={(e) => e.stopPropagation()}
              type="text"
            />
          </Dropdown>
        </div>

        <div className="space-y-6">
          <div className="flex items-center space-x-3 p-3 bg-blue-50 rounded-lg border border-blue-100">
            <LinkOutlined className="h-4 w-4 text-colorPrimary" />
            <Tooltip
              color="#000"
              placement="top"
              title={portal.portalDomainConfig?.[portal.portalDomainConfig.length - 1]?.domain}
            >
              <a
                className="text-colorPrimary hover:text-colorPrimary font-medium text-sm"
                href={`http://${portal.portalDomainConfig?.[portal.portalDomainConfig.length - 1]?.domain}`}
                onClick={handleLinkClick}
                rel="noopener noreferrer"
                style={{
                  cursor: 'pointer',
                  display: 'inline-block',
                  maxWidth: 200,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  verticalAlign: 'bottom',
                  whiteSpace: 'nowrap',
                }}
                target="_blank"
              >
                {portal.portalDomainConfig?.[portal.portalDomainConfig.length - 1]?.domain}
              </a>
            </Tooltip>
          </div>

          <div className="space-y-3">
            {/* 第一行：账号密码登录 + 开发者自动审批 */}
            <div className="grid grid-cols-2 gap-4">
              <div className="flex items-center justify-between p-2 bg-gray-50 rounded-md">
                <span className="text-xs font-medium text-gray-600">账号密码登录</span>
                <span
                  className={`px-2 py-1 rounded-full text-xs font-medium ${
                    portal.portalSettingConfig?.builtinAuthEnabled
                      ? 'bg-green-100 text-green-700'
                      : 'bg-red-100 text-red-700'
                  }`}
                >
                  {portal.portalSettingConfig?.builtinAuthEnabled ? '支持' : '不支持'}
                </span>
              </div>

              <div className="flex items-center justify-between p-2 bg-gray-50 rounded-md">
                <span className="text-xs font-medium text-gray-600">开发者自动审批</span>
                <span
                  className={`px-2 py-1 rounded-full text-xs font-medium ${
                    portal.portalSettingConfig?.autoApproveDevelopers
                      ? 'bg-green-100 text-green-700'
                      : 'bg-yellow-100 text-yellow-700'
                  }`}
                >
                  {portal.portalSettingConfig?.autoApproveDevelopers ? '是' : '否'}
                </span>
              </div>
            </div>

            {/* 第二行：订阅自动审批 + 域名配置 */}
            <div className="grid grid-cols-2 gap-4">
              <div className="flex items-center justify-between p-2 bg-gray-50 rounded-md">
                <span className="text-xs font-medium text-gray-600">订阅自动审批</span>
                <span
                  className={`px-2 py-1 rounded-full text-xs font-medium ${
                    portal.portalSettingConfig?.autoApproveSubscriptions
                      ? 'bg-green-100 text-green-700'
                      : 'bg-yellow-100 text-yellow-700'
                  }`}
                >
                  {portal.portalSettingConfig?.autoApproveSubscriptions ? '是' : '否'}
                </span>
              </div>

              <div className="flex items-center justify-between p-2 bg-gray-50 rounded-md">
                <span className="text-xs font-medium text-gray-600">域名配置</span>
                <span className="px-2 py-1 bg-purple-100 text-purple-700 rounded-full text-xs font-medium">
                  {portal.portalDomainConfig?.length || 0}个
                </span>
              </div>
            </div>
          </div>
        </div>
      </Card>
    );
  },
);

PortalCard.displayName = 'PortalCard';

export default function Portals() {
  const navigate = useNavigate();
  const [portals, setPortals] = useState<Portal[]>([]);
  const [loading, setLoading] = useState<boolean>(true); // 初始状态为 loading
  const [error, setError] = useState<string | null>(null);
  const [isModalVisible, setIsModalVisible] = useState<boolean>(false);
  const [form] = Form.useForm();
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 12,
    total: 0,
  });

  const fetchPortals = useCallback((page = 1, size = 12) => {
    setLoading(true);
    portalApi
      .getPortals({ page, size })
      .then((res) => {
        const r = res as unknown as ApiResponse<PaginatedResponse<Portal>>;
        const list = r?.data?.content || [];
        const portals: Portal[] = list.map((item) => ({
          adminId: item.adminId,
          description: item.description,
          name: item.name,
          portalDomainConfig: item.portalDomainConfig || [],
          portalId: item.portalId,
          portalSettingConfig: item.portalSettingConfig,
          portalUiConfig: item.portalUiConfig,
          title: item.name,
        }));
        setPortals(portals);
        setPagination({
          current: page,
          pageSize: size,
          total: r?.data?.totalElements || 0,
        });
      })
      .catch((err: Error) => {
        setError(err?.message || '加载失败');
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  useEffect(() => {
    setError(null);
    fetchPortals(1, 12);
  }, [fetchPortals]);

  // 处理分页变化
  const handlePaginationChange = (page: number, pageSize: number) => {
    fetchPortals(page, pageSize);
  };

  const handleCreatePortal = useCallback(() => {
    setIsModalVisible(true);
  }, []);

  const handleModalOk = useCallback(async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);

      const newPortal = {
        description: values.description,
        name: values.name,
        title: values.title,
      };

      await portalApi.createPortal(newPortal);
      message.success('Portal创建成功');
      setIsModalVisible(false);
      form.resetFields();

      fetchPortals();
    } catch {
      // message.error(error?.message || "创建失败");
    } finally {
      setLoading(false);
    }
  }, [fetchPortals, form]);

  const handleModalCancel = useCallback(() => {
    setIsModalVisible(false);
    form.resetFields();
  }, [form]);

  const handlePortalClick = useCallback(
    (portalId: string) => {
      navigate(`/portals/${portalId}`);
    },
    [navigate],
  );

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Portal</h1>
          <p className="text-gray-500 mt-2">管理和配置您的开发者门户</p>
        </div>
        <Button icon={<PlusOutlined />} onClick={handleCreatePortal} type="primary">
          创建 Portal
        </Button>
      </div>
      {error && <div className="text-red-500">{error}</div>}

      {loading ? (
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: pagination.pageSize || 12 }).map((_, index) => (
            <div className="h-full rounded-lg shadow-lg bg-white p-4" key={index}>
              <div className="flex items-start space-x-4">
                <Skeleton.Avatar active size={48} />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between mb-2">
                    <Skeleton.Input active size="small" style={{ width: 120 }} />
                    <Skeleton.Input active size="small" style={{ width: 60 }} />
                  </div>
                  <Skeleton.Input active size="small" style={{ marginBottom: 12, width: '100%' }} />
                  <Skeleton.Input active size="small" style={{ marginBottom: 8, width: '80%' }} />
                  <div className="flex items-center justify-between">
                    <Skeleton.Input active size="small" style={{ width: 60 }} />
                    <Skeleton.Input active size="small" style={{ width: 80 }} />
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <>
          <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
            {portals.map((portal) => (
              <PortalCard
                fetchPortals={() => fetchPortals(pagination.current, pagination.pageSize)}
                key={portal.portalId}
                onNavigate={handlePortalClick}
                portal={portal}
              />
            ))}
          </div>

          {pagination.total > 0 && (
            <div className="flex justify-center mt-6">
              <Pagination
                current={pagination.current}
                onChange={handlePaginationChange}
                pageSize={pagination.pageSize}
                pageSizeOptions={['6', '12', '24', '48']}
                showQuickJumper
                showSizeChanger
                showTotal={(total) => `共 ${total} 条`}
                total={pagination.total}
              />
            </div>
          )}
        </>
      )}

      <Modal
        confirmLoading={loading}
        onCancel={handleModalCancel}
        onOk={handleModalOk}
        open={isModalVisible}
        title="创建Portal"
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            label="名称"
            name="name"
            rules={[{ message: '请输入Portal名称', required: true }]}
          >
            <Input placeholder="请输入Portal名称" />
          </Form.Item>

          {/* <Form.Item
            name="title"
            label="标题"
            rules={[{ required: true, message: "请输入Portal标题" }]}
          >
            <Input placeholder="请输入Portal标题" />
          </Form.Item> */}

          <Form.Item label="描述" name="description" rules={[{ message: '请输入描述' }]}>
            <Input.TextArea placeholder="请输入Portal描述" rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

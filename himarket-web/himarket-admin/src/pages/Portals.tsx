import { useState, useCallback, memo, useEffect } from "react";
import { useNavigate } from "react-router-dom";
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
} from "antd";
import { PlusOutlined, MoreOutlined, LinkOutlined } from "@ant-design/icons";
import type { MenuProps } from "antd";
import { portalApi } from "../lib/api";

import { Portal } from '@/types'

// 优化的Portal卡片组件
const PortalCard = memo(
  ({
    portal,
    onNavigate,
    fetchPortals,
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

    const dropdownItems: MenuProps["items"] = [
     
      {
        key: "delete",
        label: "删除",
        danger: true,
        onClick: (e) => {
          e?.domEvent?.stopPropagation(); // 阻止事件冒泡
          Modal.confirm({
            title: "删除Portal",
            content: "确定要删除该Portal吗？",
            onOk: () => {
              return handleDeletePortal(portal.portalId);
            },
          });
        },
      },
    ];

    const handleDeletePortal = useCallback((portalId: string) => {
      return portalApi.deletePortal(portalId).then(() => {
        message.success("Portal删除成功");
        fetchPortals();
      }).catch((error) => {
        message.error(error?.response?.data?.message || "删除失败，请稍后重试");
        throw error;
      });
    }, [fetchPortals]);

    return (
      <Card
        className="cursor-pointer hover:shadow-xl transition-all duration-300 hover:scale-[1.02] border border-gray-100 hover:border-blue-300 bg-gradient-to-br from-white to-gray-50/30"
        onClick={handleCardClick}
        bodyStyle={{ padding: "20px" }}
      >
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center space-x-4">
            <div className="relative">
              <Avatar
                size={48}
                className="bg-gradient-to-br from-blue-500 to-blue-600 shadow-lg"
                style={{ fontSize: "18px", fontWeight: "600" }}
              >
                {portal.title.charAt(0).toUpperCase()}
              </Avatar>
              <div className="absolute -bottom-1 -right-1 w-4 h-4 bg-green-400 rounded-full border-2 border-white"></div>
            </div>
            <div>
              <h3 className="text-xl font-bold text-gray-800 mb-1">
                {portal.title}
              </h3>
              <p className="text-sm text-gray-500">{portal.description}</p>
            </div>
          </div>
          <Dropdown menu={{ items: dropdownItems }} trigger={["click"]}>
            <Button
              type="text"
              icon={<MoreOutlined />}
              onClick={(e) => e.stopPropagation()}
              className="hover:bg-gray-100 rounded-full"
            />
          </Dropdown>
        </div>

        <div className="space-y-6">
          <div className="flex items-center space-x-3 p-3 bg-blue-50 rounded-lg border border-blue-100">
            <LinkOutlined className="h-4 w-4 text-blue-500" />
            <Tooltip
              title={portal.portalDomainConfig?.[portal.portalDomainConfig.length - 1]?.domain}
              placement="top"
              color="#000"
            >
              <a
                href={`http://${portal.portalDomainConfig?.[portal.portalDomainConfig.length - 1]?.domain}`}
                target="_blank"
                rel="noopener noreferrer"
                className="text-blue-600 hover:text-blue-700 font-medium text-sm"
                onClick={handleLinkClick}
                style={{
                  display: "inline-block",
                  maxWidth: 200,
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  whiteSpace: "nowrap",
                  verticalAlign: "bottom",
                  cursor: "pointer",
                }}
              >
                {portal.portalDomainConfig?.[portal.portalDomainConfig.length - 1]?.domain}
              </a>
            </Tooltip>
          </div>

          <div className="space-y-3">
            {/* 第一行：账号密码登录 + 开发者自动审批 */}
            <div className="grid grid-cols-2 gap-4">
              <div className="flex items-center justify-between p-2 bg-gray-50 rounded-md">
                <span className="text-xs font-medium text-gray-600">
                  账号密码登录
                </span>
                <span
                  className={`px-2 py-1 rounded-full text-xs font-medium ${
                    portal.portalSettingConfig?.builtinAuthEnabled
                      ? "bg-green-100 text-green-700"
                      : "bg-red-100 text-red-700"
                  }`}
                >
                  {portal.portalSettingConfig?.builtinAuthEnabled
                    ? "支持"
                    : "不支持"}
                </span>
              </div>

              <div className="flex items-center justify-between p-2 bg-gray-50 rounded-md">
                <span className="text-xs font-medium text-gray-600">
                  开发者自动审批
                </span>
                <span
                  className={`px-2 py-1 rounded-full text-xs font-medium ${
                    portal.portalSettingConfig?.autoApproveDevelopers
                      ? "bg-green-100 text-green-700"
                      : "bg-yellow-100 text-yellow-700"
                  }`}
                >
                  {portal.portalSettingConfig?.autoApproveDevelopers
                    ? "是"
                    : "否"}
                </span>
              </div>
            </div>

            {/* 第二行：订阅自动审批 + 域名配置 */}
            <div className="grid grid-cols-2 gap-4">
              <div className="flex items-center justify-between p-2 bg-gray-50 rounded-md">
                <span className="text-xs font-medium text-gray-600">
                  订阅自动审批
                </span>
                <span
                  className={`px-2 py-1 rounded-full text-xs font-medium ${
                    portal.portalSettingConfig?.autoApproveSubscriptions
                      ? "bg-green-100 text-green-700"
                      : "bg-yellow-100 text-yellow-700"
                  }`}
                >
                  {portal.portalSettingConfig?.autoApproveSubscriptions
                    ? "是"
                    : "否"}
                </span>
              </div>

              <div className="flex items-center justify-between p-2 bg-gray-50 rounded-md">
                <span className="text-xs font-medium text-gray-600">
                  域名配置
                </span>
                <span className="px-2 py-1 bg-purple-100 text-purple-700 rounded-full text-xs font-medium">
                  {portal.portalDomainConfig?.length || 0}个
                </span>
              </div>
            </div>
          </div>

          <div className="text-center pt-4 border-t border-gray-100">
            <div className="inline-flex items-center space-x-2 text-sm text-gray-500 hover:text-blue-600 transition-colors">
              <span onClick={handleCardClick}>点击查看详情</span>
              <svg
                className="w-4 h-4"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M9 5l7 7-7 7"
                />
              </svg>
            </div>
          </div>
        </div>
      </Card>
    );
  }
);

PortalCard.displayName = "PortalCard";

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
    portalApi.getPortals({ page, size }).then((res: any) => {
      const list = res?.data?.content || [];
      const portals: Portal[] = list.map((item: any) => ({
        portalId: item.portalId,
        name: item.name,
        title: item.name,
        description: item.description,
        adminId: item.adminId,
        portalSettingConfig: item.portalSettingConfig,
        portalUiConfig: item.portalUiConfig,
        portalDomainConfig: item.portalDomainConfig || [],
      }));
      setPortals(portals);
      setPagination({
        current: page,
        pageSize: size,
        total: res?.data?.totalElements || 0,
      });
    }).catch((err: any) => {
      setError(err?.message || "加载失败");
    }).finally(() => {
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
        name: values.name,
        title: values.title,
        description: values.description,
      };

      await portalApi.createPortal(newPortal);
      message.success("Portal创建成功");
      setIsModalVisible(false);
      form.resetFields();

      fetchPortals()
    } catch (error: any) {
      // message.error(error?.message || "创建失败");
    } finally {
      setLoading(false);
    }
  }, [form]);

  const handleModalCancel = useCallback(() => {
    setIsModalVisible(false);
    form.resetFields();
  }, [form]);

  const handlePortalClick = useCallback(
    (portalId: string) => {
      navigate(`/portals/detail?id=${portalId}`);
    },
    [navigate]
  );

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Portal</h1>
          <p className="text-gray-500 mt-2">管理和配置您的开发者门户</p>
        </div>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={handleCreatePortal}
        >
          创建 Portal
        </Button>
      </div>
      {error && <div className="text-red-500">{error}</div>}
      
      {loading ? (
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: pagination.pageSize || 12 }).map((_, index) => (
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
      ) : (
        <>
          <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
            {portals.map((portal) => (
              <PortalCard
                key={portal.portalId}
                portal={portal}
                onNavigate={handlePortalClick}
                fetchPortals={() => fetchPortals(pagination.current, pagination.pageSize)}
              />
            ))}
          </div>

          {pagination.total > 0 && (
            <div className="flex justify-center mt-6">
              <Pagination
                current={pagination.current}
                pageSize={pagination.pageSize}
                total={pagination.total}
                onChange={handlePaginationChange}
                showSizeChanger
                showQuickJumper
                showTotal={(total) => `共 ${total} 条`}
                pageSizeOptions={['6', '12', '24', '48']}
              />
            </div>
          )}
        </>
      )}

      <Modal
        title="创建Portal"
        open={isModalVisible}
        onOk={handleModalOk}
        onCancel={handleModalCancel}
        confirmLoading={loading}
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="名称"
            rules={[{ required: true, message: "请输入Portal名称" }]}
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

          <Form.Item
            name="description"
            label="描述"
            rules={[{ message: "请输入描述" }]}
          >
            <Input.TextArea rows={3} placeholder="请输入Portal描述" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

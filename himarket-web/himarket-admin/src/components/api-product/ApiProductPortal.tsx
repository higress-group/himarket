import {
  PlusOutlined,
  DeleteOutlined,
  ExclamationCircleOutlined,
  GlobalOutlined,
  CheckCircleFilled,
  MinusCircleFilled,
} from '@ant-design/icons';
import { Button, Modal, message, Tooltip, Table } from 'antd';
import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';

import { DataTable } from '@/components/common/DataTable';
import { apiProductApi, portalApi } from '@/lib/api';
import { copyToClipboard } from '@/lib/utils';
import type { ApiProduct, Publication } from '@/types/api-product';

interface ApiProductPortalProps {
  apiProduct: ApiProduct;
}

interface Portal {
  portalId: string;
  portalName: string;
  autoApproveSubscription: boolean;
}

export function ApiProductPortal({ apiProduct }: ApiProductPortalProps) {
  const [publishedPortals, setPublishedPortals] = useState<Publication[]>([]);
  const [allPortals, setAllPortals] = useState<Portal[]>([]);
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [selectedPortalIds, setSelectedPortalIds] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [portalLoading, setPortalLoading] = useState(false);
  const [modalLoading, setModalLoading] = useState(false);

  // 分页状态
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);

  const navigate = useNavigate();
  const lastPublishedKeyRef = useRef<string>('');
  const allPortalsFetchedRef = useRef(false);

  // 获取已发布的门户列表
  useEffect(() => {
    if (!apiProduct.productId) {
      return;
    }
    const key = `${apiProduct.productId}-${currentPage}-${pageSize}`;
    if (lastPublishedKeyRef.current === key) {
      return;
    }
    lastPublishedKeyRef.current = key;
    fetchPublishedPortals();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [apiProduct.productId, currentPage, pageSize]);

  // 获取所有门户列表
  useEffect(() => {
    if (allPortalsFetchedRef.current) {
      return;
    }
    allPortalsFetchedRef.current = true;
    fetchAllPortals();
  }, []);

  const fetchPublishedPortals = async () => {
    setLoading(true);
    try {
      const res = await apiProductApi.getApiProductPublications(apiProduct.productId, {
        page: currentPage,
        size: pageSize,
      });
      setPublishedPortals(
        res.data.content?.map((item: unknown) => ({
          ...(item as Record<string, unknown>),
          autoApproveSubscription:
            (item as Record<string, unknown>).autoApproveSubscriptions || false,
        })) || [],
      );
      setTotal(res.data.totalElements || 0);
    } catch (error) {
      console.error('获取已发布门户失败:', error);
      // message.error('获取已发布门户失败')
    } finally {
      setLoading(false);
    }
  };

  const fetchAllPortals = async () => {
    setPortalLoading(true);
    try {
      const res = await portalApi.getPortals({
        page: 1,
        size: 500, // 获取所有门户
      });
      setAllPortals(
        res.data.content?.map((item: unknown) => {
          const record = item as Record<string, unknown>;
          const portalSettingConfig = record.portalSettingConfig as
            | Record<string, unknown>
            | undefined;
          return {
            ...record,
            autoApproveSubscription:
              (portalSettingConfig?.autoApproveSubscriptions as boolean) || false,
            portalName: record.name as string,
          };
        }) || [],
      );
    } catch (error) {
      console.error('获取门户列表失败:', error);
      // message.error('获取门户列表失败')
    } finally {
      setPortalLoading(false);
    }
  };

  const handlePageChange = (page: number, size?: number) => {
    setCurrentPage(page);
    if (size) {
      setPageSize(size);
    }
  };

  const columns = [
    {
      key: 'portalInfo',
      render: (_: unknown, record: Publication) => (
        <div>
          <Tooltip placement="topLeft" title={record.portalName}>
            <button
              className="text-blue-600 hover:text-blue-500 font-medium cursor-pointer bg-transparent border-none p-0 truncate block max-w-[200px] text-left text-xs"
              onClick={() => navigate(`/portals/${record.portalId}`)}
              type="button"
            >
              {record.portalName}
            </button>
          </Tooltip>
          <Tooltip title="点击复制">
            <button
              className="text-xs text-gray-400 mt-0.5 truncate max-w-[200px] cursor-pointer hover:text-blue-500 bg-transparent border-none p-0 block text-left"
              onClick={() =>
                copyToClipboard(record.portalId).then(() => {
                  message.success('已复制到剪贴板');
                })
              }
              type="button"
            >
              {record.portalId}
            </button>
          </Tooltip>
        </div>
      ),
      title: '门户名称/ID',
      width: 400,
    },
    {
      key: 'autoApprove',
      render: (_: unknown, record: Publication) => (
        <div className="flex items-center">
          {record.autoApproveSubscriptions ? (
            <>
              <CheckCircleFilled className="text-green-500 mr-1" style={{ fontSize: '10px' }} />
              <span className="text-xs text-gray-900">已开启</span>
            </>
          ) : (
            <>
              <MinusCircleFilled className="text-gray-400 mr-1" style={{ fontSize: '10px' }} />
              <span className="text-xs text-gray-900">已关闭</span>
            </>
          )}
        </div>
      ),
      title: '订阅自动审批',
      width: 160,
    },
    {
      key: 'action',
      render: (_: unknown, record: Publication) => (
        <Button
          danger
          icon={<DeleteOutlined />}
          onClick={() => handleDelete(record.publicationId, record.portalName)}
          type="link"
        >
          移除
        </Button>
      ),
      title: '操作',
      width: 120,
    },
  ];

  const modalColumns = [
    {
      key: 'portalInfo',
      render: (_: unknown, record: Portal) => (
        <div>
          <div className="text-xs font-normal text-gray-900 truncate">{record.portalName}</div>
          <div className="text-xs text-gray-500">{record.portalId}</div>
        </div>
      ),
      title: '门户信息',
    },
    {
      key: 'autoApprove',
      render: (_: unknown, record: Portal) => (
        <div className="flex items-center">
          {record.autoApproveSubscription ? (
            <>
              <CheckCircleFilled className="text-green-500 mr-1" style={{ fontSize: '10px' }} />
              <span className="text-xs text-gray-900">已开启</span>
            </>
          ) : (
            <>
              <MinusCircleFilled className="text-gray-400 mr-1" style={{ fontSize: '10px' }} />
              <span className="text-xs text-gray-900">已关闭</span>
            </>
          )}
        </div>
      ),
      title: '订阅自动审批',
      width: 140,
    },
  ];

  const handleAdd = () => {
    setIsModalVisible(true);
  };

  const handleDelete = (publicationId: string, portalName: string) => {
    Modal.confirm({
      cancelText: '取消',
      content: `确定要从API产品中移除门户 "${portalName}" 吗？此操作不可恢复。`,
      icon: <ExclamationCircleOutlined />,
      okText: '确认移除',
      okType: 'danger',
      onOk() {
        apiProductApi
          .cancelPublishToPortal(apiProduct.productId, publicationId)
          .then(() => {
            message.success('移除成功');
            fetchPublishedPortals();
          })
          .catch((error) => {
            console.error('移除失败:', error);
            // message.error('移除失败')
          });
      },
      title: '确认移除',
    });
  };

  const handleModalOk = async () => {
    if (selectedPortalIds.length === 0) {
      message.warning('请至少选择一个门户');
      return;
    }

    setModalLoading(true);
    try {
      // 批量发布到选中的门户
      for (const portalId of selectedPortalIds) {
        await apiProductApi.publishToPortal(apiProduct.productId, portalId);
      }
      message.success(`成功发布到 ${selectedPortalIds.length} 个门户`);
      setSelectedPortalIds([]);
      setIsModalVisible(false);
      // 重新获取已发布的门户列表
      fetchPublishedPortals();
    } catch (error) {
      console.error('发布失败:', error);
      // message.error('发布失败')
    } finally {
      setModalLoading(false);
    }
  };

  const handleModalCancel = () => {
    setIsModalVisible(false);
    setSelectedPortalIds([]);
  };

  return (
    <div className="p-6 space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold mb-2">发布门户</h1>
          <p className="text-gray-600">管理API产品发布的门户</p>
        </div>
        <Button icon={<PlusOutlined />} onClick={handleAdd} type="primary">
          发布到门户
        </Button>
      </div>

      {publishedPortals.length === 0 && !loading ? (
        <div className="text-center py-8 text-gray-500">
          <p>暂未发布到任何门户</p>
        </div>
      ) : (
        <DataTable<Publication>
          columns={columns}
          dataSource={publishedPortals}
          loading={loading}
          pagination={{
            current: currentPage,
            onChange: handlePageChange,
            pageSize: pageSize,
            total: total,
          }}
          rowKey="portalId"
        />
      )}

      <Modal
        cancelText="取消"
        confirmLoading={modalLoading}
        destroyOnClose
        okText="发布"
        onCancel={handleModalCancel}
        onOk={handleModalOk}
        open={isModalVisible}
        title="发布到门户"
        width={700}
      >
        <div className="border border-gray-200 rounded-lg overflow-hidden">
          <Table
            className="portal-selection-table"
            columns={modalColumns}
            dataSource={allPortals.filter(
              (portal) =>
                !publishedPortals.some((published) => published.portalId === portal.portalId),
            )}
            loading={portalLoading}
            locale={{
              emptyText: (
                <div className="py-8">
                  <div className="text-gray-400 mb-2">
                    <GlobalOutlined style={{ fontSize: '24px' }} />
                  </div>
                  <div className="text-gray-500 text-sm">暂无可发布的门户</div>
                </div>
              ),
            }}
            pagination={false}
            rowClassName={(record: Portal) =>
              selectedPortalIds.includes(record.portalId)
                ? 'bg-blue-50 hover:bg-blue-100'
                : 'hover:bg-gray-50'
            }
            rowKey="portalId"
            rowSelection={{
              columnWidth: 50,
              onChange: (selectedRowKeys: React.Key[]) => {
                setSelectedPortalIds(selectedRowKeys as string[]);
              },
              selectedRowKeys: selectedPortalIds,
              type: 'checkbox',
            }}
            scroll={{ y: 350 }}
            size="middle"
          />
        </div>
      </Modal>
    </div>
  );
}

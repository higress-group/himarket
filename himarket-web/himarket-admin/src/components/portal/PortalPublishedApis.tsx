import { EyeOutlined, DeleteOutlined, ExclamationCircleOutlined } from '@ant-design/icons';
import { Table, Modal, Button, Space, message, Empty } from 'antd';
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

import { apiProductApi, portalApi } from '@/lib/api';
import { ProductTypeMap } from '@/lib/utils';
import type { Portal, ApiProduct, Publication } from '@/types';

interface PortalApiProductsProps {
  portal: Portal;
}

export function PortalPublishedApis({ portal }: PortalApiProductsProps) {
  const navigate = useNavigate();
  const [apiProducts, setApiProducts] = useState<Publication[]>([]);
  const [apiProductsOptions, setApiProductsOptions] = useState<ApiProduct[]>([]);
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [selectedApiIds, setSelectedApiIds] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalLoading, setModalLoading] = useState(false);

  // 分页状态
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);

  useEffect(() => {
    if (portal.portalId) {
      fetchApiProducts();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [portal.portalId, currentPage, pageSize]);

  const fetchApiProducts = () => {
    setLoading(true);
    portalApi
      .getPortalPublications(portal.portalId, {
        page: currentPage,
        size: pageSize,
      })
      .then((res) => {
        setApiProducts(res.data.content);
        setTotal(res.data.totalElements || 0);
      })
      .finally(() => {
        setLoading(false);
      });
  };

  useEffect(() => {
    if (isModalVisible) {
      setModalLoading(true);
      apiProductApi
        .getApiProducts({
          page: 1,
          size: 500, // 获取所有可用的API
          status: 'READY',
        })
        .then((res) => {
          // 过滤掉已发布在该门户里的api
          setApiProductsOptions(
            res.data.content.filter(
              (api: ApiProduct) =>
                !apiProducts.some(
                  (publication: Publication) => publication.productId === api.productId,
                ),
            ),
          );
        })
        .finally(() => {
          setModalLoading(false);
        });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isModalVisible]); // 移除apiProducts依赖，避免重复请求

  const handlePageChange = (page: number, size?: number) => {
    setCurrentPage(page);
    if (size) {
      setPageSize(size);
    }
  };

  const columns = [
    {
      key: 'nameAndId',
      render: (_: unknown, record: Publication) => (
        <div>
          <div className="text-sm font-medium text-gray-900 truncate">{record.productName}</div>
          <div className="text-xs text-gray-500 truncate">{record.productId}</div>
        </div>
      ),
      title: '名称/ID',
      width: 280,
    },
    {
      dataIndex: 'productType',
      key: 'productType',
      render: (text: string) => ProductTypeMap[text] || text,
      title: '类型',
      width: 120,
    },
    {
      dataIndex: 'description',
      key: 'description',
      title: '描述',
      width: 400,
    },
    {
      key: 'action',
      render: (_: unknown, record: Publication) => (
        <Space size="middle">
          <Button
            icon={<EyeOutlined />}
            onClick={() => {
              navigate(`/api-products/${record.productId}`);
            }}
            type="link"
          >
            查看
          </Button>

          <Button
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(record.publicationId, record.productId, record.productName)}
            type="link"
          >
            移除
          </Button>
        </Space>
      ),
      title: '操作',
      width: 180,
    },
  ];

  const modalColumns = [
    {
      dataIndex: 'name',
      key: 'name',
      render: (_: unknown, record: ApiProduct) => (
        <div>
          <div className="text-sm font-medium text-gray-900 truncate">{record.name}</div>
          <div className="text-xs text-gray-500 truncate">{record.productId}</div>
        </div>
      ),
      title: '名称',
      width: 280,
    },
    {
      dataIndex: 'type',
      key: 'type',
      render: (type: string) => ProductTypeMap[type] || type,
      title: '类型',
      width: 120,
    },
    {
      dataIndex: 'description',
      key: 'description',
      title: '描述',
      width: 300,
    },
  ];

  const handleDelete = (publicationId: string, productId: string, productName: string) => {
    Modal.confirm({
      cancelText: '取消',
      content: `确定要从门户中移除API产品 "${productName}" 吗？此操作不可恢复。`,
      icon: <ExclamationCircleOutlined />,
      okText: '确认移除',
      okType: 'danger',
      onOk() {
        apiProductApi
          .cancelPublishToPortal(productId, publicationId)
          .then(() => {
            message.success('移除成功');
            fetchApiProducts();
            setIsModalVisible(false);
          })
          .catch(() => {
            // message.error('移除失败')
          });
      },
      title: '确认移除',
    });
  };

  const handlePublish = async () => {
    if (selectedApiIds.length === 0) {
      message.warning('请至少选择一个API');
      return;
    }

    setModalLoading(true);
    try {
      // 批量发布选中的API
      for (const productId of selectedApiIds) {
        await apiProductApi.publishToPortal(productId, portal.portalId);
      }
      message.success(`成功发布 ${selectedApiIds.length} 个API`);
      setSelectedApiIds([]);
      fetchApiProducts();
      setIsModalVisible(false);
    } catch (_error) {
      // message.error('发布失败')
    } finally {
      setModalLoading(false);
    }
  };

  const handleModalCancel = () => {
    setIsModalVisible(false);
    setSelectedApiIds([]);
  };

  return (
    <div className="p-6 space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold mb-2">API Product</h1>
          <p className="text-gray-600">管理在此Portal中发布的API产品</p>
        </div>
        <Button onClick={() => setIsModalVisible(true)} type="primary">
          发布API产品
        </Button>
      </div>

      <Table
        columns={columns}
        dataSource={apiProducts}
        loading={loading}
        locale={{
          emptyText: (
            <Empty description="暂无已发布的API产品" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ),
        }}
        pagination={{
          current: currentPage,
          onChange: handlePageChange,
          onShowSizeChange: handlePageChange,
          pageSize: pageSize,
          showQuickJumper: true,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 条`,
          total: total,
        }}
        rowKey="productId"
      />

      <Modal
        cancelText="取消"
        confirmLoading={modalLoading}
        okButtonProps={{
          disabled: selectedApiIds.length === 0,
        }}
        okText="发布"
        onCancel={handleModalCancel}
        onOk={handlePublish}
        open={isModalVisible}
        title="发布API产品"
        width={800}
      >
        <Table
          columns={modalColumns}
          dataSource={apiProductsOptions}
          loading={modalLoading}
          pagination={false}
          rowKey="productId"
          rowSelection={{
            columnWidth: 60,
            onChange: (selectedRowKeys) => {
              setSelectedApiIds(selectedRowKeys as string[]);
            },
            selectedRowKeys: selectedApiIds,
            type: 'checkbox',
          }}
          scroll={{ y: 400 }}
        />
      </Modal>
    </div>
  );
}

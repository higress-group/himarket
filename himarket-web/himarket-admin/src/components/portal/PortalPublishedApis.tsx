import { DeleteOutlined, ExclamationCircleOutlined } from '@ant-design/icons';
import { Modal, Button, message, Empty, Tooltip, Table } from 'antd';
import { useState, useEffect, useCallback } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import { DataTable } from '@/components/common/DataTable';
import { useLocale } from '@/contexts/LocaleContext';
import { apiProductApi, portalApi } from '@/lib/api';
import { ProductTypeMap, copyToClipboard } from '@/lib/utils';
import type { Portal, ApiProduct, Publication } from '@/types';

interface PortalApiProductsProps {
  portal: Portal;
}

export function PortalPublishedApis({ portal }: PortalApiProductsProps) {
  const { t } = useLocale();
  const navigate = useNavigate();
  const location = useLocation();
  const currentPath = `${location.pathname}${location.search}${location.hash}`;
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

  const handleOpenProductDetail = useCallback(
    (productId: string) => {
      navigate(`/api-products/${productId}`, {
        state: { from: currentPath },
      });
    },
    [currentPath, navigate],
  );

  const columns = [
    {
      key: 'nameAndId',
      render: (_: unknown, record: Publication) => (
        <div>
          <Tooltip placement="topLeft" title={record.productName}>
            <button
              className="text-blue-600 hover:text-blue-500 font-medium cursor-pointer bg-transparent border-none p-0 truncate block max-w-[200px] text-left text-xs"
              onClick={() => handleOpenProductDetail(record.productId)}
              type="button"
            >
              {record.productName}
            </button>
          </Tooltip>
          <Tooltip title={t('common.copyToClipboard')}>
            <button
              className="text-xs text-gray-400 mt-0.5 truncate max-w-[200px] cursor-pointer hover:text-blue-500 bg-transparent border-none p-0 block text-left"
              onClick={() =>
                copyToClipboard(record.productId).then(() => {
                  message.success(t('common.copiedToClipboard'));
                })
              }
              type="button"
            >
              {record.productId}
            </button>
          </Tooltip>
        </div>
      ),
      title: t('page.categoryDetail.nameAndId'),
      width: 280,
    },
    {
      dataIndex: 'productType',
      key: 'productType',
      render: (text: string) => ProductTypeMap[text] || text,
      title: t('common.type'),
      width: 120,
    },
    {
      dataIndex: 'description',
      key: 'description',
      title: t('common.description'),
      width: 400,
    },
    {
      key: 'action',
      render: (_: unknown, record: Publication) => (
        <Button
          danger
          icon={<DeleteOutlined />}
          onClick={() => handleDelete(record.publicationId, record.productId, record.productName)}
          type="link"
        >
          {t('common.remove')}
        </Button>
      ),
      title: t('common.operation'),
      width: 120,
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
      title: t('common.name'),
      width: 280,
    },
    {
      dataIndex: 'type',
      key: 'type',
      render: (type: string) => ProductTypeMap[type] || type,
      title: t('common.type'),
      width: 120,
    },
    {
      dataIndex: 'description',
      key: 'description',
      title: t('common.description'),
      width: 300,
    },
  ];

  const handleDelete = (publicationId: string, productId: string, productName: string) => {
    Modal.confirm({
      cancelText: t('common.cancel'),
      content: t('portal.products.removeConfirm', { name: productName }),
      icon: <ExclamationCircleOutlined />,
      okText: t('common.confirmRemove'),
      okType: 'danger',
      onOk() {
        apiProductApi
          .cancelPublishToPortal(productId, publicationId)
          .then(() => {
            message.success(t('common.removeSuccess'));
            fetchApiProducts();
            setIsModalVisible(false);
          })
          .catch(() => {
            // message.error('移除失败')
          });
      },
      title: t('common.confirmRemove'),
    });
  };

  const handlePublish = async () => {
    if (selectedApiIds.length === 0) {
      message.warning(t('portal.products.selectApi'));
      return;
    }

    setModalLoading(true);
    try {
      // 批量发布选中的API
      for (const productId of selectedApiIds) {
        await apiProductApi.publishToPortal(productId, portal.portalId);
      }
      message.success(t('portal.products.publishSuccess', { count: selectedApiIds.length }));
      setSelectedApiIds([]);
      fetchApiProducts();
      setIsModalVisible(false);
    } catch (_error) {
      message.error(t('portal.products.publishFailed'));
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
          <h1 className="text-2xl font-bold mb-2">{t('portal.products.title')}</h1>
          <p className="text-gray-600">{t('portal.products.description')}</p>
        </div>
        <Button onClick={() => setIsModalVisible(true)} type="primary">
          {t('portal.products.publishApi')}
        </Button>
      </div>

      <DataTable<Publication>
        columns={columns}
        dataSource={apiProducts}
        loading={loading}
        locale={{
          emptyText: (
            <Empty description={t('portal.products.empty')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ),
        }}
        pagination={{
          current: currentPage,
          onChange: handlePageChange,
          pageSize: pageSize,
          total: total,
        }}
        rowKey="productId"
      />

      <Modal
        cancelText={t('common.cancel')}
        confirmLoading={modalLoading}
        okButtonProps={{
          disabled: selectedApiIds.length === 0,
        }}
        okText={t('portal.products.publish')}
        onCancel={handleModalCancel}
        onOk={handlePublish}
        open={isModalVisible}
        title={t('portal.products.publishApi')}
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
            onChange: (selectedRowKeys: React.Key[]) => {
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

import { DeleteOutlined, SendOutlined, CloseOutlined } from '@ant-design/icons';
import { Button, Modal, Select, message } from 'antd';
import { useEffect, useState } from 'react';

import { useLocale } from '@/contexts/LocaleContext';
import { apiProductApi, portalApi } from '@/lib/api';
import type { ApiProduct } from '@/types/api-product';

interface BatchActionBarProps {
  selectedIds: Set<string>;
  products: ApiProduct[];
  onComplete: () => void;
  onCancel: () => void;
  inline?: boolean;
}

export default function BatchActionBar({
  inline,
  onCancel,
  onComplete,
  products,
  selectedIds,
}: BatchActionBarProps) {
  const { t } = useLocale();
  const selectedProducts = products.filter((p) => selectedIds.has(p.productId));
  const canPublish =
    selectedProducts.length > 0 &&
    selectedProducts.every((p) => p.status === 'READY' || p.status === 'PUBLISHED');

  const [publishModalVisible, setPublishModalVisible] = useState(false);
  const [selectedPortalId, setSelectedPortalId] = useState<string | undefined>();
  const [portals, setPortals] = useState<{ portalId: string; name: string }[]>([]);
  const [portalsLoading, setPortalsLoading] = useState(false);
  const [publishing, setPublishing] = useState(false);

  useEffect(() => {
    if (publishModalVisible) {
      setPortalsLoading(true);
      portalApi
        .getPortals({ page: 1, size: 100 })
        .then((res: { data: { content: Array<{ portalId: string; name: string }> } }) => {
          setPortals(res.data.content || []);
        })
        .finally(() => setPortalsLoading(false));
    }
  }, [publishModalVisible]);

  const handleBatchDelete = () => {
    Modal.confirm({
      cancelText: t('common.cancel'),
      content: t('product.batch.unrecoverable'),
      okText: t('common.confirmDelete'),
      okType: 'danger',
      onOk: async () => {
        const results = await Promise.allSettled(
          [...selectedIds].map((id) => apiProductApi.deleteApiProduct(id)),
        );
        const succeeded = results.filter((r) => r.status === 'fulfilled').length;
        const failedResults = results
          .map((r, i) => ({ id: [...selectedIds][i], result: r }))
          .filter((item) => item.result.status === 'rejected');

        if (failedResults.length > 0) {
          const failedDetails = failedResults.map((item) => {
            const product = products.find((p) => p.productId === item.id);
            const reason = (item.result as PromiseRejectedResult).reason;
            const errorMsg =
              reason?.response?.data?.message || reason?.message || t('common.unknown');
            return `${product?.name || item.id}: ${errorMsg}`;
          });
          Modal.warning({
            content: (
              <div className="mt-2">
                <p className="font-medium mb-1">{t('product.batch.failureDetail')}</p>
                <ul className="list-disc pl-4 text-sm text-gray-600">
                  {failedDetails.map((detail, i) => (
                    <li key={i}>{detail}</li>
                  ))}
                </ul>
              </div>
            ),
            title: t('product.batch.partialFailed', {
              failed: failedResults.length,
              success: succeeded,
            }),
          });
        } else {
          message.success(t('product.batch.deleteSuccess', { count: succeeded }));
        }
        onComplete();
      },
      title: t('product.batch.batchDeleteConfirm', { count: selectedIds.size }),
    });
  };

  const handleBatchPublish = async () => {
    if (!selectedPortalId) return;
    setPublishing(true);
    try {
      const results = await Promise.allSettled(
        [...selectedIds].map((id) => apiProductApi.publishToPortal(id, selectedPortalId)),
      );
      const succeeded = results.filter((r) => r.status === 'fulfilled').length;
      const failedResults = results
        .map((r, i) => ({ id: [...selectedIds][i], result: r }))
        .filter((item) => item.result.status === 'rejected');

      if (failedResults.length > 0) {
        const failedDetails = failedResults.map((item) => {
          const product = products.find((p) => p.productId === item.id);
          const reason = (item.result as PromiseRejectedResult).reason;
          const errorMsg =
            reason?.response?.data?.message || reason?.message || t('common.unknown');
          return `${product?.name || item.id}: ${errorMsg}`;
        });
        Modal.warning({
          content: (
            <div className="mt-2">
              <p className="font-medium mb-1">{t('product.batch.failureDetail')}</p>
              <ul className="list-disc pl-4 text-sm text-gray-600">
                {failedDetails.map((detail, i) => (
                  <li key={i}>{detail}</li>
                ))}
              </ul>
            </div>
          ),
          title: t('product.batch.partialFailed', {
            failed: failedResults.length,
            success: succeeded,
          }),
        });
      } else {
        message.success(t('product.batch.publishSuccess', { count: succeeded }));
      }
      setPublishModalVisible(false);
      setSelectedPortalId(undefined);
      onComplete();
    } finally {
      setPublishing(false);
    }
  };

  return (
    <>
      {inline ? (
        <div className="flex items-center gap-2">
          <Button danger icon={<DeleteOutlined />} onClick={handleBatchDelete} type="primary">
            {t('product.batch.batchDelete')}
          </Button>
          <Button
            disabled={!canPublish}
            icon={<SendOutlined />}
            onClick={() => setPublishModalVisible(true)}
            type="primary"
          >
            {t('product.batch.batchPublish')}
          </Button>
        </div>
      ) : (
        <div className="flex items-center gap-4 p-4 bg-colorPrimaryBg rounded-xl border border-colorPrimaryBorderHover mb-4">
          <span className="text-sm font-medium">
            {t('product.batch.selectedCount', { count: selectedIds.size })}
          </span>
          <Button danger icon={<DeleteOutlined />} onClick={handleBatchDelete} size="small">
            {t('product.batch.batchDelete')}
          </Button>
          <Button
            disabled={!canPublish}
            icon={<SendOutlined />}
            onClick={() => setPublishModalVisible(true)}
            size="small"
            type="primary"
          >
            {t('product.batch.batchPublish')}
          </Button>
          <Button icon={<CloseOutlined />} onClick={onCancel} size="small" type="text">
            {t('product.batch.cancelSelection')}
          </Button>
        </div>
      )}

      <Modal
        cancelText={t('common.cancel')}
        confirmLoading={publishing}
        okButtonProps={{ disabled: !selectedPortalId }}
        okText={t('product.batch.confirmPublish')}
        onCancel={() => {
          setPublishModalVisible(false);
          setSelectedPortalId(undefined);
        }}
        onOk={handleBatchPublish}
        open={publishModalVisible}
        title={t('product.batch.publishToPortal')}
      >
        <p className="mb-3 text-gray-500">
          {t('product.batch.publishDescription', { count: selectedIds.size })}
        </p>
        <Select
          loading={portalsLoading}
          onChange={setSelectedPortalId}
          options={portals.map((p) => ({ label: p.name, value: p.portalId }))}
          placeholder={t('product.batch.selectPortal')}
          style={{ width: '100%' }}
          value={selectedPortalId}
        />
      </Modal>
    </>
  );
}

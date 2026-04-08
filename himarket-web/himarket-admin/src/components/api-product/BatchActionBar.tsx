import { DeleteOutlined, SendOutlined, CloseOutlined } from '@ant-design/icons';
import { Button, Modal, Select, message } from 'antd';
import { useEffect, useState } from 'react';

import { apiProductApi, portalApi } from '@/lib/api';
import type { ApiProduct } from '@/types/api-product';

interface BatchActionBarProps {
  selectedIds: Set<string>;
  products: ApiProduct[];
  onComplete: () => void;
  onCancel: () => void;
}

export default function BatchActionBar({
  onCancel,
  onComplete,
  products,
  selectedIds,
}: BatchActionBarProps) {
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
      cancelText: '取消',
      content: '此操作不可恢复。',
      okText: '确认删除',
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
            const errorMsg = reason?.response?.data?.message || reason?.message || '未知错误';
            return `${product?.name || item.id}: ${errorMsg}`;
          });
          Modal.warning({
            content: (
              <div className="mt-2">
                <p className="font-medium mb-1">失败详情：</p>
                <ul className="list-disc pl-4 text-sm text-gray-600">
                  {failedDetails.map((detail, i) => (
                    <li key={i}>{detail}</li>
                  ))}
                </ul>
              </div>
            ),
            title: `成功 ${succeeded} 个，失败 ${failedResults.length} 个`,
          });
        } else {
          message.success(`成功删除 ${succeeded} 个产品`);
        }
        onComplete();
      },
      title: `确认批量删除 ${selectedIds.size} 个产品？`,
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
          const errorMsg = reason?.response?.data?.message || reason?.message || '未知错误';
          return `${product?.name || item.id}: ${errorMsg}`;
        });
        Modal.warning({
          content: (
            <div className="mt-2">
              <p className="font-medium mb-1">失败详情：</p>
              <ul className="list-disc pl-4 text-sm text-gray-600">
                {failedDetails.map((detail, i) => (
                  <li key={i}>{detail}</li>
                ))}
              </ul>
            </div>
          ),
          title: `成功 ${succeeded} 个，失败 ${failedResults.length} 个`,
        });
      } else {
        message.success(`成功发布 ${succeeded} 个产品`);
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
      <div className="flex items-center gap-4 p-4 bg-colorPrimaryBg rounded-xl border border-colorPrimaryBorderHover mb-4">
        <span className="text-sm font-medium">已选择 {selectedIds.size} 项</span>
        <Button danger icon={<DeleteOutlined />} onClick={handleBatchDelete}>
          批量删除
        </Button>
        <Button
          disabled={!canPublish}
          icon={<SendOutlined />}
          onClick={() => setPublishModalVisible(true)}
          type="primary"
        >
          批量发布
        </Button>
        <Button icon={<CloseOutlined />} onClick={onCancel} type="text">
          取消选择
        </Button>
      </div>

      <Modal
        cancelText="取消"
        confirmLoading={publishing}
        okButtonProps={{ disabled: !selectedPortalId }}
        okText="确认发布"
        onCancel={() => {
          setPublishModalVisible(false);
          setSelectedPortalId(undefined);
        }}
        onOk={handleBatchPublish}
        open={publishModalVisible}
        title="批量发布到门户"
      >
        <p className="mb-3 text-gray-500">将选中的 {selectedIds.size} 个产品发布到指定门户</p>
        <Select
          loading={portalsLoading}
          onChange={setSelectedPortalId}
          options={portals.map((p) => ({ label: p.name, value: p.portalId }))}
          placeholder="请选择目标门户"
          style={{ width: '100%' }}
          value={selectedPortalId}
        />
      </Modal>
    </>
  );
}

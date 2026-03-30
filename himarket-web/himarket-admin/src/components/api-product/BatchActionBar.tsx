import { useEffect, useState } from 'react';
import { Button, Modal, Select, message } from 'antd';
import { DeleteOutlined, SendOutlined, CloseOutlined } from '@ant-design/icons';
import type { ApiProduct } from '@/types/api-product';
import { apiProductApi, portalApi } from '@/lib/api';

interface BatchActionBarProps {
  selectedIds: Set<string>;
  products: ApiProduct[];
  onComplete: () => void;
  onCancel: () => void;
}

export default function BatchActionBar({ selectedIds, products, onComplete, onCancel }: BatchActionBarProps) {
  const selectedProducts = products.filter(p => selectedIds.has(p.productId));
  const canPublish = selectedProducts.length > 0 && selectedProducts.every(p => p.status === 'READY' || p.status === 'PUBLISHED');

  const [publishModalVisible, setPublishModalVisible] = useState(false);
  const [selectedPortalId, setSelectedPortalId] = useState<string | undefined>();
  const [portals, setPortals] = useState<{ portalId: string; name: string }[]>([]);
  const [portalsLoading, setPortalsLoading] = useState(false);
  const [publishing, setPublishing] = useState(false);

  useEffect(() => {
    if (publishModalVisible) {
      setPortalsLoading(true);
      portalApi.getPortals({ page: 1, size: 100 }).then((res: any) => {
        setPortals(res.data.content || []);
      }).finally(() => setPortalsLoading(false));
    }
  }, [publishModalVisible]);

  const handleBatchDelete = () => {
    Modal.confirm({
      title: `确认批量删除 ${selectedIds.size} 个产品？`,
      content: '此操作不可恢复。',
      okText: '确认删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        const results = await Promise.allSettled(
          [...selectedIds].map(id => apiProductApi.deleteApiProduct(id))
        );
        const succeeded = results.filter(r => r.status === 'fulfilled').length;
        const failedResults = results
          .map((r, i) => ({ result: r, id: [...selectedIds][i] }))
          .filter(item => item.result.status === 'rejected');

        if (failedResults.length > 0) {
          const failedDetails = failedResults.map(item => {
            const product = products.find(p => p.productId === item.id);
            const reason = (item.result as PromiseRejectedResult).reason;
            const errorMsg = reason?.response?.data?.message || reason?.message || '未知错误';
            return `${product?.name || item.id}: ${errorMsg}`;
          });
          Modal.warning({
            title: `成功 ${succeeded} 个，失败 ${failedResults.length} 个`,
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
          });
        } else {
          message.success(`成功删除 ${succeeded} 个产品`);
        }
        onComplete();
      },
    });
  };

  const handleBatchPublish = async () => {
    if (!selectedPortalId) return;
    setPublishing(true);
    try {
      const results = await Promise.allSettled(
        [...selectedIds].map(id => apiProductApi.publishToPortal(id, selectedPortalId))
      );
      const succeeded = results.filter(r => r.status === 'fulfilled').length;
      const failedResults = results
        .map((r, i) => ({ result: r, id: [...selectedIds][i] }))
        .filter(item => item.result.status === 'rejected');

      if (failedResults.length > 0) {
        const failedDetails = failedResults.map(item => {
          const product = products.find(p => p.productId === item.id);
          const reason = (item.result as PromiseRejectedResult).reason;
          const errorMsg = reason?.response?.data?.message || reason?.message || '未知错误';
          return `${product?.name || item.id}: ${errorMsg}`;
        });
        Modal.warning({
          title: `成功 ${succeeded} 个，失败 ${failedResults.length} 个`,
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
          type="primary"
          icon={<SendOutlined />}
          disabled={!canPublish}
          onClick={() => setPublishModalVisible(true)}
        >
          批量发布
        </Button>
        <Button type="text" icon={<CloseOutlined />} onClick={onCancel}>
          取消选择
        </Button>
      </div>

      <Modal
        title="批量发布到门户"
        open={publishModalVisible}
        onCancel={() => { setPublishModalVisible(false); setSelectedPortalId(undefined); }}
        onOk={handleBatchPublish}
        okText="确认发布"
        cancelText="取消"
        confirmLoading={publishing}
        okButtonProps={{ disabled: !selectedPortalId }}
      >
        <p className="mb-3 text-gray-500">
          将选中的 {selectedIds.size} 个产品发布到指定门户
        </p>
        <Select
          placeholder="请选择目标门户"
          value={selectedPortalId}
          onChange={setSelectedPortalId}
          loading={portalsLoading}
          style={{ width: '100%' }}
          options={portals.map(p => ({ label: p.name, value: p.portalId }))}
        />
      </Modal>
    </>
  );
}

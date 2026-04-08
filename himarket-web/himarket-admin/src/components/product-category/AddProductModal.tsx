import { Modal, Table, message } from 'antd';
import { useState, useEffect } from 'react';

import { apiProductApi } from '@/lib/api';
import { bindProductsToCategory } from '@/lib/productCategoryApi';
import { ProductTypeMap } from '@/lib/utils';
import type { ApiProduct } from '@/types/api-product';

interface AddProductModalProps {
  visible: boolean;
  categoryId: string;
  onCancel: () => void;
  onSuccess: () => void;
}

const AddProductModal: React.FC<AddProductModalProps> = ({
  categoryId,
  onCancel,
  onSuccess,
  visible,
}) => {
  const [availableProducts, setAvailableProducts] = useState<ApiProduct[]>([]);
  const [selectedProductIds, setSelectedProductIds] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [addLoading, setAddLoading] = useState(false);

  // Fetch available products
  const fetchAvailableProducts = async () => {
    if (!categoryId) return;

    try {
      setLoading(true);
      const response = await apiProductApi.getApiProducts({
        excludeCategoryId: categoryId,
        page: 1,
        size: 100,
      });

      setAvailableProducts(response.data.content || []);
    } catch (error) {
      console.error('Failed to fetch available products:', error);
      message.error('获取可用产品失败');
    } finally {
      setLoading(false);
    }
  };

  // Reset state when modal opens
  useEffect(() => {
    if (visible && categoryId) {
      setSelectedProductIds([]);
      fetchAvailableProducts();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible, categoryId]);

  // Handle add products
  const handleAddProducts = async () => {
    if (selectedProductIds.length === 0) {
      message.warning('请选择要添加的产品');
      return;
    }

    try {
      setAddLoading(true);
      await bindProductsToCategory(categoryId, selectedProductIds);
      message.success('产品添加成功');
      onSuccess();
      onCancel();
    } catch (error) {
      console.error('Failed to add products:', error);
      message.error('添加产品失败');
    } finally {
      setAddLoading(false);
    }
  };

  // 完全按照Portal表单的列定义
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

  return (
    <Modal
      cancelText="取消"
      confirmLoading={addLoading}
      okButtonProps={{
        disabled: selectedProductIds.length === 0,
      }}
      okText="添加"
      onCancel={onCancel}
      onOk={handleAddProducts}
      open={visible}
      title="添加API产品"
      width={800}
    >
      <Table
        columns={modalColumns}
        dataSource={availableProducts}
        loading={loading}
        pagination={false}
        rowKey="productId"
        rowSelection={{
          columnWidth: 60,
          onChange: (selectedRowKeys) => {
            setSelectedProductIds(selectedRowKeys as string[]);
          },
          selectedRowKeys: selectedProductIds,
          type: 'checkbox',
        }}
        scroll={{ y: 400 }}
      />
    </Modal>
  );
};

export default AddProductModal;

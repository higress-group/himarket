import { useState, useEffect } from 'react';
import {
  Modal,
  Table,
  message
} from 'antd';
import { bindProductsToCategory } from '@/lib/productCategoryApi';
import { apiProductApi } from '@/lib/api';
import { ProductTypeMap } from '@/lib/utils';
import type { ApiProduct } from '@/types/api-product';

interface AddProductModalProps {
  visible: boolean;
  categoryId: string;
  onCancel: () => void;
  onSuccess: () => void;
}

const AddProductModal: React.FC<AddProductModalProps> = ({
  visible,
  categoryId,
  onCancel,
  onSuccess
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
        page: 1,
        size: 100,
        excludeCategoryId: categoryId
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
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      width: 280,
      render: (_: any, record: ApiProduct) => (
        <div>
          <div className="text-sm font-medium text-gray-900 truncate">
            {record.name}
          </div>
          <div className="text-xs text-gray-500 truncate">
            {record.productId}
          </div>
        </div>
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 120,
      render: (type: string) => ProductTypeMap[type] || type,
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      width: 300,
    },
  ];

  return (
    <Modal
      title="添加API产品"
      open={visible}
      onOk={handleAddProducts}
      onCancel={onCancel}
      okText="添加"
      cancelText="取消"
      width={800}
      confirmLoading={addLoading}
      okButtonProps={{
        disabled: selectedProductIds.length === 0
      }}
    >
      <Table
        columns={modalColumns}
        dataSource={availableProducts}
        rowKey="productId"
        loading={loading}
        pagination={false}
        scroll={{ y: 400 }}
        rowSelection={{
          type: 'checkbox',
          selectedRowKeys: selectedProductIds,
          onChange: (selectedRowKeys) => {
            setSelectedProductIds(selectedRowKeys as string[]);
          },
          columnWidth: 60,
        }}
      />
    </Modal>
  );
};

export default AddProductModal;

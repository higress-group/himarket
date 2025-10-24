import { useState, useEffect } from 'react';
import { Button, Card, Table, Modal, Form, Input, message, Popconfirm, Pagination } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { getProductCategoriesByPage, createProductCategory, updateProductCategory, deleteProductCategory } from '@/lib/productCategoryApi';
import type { ProductCategory, ProductCategoryPage } from '@/types/product-category';

export default function ProductCategories() {
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingCategory, setEditingCategory] = useState<ProductCategory | null>(null);
  const [form] = Form.useForm();
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  });

  // 获取产品类别列表
  const fetchCategories = async (page: number = 1, pageSize: number = 10) => {
    try {
      setLoading(true);
      const response = await getProductCategoriesByPage(page, pageSize);
      const pageData: ProductCategoryPage = response.data;
      setCategories(pageData.content);
      setPagination({
        current: pageData.number,
        pageSize: pageData.size,
        total: pageData.totalElements,
      });
    } catch (error) {
      console.error('获取产品类别失败:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCategories();
  }, []);

  // 处理分页变化
  const handlePageChange = (page: number, pageSize?: number) => {
    fetchCategories(page, pageSize || pagination.pageSize);
  };

  // 处理页面大小变化
  const handlePageSizeChange = (size: number) => {
    setPagination({ ...pagination, pageSize: size });
    fetchCategories(1, size);
  };

  // 处理创建类别
  const handleCreate = async (values: any) => {
    try {
      await createProductCategory(values);
      message.success('创建成功');
      setModalVisible(false);
      form.resetFields();
      fetchCategories(pagination.current, pagination.pageSize);
    } catch (error: any) {
      console.error('创建失败:', error);
    }
  };

  // 处理更新类别
  const handleUpdate = async (values: any) => {
    if (!editingCategory) return;
    
    try {
      await updateProductCategory(editingCategory.categoryId, values);
      message.success('更新成功');
      setModalVisible(false);
      form.resetFields();
      setEditingCategory(null);
      fetchCategories(pagination.current, pagination.pageSize);
    } catch (error: any) {
      console.error('更新失败:', error);
    }
  };

  // 处理删除类别
  const handleDelete = async (categoryId: string) => {
    try {
      await deleteProductCategory(categoryId);
      message.success('删除成功');
      fetchCategories(pagination.current, pagination.pageSize);
    } catch (error: any) {
      console.error('删除失败:', error);
    }
  };

  // 打开编辑弹窗
  const handleEdit = (category: ProductCategory) => {
    setEditingCategory(category);
    form.setFieldsValue({
      code: category.code,
      name: category.name,
      description: category.description,
    });
    setModalVisible(true);
  };

  const columns: ColumnsType<ProductCategory> = [
    {
      title: '编码',
      dataIndex: 'code',
      key: 'code',
    },
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
    },
    {
      title: '操作',
      key: 'action',
      render: (_, record) => (
        <div className="flex space-x-2">
          <Button 
            type="link" 
            size="small"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
          >
            编辑
          </Button>
          <Popconfirm
            title="确认删除"
            description={`确定要删除类别 "${record.name}" 吗？`}
            onConfirm={() => handleDelete(record.categoryId)}
            okText="确认"
            cancelText="取消"
          >
            <Button type="link" danger size="small" icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">产品类别管理</h1>
          <p className="text-gray-500 mt-2">
            管理和配置产品类别
          </p>
        </div>
        <Button 
          type="primary" 
          icon={<PlusOutlined />}
          onClick={() => {
            setEditingCategory(null);
            form.resetFields();
            setModalVisible(true);
          }}
        >
          创建类别
        </Button>
      </div>

      <Card>
        <Table
          loading={loading}
          dataSource={categories}
          columns={columns}
          rowKey="categoryId"
          pagination={false}
        />
        <div className="mt-4 flex justify-end">
          <Pagination
            current={pagination.current}
            pageSize={pagination.pageSize}
            total={pagination.total}
            onChange={handlePageChange}
            onShowSizeChange={handlePageSizeChange}
            showSizeChanger
            showQuickJumper
            showTotal={(total) => `共 ${total} 条记录`}
          />
        </div>
      </Card>

      {/* 创建/编辑类别弹窗 */}
      <Modal
        title={editingCategory ? "编辑产品类别" : "创建产品类别"}
        open={modalVisible}
        onOk={() => form.submit()}
        onCancel={() => {
          setModalVisible(false);
          form.resetFields();
          setEditingCategory(null);
        }}
        width={500}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={editingCategory ? handleUpdate : handleCreate}
        >
          <Form.Item
            label="编码"
            name="code"
            rules={[{ required: true, message: '请输入编码' }]}
          >
            <Input placeholder="请输入编码" disabled={!!editingCategory} />
          </Form.Item>

          <Form.Item
            label="名称"
            name="name"
            rules={[{ required: true, message: '请输入名称' }]}
          >
            <Input placeholder="请输入名称" />
          </Form.Item>

          <Form.Item
            label="描述"
            name="description"
          >
            <Input.TextArea placeholder="请输入描述" rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
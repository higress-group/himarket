import { CameraOutlined } from '@ant-design/icons';
import { Modal, Form, Input, message, Radio, Space } from 'antd';
import { useState, useEffect } from 'react';

import { createProductCategory, updateProductCategory } from '@/lib/productCategoryApi';
import type {
  ProductCategory,
  CreateProductCategoryParam,
  UpdateProductCategoryParam,
  ProductIcon,
} from '@/types/product-category';

import type { UploadFile } from 'antd/es/upload/interface';

interface CategoryFormModalProps {
  visible: boolean;
  onCancel: () => void;
  onSuccess: () => void;
  category?: ProductCategory | null;
  isEdit?: boolean;
}

const CategoryFormModal: React.FC<CategoryFormModalProps> = ({
  category,
  isEdit = false,
  onCancel,
  onSuccess,
  visible,
}) => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [iconMode, setIconMode] = useState<'URL' | 'BASE64'>('URL');
  const [fileList, setFileList] = useState<UploadFile[]>([]);

  useEffect(() => {
    if (visible) {
      if (isEdit && category) {
        // 编辑模式：填充表单
        form.setFieldsValue({
          description: category.description || '',
          name: category.name,
        });

        if (category.icon) {
          setIconMode(category.icon.type);
          if (category.icon.type === 'URL') {
            form.setFieldValue('iconUrl', category.icon.value);
          }
        }
      } else {
        // 创建模式：清空表单
        form.resetFields();
        setIconMode('URL');
        setFileList([]);
      }
    }
  }, [visible, isEdit, category, form]);

  const handleCancel = () => {
    form.resetFields();
    setFileList([]);
    onCancel();
  };

  const getBase64 = (file: File): Promise<string> =>
    new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.readAsDataURL(file);
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = (error) => reject(error);
    });

  const handleSubmit = async () => {
    try {
      setLoading(true);
      const values = await form.validateFields();

      // 构建图标对象
      let icon: ProductIcon | undefined;

      if (iconMode === 'URL' && values.iconUrl) {
        icon = { type: 'URL', value: values.iconUrl };
      } else if (iconMode === 'BASE64' && values.icon) {
        // 使用上传的图片（BASE64格式）
        icon = { type: 'BASE64', value: values.icon };
      }

      const categoryData: CreateProductCategoryParam | UpdateProductCategoryParam = {
        description: values.description,
        icon,
        name: values.name,
      };

      // 调用相应的API
      if (isEdit && category) {
        // 调用更新API
        await updateProductCategory(
          category.categoryId,
          categoryData as UpdateProductCategoryParam,
        );
        message.success('更新成功');
      } else {
        // 调用创建API
        await createProductCategory(categoryData as CreateProductCategoryParam);
        message.success('创建成功');
      }

      onSuccess();
      handleCancel();
    } catch (error) {
      console.error('操作失败:', error);
      message.error(isEdit ? '更新失败' : '创建失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      confirmLoading={loading}
      okText={isEdit ? '更新' : '创建'}
      onCancel={handleCancel}
      onOk={handleSubmit}
      open={visible}
      title={isEdit ? '编辑类别' : '创建类别'}
      width={600}
    >
      <Form className="mt-4" form={form} layout="vertical">
        <Form.Item
          label="名称"
          name="name"
          rules={[
            { message: '请输入名称', required: true },
            { max: 50, message: '名称不能超过50个字符' },
          ]}
        >
          <Input placeholder="如：数据分析、API网关、支付服务等" />
        </Form.Item>

        <Form.Item
          label="描述"
          name="description"
          rules={[{ max: 256, message: '描述不能超过256个字符' }]}
        >
          <Input.TextArea
            maxLength={256}
            placeholder="描述用途和特点，帮助用户更好地理解..."
            rows={3}
            showCount
          />
        </Form.Item>

        <Form.Item label="Icon设置" style={{ marginBottom: '16px' }}>
          <Space direction="vertical" style={{ width: '100%' }}>
            <Radio.Group
              onChange={(e) => {
                setIconMode(e.target.value);
                setFileList([]);
                form.setFieldValue('iconUrl', '');
              }}
              value={iconMode}
            >
              <Radio value="URL">图片链接</Radio>
              <Radio value="BASE64">本地上传</Radio>
            </Radio.Group>

            {iconMode === 'URL' ? (
              <Form.Item
                name="iconUrl"
                rules={[
                  {
                    message: '请输入有效的图片链接',
                    type: 'url',
                  },
                ]}
                style={{ marginBottom: 0 }}
              >
                <Input placeholder="请输入图片链接地址" />
              </Form.Item>
            ) : (
              <Form.Item name="icon" style={{ marginBottom: 0 }}>
                <div
                  onClick={() => {
                    const input = document.createElement('input');
                    input.type = 'file';
                    input.accept = 'image/*';
                    input.onchange = (e) => {
                      const file = (e.target as HTMLInputElement).files?.[0];
                      if (file) {
                        const maxSize = 16 * 1024; // 16KB
                        if (file.size > maxSize) {
                          message.error(
                            `图片大小不能超过 16KB，当前图片大小为 ${Math.round(file.size / 1024)}KB`,
                          );
                          return;
                        }

                        const newFileList = [
                          {
                            name: file.name,
                            status: 'done' as const,
                            uid: Date.now().toString(),
                            url: URL.createObjectURL(file),
                          },
                        ];
                        setFileList(newFileList);
                        getBase64(file).then((base64) => {
                          form.setFieldsValue({ icon: base64 });
                        });
                      }
                    };
                    input.click();
                  }}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      e.currentTarget.click();
                    }
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.borderColor = '#1890ff';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.borderColor = '#d9d9d9';
                  }}
                  role="button"
                  style={{
                    alignItems: 'center',
                    border: '1px dashed #d9d9d9',
                    borderRadius: '8px',
                    cursor: 'pointer',
                    display: 'flex',
                    height: '80px',
                    justifyContent: 'center',
                    position: 'relative',
                    transition: 'border-color 0.3s',
                    width: '80px',
                  }}
                  tabIndex={0}
                >
                  {fileList.length >= 1 ? (
                    <img
                      alt="uploaded"
                      src={fileList[0]?.url ?? ''}
                      style={{
                        borderRadius: '6px',
                        height: '100%',
                        objectFit: 'cover',
                        width: '100%',
                      }}
                    />
                  ) : (
                    <div
                      style={{
                        alignItems: 'center',
                        color: '#999',
                        display: 'flex',
                        flexDirection: 'column',
                        justifyContent: 'center',
                      }}
                    >
                      <CameraOutlined style={{ fontSize: '16px', marginBottom: '6px' }} />
                      <span style={{ color: '#999', fontSize: '12px' }}>上传图片</span>
                    </div>
                  )}
                </div>
              </Form.Item>
            )}
          </Space>
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default CategoryFormModal;

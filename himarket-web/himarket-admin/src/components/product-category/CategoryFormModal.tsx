import { useState, useEffect } from 'react';
import {
  Modal,
  Form,
  Input,
  message,
  Radio,
  Space,
} from 'antd';
import { CameraOutlined } from '@ant-design/icons';
import type { UploadFile } from 'antd/es/upload/interface';
import type { ProductCategory, CreateProductCategoryParam, UpdateProductCategoryParam, ProductIcon } from '@/types/product-category';
import { createProductCategory, updateProductCategory } from '@/lib/productCategoryApi';

interface CategoryFormModalProps {
  visible: boolean;
  onCancel: () => void;
  onSuccess: () => void;
  category?: ProductCategory | null;
  isEdit?: boolean;
}

const CategoryFormModal: React.FC<CategoryFormModalProps> = ({
  visible,
  onCancel,
  onSuccess,
  category,
  isEdit = false,
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
          name: category.name,
          description: category.description || '',
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
      reader.onerror = error => reject(error);
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
        name: values.name,
        description: values.description,
        icon,
      };

      // 调用相应的API
      if (isEdit && category) {
        // 调用更新API
        await updateProductCategory(category.categoryId, categoryData as UpdateProductCategoryParam);
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
      title={isEdit ? '编辑类别' : '创建类别'}
      open={visible}
      onOk={handleSubmit}
      onCancel={handleCancel}
      confirmLoading={loading}
      width={600}
      okText={isEdit ? '更新' : '创建'}
    >
      <Form form={form} layout="vertical" className="mt-4">
        <Form.Item
          label="名称"
          name="name"
          rules={[
            { required: true, message: '请输入名称' },
            { max: 50, message: '名称不能超过50个字符' }
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
            placeholder="描述用途和特点，帮助用户更好地理解..."
            rows={3}
            showCount
            maxLength={256}
          />
        </Form.Item>

        <Form.Item label="Icon设置" style={{ marginBottom: '16px' }}>
          <Space direction="vertical" style={{ width: '100%' }}>
            <Radio.Group 
              value={iconMode} 
              onChange={(e) => {
                setIconMode(e.target.value);
                setFileList([]);
                form.setFieldValue('iconUrl', '');
              }}
            >
              <Radio value="URL">图片链接</Radio>
              <Radio value="BASE64">本地上传</Radio>
            </Radio.Group>
            
            {iconMode === 'URL' ? (
              <Form.Item 
                name="iconUrl" 
                style={{ marginBottom: 0 }}
                rules={[
                  { 
                    type: 'url', 
                    message: '请输入有效的图片链接' 
                  }
                ]}
              >
                <Input placeholder="请输入图片链接地址" />
              </Form.Item>
            ) : (
              <Form.Item name="icon" style={{ marginBottom: 0 }}>
                <div 
                  style={{ 
                    width: '80px', 
                    height: '80px',
                    border: '1px dashed #d9d9d9',
                    borderRadius: '8px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    cursor: 'pointer',
                    transition: 'border-color 0.3s',
                    position: 'relative'
                  }}
                  onClick={() => {
                    const input = document.createElement('input');
                    input.type = 'file';
                    input.accept = 'image/*';
                    input.onchange = (e) => {
                      const file = (e.target as HTMLInputElement).files?.[0];
                      if (file) {
                        const maxSize = 16 * 1024; // 16KB
                        if (file.size > maxSize) {
                          message.error(`图片大小不能超过 16KB，当前图片大小为 ${Math.round(file.size / 1024)}KB`);
                          return;
                        }
                        
                        const newFileList = [{
                          uid: Date.now().toString(),
                          name: file.name,
                          status: 'done' as const,
                          url: URL.createObjectURL(file)
                        }];
                        setFileList(newFileList);
                        getBase64(file).then((base64) => {
                          form.setFieldsValue({ icon: base64 });
                        });
                      }
                    };
                    input.click();
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.borderColor = '#1890ff';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.borderColor = '#d9d9d9';
                  }}
                >
                  {fileList.length >= 1 ? (
                    <img 
                      src={fileList[0].url} 
                      alt="uploaded" 
                      style={{ width: '100%', height: '100%', objectFit: 'cover', borderRadius: '6px' }}
                    />
                  ) : (
                    <div style={{ 
                      display: 'flex', 
                      flexDirection: 'column', 
                      alignItems: 'center', 
                      justifyContent: 'center',
                      color: '#999'
                    }}>
                      <CameraOutlined style={{ fontSize: '16px', marginBottom: '6px' }} />
                      <span style={{ fontSize: '12px', color: '#999' }}>上传图片</span>
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

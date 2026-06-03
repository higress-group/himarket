import { CameraOutlined } from '@ant-design/icons';
import { Modal, Form, Input, message, Radio, Space } from 'antd';
import { useState, useEffect } from 'react';

import { useLocale } from '@/contexts/LocaleContext';
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
  const { t } = useLocale();
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
        message.success(t('page.categoryDetail.updateSuccess'));
      } else {
        // 调用创建API
        await createProductCategory(categoryData as CreateProductCategoryParam);
        message.success(t('page.categoryDetail.createSuccess'));
      }

      onSuccess();
      handleCancel();
    } catch (error) {
      console.error('操作失败:', error);
      message.error(
        isEdit ? t('page.categoryDetail.updateFailed') : t('page.categoryDetail.createFailed'),
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      cancelText={t('common.cancel')}
      confirmLoading={loading}
      okText={isEdit ? t('action.update') : t('action.create')}
      onCancel={handleCancel}
      onOk={handleSubmit}
      open={visible}
      title={isEdit ? t('page.categoryDetail.editTitle') : t('page.categoryDetail.createTitle')}
      width={600}
    >
      <Form className="mt-4" form={form} layout="vertical">
        <Form.Item
          label={t('common.name')}
          name="name"
          rules={[
            { message: t('page.categoryDetail.nameRequired'), required: true },
            { max: 50, message: t('page.categoryDetail.nameMax') },
          ]}
        >
          <Input placeholder={t('page.categoryDetail.namePlaceholder')} />
        </Form.Item>

        <Form.Item
          label={t('common.description')}
          name="description"
          rules={[{ max: 256, message: t('page.categoryDetail.descriptionMax') }]}
        >
          <Input.TextArea
            maxLength={256}
            placeholder={t('page.categoryDetail.descriptionPlaceholder')}
            rows={3}
            showCount
          />
        </Form.Item>

        <Form.Item label={t('page.categoryDetail.iconSetting')} style={{ marginBottom: '16px' }}>
          <Space direction="vertical" style={{ width: '100%' }}>
            <Radio.Group
              onChange={(e) => {
                setIconMode(e.target.value);
                setFileList([]);
                form.setFieldValue('iconUrl', '');
              }}
              value={iconMode}
            >
              <Radio value="URL">{t('page.categoryDetail.imageLink')}</Radio>
              <Radio value="BASE64">{t('page.categoryDetail.localUpload')}</Radio>
            </Radio.Group>

            {iconMode === 'URL' ? (
              <Form.Item
                name="iconUrl"
                rules={[
                  {
                    message: t('page.categoryDetail.imageUrlInvalid'),
                    type: 'url',
                  },
                ]}
                style={{ marginBottom: 0 }}
              >
                <Input placeholder={t('page.categoryDetail.imageUrlPlaceholder')} />
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
                            t('page.categoryDetail.imageSizeLimit', {
                              size: Math.round(file.size / 1024),
                            }),
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
                      <span style={{ color: '#999', fontSize: '12px' }}>
                        {t('page.categoryDetail.uploadImage')}
                      </span>
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

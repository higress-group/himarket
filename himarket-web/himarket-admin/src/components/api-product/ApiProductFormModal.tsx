import { CameraOutlined } from '@ant-design/icons';
import { Modal, Form, Input, Select, Image, message, Switch, Radio, Space } from 'antd';
import { useState, useEffect } from 'react';

import { apiProductApi } from '@/lib/api';
import { getProductCategories } from '@/lib/productCategoryApi';
import type { ApiProduct } from '@/types/api-product';
import type { ProductCategory } from '@/types/product-category';

import ModelFeatureForm from './ModelFeatureForm';
import SkillConfigForm from './SkillConfigForm';
import WorkerConfigForm from './WorkerConfigForm';

import type { UploadFile } from 'antd';

interface ApiProductFormModalProps {
  visible: boolean;
  onCancel: () => void;
  onSuccess: () => void;
  productId?: string;
  initialData?: Partial<ApiProduct>;
}

export default function ApiProductFormModal({
  initialData,
  onCancel,
  onSuccess,
  productId,
  visible,
}: ApiProductFormModalProps) {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewImage, setPreviewImage] = useState('');
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [iconMode, setIconMode] = useState<'BASE64' | 'URL'>('URL');
  const [productCategories, setProductCategories] = useState<ProductCategory[]>([]);
  const isEditMode = !!productId;

  // Watch product type to show/hide feature form
  const productType = Form.useWatch('type', form);

  // 获取产品类别列表
  const fetchProductCategories = async () => {
    try {
      const response = await getProductCategories();
      setProductCategories(response.data.content || []);
    } catch (error: unknown) {
      console.error('获取产品类别失败:', error);
      message.error('获取产品类别失败');
    }
  };

  // 初始化时加载已有数据
  useEffect(() => {
    if (!visible) return;

    fetchProductCategories();

    if (isEditMode && initialData && initialData.name) {
      // 延迟设置表单值，确保表单组件已完全渲染
      setTimeout(() => {
        form.setFieldsValue({
          autoApprove: initialData.autoApprove,
          description: initialData.description,
          feature: initialData.feature,
          name: initialData.name,
          type: initialData.type,
        });
      }, 300);

      // 处理 icon 字段
      if (initialData.icon) {
        if (
          typeof initialData.icon === 'object' &&
          initialData.icon.type &&
          initialData.icon.value
        ) {
          // 新格式：{ type: 'BASE64' | 'URL', value: string }
          const iconType = initialData.icon.type as 'BASE64' | 'URL';
          const iconValue = initialData.icon.value;

          setIconMode(iconType);

          if (iconType === 'BASE64') {
            setFileList([
              {
                name: '头像.png',
                status: 'done',
                uid: '-1',
                url: iconValue,
              },
            ]);
            setTimeout(() => {
              form.setFieldsValue({ icon: iconValue });
            }, 100);
          } else {
            setTimeout(() => {
              form.setFieldsValue({ iconUrl: iconValue });
            }, 100);
          }
        } else {
          // 兼容旧格式（字符串格式）
          const iconStr = initialData.icon as unknown as string;
          if (iconStr && typeof iconStr === 'string' && iconStr.includes('value=')) {
            const startIndex = iconStr.indexOf('value=') + 6;
            const endIndex = iconStr.length - 1;
            const base64Data = iconStr.substring(startIndex, endIndex).trim();

            setIconMode('BASE64');
            setFileList([
              {
                name: '头像.png',
                status: 'done',
                uid: '-1',
                url: base64Data,
              },
            ]);
            setTimeout(() => {
              form.setFieldsValue({ icon: base64Data });
            }, 100);
          }
        }
      }

      // 获取产品已关联的类别
      if (initialData.productId) {
        apiProductApi
          .getProductCategories(initialData.productId)
          .then((response) => {
            const categoryIds = response.data.map(
              (category: { categoryId: string }) => category.categoryId,
            );
            setTimeout(() => {
              form.setFieldsValue({ categories: categoryIds });
            }, 100);
          })
          .catch((error) => {
            console.error('获取产品关联类别失败:', error);
          });
      }
    } else if (visible && !isEditMode) {
      // 新建模式下清空表单
      form.resetFields();
      setFileList([]);
      setIconMode('URL');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible]);

  // 将文件转为 Base64
  const getBase64 = (file: File): Promise<string> =>
    new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.readAsDataURL(file);
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = (error) => reject(error);
    });

  const uploadButton = (
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
  );

  // 处理Icon模式切换
  const handleIconModeChange = (mode: 'BASE64' | 'URL') => {
    setIconMode(mode);
    // 清空相关字段
    if (mode === 'URL') {
      form.setFieldsValue({ icon: undefined });
      setFileList([]);
    } else {
      form.setFieldsValue({ iconUrl: undefined });
    }
  };

  const resetForm = () => {
    form.resetFields();
    setFileList([]);
    setPreviewImage('');
    setPreviewOpen(false);
    setIconMode('URL');
  };

  const handleCancel = () => {
    resetForm();
    onCancel();
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);

      const { categories, icon, iconUrl, ...otherValues } = values;

      if (isEditMode) {
        const params = { ...otherValues };

        // Merge feature fields with initial data to prevent data loss
        if (initialData?.feature) {
          const mergedFeature = { ...initialData.feature };

          // Merge skillConfig fields
          if (initialData.feature.skillConfig) {
            mergedFeature.skillConfig = {
              ...initialData.feature.skillConfig,
              ...(otherValues.feature?.skillConfig || {}),
            };
          }

          // Merge workerConfig fields
          if (initialData.feature.workerConfig) {
            mergedFeature.workerConfig = {
              ...initialData.feature.workerConfig,
              ...(otherValues.feature?.workerConfig || {}),
            };
          }

          // Merge modelFeature fields
          if (initialData.feature.modelFeature) {
            mergedFeature.modelFeature = {
              ...initialData.feature.modelFeature,
              ...(otherValues.feature?.modelFeature || {}),
            };
          }

          params.feature = mergedFeature;
        }

        // 处理icon字段
        if (iconMode === 'BASE64' && icon) {
          params.icon = {
            type: 'BASE64',
            value: icon,
          };
        } else if (iconMode === 'URL' && iconUrl) {
          params.icon = {
            type: 'URL',
            value: iconUrl,
          };
        } else if (!icon && !iconUrl) {
          // 如果两种模式都没有提供icon，保持原有icon不变
          delete params.icon;
        }

        // 将类别信息合并到参数中
        if (categories) {
          params.categories = categories;
        }

        await apiProductApi.updateApiProduct(productId, params);

        message.success('API Product 更新成功');
      } else {
        const params = { ...otherValues };

        // 处理icon字段
        if (iconMode === 'BASE64' && icon) {
          params.icon = {
            type: 'BASE64',
            value: icon,
          };
        } else if (iconMode === 'URL' && iconUrl) {
          params.icon = {
            type: 'URL',
            value: iconUrl,
          };
        }

        // 将类别信息合并到参数中
        if (categories) {
          params.categories = categories;
        }

        await apiProductApi.createApiProduct(params);

        message.success('API Product 创建成功');
      }

      resetForm();
      onSuccess();
    } catch (error: unknown) {
      const err = error as { errorFields?: unknown };
      if (err?.errorFields) return;
      message.error('操作失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      confirmLoading={loading}
      onCancel={handleCancel}
      onOk={handleSubmit}
      open={visible}
      title={isEditMode ? '编辑 API Product' : '创建 API Product'}
      width={600}
    >
      <Form form={form} layout="vertical">
        <Form.Item
          label="名称"
          name="name"
          rules={[{ message: '请输入API Product名称', required: true }]}
        >
          <Input placeholder="请输入API Product名称" />
        </Form.Item>

        <Form.Item
          label="描述"
          name="description"
          rules={[{ message: '请输入描述', required: true }]}
        >
          <Input.TextArea placeholder="请输入描述" rows={3} />
        </Form.Item>

        <Form.Item label="类型" name="type" rules={[{ message: '请选择类型', required: true }]}>
          <Select
            onChange={() => {
              form.setFieldValue('feature', undefined);
            }}
            placeholder="请选择类型"
          >
            <Select.Option value="MODEL_API">Model API</Select.Option>
            <Select.Option value="MCP_SERVER">MCP Server</Select.Option>
            <Select.Option value="AGENT_SKILL">Agent Skill</Select.Option>
            <Select.Option value="WORKER">Worker</Select.Option>
            <Select.Option value="AGENT_API">Agent API</Select.Option>
            <Select.Option value="REST_API">REST API</Select.Option>
          </Select>
        </Form.Item>

        <Form.Item label="产品类别" name="categories">
          <Select
            filterOption={(input, option) =>
              (option?.searchText || '').toLowerCase().includes(input.toLowerCase())
            }
            maxTagCount={3}
            maxTagTextLength={10}
            mode="multiple"
            optionLabelProp="label"
            placeholder="请选择产品类别（可多选）"
          >
            {productCategories.map((category) => {
              return (
                <Select.Option
                  key={category.categoryId}
                  label={category.name}
                  searchText={`${category.name} ${category.description || ''}`}
                  value={category.categoryId}
                >
                  <div>
                    <div className="font-medium">{category.name}</div>
                    {category.description && (
                      <div className="text-xs text-gray-500 truncate">{category.description}</div>
                    )}
                  </div>
                </Select.Option>
              );
            })}
          </Select>
        </Form.Item>

        {productType !== 'AGENT_SKILL' && productType !== 'WORKER' && (
          <Form.Item
            label="自动审批订阅"
            name="autoApprove"
            tooltip={{
              overlayInnerStyle: {
                backgroundColor: '#ffffff',
                border: '1px solid #d9d9d9',
                borderRadius: '6px',
                boxShadow: '0 4px 12px rgba(0, 0, 0, 0.15)',
                color: '#000000',
              },
              overlayStyle: {
                maxWidth: '300px',
              },
              placement: 'topLeft',
              title: (
                <div
                  style={{
                    backgroundColor: '#ffffff',
                    color: '#000000',
                    fontSize: '13px',
                    lineHeight: '1.4',
                    padding: '4px 0',
                  }}
                >
                  启用后，该产品的订阅申请将自动审批通过，否则使用Portal的消费者订阅审批设置。
                </div>
              ),
            }}
            valuePropName="checked"
          >
            <Switch />
          </Form.Item>
        )}

        <Form.Item label="Icon设置" style={{ marginBottom: '16px' }}>
          <Space direction="vertical" style={{ width: '100%' }}>
            <Radio.Group onChange={(e) => handleIconModeChange(e.target.value)} value={iconMode}>
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
                    // 触发文件选择
                    const input = document.createElement('input');
                    input.type = 'file';
                    input.accept = 'image/*';
                    input.onchange = (e) => {
                      const file = (e.target as HTMLInputElement).files?.[0];
                      if (file) {
                        // 验证文件大小，限制为16KB
                        const maxSize = 16 * 1024; // 16KB
                        if (file.size > maxSize) {
                          message.error(
                            `图片大小不能超过 16KB，当前图片大小为 ${Math.round(file.size / 1024)}KB`,
                          );
                          return;
                        }

                        const newFileList: UploadFile[] = [
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
                    <button
                      className="bg-transparent border-none p-0 cursor-pointer"
                      onClick={(e) => {
                        e.stopPropagation();
                        // 预览图片
                        setPreviewImage(fileList[0]?.url || '');
                        setPreviewOpen(true);
                      }}
                      type="button"
                    >
                      <img
                        alt="uploaded"
                        src={fileList[0]?.url}
                        style={{
                          borderRadius: '6px',
                          height: '100%',
                          objectFit: 'cover',
                          width: '100%',
                        }}
                      />
                    </button>
                  ) : (
                    uploadButton
                  )}
                  {fileList.length >= 1 && (
                    <div
                      onClick={(e) => {
                        e.stopPropagation();
                        setFileList([]);
                        form.setFieldsValue({ icon: null });
                      }}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' || e.key === ' ') {
                          e.preventDefault();
                          e.currentTarget.click();
                        }
                      }}
                      role="button"
                      style={{
                        alignItems: 'center',
                        background: 'rgba(0, 0, 0, 0.5)',
                        borderRadius: '50%',
                        color: 'white',
                        cursor: 'pointer',
                        display: 'flex',
                        fontSize: '10px',
                        height: '16px',
                        justifyContent: 'center',
                        position: 'absolute',
                        right: '4px',
                        top: '4px',
                        width: '16px',
                      }}
                      tabIndex={0}
                    >
                      ×
                    </div>
                  )}
                </div>
              </Form.Item>
            )}
          </Space>
        </Form.Item>

        {/* 图片预览弹窗 */}
        {previewImage && (
          <Image
            preview={{
              afterOpenChange: (visible) => {
                if (!visible) setPreviewImage('');
              },
              onVisibleChange: (visible) => setPreviewOpen(visible),
              visible: previewOpen,
            }}
            src={previewImage}
            wrapperStyle={{ display: 'none' }}
          />
        )}

        {/* Feature Configuration */}
        {productType === 'MODEL_API' && (
          <ModelFeatureForm initialExpanded={isEditMode && !!initialData?.feature} />
        )}
        {productType === 'AGENT_SKILL' && <SkillConfigForm />}
        {productType === 'WORKER' && <WorkerConfigForm />}
      </Form>
    </Modal>
  );
}

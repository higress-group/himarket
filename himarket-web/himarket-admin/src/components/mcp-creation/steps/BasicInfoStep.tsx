import { CameraOutlined, PlusOutlined } from '@ant-design/icons';
import { Form, Input, Select, Radio, Tag, Image, message } from 'antd';
import { useState, useEffect } from 'react';

import { getProductCategories } from '@/lib/productCategoryApi';
import type { ProductCategory } from '@/types/product-category';

import type { StepProps } from '../types';
import type { UploadFile } from 'antd';

const toBase64 = (file: File): Promise<string> =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.readAsDataURL(file);
    reader.onload = () => resolve(reader.result as string);
    reader.onerror = reject;
  });

export default function BasicInfoStep({ mode }: StepProps) {
  const [iconMode, setIconMode] = useState<'URL' | 'BASE64'>('URL');
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewImage, setPreviewImage] = useState('');
  const [tagInput, setTagInput] = useState('');
  const [categories, setCategories] = useState<ProductCategory[]>([]);

  const form = Form.useFormInstance();
  const tags: string[] = Form.useWatch('tags', form) ?? [];

  useEffect(() => {
    getProductCategories()
      .then((res) => setCategories(res.data.content || []))
      .catch(() => setCategories([]));
  }, []);

  // ---- Icon helpers ----
  const handleIconModeChange = (newMode: 'URL' | 'BASE64') => {
    setIconMode(newMode);
    if (newMode === 'URL') {
      form.setFieldsValue({ icon: undefined });
      setFileList([]);
    } else {
      form.setFieldsValue({ iconUrl: undefined });
    }
  };

  const handleFileSelect = () => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = 'image/*';
    input.onchange = (e) => {
      const file = (e.target as HTMLInputElement).files?.[0];
      if (!file) return;
      if (file.size > 16 * 1024) {
        message.error(`图片大小不能超过 16KB，当前 ${Math.round(file.size / 1024)}KB`);
        return;
      }
      setFileList([
        {
          name: file.name,
          status: 'done',
          uid: Date.now().toString(),
          url: URL.createObjectURL(file),
        },
      ]);
      toBase64(file).then((b64) => form.setFieldsValue({ icon: b64 }));
    };
    input.click();
  };

  // ---- Tags helpers ----
  const handleAddTag = () => {
    const val = tagInput.trim();
    if (!val) return;
    if (tags.includes(val)) {
      message.warning('标签已存在');
      return;
    }
    form.setFieldsValue({ tags: [...tags, val] });
    setTagInput('');
  };

  const handleRemoveTag = (removed: string) => {
    form.setFieldsValue({ tags: tags.filter((t) => t !== removed) });
  };

  return (
    <div>
      {/* MCP 展示名称 + MCP 英文名称 */}
      {mode === 'manual' ? (
        <div className="grid grid-cols-2 gap-4">
          <Form.Item
            label="MCP 展示名称"
            name="name"
            rules={[
              { message: '请输入 MCP 展示名称', required: true },
              { max: 50, message: '最多 50 个字符' },
            ]}
          >
            <Input maxLength={50} placeholder="例如：我的 MCP Server" />
          </Form.Item>

          <Form.Item
            label="MCP 英文名称"
            name="mcpName"
            rules={[
              { message: '请输入 MCP 英文名称', required: true },
              { message: '小写字母开头，仅含小写字母、数字、连字符', pattern: /^[a-z][a-z0-9-]*$/ },
              { max: 63, message: '最多 63 个字符' },
            ]}
          >
            <Input maxLength={63} placeholder="例如：my-mcp-server" />
          </Form.Item>
        </div>
      ) : (
        <Form.Item
          label="MCP 展示名称"
          name="name"
          rules={[
            { message: '请输入 MCP 展示名称', required: true },
            { max: 50, message: '最多 50 个字符' },
          ]}
        >
          <Input maxLength={50} placeholder="例如：我的 MCP Server" />
        </Form.Item>
      )}

      {/* 描述 */}
      <Form.Item
        label="描述"
        name="description"
        rules={[
          { message: '请输入描述', required: true },
          { max: 512, message: '最多 512 个字符' },
        ]}
      >
        <Input.TextArea maxLength={512} placeholder="请输入 MCP Server 描述" rows={3} showCount />
      </Form.Item>

      {/* 自定义标签 */}
      <Form.Item label="自定义标签">
        <Form.Item hidden name="tags">
          <Input />
        </Form.Item>
        <div>
          <div className="flex items-center gap-2 mb-2">
            <Input
              onChange={(e) => setTagInput(e.target.value)}
              onPressEnter={(e) => {
                e.preventDefault();
                handleAddTag();
              }}
              placeholder="输入后按回车添加"
              size="small"
              suffix={
                <PlusOutlined
                  className="text-gray-400 hover:text-blue-500 cursor-pointer transition-colors"
                  onClick={handleAddTag}
                />
              }
              value={tagInput}
            />
          </div>
          <div className="flex flex-wrap gap-1.5 min-h-[24px]">
            {tags.map((tag) => (
              <Tag
                className="m-0"
                closable
                color="blue"
                key={tag}
                onClose={() => handleRemoveTag(tag)}
              >
                {tag}
              </Tag>
            ))}
            {!tags.length && <span className="text-xs text-gray-300">暂无标签</span>}
          </div>
        </div>
      </Form.Item>

      {/* 分类 */}
      <Form.Item label="分类" name="categories">
        <Select
          filterOption={(input, option) =>
            (option?.searchText || '').toLowerCase().includes(input.toLowerCase())
          }
          maxTagCount={3}
          maxTagTextLength={10}
          mode="multiple"
          optionLabelProp="label"
          placeholder="请选择分类（可多选）"
        >
          {categories.map((cat) => (
            <Select.Option
              key={cat.categoryId}
              label={cat.name}
              searchText={`${cat.name} ${cat.description || ''}`}
              value={cat.categoryId}
            >
              <div>
                <div className="font-medium">{cat.name}</div>
                {cat.description && (
                  <div className="text-xs text-gray-500 truncate">{cat.description}</div>
                )}
              </div>
            </Select.Option>
          ))}
        </Select>
      </Form.Item>

      {/* 仓库地址 — 仅 manual 模式 */}
      {mode === 'manual' && (
        <Form.Item
          label="仓库地址"
          name="repoUrl"
          rules={[{ message: '请输入有效的 URL 地址', type: 'url' }]}
        >
          <Input placeholder="https://github.com/your-org/your-mcp-server" />
        </Form.Item>
      )}

      {/* Icon 设置 */}
      <Form.Item label="Icon 设置" style={{ marginBottom: 16 }}>
        <div className="space-y-2">
          <Radio.Group onChange={(e) => handleIconModeChange(e.target.value)} value={iconMode}>
            <Radio value="URL">图片链接</Radio>
            <Radio value="BASE64">本地上传</Radio>
          </Radio.Group>

          {iconMode === 'URL' ? (
            <Form.Item
              name="iconUrl"
              rules={[{ message: '请输入有效的图片链接', type: 'url' }]}
              style={{ marginBottom: 0 }}
            >
              <Input placeholder="请输入图片链接地址" />
            </Form.Item>
          ) : (
            <Form.Item name="icon" style={{ marginBottom: 0 }}>
              <div
                className="w-16 h-16 border border-dashed border-gray-300 rounded-lg flex items-center justify-center cursor-pointer relative transition-colors hover:border-blue-400"
                onClick={handleFileSelect}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    handleFileSelect();
                  }
                }}
                role="button"
                tabIndex={0}
              >
                {fileList.length > 0 ? (
                  <img
                    alt="icon"
                    className="w-full h-full object-cover rounded-md"
                    onClick={(e) => {
                      e.stopPropagation();
                      setPreviewImage(fileList[0]?.url || '');
                      setPreviewOpen(true);
                    }}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        e.stopPropagation();
                        setPreviewImage(fileList[0]?.url || '');
                        setPreviewOpen(true);
                      }
                    }}
                    role="button"
                    src={fileList[0]?.url || ''}
                    tabIndex={0}
                  />
                ) : (
                  <div className="flex flex-col items-center text-gray-400">
                    <CameraOutlined className="text-sm mb-0.5" />
                    <span className="text-[10px]">上传</span>
                  </div>
                )}
                {fileList.length > 0 && (
                  <div
                    className="absolute -top-1 -right-1 w-4 h-4 bg-black/50 rounded-full flex items-center justify-center text-white text-[10px] cursor-pointer"
                    onClick={(e) => {
                      e.stopPropagation();
                      setFileList([]);
                      form.setFieldsValue({ icon: null });
                    }}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        e.stopPropagation();
                        setFileList([]);
                        form.setFieldsValue({ icon: null });
                      }
                    }}
                    role="button"
                    tabIndex={0}
                  >
                    ×
                  </div>
                )}
              </div>
            </Form.Item>
          )}
        </div>
      </Form.Item>

      {previewImage && (
        <Image
          preview={{
            afterOpenChange: (vis) => {
              if (!vis) setPreviewImage('');
            },
            onVisibleChange: setPreviewOpen,
            visible: previewOpen,
          }}
          src={previewImage}
          wrapperStyle={{ display: 'none' }}
        />
      )}
    </div>
  );
}

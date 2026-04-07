import { useState, useEffect } from 'react'
import {
  Form,
  Input,
  Select,
  Radio,
  Tag,
  Image,
  message,
} from 'antd'
import type { UploadFile } from 'antd'
import { CameraOutlined, PlusOutlined } from '@ant-design/icons'
import { getProductCategories } from '@/lib/productCategoryApi'
import type { ProductCategory } from '@/types/product-category'
import type { StepProps } from '../types'

const toBase64 = (file: File): Promise<string> =>
  new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.readAsDataURL(file)
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = reject
  })

export default function BasicInfoStep({ mode }: StepProps) {
  const [iconMode, setIconMode] = useState<'URL' | 'BASE64'>('URL')
  const [fileList, setFileList] = useState<UploadFile[]>([])
  const [previewOpen, setPreviewOpen] = useState(false)
  const [previewImage, setPreviewImage] = useState('')
  const [tagInput, setTagInput] = useState('')
  const [categories, setCategories] = useState<ProductCategory[]>([])

  const form = Form.useFormInstance()
  const tags: string[] = Form.useWatch('tags', form) ?? []

  useEffect(() => {
    getProductCategories()
      .then((res) => setCategories(res.data.content || []))
      .catch(() => setCategories([]))
  }, [])

  // ---- Icon helpers ----
  const handleIconModeChange = (newMode: 'URL' | 'BASE64') => {
    setIconMode(newMode)
    if (newMode === 'URL') {
      form.setFieldsValue({ icon: undefined }); setFileList([])
    } else {
      form.setFieldsValue({ iconUrl: undefined })
    }
  }

  const handleFileSelect = () => {
    const input = document.createElement('input')
    input.type = 'file'; input.accept = 'image/*'
    input.onchange = (e) => {
      const file = (e.target as HTMLInputElement).files?.[0]
      if (!file) return
      if (file.size > 16 * 1024) {
        message.error(`图片大小不能超过 16KB，当前 ${Math.round(file.size / 1024)}KB`)
        return
      }
      setFileList([{ uid: Date.now().toString(), name: file.name, status: 'done', url: URL.createObjectURL(file) }])
      toBase64(file).then((b64) => form.setFieldsValue({ icon: b64 }))
    }
    input.click()
  }

  // ---- Tags helpers ----
  const handleAddTag = () => {
    const val = tagInput.trim()
    if (!val) return
    if (tags.includes(val)) { message.warning('标签已存在'); return }
    form.setFieldsValue({ tags: [...tags, val] })
    setTagInput('')
  }

  const handleRemoveTag = (removed: string) => {
    form.setFieldsValue({ tags: tags.filter((t) => t !== removed) })
  }

  return (
    <div>
      {/* MCP 展示名称 + MCP 英文名称 */}
      {mode === 'manual' ? (
        <div className="grid grid-cols-2 gap-4">
          <Form.Item
            label="MCP 展示名称"
            name="name"
            rules={[
              { required: true, message: '请输入 MCP 展示名称' },
              { max: 50, message: '最多 50 个字符' },
            ]}
          >
            <Input placeholder="例如：我的 MCP Server" maxLength={50} />
          </Form.Item>

          <Form.Item
            label="MCP 英文名称"
            name="mcpName"
            rules={[
              { required: true, message: '请输入 MCP 英文名称' },
              { pattern: /^[a-z][a-z0-9-]*$/, message: '小写字母开头，仅含小写字母、数字、连字符' },
              { max: 63, message: '最多 63 个字符' },
            ]}
          >
            <Input placeholder="例如：my-mcp-server" maxLength={63} />
          </Form.Item>
        </div>
      ) : (
        <Form.Item
          label="MCP 展示名称"
          name="name"
          rules={[
            { required: true, message: '请输入 MCP 展示名称' },
            { max: 50, message: '最多 50 个字符' },
          ]}
        >
          <Input placeholder="例如：我的 MCP Server" maxLength={50} />
        </Form.Item>
      )}

      {/* 描述 */}
      <Form.Item
        label="描述"
        name="description"
        rules={[
          { required: true, message: '请输入描述' },
          { max: 512, message: '最多 512 个字符' },
        ]}
      >
        <Input.TextArea placeholder="请输入 MCP Server 描述" rows={3} maxLength={512} showCount />
      </Form.Item>

      {/* 自定义标签 */}
      <Form.Item label="自定义标签">
        <Form.Item name="tags" hidden><Input /></Form.Item>
        <div>
          <div className="flex items-center gap-2 mb-2">
            <Input
              value={tagInput}
              onChange={(e) => setTagInput(e.target.value)}
              onPressEnter={(e) => { e.preventDefault(); handleAddTag() }}
              placeholder="输入后按回车添加"
              size="small"
              suffix={
                <PlusOutlined
                  className="text-gray-400 hover:text-blue-500 cursor-pointer transition-colors"
                  onClick={handleAddTag}
                />
              }
            />
          </div>
          <div className="flex flex-wrap gap-1.5 min-h-[24px]">
            {tags.map((tag) => (
              <Tag key={tag} closable onClose={() => handleRemoveTag(tag)} color="blue" className="m-0">{tag}</Tag>
            ))}
            {!tags.length && <span className="text-xs text-gray-300">暂无标签</span>}
          </div>
        </div>
      </Form.Item>

      {/* 分类 */}
      <Form.Item label="分类" name="categories">
        <Select
          mode="multiple"
          placeholder="请选择分类（可多选）"
          maxTagCount={3}
          maxTagTextLength={10}
          optionLabelProp="label"
          filterOption={(input, option) =>
            (option?.searchText || '').toLowerCase().includes(input.toLowerCase())
          }
        >
          {categories.map((cat) => (
            <Select.Option key={cat.categoryId} value={cat.categoryId} label={cat.name} searchText={`${cat.name} ${cat.description || ''}`}>
              <div>
                <div className="font-medium">{cat.name}</div>
                {cat.description && <div className="text-xs text-gray-500 truncate">{cat.description}</div>}
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
          rules={[{ type: 'url', message: '请输入有效的 URL 地址' }]}
        >
          <Input placeholder="https://github.com/your-org/your-mcp-server" />
        </Form.Item>
      )}

      {/* Icon 设置 */}
      <Form.Item label="Icon 设置" style={{ marginBottom: 16 }}>
        <div className="space-y-2">
          <Radio.Group value={iconMode} onChange={(e) => handleIconModeChange(e.target.value)}>
            <Radio value="URL">图片链接</Radio>
            <Radio value="BASE64">本地上传</Radio>
          </Radio.Group>

          {iconMode === 'URL' ? (
            <Form.Item name="iconUrl" style={{ marginBottom: 0 }} rules={[{ type: 'url', message: '请输入有效的图片链接' }]}>
              <Input placeholder="请输入图片链接地址" />
            </Form.Item>
          ) : (
            <Form.Item name="icon" style={{ marginBottom: 0 }}>
              <div
                className="w-16 h-16 border border-dashed border-gray-300 rounded-lg flex items-center justify-center cursor-pointer relative transition-colors hover:border-blue-400"
                onClick={handleFileSelect}
              >
                {fileList.length > 0 ? (
                  <img src={fileList[0].url} alt="icon" className="w-full h-full object-cover rounded-md"
                    onClick={(e) => { e.stopPropagation(); setPreviewImage(fileList[0].url || ''); setPreviewOpen(true) }} />
                ) : (
                  <div className="flex flex-col items-center text-gray-400">
                    <CameraOutlined className="text-sm mb-0.5" />
                    <span className="text-[10px]">上传</span>
                  </div>
                )}
                {fileList.length > 0 && (
                  <div className="absolute -top-1 -right-1 w-4 h-4 bg-black/50 rounded-full flex items-center justify-center text-white text-[10px] cursor-pointer"
                    onClick={(e) => { e.stopPropagation(); setFileList([]); form.setFieldsValue({ icon: null }) }}>×</div>
                )}
              </div>
            </Form.Item>
          )}
        </div>
      </Form.Item>

      {previewImage && (
        <Image wrapperStyle={{ display: 'none' }} preview={{ visible: previewOpen, onVisibleChange: setPreviewOpen, afterOpenChange: (vis) => { if (!vis) setPreviewImage('') } }} src={previewImage} />
      )}
    </div>
  )
}

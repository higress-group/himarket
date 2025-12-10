import { Card, Button, Space, message } from 'antd'
import { SaveOutlined, UploadOutlined, FileMarkdownOutlined, EditOutlined } from '@ant-design/icons'
import { useEffect, useState, useRef } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm';
import MdEditor from 'react-markdown-editor-lite'
import 'react-markdown-editor-lite/lib/index.css'
import type { ApiProduct } from '@/types/api-product'
import { apiProductApi } from '@/lib/api'

interface ApiProductUsageGuideProps {
  apiProduct: ApiProduct
  handleRefresh: () => void
}

export function ApiProductUsageGuide({ apiProduct, handleRefresh }: ApiProductUsageGuideProps) {
  const [content, setContent] = useState(apiProduct.document || '')
  const [isEditing, setIsEditing] = useState(false)
  const [originalContent, setOriginalContent] = useState(apiProduct.document || '')
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    const doc = apiProduct.document || ''
    setContent(doc)
    setOriginalContent(doc)
  }, [apiProduct.document])

  const handleEdit = () => {
    setIsEditing(true)
  }

  const handleSave = () => {
    // 提取 categoryIds 以保留产品类别信息
    const categoryIds = apiProduct.categories?.map(cat => cat.categoryId) || [];
    
    apiProductApi.updateApiProduct(apiProduct.productId, {
      document: content,
      categories: categoryIds
    }).then(() => {
      message.success('保存成功')
      setIsEditing(false)
      setOriginalContent(content)
      handleRefresh();
    })
  }

  const handleCancel = () => {
    setContent(originalContent)
    setIsEditing(false)
  }

  const handleEditorChange = ({ text }: { text: string }) => {
    setContent(text)
  }

  const handleFileImport = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (file) {
      if (file.type !== 'text/markdown' && !file.name.endsWith('.md')) {
        message.error('请选择 Markdown 文件 (.md)')
        return
      }

      const reader = new FileReader()
      reader.onload = (e) => {
        const content = e.target?.result as string
        setContent(content)
        setIsEditing(true)
        message.success('文件导入成功')
      }
      reader.readAsText(file)
    }
    // 清空 input 值，允许重复选择同一文件
    if (event.target) {
      event.target.value = ''
    }
  }

  const triggerFileInput = () => {
    fileInputRef.current?.click()
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold mb-2">使用指南</h1>
          <p className="text-gray-600">编辑和发布使用指南</p>
        </div>
        <Space>
          {isEditing ? (
            <>
              <Button icon={<UploadOutlined />} onClick={triggerFileInput}>
                导入文件
              </Button>
              <Button onClick={handleCancel}>
                取消
              </Button>
              <Button type="primary" icon={<SaveOutlined />} onClick={handleSave}>
                保存
              </Button>
            </>
          ) : (
            <>
              <Button icon={<UploadOutlined />} onClick={triggerFileInput}>
                导入文件
              </Button>
              <Button type="primary" icon={<EditOutlined />} onClick={handleEdit}>
                编辑
              </Button>
            </>
          )}
        </Space>
      </div>

      <Card>
        {isEditing ? (
          <>
            <MdEditor
              value={content}
              onChange={handleEditorChange}
              style={{ height: '600px', width: '100%' }}
              placeholder="请输入使用指南内容..."
              renderHTML={(text) => <ReactMarkdown remarkPlugins={[remarkGfm]}>{text}</ReactMarkdown>}
              canView={{ menu: true, md: true, html: true, both: true, fullScreen: false, hideMenu: false }}
              htmlClass="custom-html-style"
              markdownClass="custom-markdown-style"
            />
            <div className="mt-4 text-sm text-gray-500">
              💡 支持Markdown格式：代码块、表格、链接、图片等语法
            </div>
          </>
        ) : (
          <div className="min-h-[400px]">
            {content ? (
              <div 
                className="prose prose-lg max-w-none"
                style={{
                  lineHeight: '1.7',
                  color: '#374151',
                  fontSize: '16px',
                  fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif'
                }}
              >
                <style>{`
                  .prose h1 {
                    color: #111827;
                    font-weight: 700;
                    font-size: 2.25rem;
                    line-height: 1.2;
                    margin-top: 0;
                    margin-bottom: 1.5rem;
                    border-bottom: 2px solid #e5e7eb;
                    padding-bottom: 0.5rem;
                  }
                  .prose h2 {
                    color: #1f2937;
                    font-weight: 600;
                    font-size: 1.875rem;
                    line-height: 1.3;
                    margin-top: 2rem;
                    margin-bottom: 1rem;
                    border-bottom: 1px solid #e5e7eb;
                    padding-bottom: 0.25rem;
                  }
                  .prose h3 {
                    color: #374151;
                    font-weight: 600;
                    font-size: 1.5rem;
                    margin-top: 1.5rem;
                    margin-bottom: 0.75rem;
                  }
                  .prose p {
                    margin-bottom: 1.25rem;
                    color: #4b5563;
                    line-height: 1.7;
                    font-size: 16px;
                  }
                  .prose code {
                    background-color: #f3f4f6;
                    border: 1px solid #e5e7eb;
                    border-radius: 0.375rem;
                    padding: 0.125rem 0.375rem;
                    font-size: 0.875rem;
                    color: #374151;
                    font-weight: 500;
                  }
                  .prose pre {
                    background-color: #1f2937;
                    border-radius: 0.5rem;
                    padding: 1.25rem;
                    overflow-x: auto;
                    margin: 1.5rem 0;
                    border: 1px solid #374151;
                  }
                  .prose pre code {
                    background-color: transparent;
                    border: none;
                    color: #f9fafb;
                    padding: 0;
                    font-size: 0.875rem;
                    font-weight: normal;
                  }
                  .prose blockquote {
                    border-left: 4px solid #3b82f6;
                    padding-left: 1rem;
                    margin: 1.5rem 0;
                    color: #6b7280;
                    font-style: italic;
                    background-color: #f8fafc;
                    padding: 1rem;
                    border-radius: 0.375rem;
                    font-size: 16px;
                  }
                  .prose ul, .prose ol {
                    margin: 1.25rem 0;
                    padding-left: 1.5rem;
                  }
                  .prose ol {
                    list-style-type: decimal;
                    list-style-position: outside;
                  }
                  .prose ul {
                    list-style-type: disc;
                    list-style-position: outside;
                  }
                  .prose li {
                    margin: 0.5rem 0;
                    color: #4b5563;
                    display: list-item;
                    font-size: 16px;
                  }
                  .prose ol li {
                    padding-left: 0.25rem;
                  }
                  .prose ul li {
                    padding-left: 0.25rem;
                  }
                  .prose table {
                    width: 100%;
                    border-collapse: collapse;
                    margin: 1.5rem 0;
                    font-size: 16px;
                  }
                  .prose th, .prose td {
                    border: 1px solid #d1d5db;
                    padding: 0.75rem;
                    text-align: left;
                  }
                  .prose th {
                    background-color: #f9fafb;
                    font-weight: 600;
                    color: #374151;
                    font-size: 16px;
                  }
                  .prose td {
                    color: #4b5563;
                    font-size: 16px;
                  }
                  .prose a {
                    color: #3b82f6;
                    text-decoration: underline;
                    font-weight: 500;
                    transition: color 0.2s;
                    font-size: inherit;
                  }
                  .prose a:hover {
                    color: #1d4ed8;
                  }
                  .prose strong {
                    color: #111827;
                    font-weight: 600;
                    font-size: inherit;
                  }
                  .prose em {
                    color: #6b7280;
                    font-style: italic;
                    font-size: inherit;
                  }
                  .prose hr {
                    border: none;
                    height: 1px;
                    background-color: #e5e7eb;
                    margin: 2rem 0;
                  }
                `}</style>
                <ReactMarkdown remarkPlugins={[remarkGfm]}>{content}</ReactMarkdown>
              </div>
            ) : (
              <div className="flex items-center justify-center h-[400px] text-gray-400 border-2 border-dashed border-gray-200 rounded-lg">
                <div className="text-center">
                  <FileMarkdownOutlined className="text-4xl mb-4 text-gray-300" />
                  <p className="text-lg">暂无使用指南</p>
                  <p className="text-sm">点击编辑按钮开始撰写</p>
                </div>
              </div>
            )}
          </div>
        )}
      </Card>

      {/* 隐藏的文件输入框 */}
      <input
        ref={fileInputRef}
        type="file"
        accept=".md,text/markdown"
        onChange={handleFileImport}
        style={{ display: 'none' }}
      />
    </div>
  )
} 
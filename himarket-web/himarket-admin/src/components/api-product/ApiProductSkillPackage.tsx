import { useState, useEffect, useRef } from 'react'
import { Upload, message, Spin, Tooltip, Alert, Button as AntButton } from 'antd'
import { InboxOutlined, FolderOutlined, FolderOpenOutlined, FileOutlined } from '@ant-design/icons'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import rehypeHighlight from 'rehype-highlight'
import MonacoEditor from 'react-monaco-editor'
import { skillApi } from '@/lib/api'
import 'github-markdown-css/github-markdown-light.css'
import 'highlight.js/styles/github.css'

interface SkillFileTreeNode {
  name: string
  path: string
  type: 'file' | 'directory'
  encoding?: string
  size?: number
  children?: SkillFileTreeNode[]
}

interface FileContent {
  path: string
  content: string
  encoding: string
  size: number
}

interface ApiProductSkillPackageProps {
  apiProduct: import('@/types/api-product').ApiProduct
  onUploadSuccess?: () => void
}

// ── 自定义文件树（与前台 SkillFileTree 对齐）─────────────────
interface TreeNodeProps {
  node: SkillFileTreeNode
  selectedPath?: string
  onSelect: (path: string) => void
  depth: number
}

function TreeNode({ node, selectedPath, onSelect, depth }: TreeNodeProps) {
  const [expanded, setExpanded] = useState(true)
  const isDir = node.type === 'directory'
  const isSelected = node.path === selectedPath

  return (
    <div>
      <Tooltip title={node.name} placement="right" mouseEnterDelay={0.8}>
        <div
          className={`
            flex items-center gap-1.5 px-2 py-1 rounded cursor-pointer text-sm select-none
            transition-colors duration-100
            ${isSelected ? 'bg-purple-100 text-purple-700' : 'hover:bg-gray-100 text-gray-700'}
          `}
          style={{ paddingLeft: `${8 + depth * 16}px` }}
          onClick={() => isDir ? setExpanded(v => !v) : onSelect(node.path)}
        >
          {isDir
            ? expanded
              ? <FolderOpenOutlined className="text-yellow-500 flex-shrink-0" />
              : <FolderOutlined className="text-yellow-500 flex-shrink-0" />
            : <FileOutlined className="text-blue-400 flex-shrink-0" />
          }
          <span className="truncate">{node.name}</span>
        </div>
      </Tooltip>
      {isDir && expanded && node.children && node.children.length > 0 && (
        <div>
          {node.children.map(child => (
            <TreeNode key={child.path} node={child} selectedPath={selectedPath} onSelect={onSelect} depth={depth + 1} />
          ))}
        </div>
      )}
    </div>
  )
}

function SkillFileTree({ nodes, selectedPath, onSelect }: { nodes: SkillFileTreeNode[]; selectedPath?: string; onSelect: (p: string) => void }) {
  return (
    <div className="py-1">
      {nodes.map(node => (
        <TreeNode key={node.path} node={node} selectedPath={selectedPath} onSelect={onSelect} depth={0} />
      ))}
    </div>
  )
}

function parseFrontMatter(content: string): { entries: [string, string][]; body: string } {
  const t = content.trim()
  if (!t.startsWith('---')) return { entries: [], body: t }
  const end = t.indexOf('---', 3)
  if (end === -1) return { entries: [], body: t }
  const yamlBlock = t.substring(3, end).trim()
  const body = t.substring(end + 3).trim()
  const entries: [string, string][] = yamlBlock.split('\n').flatMap((line) => {
    const idx = line.indexOf(':')
    if (idx <= 0) return []
    const k = line.substring(0, idx).trim()
    let v = line.substring(idx + 1).trim()
    if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) v = v.slice(1, -1)
    return [[k, v]] as [string, string][]
  })
  return { entries, body }
}

function findNode(nodes: SkillFileTreeNode[], path: string): SkillFileTreeNode | null {
  for (const node of nodes) {
    if (node.path === path) return node
    if (node.children) { const f = findNode(node.children, path); if (f) return f }
  }
  return null
}

export function ApiProductSkillPackage({ apiProduct, onUploadSuccess }: ApiProductSkillPackageProps) {
  const productId = apiProduct.productId
  const hasNacos = !!(apiProduct.skillConfig?.nacosId)
  const [fileTree, setFileTree] = useState<SkillFileTreeNode[]>([])
  const [selectedPath, setSelectedPath] = useState<string | undefined>()
  const [selectedFile, setSelectedFile] = useState<FileContent | null>(null)
  const [loadingTree, setLoadingTree] = useState(false)
  const [loadingFile, setLoadingFile] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [treeWidth, setTreeWidth] = useState(240)
  const isDragging = useRef(false)

  const handleDragStart = (e: React.MouseEvent) => {
    e.preventDefault()
    isDragging.current = true
    const startX = e.clientX
    const startWidth = treeWidth
    const onMove = (ev: MouseEvent) => {
      if (!isDragging.current) return
      setTreeWidth(Math.min(520, Math.max(160, startWidth + ev.clientX - startX)))
    }
    const onUp = () => {
      isDragging.current = false
      window.removeEventListener('mousemove', onMove)
      window.removeEventListener('mouseup', onUp)
    }
    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onUp)
  }

  const fetchFileTree = async () => {
    setLoadingTree(true)
    try {
      const res: any = await skillApi.getSkillFiles(productId)
      const nodes: SkillFileTreeNode[] = res.data || []
      setFileTree(nodes)
      if (findNode(nodes, 'SKILL.md')) loadFileContent('SKILL.md')
    } catch {
    } finally {
      setLoadingTree(false)
    }
  }

  const loadFileContent = async (path: string) => {
    setSelectedPath(path)
    setLoadingFile(true)
    try {
      const res: any = await skillApi.getSkillFileContent(productId, path)
      setSelectedFile(res.data)
    } catch {
    } finally {
      setLoadingFile(false)
    }
  }

  useEffect(() => { fetchFileTree() }, [productId])

  const customRequest = async (options: any) => {
    const { file, onSuccess, onError } = options
    setUploading(true)
    try {
      const res: any = await skillApi.uploadSkillPackage(productId, file)
      message.success('上传成功')
      onSuccess(res)
      await fetchFileTree()
      onUploadSuccess?.()
    } catch (error: any) {
      message.destroy()
      message.error(error.response?.data?.message || '上传失败')
      onError(error)
    } finally {
      setUploading(false)
    }
  }

  const renderPreview = () => {
    if (loadingFile) return <div className="flex items-center justify-center h-full"><Spin /></div>

    if (!selectedFile) return (
      <div className="flex items-center justify-center h-full text-gray-400">
        <div className="text-center">
          <FileOutlined className="text-4xl mb-2 text-gray-300" />
          <p>点击左侧文件查看内容</p>
        </div>
      </div>
    )

    if (selectedFile.encoding === 'base64') return (
      <div className="flex items-center justify-center h-full text-gray-400">
        <p>二进制文件，不支持预览</p>
      </div>
    )

    if (selectedFile.path.endsWith('.md')) {
      const { entries, body } = parseFrontMatter(selectedFile.content)
      return (
        <div className="p-6 overflow-auto h-full">
          {entries.length > 0 && (
            <table className="mb-6 w-full text-[13px] border-collapse">
              <thead>
                <tr className="bg-[#f6f8fa]">
                  {entries.map(([k]) => (
                    <th key={k} className="border border-[#d0d7de] px-3 py-1.5 text-left font-semibold text-[#1f2328]">{k}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                <tr>
                  {entries.map(([k, v]) => (
                    <td key={k} className="border border-[#d0d7de] px-3 py-1.5 text-[#1f2328] align-top">{v}</td>
                  ))}
                </tr>
              </tbody>
            </table>
          )}
          <div className="markdown-body">
            <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeHighlight]}>{body}</ReactMarkdown>
          </div>
        </div>
      )
    }

    const lang = (() => {
      const ext = selectedFile.path.split('.').pop()?.toLowerCase() ?? ''
      const map: Record<string, string> = { py: 'python', js: 'javascript', ts: 'typescript', tsx: 'typescript', jsx: 'javascript', json: 'json', yaml: 'yaml', yml: 'yaml', sh: 'shell', bash: 'shell', css: 'css', html: 'html', xml: 'xml', sql: 'sql', java: 'java', go: 'go', rs: 'rust', rb: 'ruby', kt: 'kotlin', swift: 'swift', c: 'c', cpp: 'cpp', h: 'c', hpp: 'cpp' }
      return map[ext] || 'plaintext'
    })()

    return (
      <MonacoEditor
        width="100%"
        height="100%"
        language={lang}
        value={selectedFile.content}
        options={{ readOnly: true, minimap: { enabled: false }, scrollBeyondLastLine: false, fontSize: 13, lineHeight: 20 }}
        theme="vs"
      />
    )
  }

  return (
    <div className="p-6 space-y-4 h-full flex flex-col">
      <div>
        <h1 className="text-2xl font-bold mb-1">Skill Package</h1>
        <p className="text-gray-600">上传并管理技能包文件</p>
      </div>

      {!hasNacos && (
        <Alert
          type="warning"
          showIcon
          message="尚未关联 Nacos 实例"
          description="请先在 Link Nacos 页面关联 Nacos 实例后，才能上传和管理技能包。如果还没有导入 Nacos 实例，请前往 Nacos 实例管理页面导入。"
          action={
            <AntButton size="small" type="primary" href="/nacos-consoles">
              前往 Nacos 管理
            </AntButton>
          }
        />
      )}

      <Upload.Dragger accept=".zip,.tar.gz" customRequest={customRequest} showUploadList={false} disabled={uploading || !hasNacos} style={{ padding: '8px 0' }}>
        <p className="ant-upload-drag-icon"><InboxOutlined /></p>
        <p className="ant-upload-text">点击或拖拽上传 Skill 包</p>
        <p className="ant-upload-hint">支持 .zip 和 .tar.gz 格式，最大 50MB</p>
      </Upload.Dragger>

      <div className="flex border rounded-lg overflow-hidden" style={{ height: 560 }}>
        <div className="border-r bg-gray-50 overflow-y-auto overflow-x-hidden flex-shrink-0 p-2" style={{ width: treeWidth }}>
          {loadingTree
            ? <div className="flex justify-center pt-4"><Spin size="small" /></div>
            : fileTree.length === 0
              ? <div className="text-gray-400 text-sm text-center pt-4">暂无文件</div>
              : <SkillFileTree nodes={fileTree} selectedPath={selectedPath} onSelect={loadFileContent} />
          }
        </div>
        {/* 拖拽分隔条 */}
        <div
          onMouseDown={handleDragStart}
          className="w-1 flex-shrink-0 cursor-col-resize hover:bg-blue-200 transition-colors bg-transparent"
        />
        <div className="flex-1 overflow-auto h-full">{renderPreview()}</div>
      </div>
    </div>
  )
}

import { Modal } from 'antd'
import {
  EditOutlined,
  CloudDownloadOutlined,
  ApiOutlined,
  DatabaseOutlined,
  ShopOutlined,
} from '@ant-design/icons'
import type { McpCreationSelectorProps, CreationMode } from './types'

interface OptionItem {
  mode: CreationMode
  icon: React.ReactNode
  title: string
  description: string
}

const MANUAL_OPTION: OptionItem = {
  mode: 'manual',
  icon: <EditOutlined />,
  title: '手动输入 MCP 信息',
  description: '手动填写 MCP 产品信息、配置协议和连接方式',
}

const IMPORT_OPTIONS: OptionItem[] = [
  {
    mode: 'gateway',
    icon: <ApiOutlined />,
    title: '从网关导入',
    description: '从已导入的网关实例中选择 MCP Server',
  },
  {
    mode: 'nacos',
    icon: <DatabaseOutlined />,
    title: '从 Nacos 导入',
    description: '从 Nacos 服务注册中心导入 MCP Server',
  },
  {
    mode: 'vendor',
    icon: <ShopOutlined />,
    title: '从第三方市场导入',
    description: '从 ModelScope、MCP Registry 等市场批量导入',
  },
]

function OptionCard({ item, onSelect }: { item: OptionItem; onSelect: (mode: CreationMode) => void }) {
  return (
    <div
      role="button"
      tabIndex={0}
      onClick={() => onSelect(item.mode)}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          onSelect(item.mode)
        }
      }}
      className="flex items-start gap-3 rounded-lg border border-gray-200 px-4 py-3.5 cursor-pointer transition-all duration-150 hover:border-blue-400 hover:bg-blue-50/40 hover:shadow-sm"
    >
      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-blue-50 text-blue-500 text-base">
        {item.icon}
      </div>
      <div className="min-w-0">
        <div className="text-sm font-medium text-gray-800">{item.title}</div>
        <div className="mt-0.5 text-xs text-gray-400 leading-relaxed">{item.description}</div>
      </div>
    </div>
  )
}

export function McpCreationSelector({ visible, onCancel, onSelect }: McpCreationSelectorProps) {
  return (
    <Modal
      open={visible}
      title="创建 MCP"
      footer={null}
      onCancel={onCancel}
      width={520}
      destroyOnClose
    >
      <div className="py-2 space-y-5">
        {/* 手动输入 */}
        <div>
          <div className="mb-2 text-xs font-medium text-gray-500 tracking-wide">手动创建</div>
          <OptionCard item={MANUAL_OPTION} onSelect={onSelect} />
        </div>

        {/* 导入 MCP 信息 */}
        <div>
          <div className="mb-2 flex items-center gap-1.5 text-xs font-medium text-gray-500 tracking-wide">
            <CloudDownloadOutlined className="text-gray-400" />
            导入 MCP 信息
          </div>
          <div className="space-y-2">
            {IMPORT_OPTIONS.map((item) => (
              <OptionCard key={item.mode} item={item} onSelect={onSelect} />
            ))}
          </div>
        </div>
      </div>
    </Modal>
  )
}

export default McpCreationSelector

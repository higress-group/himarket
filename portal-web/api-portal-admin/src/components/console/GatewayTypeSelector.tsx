import { Modal, Radio, Button, Space } from 'antd'
import { useState } from 'react'
import { GatewayType } from '@/types'
import { GATEWAY_TYPE_LABELS } from '@/lib/constant'

interface GatewayTypeSelectorProps {
  visible: boolean
  onCancel: () => void
  onSelect: (type: GatewayType) => void
}

export default function GatewayTypeSelector({ visible, onCancel, onSelect }: GatewayTypeSelectorProps) {
  const [selectedType, setSelectedType] = useState<GatewayType>('APIG_API')

  const handleConfirm = () => {
    onSelect(selectedType)
  }

  const handleCancel = () => {
    setSelectedType('APIG_API')
    onCancel()
  }

  return (
    <Modal
      title="选择网关类型"
      open={visible}
      onCancel={handleCancel}
      footer={[
        <Button key="cancel" onClick={handleCancel}>
          取消
        </Button>,
        <Button key="confirm" type="primary" onClick={handleConfirm}>
          确定
        </Button>
      ]}
      width={500}
    >
      <div className="py-4">
        <Radio.Group 
          value={selectedType} 
          onChange={(e) => setSelectedType(e.target.value)}
          className="w-full"
        >
          <Space direction="vertical" className="w-full">
            <Radio value="APIG_API" className="w-full p-3 border rounded-lg hover:bg-gray-50">
              <div className="ml-2">
                <div className="font-medium">{GATEWAY_TYPE_LABELS.APIG_API}</div>
                <div className="text-sm text-gray-500">阿里云 API 网关服务</div>
              </div>
            </Radio>
            <Radio value="APIG_AI" className="w-full p-3 border rounded-lg hover:bg-gray-50">
              <div className="ml-2">
                <div className="font-medium">{GATEWAY_TYPE_LABELS.APIG_AI}</div>
                <div className="text-sm text-gray-500">阿里云 AI 网关服务</div>
              </div>
            </Radio>
            <Radio value="HIGRESS" className="w-full p-3 border rounded-lg hover:bg-gray-50">
              <div className="ml-2">
                <div className="font-medium">{GATEWAY_TYPE_LABELS.HIGRESS}</div>
                <div className="text-sm text-gray-500">Higress 云原生网关</div>
              </div>
            </Radio>
            <Radio value="ADP_AI_GATEWAY" className="w-full p-3 border rounded-lg hover:bg-gray-50">
              <div className="ml-2">
                <div className="font-medium">{GATEWAY_TYPE_LABELS.ADP_AI_GATEWAY}</div>
                <div className="text-sm text-gray-500">专有云 AI 网关服务</div>
              </div>
            </Radio>
            <Radio value="APSARA_GATEWAY" className="w-full p-3 border rounded-lg hover:bg-gray-50">
              <div className="ml-2">
                <div className="font-medium">{GATEWAY_TYPE_LABELS.APSARA_GATEWAY}</div>
                <div className="text-sm text-gray-500">阿里云飞天企业版 AI 网关</div>
              </div>
            </Radio>
          </Space>
        </Radio.Group>
      </div>
    </Modal>
  )
}

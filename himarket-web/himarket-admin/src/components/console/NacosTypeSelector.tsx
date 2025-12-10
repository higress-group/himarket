import { Modal, Radio, Button, Space } from 'antd'
import { useState } from 'react'

export type NacosImportType = 'OPEN_SOURCE' | 'MSE'

interface NacosTypeSelectorProps {
  visible: boolean
  onCancel: () => void
  onSelect: (type: NacosImportType) => void
}

export default function NacosTypeSelector({ visible, onCancel, onSelect }: NacosTypeSelectorProps) {
  const [selectedType, setSelectedType] = useState<NacosImportType>('MSE')

  const handleConfirm = () => {
    onSelect(selectedType)
  }

  const handleCancel = () => {
  setSelectedType('OPEN_SOURCE')
    onCancel()
  }

  return (
    <Modal
      title="选择 Nacos 类型"
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
            <Radio value="MSE" className="w-full p-3 border rounded-lg hover:bg-gray-50">
              <div className="ml-2">
                <div className="font-medium">MSE Nacos</div>
                <div className="text-sm text-gray-500">通过阿里云 MSE 账号授权后选择实例导入</div>
              </div>
            </Radio>
            <Radio value="OPEN_SOURCE" className="w-full p-3 border rounded-lg hover:bg-gray-50">
              <div className="ml-2">
                <div className="font-medium">开源 Nacos</div>
                <div className="text-sm text-gray-500">使用已有自建/开源 Nacos 地址登录创建</div>
              </div>
            </Radio>
          </Space>
        </Radio.Group>
      </div>
    </Modal>
  )
}

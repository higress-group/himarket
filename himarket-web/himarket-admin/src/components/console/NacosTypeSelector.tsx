import { Modal, Radio, Button, Space } from 'antd';
import { useState } from 'react';

export type NacosImportType = 'OPEN_SOURCE' | 'MSE';

interface NacosTypeSelectorProps {
  visible: boolean;
  onCancel: () => void;
  onSelect: (type: NacosImportType) => void;
}

export default function NacosTypeSelector({ onCancel, onSelect, visible }: NacosTypeSelectorProps) {
  const [selectedType, setSelectedType] = useState<NacosImportType>('MSE');

  const handleConfirm = () => {
    onSelect(selectedType);
  };

  const handleCancel = () => {
    setSelectedType('OPEN_SOURCE');
    onCancel();
  };

  return (
    <Modal
      footer={[
        <Button key="cancel" onClick={handleCancel}>
          取消
        </Button>,
        <Button key="confirm" onClick={handleConfirm} type="primary">
          确定
        </Button>,
      ]}
      onCancel={handleCancel}
      open={visible}
      title="选择 Nacos 类型"
      width={500}
    >
      <div className="py-4">
        <Radio.Group
          className="w-full"
          onChange={(e) => setSelectedType(e.target.value)}
          value={selectedType}
        >
          <Space className="w-full" direction="vertical">
            <Radio className="w-full p-3 border rounded-lg hover:bg-gray-50" value="MSE">
              <div className="ml-2">
                <div className="font-medium">MSE Nacos</div>
                <div className="text-sm text-gray-500">通过阿里云 MSE 账号授权后选择实例导入</div>
              </div>
            </Radio>
            <Radio className="w-full p-3 border rounded-lg hover:bg-gray-50" value="OPEN_SOURCE">
              <div className="ml-2">
                <div className="font-medium">开源 Nacos</div>
                <div className="text-sm text-gray-500">使用已有自建/开源 Nacos 地址登录创建</div>
              </div>
            </Radio>
          </Space>
        </Radio.Group>
      </div>
    </Modal>
  );
}

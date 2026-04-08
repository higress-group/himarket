import { Modal, Radio, Button, Space } from 'antd';
import { useState } from 'react';

import { GATEWAY_TYPE_LABELS } from '@/lib/constant';
import type { GatewayType } from '@/types';

interface GatewayTypeSelectorProps {
  visible: boolean;
  onCancel: () => void;
  onSelect: (type: GatewayType) => void;
}

export default function GatewayTypeSelector({
  onCancel,
  onSelect,
  visible,
}: GatewayTypeSelectorProps) {
  const [selectedType, setSelectedType] = useState<GatewayType>('APIG_API');

  const handleConfirm = () => {
    onSelect(selectedType);
  };

  const handleCancel = () => {
    setSelectedType('APIG_API');
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
      title="选择网关类型"
      width={500}
    >
      <div className="py-4">
        <Radio.Group
          className="w-full"
          onChange={(e) => setSelectedType(e.target.value)}
          value={selectedType}
        >
          <Space className="w-full" direction="vertical">
            <Radio className="w-full p-3 border rounded-lg hover:bg-gray-50" value="APIG_API">
              <div className="ml-2">
                <div className="font-medium">{GATEWAY_TYPE_LABELS.APIG_API}</div>
                <div className="text-sm text-gray-500">阿里云 API 网关服务</div>
              </div>
            </Radio>
            <Radio className="w-full p-3 border rounded-lg hover:bg-gray-50" value="APIG_AI">
              <div className="ml-2">
                <div className="font-medium">{GATEWAY_TYPE_LABELS.APIG_AI}</div>
                <div className="text-sm text-gray-500">阿里云 AI 网关服务</div>
              </div>
            </Radio>
            <Radio className="w-full p-3 border rounded-lg hover:bg-gray-50" value="HIGRESS">
              <div className="ml-2">
                <div className="font-medium">{GATEWAY_TYPE_LABELS.HIGRESS}</div>
                <div className="text-sm text-gray-500">Higress 云原生网关</div>
              </div>
            </Radio>
            <Radio className="w-full p-3 border rounded-lg hover:bg-gray-50" value="ADP_AI_GATEWAY">
              <div className="ml-2">
                <div className="font-medium">{GATEWAY_TYPE_LABELS.ADP_AI_GATEWAY}</div>
                <div className="text-sm text-gray-500">专有云 AI 网关服务</div>
              </div>
            </Radio>
            <Radio className="w-full p-3 border rounded-lg hover:bg-gray-50" value="APSARA_GATEWAY">
              <div className="ml-2">
                <div className="font-medium">{GATEWAY_TYPE_LABELS.APSARA_GATEWAY}</div>
                <div className="text-sm text-gray-500">阿里云飞天企业版 AI 网关</div>
              </div>
            </Radio>
          </Space>
        </Radio.Group>
      </div>
    </Modal>
  );
}

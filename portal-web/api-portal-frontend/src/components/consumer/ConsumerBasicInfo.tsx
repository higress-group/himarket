import { Descriptions } from "antd";
import type { Consumer } from "../../types/consumer";

interface ConsumerBasicInfoProps {
  consumer: Consumer;
}

export function ConsumerBasicInfo({ consumer }: ConsumerBasicInfoProps) {
  return (
    <div className="bg-white/60 backdrop-blur-sm rounded-xl p-6 border border-white/60 shadow-sm">
      <h3 className="text-base font-semibold text-gray-900 mb-4">基本信息</h3>
      <Descriptions column={2} size="middle">
        <Descriptions.Item label="名称">{consumer.name}</Descriptions.Item>
        <Descriptions.Item label="描述">{consumer.description || '-'}</Descriptions.Item>
      </Descriptions>
    </div>
  );
}

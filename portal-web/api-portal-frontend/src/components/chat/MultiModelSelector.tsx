import { useState } from "react";
import { Modal, Checkbox, Spin } from "antd";
import { RobotOutlined, ThunderboltOutlined, BulbOutlined, PictureOutlined } from "@ant-design/icons";

interface Model {
  id: string;
  name: string;
  description: string;
  category: string;
  icon: string;
  productCategories: string[]; // 产品分类 ID 数组
}

interface MultiModelSelectorProps {
  currentModel: string;
  excludeModels?: string[];
  onConfirm: (models: string[]) => void;
  onCancel: () => void;
  modelList?: Model[];
  loading?: boolean;
}

// 图标映射组件
const ModelIcon = ({ iconType }: { iconType: string }) => {
  const iconClass = "text-lg";
  switch (iconType) {
    case "robot":
      return <RobotOutlined className={iconClass} />;
    case "thunderbolt":
      return <ThunderboltOutlined className={iconClass} />;
    case "bulb":
      return <BulbOutlined className={iconClass} />;
    case "picture":
      return <PictureOutlined className={iconClass} />;
    default:
      return <RobotOutlined className={iconClass} />;
  }
};

export function MultiModelSelector({ currentModel, excludeModels = [], onConfirm, onCancel, modelList = [], loading = false }: MultiModelSelectorProps) {
  const [selectedModels, setSelectedModels] = useState<string[]>([]);

  // 过滤掉已排除的模型
  const availableModels = modelList.filter(model => !excludeModels.includes(model.id));

  const handleToggleModel = (modelId: string) => {
    // 当前模型不能被取消选择
    if (modelId === currentModel) return;

    setSelectedModels((prev) => {
      if (prev.includes(modelId)) {
        return prev.filter((id) => id !== modelId);
      } else {
        // 计算还能选择多少个（总共3个减去已排除的）
        const maxSelectable = 3 - excludeModels.length;
        if (prev.length >= maxSelectable) {
          return prev;
        }
        return [...prev, modelId];
      }
    });
  };

  const handleConfirm = () => {
    if (selectedModels.length >= 1) {
      onConfirm(selectedModels);
    }
  };

  // 计算还能选择多少个
  const maxSelectable = 3 - excludeModels.length;

  return (
    <Modal
      title="选择对比模型"
      open={true}
      onOk={handleConfirm}
      onCancel={onCancel}
      okText="开始对比"
      cancelText="取消"
      okButtonProps={{ disabled: selectedModels.length < 1 }}
      width={600}
    >
      <div className="py-4">
        <div className="mb-4 text-sm text-gray-500">
          {excludeModels.length > 0 ? (
            <>
              已选模型：<span className="font-semibold text-colorPrimary">{excludeModels.join(', ')}</span>
              {" "}| 再选择 1-{maxSelectable} 个模型进行对比（已选 {selectedModels.length}/{maxSelectable}）
            </>
          ) : (
            <>
              当前模型：<span className="font-semibold text-colorPrimary">{currentModel}</span>
              {" "}| 再选择 1-2 个模型进行对比（已选 {selectedModels.length}/2）
            </>
          )}
        </div>

        {loading ? (
          <div className="flex items-center justify-center py-12">
            <Spin size="large" tip="加载模型列表..." />
          </div>
        ) : (
          <div className="space-y-2 max-h-[400px] overflow-y-auto">
            {availableModels.map((model) => {
              const isCurrentModel = model.id === currentModel && excludeModels.length === 0;
              const isSelected = selectedModels.includes(model.id);
              const isDisabled = !isCurrentModel && !isSelected && selectedModels.length >= maxSelectable;

              return (
                <div
                  key={model.id}
                  onClick={() => !isDisabled && handleToggleModel(model.id)}
                  className={`
                    p-4 rounded-xl border transition-all duration-200
                    ${
                      isCurrentModel
                        ? "bg-colorPrimary/5 border-colorPrimary/30 cursor-default"
                        : isSelected
                        ? "bg-colorPrimary/10 border-colorPrimary"
                        : isDisabled
                        ? "bg-gray-50 border-gray-200 cursor-not-allowed opacity-50"
                        : "bg-white border-gray-200 hover:border-colorPrimary hover:bg-colorPrimary/5 cursor-pointer"
                    }
                  `}
                >
                  <div className="flex items-start gap-3">
                    {isCurrentModel ? (
                      <div className="mt-0.5 text-xs text-colorPrimary font-medium">当前</div>
                    ) : (
                      <Checkbox
                        checked={isSelected}
                        disabled={isDisabled}
                        onChange={() => handleToggleModel(model.id)}
                      />
                    )}

                    <ModelIcon iconType={model.icon} />

                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <span className="font-semibold text-gray-900">{model.name}</span>
                      </div>
                      <p className="text-sm text-gray-600">{model.description}</p>
                    </div>
                  </div>
                </div>
              );
            })}
            {!loading && availableModels.length === 0 && (
              <div className="text-center py-12 text-gray-400">
                暂无可选模型
              </div>
            )}
          </div>
        )}
      </div>
    </Modal>
  );
}

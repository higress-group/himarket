import { useState } from "react";
import { Modal, Checkbox, Spin } from "antd";
import { ProductIconRenderer } from "../icon/ProductIconRenderer";
import type { IProductDetail } from "../../lib/apis";


interface MultiModelSelectorProps {
  currentModelId: string;
  excludeModels?: string[];
  onConfirm: (models: string[]) => void;
  onCancel: () => void;
  modelList?: IProductDetail[];
  loading?: boolean;
}

export function MultiModelSelector({ currentModelId, excludeModels = [], onConfirm, onCancel, modelList = [], loading = false }: MultiModelSelectorProps) {
  const [selectedModels, setSelectedModels] = useState<string[]>([]);

  // 过滤掉已排除的模型
  const availableModels = modelList.filter(model => !excludeModels.includes(model.productId));

  // 根据模型ID获取模型名称
  const getModelName = (modelId: string) => {
    const model = modelList.find(m => m.productId === modelId);
    return model ? model.name : modelId;
  };

  const handleToggleModel = (modelId: string) => {
    // 当前模型不能被取消选择
    if (modelId === currentModelId) return;

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
      okButtonProps={{
        disabled: selectedModels.length < 1,
        className: "rounded-lg"
      }}
      cancelButtonProps={{
        className: "rounded-lg"
      }}
      width={600}
      className="multi-model-selector-modal"
      styles={{
        body: {
          borderRadius: '16px',
          overflow: 'hidden'
        }
      }}
    >
      <div className="py-4">
        <div className="mb-4 text-sm text-gray-500">
          {excludeModels.length > 0 ? (
            <>
              已选模型：<span className="font-medium text-colorPrimary">{excludeModels.map(id => getModelName(id)).join('、')}</span>
              {" "}| 再选择 1-{maxSelectable} 个模型（已选 {selectedModels.length}/{maxSelectable}）
            </>
          ) : (
            <>
              当前模型：<span className="font-medium text-colorPrimary">{getModelName(currentModelId)}</span>
              {" "}| 再选择 1-2 个模型（已选 {selectedModels.length}/2）
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
              const isCurrentModel = model.productId === currentModelId && excludeModels.length === 0;
              const isSelected = selectedModels.includes(model.productId);
              const isDisabled = !isCurrentModel && !isSelected && selectedModels.length >= maxSelectable;

              return (
                <div
                  key={model.productId}
                  onClick={() => !isDisabled && handleToggleModel(model.productId)}
                  className={`
                    px-4 py-3 rounded-xl border transition-all duration-200
                    ${
                      isCurrentModel
                        ? "bg-colorPrimary/5 border-colorPrimary/30 cursor-default"
                        : isSelected
                        ? "bg-colorPrimary/10 border-colorPrimary shadow-sm "
                        : isDisabled
                        ? "bg-gray-50 border-gray-200 cursor-not-allowed opacity-60"
                        : "bg-white border-gray-200 hover:border-colorPrimary/50 hover:bg-colorPrimaryBgHover cursor-pointer hover:shadow-sm"
                    }
                  `}
                >
                  <div className="flex items-center gap-3">
                    {isCurrentModel ? (
                      <div className="px-2 py-0.5 text-xs text-colorPrimary font-medium bg-colorPrimary/10 rounded">
                        当前
                      </div>
                    ) : (
                      <Checkbox
                        checked={isSelected}
                        disabled={isDisabled}
                        onChange={() => handleToggleModel(model.productId)}
                        onClick={(e) => e.stopPropagation()}
                      />
                    )}

                    <ProductIconRenderer iconType={model.icon?.value} className="w-6 h-6" />

                    <div className="flex-1 min-w-0">
                      <div className="font-semibold text-gray-900 mb-0.5">{model.name}</div>
                      <p className="text-sm text-gray-500 line-clamp-1">{model.description}</p>
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

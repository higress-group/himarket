import { Button, Checkbox, Modal, Spin } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { ProductIconRenderer } from '../icon/ProductIconRenderer';

import type { IProductDetail } from '../../lib/apis';

interface MultiModelSelectorProps {
  currentModelId: string;
  excludeModels?: string[];
  onConfirm: (models: string[]) => void;
  onCancel: () => void;
  modelList?: IProductDetail[];
  loading?: boolean;
}

export function MultiModelSelector({
  currentModelId,
  excludeModels = [],
  loading = false,
  modelList = [],
  onCancel,
  onConfirm,
}: MultiModelSelectorProps) {
  const { t } = useTranslation('chat');
  const [selectedModels, setSelectedModels] = useState<string[]>([]);

  // 过滤掉已排除的模型
  const availableModels = modelList.filter((model) => !excludeModels.includes(model.productId));

  // 根据模型ID获取模型名称
  const getModelName = (modelId: string) => {
    const model = modelList.find((m) => m.productId === modelId);
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
  const baseModelIds = excludeModels.length > 0 ? excludeModels : [currentModelId];

  return (
    <Modal
      className="multi-model-selector-modal"
      footer={null}
      onCancel={onCancel}
      open={true}
      styles={{
        body: {
          borderRadius: 16,
          overflow: 'hidden',
          paddingTop: 14,
        },
      }}
      title={t('multiModel.title')}
      width={700}
    >
      <div className="pb-1">
        <div className="mb-4 rounded-[16px] bg-[#F7F9FC] p-4">
          <div className="flex items-start justify-between gap-4">
            <div className="min-w-0">
              <div className="mb-2 text-xs font-medium text-gray-400">
                {t('multiModel.baseModels')}
              </div>
              <div className="flex flex-wrap gap-2">
                {baseModelIds.map((modelId) => (
                  <span
                    className="max-w-[220px] truncate rounded-[10px] border border-[#E1E8F2] bg-white px-3 py-1.5 text-sm font-medium text-gray-800"
                    key={modelId}
                  >
                    {getModelName(modelId)}
                  </span>
                ))}
              </div>
            </div>
            <div className="flex-shrink-0 rounded-[12px] bg-white px-3.5 py-2 text-sm font-medium text-gray-500 shadow-[0_6px_18px_rgba(37,56,88,0.045)]">
              <span className="text-colorPrimary">{selectedModels.length}</span>
              <span className="mx-1 text-gray-300">/</span>
              <span>{maxSelectable}</span>
            </div>
          </div>
          <div className="mt-3 text-sm text-gray-500">
            {excludeModels.length > 0
              ? maxSelectable === 1
                ? t('multiModel.selectOne', { current: selectedModels.length })
                : t('multiModel.selectMore', {
                    current: selectedModels.length,
                    max: maxSelectable,
                  })
              : t('multiModel.selectMoreDefault', { current: selectedModels.length })}
          </div>
        </div>

        {loading ? (
          <div className="flex items-center justify-center py-12">
            <Spin size="large" tip={t('multiModel.loadingModels')} />
          </div>
        ) : (
          <div className="max-h-[400px] space-y-2 overflow-y-auto pr-1">
            {availableModels.map((model) => {
              const isCurrentModel =
                model.productId === currentModelId && excludeModels.length === 0;
              const isSelected = selectedModels.includes(model.productId);
              const isDisabled =
                !isCurrentModel && !isSelected && selectedModels.length >= maxSelectable;

              return (
                <button
                  className={`
                    flex w-full items-center gap-3.5 rounded-[14px] border px-4 py-3 text-left transition-all duration-200 active:scale-[0.99]
                    ${
                      isCurrentModel
                        ? 'cursor-default border-colorPrimary/25 bg-[#F8FAFF]'
                        : isSelected
                          ? 'border-colorPrimary/45 bg-[#F6F7FF]'
                          : isDisabled
                            ? 'cursor-not-allowed border-gray-200 bg-gray-50 opacity-60'
                            : 'cursor-pointer border-[#DDE5F0] bg-white hover:border-colorPrimary/35 hover:bg-[#FAFBFF]'
                    }
                  `}
                  disabled={isDisabled}
                  key={model.productId}
                  onClick={() => !isDisabled && handleToggleModel(model.productId)}
                  type="button"
                >
                  <div className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-[12px] border border-[#E6ECF4] bg-[#F8FAFC]">
                    <ProductIconRenderer className="h-6 w-6" iconType={model.icon?.value} />
                  </div>

                  <div className="min-w-0 flex-1">
                    <div className="mb-0.5 truncate font-semibold text-gray-900">{model.name}</div>
                    <p className="line-clamp-1 text-sm text-gray-500">
                      {model.description || t('multiModel.noDescription')}
                    </p>
                  </div>

                  <div className="flex flex-shrink-0 items-center">
                    {isCurrentModel ? (
                      <div className="rounded-[9px] bg-colorPrimary/10 px-2.5 py-1 text-xs font-medium text-colorPrimary">
                        {t('multiModel.current')}
                      </div>
                    ) : (
                      <Checkbox
                        checked={isSelected}
                        disabled={isDisabled}
                        onChange={() => handleToggleModel(model.productId)}
                        onClick={(e) => e.stopPropagation()}
                      />
                    )}
                  </div>
                </button>
              );
            })}
            {!loading && availableModels.length === 0 && (
              <div className="flex flex-col items-center justify-center rounded-[16px] border border-dashed border-[#DDE5F0] bg-[#F8FAFC] py-12 text-center text-gray-400">
                {t('multiModel.noAvailableModels')}
              </div>
            )}
          </div>
        )}

        <div className="mt-5 flex items-center justify-between gap-3 border-t border-[#EEF2F7] pt-4">
          <div className="text-sm text-gray-500">
            {t('multiModel.selectedCount', {
              current: selectedModels.length,
              max: maxSelectable,
            })}
          </div>
          <div className="flex items-center gap-2">
            <Button className="rounded-[10px]" onClick={onCancel}>
              {t('multiModel.cancel')}
            </Button>
            <Button
              className="rounded-[10px]"
              disabled={selectedModels.length < 1}
              onClick={handleConfirm}
              type="primary"
            >
              {t('multiModel.startCompare')}
            </Button>
          </div>
        </div>
      </div>
    </Modal>
  );
}

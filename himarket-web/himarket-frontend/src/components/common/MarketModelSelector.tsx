import { Select, Alert, Spin, Button } from 'antd';
import { RefreshCw } from 'lucide-react';
import { useState, useEffect, useCallback } from 'react';

import { getMarketModels, type MarketModelInfo } from '../../lib/apis/cliProvider';

// ============ 类型定义 ============

/** 市场模型选择结果：仅包含标识符 */
export interface MarketModelSelection {
  productId: string;
  name: string;
}

export interface MarketModelSelectorProps {
  /** 是否启用（开关状态） */
  enabled: boolean;
  /** 选择模型后回调，data 为 null 表示未选择 */
  onChange: (data: MarketModelSelection | null) => void;
}

// ============ 组件 ============

export function MarketModelSelector({ enabled, onChange }: MarketModelSelectorProps) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [models, setModels] = useState<MarketModelInfo[]>([]);
  const [selectedProductId, setSelectedProductId] = useState<string | null>(null);

  const fetchModels = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await getMarketModels();
      const data = res.data;
      const fetchedModels = data.models ?? [];
      setModels(fetchedModels);
      // 自动选中第一个模型
      if (fetchedModels.length > 0) {
        const first = fetchedModels[0];
        if (first) {
          setSelectedProductId(first.productId);
          onChange({
            name: first.name,
            productId: first.productId,
          });
        }
      }
    } catch (err: unknown) {
      // 401 未登录
      const response = (err as Record<string, unknown>)?.response as
        | Record<string, unknown>
        | undefined;
      if (response?.status === 401) {
        setError('请先登录以使用模型市场模型');
      } else {
        setError(err instanceof Error ? err.message : '获取模型市场模型列表失败');
      }
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // enabled 变为 true 时获取数据
  useEffect(() => {
    if (enabled) {
      setSelectedProductId(null);
      onChange(null);
      fetchModels();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, fetchModels]);

  // 选择模型时仅传递标识符
  const handleSelect = useCallback(
    (productId: string) => {
      setSelectedProductId(productId);
      const model = models.find((m) => m.productId === productId);
      if (model) {
        onChange({
          name: model.name,
          productId: model.productId,
        });
      } else {
        onChange(null);
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [models],
  );

  if (!enabled) {
    return null;
  }

  // 加载中
  if (loading) {
    return (
      <div className="flex justify-center py-4">
        <Spin size="small" />
      </div>
    );
  }

  // 错误状态
  if (error) {
    return (
      <div className="flex flex-col items-center gap-2 w-full">
        <Alert className="w-full" message={error} showIcon type="error" />
        <Button icon={<RefreshCw size={14} />} onClick={fetchModels} size="small">
          重试
        </Button>
      </div>
    );
  }

  // 模型列表为空
  if (models.length === 0) {
    return (
      <Alert
        className="w-full"
        message="暂无已订阅的模型，请先在模型市场中订阅模型"
        showIcon
        type="info"
      />
    );
  }

  // 模型列表非空，展示下拉选择器
  return (
    <div className="flex flex-col gap-1.5 w-full">
      <label className="text-sm font-medium text-gray-600 text-center">模型市场模型</label>
      <Select
        className="w-full"
        onChange={handleSelect}
        options={models.map((m) => ({
          label: m.name,
          value: m.productId,
        }))}
        placeholder="选择模型"
        value={selectedProductId ?? undefined}
      />
    </div>
  );
}

import { useState, useEffect, useCallback } from "react";
import { Select, Alert, Spin, Button } from "antd";
import { RefreshCw } from "lucide-react";
import {
  getMarketModels,
  type MarketModelInfo,
} from "../../lib/apis/cliProvider";

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

export function MarketModelSelector({
  enabled,
  onChange,
}: MarketModelSelectorProps) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [models, setModels] = useState<MarketModelInfo[]>([]);
  const [selectedProductId, setSelectedProductId] = useState<string | null>(
    null
  );

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
        setSelectedProductId(first.productId);
        onChange({
          productId: first.productId,
          name: first.name,
        });
      }
    } catch (err: any) {
      // 401 未登录
      if (err?.response?.status === 401) {
        setError("请先登录以使用模型市场模型");
      } else {
        setError(
          err instanceof Error ? err.message : "获取模型市场模型列表失败"
        );
      }
    } finally {
      setLoading(false);
    }
  }, []);

  // enabled 变为 true 时获取数据
  useEffect(() => {
    if (enabled) {
      setSelectedProductId(null);
      onChange(null);
      fetchModels();
    }
  }, [enabled, fetchModels]);

  // 选择模型时仅传递标识符
  const handleSelect = useCallback(
    (productId: string) => {
      setSelectedProductId(productId);
      const model = models.find((m) => m.productId === productId);
      if (model) {
        onChange({
          productId: model.productId,
          name: model.name,
        });
      } else {
        onChange(null);
      }
    },
    [models, onChange]
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
        <Alert message={error} type="error" showIcon className="w-full" />
        <Button
          size="small"
          icon={<RefreshCw size={14} />}
          onClick={fetchModels}
        >
          重试
        </Button>
      </div>
    );
  }

  // 模型列表为空
  if (models.length === 0) {
    return (
      <Alert
        message="暂无已订阅的模型，请先在模型市场中订阅模型"
        type="info"
        showIcon
        className="w-full"
      />
    );
  }

  // 模型列表非空，展示下拉选择器
  return (
    <div className="flex flex-col gap-1.5 w-full">
      <label className="text-sm font-medium text-gray-600 text-center">
        模型市场模型
      </label>
      <Select
        value={selectedProductId ?? undefined}
        onChange={handleSelect}
        placeholder="选择模型"
        className="w-full"
        options={models.map((m) => ({
          value: m.productId,
          label: m.name,
        }))}
      />
    </div>
  );
}

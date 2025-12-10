import { useState } from "react";
import { DownOutlined, CheckOutlined, SearchOutlined } from "@ant-design/icons";
import { Dropdown, Input, Tabs, Spin } from "antd";
import { ProductIconRenderer } from "../icon/ProductIconRenderer";
import type { ICategory, IProductDetail } from "../../lib/apis";


interface ModelSelectorProps {
  selectedModelId: string;
  onSelectModel: (model: IProductDetail) => void;
  modelList?: IProductDetail[];
  loading?: boolean;
  categories: ICategory[]; // 分类列表
  categoriesLoading?: boolean; // 分类加载状态
}

export function ModelSelector({
  selectedModelId,
  onSelectModel,
  modelList = [],
  loading = false,
  categories = [],
  categoriesLoading = false,
}: ModelSelectorProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [activeCategory, setActiveCategory] = useState<string>("all");

  const currentModel = modelList.find(m => m.productId === selectedModelId) || modelList[0];

  // 根据分类和搜索过滤模型
  const filteredModels = modelList.filter(model => {
    // 分类过滤：如果选择"全部"或模型的 productCategories 包含当前选中的分类
    const matchesCategory = activeCategory === "all" ||
      model.categories.map(c => c.categoryId).includes(activeCategory);

    // 搜索过滤
    const matchesSearch = searchQuery === "" ||
      model.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      model.description.toLowerCase().includes(searchQuery.toLowerCase());

    return matchesCategory && matchesSearch;
  });

  const handleModelSelect = (model: IProductDetail) => {
    onSelectModel(model);
    setIsOpen(false);
    setSearchQuery("");
  };

  // 浮层内容
  const dropdownContent = (
    <div className="bg-white rounded-lg shadow-xl border border-gray-200 w-[420px] max-h-[500px] flex flex-col">
      {/* 搜索框 */}
      <div className="p-4 pb-3">
        <Input
          prefix={<SearchOutlined className="text-gray-400" />}
          placeholder="搜索模型..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="rounded-lg"
          allowClear
        />
      </div>

      {/* Tab 分类 */}
      <div className="px-4">
        {categoriesLoading ? (
          <div className="flex items-center justify-center py-4">
            <span className="text-gray-400 text-sm">加载分类中...</span>
          </div>
        ) : (
          <Tabs
            activeKey={activeCategory}
            onChange={setActiveCategory}
            items={categories.map(category => ({
              key: category.categoryId,
              label: category.name,
            }))}
          />
        )}
      </div>

      {/* 模型列表 */}
      <div className="flex-1 overflow-y-auto px-4 pb-4">
        {loading ? (
          <div className="flex items-center justify-center py-8">
            <Spin tip="加载中..." />
          </div>
        ) : (
          <div className="space-y-1">
            {filteredModels.map(model => (
              <div
                key={model.productId}
                onClick={() => handleModelSelect(model)}
                className={`
                  px-3 py-2.5 rounded-lg cursor-pointer
                  flex items-center gap-3
                  transition-all duration-200
                  hover:bg-colorPrimaryBgHover hover:scale-[1.01]
                  ${
                    model.productId === selectedModelId
                      ? "bg-colorPrimary/10 text-colorPrimary"
                      : "text-gray-700 hover:text-gray-900"
                  }
                `}
              >
                <ProductIconRenderer iconType={model.icon?.value} className="w-5 h-5" />
                <span className="font-medium flex-1">{model.name}</span>
                {model.productId === selectedModelId && (
                  <CheckOutlined className="text-colorPrimary text-xs" />
                )}
              </div>
            ))}
            {!loading && filteredModels.length === 0 && (
              <div className="text-center py-8 text-gray-400">
                未找到匹配的模型
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );

  return (
    <>
      {/* 顶部模型选择器 */}
      <div className="p-4 pr-0">
        <Dropdown
          open={isOpen}
          onOpenChange={setIsOpen}
          popupRender={() => dropdownContent}
          trigger={['click']}
          placement="bottomLeft"
        >
          {/* 当前模型 */}
          <button
            className="flex items-center gap-2 px-4 py-2 rounded-xl transition-all duration-200 hover:scale-[1.01] hover:bg-colorPrimaryBgHover"
          >
            {currentModel?.icon && (
              <ProductIconRenderer iconType={currentModel.icon.value} className="w-5 h-5" />
            )}
            <span className="font-medium text-gray-900">
              {currentModel?.name || "选择模型"}
            </span>
            <DownOutlined className={`text-xs text-gray-500 transition-transform duration-200 ${isOpen ? 'rotate-180' : ''}`} />
          </button>
        </Dropdown>
      </div>
    </>
  );
}

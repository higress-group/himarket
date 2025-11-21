import { useState } from "react";
import { DownOutlined, CheckOutlined, SearchOutlined, RobotOutlined, ThunderboltOutlined, BulbOutlined, PictureOutlined } from "@ant-design/icons";
import { Dropdown, Input, Tabs, Spin } from "antd";

interface Model {
  id: string;
  name: string;
  description: string;
  category: string;
  icon: string;
  productCategories: string[]; // 产品分类 ID 数组
}

interface ModelSelectorProps {
  selectedModel: string;
  onSelectModel: (modelId: string) => void;
  modelList?: Model[];
  loading?: boolean;
  categories?: Array<{ id: string; name: string }>; // 分类列表
  categoriesLoading?: boolean; // 分类加载状态
}

// 图标映射组件
const ModelIcon = ({ iconType }: { iconType: string }) => {
  const iconClass = "text-base";
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

export function ModelSelector({
  selectedModel,
  onSelectModel,
  modelList = [],
  loading = false,
  categories = [],
  categoriesLoading = false,
}: ModelSelectorProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [activeCategory, setActiveCategory] = useState("all");

  const currentModel = modelList.find(m => m.id === selectedModel) || modelList[0];

  // 根据分类和搜索过滤模型
  const filteredModels = modelList.filter(model => {
    // 分类过滤：如果选择"全部"或模型的 productCategories 包含当前选中的分类
    const matchesCategory = activeCategory === "all" ||
      model.productCategories.includes(activeCategory);

    // 搜索过滤
    const matchesSearch = searchQuery === "" ||
      model.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      model.description.toLowerCase().includes(searchQuery.toLowerCase());

    return matchesCategory && matchesSearch;
  });

  const handleModelSelect = (modelId: string) => {
    onSelectModel(modelId);
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
              key: category.id,
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
                key={model.id}
                onClick={() => handleModelSelect(model.id)}
                className={`
                  px-3 py-2.5 rounded-lg cursor-pointer
                  flex items-center gap-3
                  transition-all duration-200
                  hover:bg-gray-50 hover:scale-[1.01]
                  ${
                    model.id === selectedModel
                      ? "bg-primary-50 text-primary-600"
                      : "text-gray-700 hover:text-gray-900"
                  }
                `}
              >
                <ModelIcon iconType={model.icon} />
                <span className="font-medium flex-1">{model.name}</span>
                {model.id === selectedModel && (
                  <CheckOutlined className="text-primary-500 text-xs" />
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
      <div className="p-4">
        <Dropdown
          open={isOpen}
          onOpenChange={setIsOpen}
          popupRender={() => dropdownContent}
          trigger={['click']}
          placement="bottomLeft"
        >
          <button
            className="flex items-center gap-2 px-4 py-2 rounded-lg transition-all duration-200 hover:scale-[1.01]"
          >
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

import { DownOutlined, CheckOutlined, SearchOutlined } from '@ant-design/icons';
import { Dropdown, Input, Tabs, Spin } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import EmptyData from '../../assets/empty-data.svg';
import { ProductIconRenderer } from '../icon/ProductIconRenderer';

import type { ICategory, IProductDetail } from '../../lib/apis';

interface ModelSelectorProps {
  selectedModelId: string;
  onSelectModel: (model: IProductDetail) => void;
  modelList?: IProductDetail[];
  loading?: boolean;
  categories: ICategory[]; // 分类列表
  categoriesLoading?: boolean; // 分类加载状态
}

export function ModelSelector({
  categories = [],
  categoriesLoading = false,
  loading = false,
  modelList = [],
  onSelectModel,
  selectedModelId,
}: ModelSelectorProps) {
  const { t } = useTranslation('chat');
  const [isOpen, setIsOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [activeCategory, setActiveCategory] = useState<string>('all');

  const currentModel = modelList.find((m) => m.productId === selectedModelId) || modelList[0];

  // 根据分类和搜索过滤模型
  const filteredModels = modelList.filter((model) => {
    // 分类过滤：如果选择"全部"或模型的 productCategories 包含当前选中的分类
    const matchesCategory =
      activeCategory === 'all' ||
      model.categories.map((c) => c.categoryId).includes(activeCategory);

    // 搜索过滤
    const matchesSearch =
      searchQuery === '' ||
      model.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      model.description.toLowerCase().includes(searchQuery.toLowerCase());

    return matchesCategory && matchesSearch;
  });

  const handleModelSelect = (model: IProductDetail) => {
    onSelectModel(model);
    setIsOpen(false);
    setSearchQuery('');
  };

  // 浮层内容
  const dropdownContent = (
    <div className="flex max-h-[500px] w-[min(420px,calc(100vw-48px))] flex-col rounded-[14px] border border-[#DDE5F0] bg-white shadow-xl">
      {/* 搜索框 */}
      <div className="p-4 pb-3">
        <Input
          allowClear
          className="rounded-lg"
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder={t('modelSelector.searchPlaceholder')}
          prefix={<SearchOutlined className="text-gray-400" />}
          value={searchQuery}
        />
      </div>

      {/* Tab 分类 */}
      <div className="px-4">
        {categoriesLoading ? (
          <div className="flex items-center justify-center py-4">
            <span className="text-gray-400 text-sm">{t('modelSelector.loadingCategories')}</span>
          </div>
        ) : (
          <Tabs
            activeKey={activeCategory}
            items={categories.map((category) => ({
              key: category.categoryId,
              label: category.name,
            }))}
            onChange={setActiveCategory}
          />
        )}
      </div>

      {/* 模型列表 */}
      <div className="flex-1 overflow-y-auto px-4 pb-4">
        {loading ? (
          <div className="flex items-center justify-center py-8">
            <Spin tip={t('modelSelector.loading')} />
          </div>
        ) : (
          <div className="space-y-1">
            {filteredModels.map((model) => (
              <button
                className={`
                  px-3 py-2.5 rounded-lg cursor-pointer
                  flex items-center gap-3 w-full text-left
                  transition-all duration-200
                  hover:bg-colorPrimaryBgHover hover:scale-[1.01]
                  ${
                    model.productId === selectedModelId
                      ? 'bg-colorPrimary/10 text-colorPrimary'
                      : 'text-gray-700 hover:text-gray-900'
                  }
                `}
                key={model.productId}
                onClick={() => handleModelSelect(model)}
                type="button"
              >
                <ProductIconRenderer className="w-5 h-5" iconType={model.icon?.value} />
                <span className="font-medium flex-1">{model.name}</span>
                {model.productId === selectedModelId && (
                  <CheckOutlined className="text-colorPrimary text-xs" />
                )}
              </button>
            ))}
            {!loading && filteredModels.length === 0 && (
              <div className="flex flex-col items-center justify-center gap-3 py-10 text-center text-gray-400">
                <img alt="" className="w-28 opacity-80" src={EmptyData} />
                <div className="text-[15px] font-medium text-gray-700">
                  {t('modelSelector.noModels')}
                </div>
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
      <div className="min-w-0">
        <Dropdown
          onOpenChange={setIsOpen}
          open={isOpen}
          placement="bottomLeft"
          popupRender={() => dropdownContent}
          trigger={['click']}
        >
          {/* 当前模型 */}
          <button
            className="flex h-11 max-w-[360px] items-center gap-2.5 rounded-[12px] border border-white/60 bg-white/50 px-3 pr-3.5 text-gray-900 shadow-[inset_0_1px_0_rgba(255,255,255,0.6)] backdrop-blur-md transition-all duration-200 hover:border-white/80 hover:bg-white/70 hover:shadow-[0_8px_22px_rgba(37,56,88,0.06)] active:scale-[0.98]"
            type="button"
          >
            {currentModel && (
              <span className="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-[8px] border border-white/60 bg-white/60 text-colorPrimary shadow-[inset_0_1px_0_rgba(255,255,255,0.65)]">
                <ProductIconRenderer className="h-4 w-4" iconType={currentModel.icon?.value} />
              </span>
            )}
            <span className="truncate font-medium text-gray-900">
              {currentModel?.name || t('modelSelector.selectModel')}
            </span>
            <DownOutlined
              className={`text-xs text-gray-500 transition-transform duration-200 ${isOpen ? 'rotate-180' : ''}`}
            />
          </button>
        </Dropdown>
      </div>
    </>
  );
}

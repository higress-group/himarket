import { SearchOutlined, DownloadOutlined, ClockCircleOutlined } from '@ant-design/icons';
import { Input, message, Pagination } from 'antd';
import dayjs from 'dayjs';
import { useState, useEffect, useCallback, useRef } from 'react';
import { Trans } from 'react-i18next';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';

import { EmptyState } from '../components/EmptyState';
import { Layout } from '../components/Layout';
import { CardGridSkeleton } from '../components/loading';
import { LoginPrompt } from '../components/LoginPrompt';
import BackToTopButton from '../components/scroll-to-top';
import { CategoryMenu } from '../components/square/CategoryMenu';
import { ModelCard } from '../components/square/ModelCard';
import { ProductMarketLayout } from '../components/square/ProductMarketLayout';
import { SkillCard } from '../components/square/SkillCard';
import { WorkerCard } from '../components/square/WorkerCard';
import APIs, { type ICategory } from '../lib/apis';
import { getIconString } from '../lib/iconUtils';
import { getProductCardTags } from '../lib/utils/productCardTags';

import type { IProductDetail } from '../lib/apis/product';

function Square(props: { activeType: string }) {
  const { activeType } = props;
  const navigate = useNavigate();
  const { t } = useTranslation('square');
  const [loginPromptOpen, setLoginPromptOpen] = useState(false);

  const [activeCategory, setActiveCategory] = useState('all');
  const [searchQuery, setSearchQuery] = useState('');
  const searchQueryRef = useRef('');
  const [products, setProducts] = useState<IProductDetail[]>([]);
  const [categories, setCategories] = useState<Array<{ id: string; name: string; count: number }>>(
    [],
  );
  const [loading, setLoading] = useState(true);
  const [categoriesLoading, setCategoriesLoading] = useState(true);
  const [sortBy, setSortBy] = useState<string>('DOWNLOAD_COUNT');

  const showSortControl = activeType === 'AGENT_SKILL' || activeType === 'WORKER';
  const useNewLayout =
    activeType === 'MCP_SERVER' ||
    activeType === 'AGENT_API' ||
    activeType === 'MODEL_API' ||
    activeType === 'REST_API';
  const enableSortControl = useNewLayout || showSortControl;

  // 分页相关状态
  const [currentPage, setCurrentPage] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const PAGE_SIZE = 12;

  // 滚动容器 ref，供 BackToTopButton 使用
  const scrollContainerRef = useRef<HTMLDivElement>(null);

  // activeType 切换时立即重置全部状态，避免旧数据闪烁
  useEffect(() => {
    setProducts([]);
    setCategories([]);
    setLoading(true);
    setCategoriesLoading(true);
    searchQueryRef.current = '';
    setSearchQuery('');
    setCurrentPage(1);
    setSortBy('DOWNLOAD_COUNT');
    setActiveCategory('all');
    setTotalElements(0);

    const fetchCategories = async () => {
      try {
        const productType = activeType;
        const response = await APIs.getCategoriesByProductType({ productType });

        if (response.code === 'SUCCESS' && response.data?.content) {
          const categoryList = response.data.content.map((cat: ICategory) => ({
            count: 0,
            id: cat.categoryId,
            name: cat.name,
          }));

          setCategories([{ count: 0, id: 'all', name: t('allCategory') }, ...categoryList]);
        } else {
          setCategories([{ count: 0, id: 'all', name: t('allCategory') }]);
        }
      } catch (error) {
        console.error('Failed to fetch categories:', error);
        message.error(t('fetchCategoriesFailed'));
      } finally {
        setCategoriesLoading(false);
      }
    };

    fetchCategories();
  }, [activeType, t]);

  // 获取产品列表
  const fetchProducts = useCallback(
    async (searchText?: string, page?: number) => {
      setLoading(true);
      try {
        const productType = activeType;
        const categoryIds = activeCategory === 'all' ? undefined : [activeCategory];
        const name = (searchText ?? '').trim() || undefined;
        // Frontend pagination is 1-based; backend handles database page conversion.
        const pageIndex = page ?? currentPage;

        const response = await APIs.getProducts({
          categoryIds,
          name,
          page: pageIndex,
          size: PAGE_SIZE,
          sortBy: enableSortControl ? sortBy : undefined,
          type: productType,
        });
        if (response.code === 'SUCCESS' && response.data?.content) {
          setProducts(response.data.content);
          setTotalElements(response.data.totalElements);
        }
      } catch (error) {
        console.error('Failed to fetch products:', error);
        message.error(t('fetchProductsFailed'));
      } finally {
        setLoading(false);
      }
    },
    [activeType, activeCategory, currentPage, sortBy, enableSortControl, t],
  );

  useEffect(() => {
    fetchProducts(searchQueryRef.current);
  }, [fetchProducts]);

  const handlePageChange = (page: number) => {
    setCurrentPage(page);
  };

  const handleSearchChange = (value: string) => {
    searchQueryRef.current = value;
    setSearchQuery(value);
  };

  // 即时搜索：搜索按钮和回车键
  const handleSearch = () => {
    setCurrentPage(1);
    fetchProducts(searchQueryRef.current, 1);
  };

  const filteredModels = products;

  // 根据产品类型获取引导语
  const getSlogan = (): {
    title: string;
    enLabel?: string;
    slogan?: string;
    subtitleKey?: string;
  } | null => {
    switch (activeType) {
      case 'MCP_SERVER':
        return {
          enLabel: 'Model Context Protocol',
          slogan: t('mcpMarketSlogan'),
          title: t('mcpMarketTitle'),
        };
      case 'AGENT_API':
        return {
          enLabel: 'Autonomous AI Agent',
          slogan: t('agentMarketSlogan'),
          title: t('agentMarketTitle'),
        };
      case 'MODEL_API':
        return {
          enLabel: 'Large Language Model',
          slogan: t('modelMarketSlogan'),
          title: t('modelMarketTitle'),
        };
      case 'REST_API':
        return {
          enLabel: 'RESTful API',
          slogan: t('apiMarketSlogan'),
          title: t('apiMarketTitle'),
        };
      case 'AGENT_SKILL':
        return { subtitleKey: 'skillMarketSubtitle', title: t('skillMarketTitle') };
      case 'WORKER':
        return { subtitleKey: 'workerMarketSubtitle', title: t('workerMarketTitle') };
      default:
        return null;
    }
  };

  const getWatermarkLabel = () => {
    switch (activeType) {
      case 'MCP_SERVER':
        return 'MCP Marketplace';
      case 'AGENT_API':
        return 'Agent Marketplace';
      case 'MODEL_API':
        return 'Model Marketplace';
      case 'REST_API':
        return 'API Marketplace';
      default:
        return '';
    }
  };

  const getUpdatedAtLabel = (product: IProductDetail) => {
    const updatedAt = product.updatedAt || product.createAt;
    return `${t('updatedAt')} ${dayjs(updatedAt).format('YYYY-MM-DD')}`;
  };

  const handleViewDetail = (product: IProductDetail) => {
    switch (product.type) {
      case 'MODEL_API':
        navigate(`/models/${product.productId}`);
        break;
      case 'MCP_SERVER':
        navigate(`/mcp/${product.productId}`);
        break;
      case 'AGENT_API':
        navigate(`/agents/${product.productId}`);
        break;
      case 'REST_API':
        navigate(`/apis/${product.productId}`);
        break;
      case 'AGENT_SKILL':
        navigate(`/skills/${product.productId}`);
        break;
      case 'WORKER':
        navigate(`/workers/${product.productId}`);
        break;
      default:
        console.warn(t('unknownProductType'), product.type);
    }
  };

  const slogan = getSlogan();
  const watermarkLabel = getWatermarkLabel();

  // 共享的卡片渲染
  const productCards = filteredModels.map((product) =>
    product.type === 'AGENT_SKILL' ? (
      <SkillCard
        description={product.description}
        downloadCount={product.skillConfig?.downloadCount}
        key={product.productId}
        name={product.name}
        onClick={() => handleViewDetail(product)}
        releaseDate={dayjs(product.createAt).format('YYYY-MM-DD HH:mm:ss')}
        skillTags={product.skillConfig?.skillTags}
      />
    ) : product.type === 'WORKER' ? (
      <WorkerCard
        description={product.description}
        downloadCount={product.workerConfig?.downloadCount}
        key={product.productId}
        name={product.name}
        onClick={() => handleViewDetail(product)}
        releaseDate={dayjs(product.createAt).format('YYYY-MM-DD HH:mm:ss')}
        workerTags={product.workerConfig?.tags}
      />
    ) : (
      <ModelCard
        description={product.description || t('noDescription')}
        icon={getIconString(product.icon, product.name)}
        key={product.productId}
        name={product.name}
        onClick={() => handleViewDetail(product)}
        tags={getProductCardTags(product, t)}
        updatedAt={getUpdatedAtLabel(product)}
      />
    ),
  );

  const paginationSection = !loading && totalElements > PAGE_SIZE && (
    <div className="flex justify-center mt-8 mb-4">
      <Pagination
        current={currentPage}
        onChange={handlePageChange}
        pageSize={PAGE_SIZE}
        showQuickJumper
        showSizeChanger={false}
        total={totalElements}
      />
    </div>
  );

  return (
    <Layout>
      <div
        className="flex flex-col h-[calc(100vh-96px)] overflow-auto scrollbar-hide"
        ref={scrollContainerRef}
      >
        {useNewLayout ? (
          // 产品市场列表：MCP / Agent / Model / API
          <ProductMarketLayout
            activeCategory={activeCategory}
            categories={categories}
            categoriesLoading={categoriesLoading}
            categoryLabel={t('category')}
            emptyLabel={t('emptyData')}
            hasProducts={filteredModels.length > 0}
            loading={loading}
            onCategorySelect={(categoryId) => {
              setActiveCategory(categoryId);
              setCurrentPage(1);
            }}
            onSearch={handleSearch}
            onSearchChange={handleSearchChange}
            onSortChange={(nextSortBy) => {
              setSortBy(nextSortBy);
              setCurrentPage(1);
            }}
            pagination={paginationSection}
            productCards={productCards}
            searchPlaceholder={t('searchPlaceholder')}
            searchQuery={searchQuery}
            slogan={slogan?.slogan}
            sortBy={sortBy}
            sortMostDownloadsLabel={t('sortMostDownloads')}
            sortRecentlyUpdatedLabel={t('sortRecentlyUpdated')}
            title={slogan?.title}
            watermarkLabel={watermarkLabel}
          />
        ) : (
          // 旧设计：Skill / Worker（完全保持原始版本）
          <>
            {/* 引导语 */}
            {slogan && slogan.subtitleKey && (
              <div className="text-center py-6">
                <h1 className="text-4xl font-bold mb-3">{slogan.title}</h1>
                <p className="text-gray-500 text-base flex items-baseline justify-center gap-0">
                  <Trans
                    components={{
                      1: (
                        <span className="text-4xl font-extrabold text-blue-500 mx-1 tabular-nums leading-none relative -top-[2px]" />
                      ),
                    }}
                    i18nKey={slogan.subtitleKey}
                    t={t}
                    values={{ count: totalElements }}
                  />
                </p>
              </div>
            )}

            {/* 搜索区域 */}
            <div className="flex-shrink-0">
              <div className="flex flex-col gap-4 px-6 py-4">
                {/* 排序 */}
                {showSortControl && (
                  <div className="flex items-center justify-center text-sm">
                    <div className="inline-flex items-center p-[3px] rounded-xl bg-gray-100/80 backdrop-blur-sm">
                      {[
                        {
                          icon: <DownloadOutlined />,
                          label: t('sortMostDownloads'),
                          value: 'DOWNLOAD_COUNT',
                        },
                        {
                          icon: <ClockCircleOutlined />,
                          label: t('sortRecentlyUpdated'),
                          value: 'UPDATED_AT',
                        },
                      ].map((option) => (
                        <button
                          className={`
                            flex items-center gap-1.5 px-3.5 py-1.5 rounded-[10px] text-[13px] font-medium
                            transition-all duration-200 ease-out cursor-pointer select-none
                            ${
                              sortBy === option.value
                                ? 'bg-white text-gray-900 shadow-[0_1px_3px_rgba(0,0,0,0.08),0_0_0_1px_rgba(0,0,0,0.04)]'
                                : 'text-gray-500 hover:text-gray-700'
                            }
                          `}
                          key={option.value}
                          onClick={() => {
                            setSortBy(option.value);
                            setCurrentPage(1);
                          }}
                          type="button"
                        >
                          <span
                            className={`text-xs transition-colors duration-200 ${sortBy === option.value ? 'text-indigo-500' : 'text-gray-500'}`}
                          >
                            {option.icon}
                          </span>
                          {option.label}
                        </button>
                      ))}
                    </div>
                  </div>
                )}

                {/* 搜索框 */}
                <div className="flex items-center justify-center">
                  <div className="w-full max-w-3xl">
                    <Input
                      className="rounded-xl text-base"
                      onChange={(e) => handleSearchChange(e.target.value)}
                      onPressEnter={handleSearch}
                      placeholder={t('searchPlaceholder')}
                      size="large"
                      suffix={
                        <button
                          className="bg-black hover:bg-gray-800 text-white rounded-lg p-2 transition-colors"
                          onClick={handleSearch}
                          type="button"
                        >
                          <SearchOutlined className="text-lg" />
                        </button>
                      }
                      value={searchQuery}
                    />
                  </div>
                </div>

                {/* 分类菜单 */}
                <div className="flex-1 min-w-0">
                  <CategoryMenu
                    activeCategory={activeCategory}
                    categories={categories}
                    loading={categoriesLoading}
                    onSelectCategory={setActiveCategory}
                  />
                </div>
              </div>
            </div>

            {/* 内容区域：Grid 卡片展示 */}
            <div className="flex-1 px-4 pt-4 pb-4 flex-shrink-0">
              <div className="pb-4">
                {loading ? (
                  <CardGridSkeleton count={8} />
                ) : (
                  <>
                    <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6 max-w-[1600px] mx-auto animate-in fade-in duration-300">
                      {productCards}
                      {!loading && filteredModels.length === 0 && (
                        <EmptyState className="col-span-full" description={t('emptyData')} />
                      )}
                    </div>
                    {paginationSection}
                  </>
                )}
              </div>
            </div>
          </>
        )}
      </div>
      <BackToTopButton container={scrollContainerRef.current ?? undefined} />
      <LoginPrompt
        contextMessage={t('loginPromptContext')}
        onClose={() => setLoginPromptOpen(false)}
        open={loginPromptOpen}
      />
    </Layout>
  );
}

export default Square;

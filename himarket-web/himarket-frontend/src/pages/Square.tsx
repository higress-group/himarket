import {
  SearchOutlined,
  DownloadOutlined,
  FireOutlined,
  ClockCircleOutlined,
  FolderOutlined,
} from '@ant-design/icons';
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
import { SkillCard } from '../components/square/SkillCard';
import { WorkerCard } from '../components/square/WorkerCard';
import APIs, { type ICategory } from '../lib/apis';
import { getIconString } from '../lib/iconUtils';

import type { IProductDetail } from '../lib/apis/product';

function SparkleStar({ className = 'w-5 h-5' }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
      <path
        d="M12 2C12.5 8 14 9.5 20 10C14 10.5 12.5 12 12 18C11.5 12 10 10.5 4 10C10 9.5 11.5 8 12 2Z"
        fill="currentColor"
      />
      <path
        d="M6 4C6.2 6.5 7 7.3 9.5 7.5C7 7.7 6.2 8.5 6 11C5.8 8.5 5 7.7 2.5 7.5C5 7.3 5.8 6.5 6 4Z"
        fill="currentColor"
        opacity="0.6"
      />
      <path
        d="M18 14C18.2 16.5 19 17.3 21.5 17.5C19 17.7 18.2 18.5 18 21C17.8 18.5 17 17.7 14.5 17.5C17 17.3 17.8 16.5 18 14Z"
        fill="currentColor"
        opacity="0.6"
      />
    </svg>
  );
}

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
          slogan: '基于 MCP 协议的标准化工具服务，即插即用扩展 AI 能力边界',
          title: 'MCP 市场',
        };
      case 'AGENT_API':
        return {
          enLabel: 'Autonomous AI Agent',
          slogan: '可编排的智能体服务，让 AI 自主理解并执行复杂任务',
          title: 'Agent 市场',
        };
      case 'MODEL_API':
        return {
          enLabel: 'Large Language Model',
          slogan: '一键接入主流大模型，按需调用顶尖 AI 推理能力',
          title: 'Model 市场',
        };
      case 'REST_API':
        return {
          enLabel: 'RESTful API',
          slogan: '标准化 REST API 集合，快速构建应用数据底座',
          title: 'API 市场',
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
        description={product.description}
        icon={getIconString(product.icon, product.name)}
        key={product.productId}
        name={product.name}
        onClick={() => handleViewDetail(product)}
        releaseDate={dayjs(product.createAt).format('YYYY-MM-DD HH:mm:ss')}
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
          <div className="flex w-full flex-1 flex-col px-4 pt-4 pb-4">
            {slogan && slogan.enLabel && slogan.slogan && (
              <div className="pb-4">
                <div className="relative px-1 py-2">
                  {watermarkLabel && (
                    <div className="pointer-events-none text-[72px] font-extrabold leading-none text-colorPrimary/[0.1]">
                      {watermarkLabel}
                    </div>
                  )}
                  <h1 className="sr-only">{slogan.title}</h1>
                  <p className="sr-only">{slogan.slogan}</p>
                </div>
              </div>
            )}

            <div className="flex flex-1 flex-col gap-6 md:flex-row">
              <aside className="w-full flex-shrink-0 self-start rounded-xl border border-white/70 bg-white/55 p-3 shadow-[0_8px_28px_rgba(99,102,241,0.05)] backdrop-blur-sm md:w-[15.5rem]">
                <span className="mb-3 inline-block rounded-md bg-colorPrimaryBg px-2.5 py-1 text-xs font-semibold tracking-wide text-colorPrimary md:mb-3">
                  分类
                </span>
                <div className="flex gap-2 overflow-x-auto scrollbar-hide md:flex-col md:overflow-visible">
                  {categoriesLoading
                    ? Array.from({ length: 5 }).map((_, i) => (
                        <div
                          className="mx-1 h-10 flex-shrink-0 animate-pulse rounded-lg bg-gray-200/70"
                          key={i}
                          style={{ width: `${100 + i * 10}px` }}
                        />
                      ))
                    : categories.map((category) => {
                        const isActive = category.id === activeCategory;
                        return (
                          <button
                            className={`flex flex-shrink-0 items-center gap-3 rounded-[10px] border px-3 py-2.5 text-left transition-all duration-200 md:w-full ${
                              isActive
                                ? 'border-colorPrimary/20 bg-white text-colorPrimary shadow-sm'
                                : 'border-transparent bg-white/55 text-gray-700 hover:border-gray-200 hover:bg-white hover:shadow-sm'
                            }`}
                            key={category.id}
                            onClick={() => {
                              setActiveCategory(category.id);
                              setCurrentPage(1);
                            }}
                            type="button"
                          >
                            <div
                              className={`flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-md ${
                                isActive
                                  ? 'bg-colorPrimary text-white'
                                  : 'border border-gray-200 bg-white text-gray-500'
                              }`}
                            >
                              <FolderOutlined className="text-sm" />
                            </div>
                            <div className="min-w-0 flex-1">
                              <div
                                className={`truncate text-sm font-semibold ${
                                  isActive ? 'text-colorPrimary' : 'text-gray-700'
                                }`}
                              >
                                {category.name}
                              </div>
                            </div>
                            {isActive && (
                              <div className="flex-shrink-0 text-colorPrimary">
                                <SparkleStar className="h-5 w-5" />
                              </div>
                            )}
                            {!isActive && category.count > 0 && (
                              <span className="flex-shrink-0 rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600">
                                {category.count}
                              </span>
                            )}
                          </button>
                        );
                      })}
                </div>
              </aside>

              <div className="flex-1 min-w-0 flex flex-col">
                <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center">
                  <div className="w-full sm:max-w-[420px]">
                    <Input
                      className="rounded-[10px] bg-white/80 backdrop-blur-sm"
                      onChange={(e) => handleSearchChange(e.target.value)}
                      onPressEnter={handleSearch}
                      placeholder={t('searchPlaceholder')}
                      prefix={<SearchOutlined className="text-gray-400" />}
                      size="large"
                      value={searchQuery}
                    />
                  </div>

                  <div className="inline-flex w-fit items-center overflow-hidden rounded-[10px] border border-[#D6DEEA] bg-white shadow-[0_8px_24px_rgba(74,85,120,0.18)] p-[2px] gap-[2px]">
                    {[
                      {
                        icon: <FireOutlined />,
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
                          flex h-9 items-center gap-1 rounded-[8px] px-3 text-xs font-medium
                          transition-all duration-200 ease-out
                          ${
                            sortBy === option.value
                              ? 'bg-colorPrimary text-white'
                              : 'text-gray-500 hover:bg-gray-100 hover:text-gray-700'
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
                          className={`text-xs transition-colors duration-200 ${
                            sortBy === option.value ? 'text-white' : 'text-gray-500'
                          }`}
                        >
                          {option.icon}
                        </span>
                        {option.label}
                      </button>
                    ))}
                  </div>
                </div>

                <div className="flex-1">
                  {loading ? (
                    <CardGridSkeleton count={8} />
                  ) : (
                    <>
                      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6 animate-in fade-in duration-300">
                        {productCards}
                        {!loading && filteredModels.length === 0 && (
                          <EmptyState className="col-span-full" description="暂无数据" />
                        )}
                      </div>
                      {paginationSection}
                    </>
                  )}
                </div>
              </div>
            </div>
          </div>
        ) : (
          // 旧设计：Skill / Worker / REST_API（完全保持原始版本）
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
                        <EmptyState className="col-span-full" description="暂无数据" />
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

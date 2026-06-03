import {
  ClockCircleOutlined,
  FireOutlined,
  FolderOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { Input } from 'antd';

import { EmptyState } from '../EmptyState';

import type { ReactNode } from 'react';

interface ProductMarketCategory {
  id: string;
  name: string;
  count: number;
}

interface ProductMarketLayoutProps {
  activeCategory: string;
  categories: ProductMarketCategory[];
  categoriesLoading: boolean;
  categoryLabel: string;
  emptyLabel: string;
  hasProducts: boolean;
  loading: boolean;
  onCategorySelect: (categoryId: string) => void;
  onSearch: () => void;
  onSearchChange: (value: string) => void;
  onSortChange: (sortBy: string) => void;
  pagination: ReactNode;
  productCards: ReactNode;
  searchPlaceholder: string;
  searchQuery: string;
  sortBy: string;
  sortMostDownloadsLabel: string;
  sortRecentlyUpdatedLabel: string;
  slogan?: string;
  title?: string;
  watermarkLabel: string;
}

function ProductMarketSkeleton({ count = 6 }: { count?: number }) {
  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-2 xl:grid-cols-3">
      {Array.from({ length: count }).map((_, index) => (
        <div
          className="h-[176px] animate-pulse rounded-xl border border-[#DDE4EF] bg-white p-4 shadow-[0_6px_20px_rgba(31,42,68,0.04)]"
          key={index}
        >
          <div className="mb-3 flex items-center gap-3">
            <div className="h-11 w-11 flex-shrink-0 rounded-[10px] bg-[#EEF3FA]" />
            <div className="min-w-0 flex-1">
              <div className="mb-2 h-4 w-3/5 rounded bg-[#E7EDF6]" />
              <div className="h-3 w-28 rounded bg-[#EEF3FA]" />
            </div>
          </div>
          <div className="mb-4 flex gap-2">
            <div className="h-6 w-16 rounded-md bg-[#EEF3FA]" />
            <div className="h-6 w-20 rounded-md bg-[#EEF3FA]" />
          </div>
          <div className="space-y-2">
            <div className="h-3.5 w-full rounded bg-[#EEF3FA]" />
            <div className="h-3.5 w-4/5 rounded bg-[#EEF3FA]" />
          </div>
        </div>
      ))}
    </div>
  );
}

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

export function ProductMarketLayout({
  activeCategory,
  categories,
  categoriesLoading,
  categoryLabel,
  emptyLabel,
  hasProducts,
  loading,
  onCategorySelect,
  onSearch,
  onSearchChange,
  onSortChange,
  pagination,
  productCards,
  searchPlaceholder,
  searchQuery,
  slogan,
  sortBy,
  sortMostDownloadsLabel,
  sortRecentlyUpdatedLabel,
  title,
  watermarkLabel,
}: ProductMarketLayoutProps) {
  return (
    <div className="mx-auto flex w-full max-w-[1280px] flex-1 flex-col gap-6 px-0 py-6 min-[1800px]:max-w-[1480px] min-[2400px]:max-w-[1680px]">
      {watermarkLabel && (
        <section className="relative overflow-hidden py-2">
          <div className="pointer-events-none select-none text-[64px] font-extrabold leading-none text-colorPrimary/[0.1] sm:text-[76px]">
            {watermarkLabel}
          </div>
          {title && <h1 className="sr-only">{title}</h1>}
          {slogan && <p className="sr-only">{slogan}</p>}
        </section>
      )}

      <div className="grid flex-1 grid-cols-1 gap-5 md:grid-cols-[15rem_minmax(0,1fr)]">
        <aside className="w-full flex-shrink-0 self-start rounded-xl border border-[#DDE4EF] bg-white/75 p-3 shadow-[0_6px_20px_rgba(31,42,68,0.04)] backdrop-blur-sm md:sticky md:top-20">
          <span className="mb-3 inline-block rounded-md bg-[#F3F6FB] px-2.5 py-1 text-xs font-semibold tracking-wide text-[#647086] md:mb-3">
            {categoryLabel}
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
                      className={`flex flex-shrink-0 items-center gap-3 rounded-[9px] border px-3 py-2 text-left transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-colorPrimary/25 md:w-full ${
                        isActive
                          ? 'border-[#D8E1F1] bg-white text-gray-950 shadow-[0_4px_14px_rgba(31,42,68,0.06)]'
                          : 'border-transparent bg-transparent text-gray-600 hover:border-[#E3E8F1] hover:bg-white/80 hover:text-gray-900'
                      }`}
                      key={category.id}
                      onClick={() => onCategorySelect(category.id)}
                      type="button"
                    >
                      <div
                        className={`flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-md ${
                          isActive
                            ? 'bg-[#EEF3FF] text-colorPrimary'
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

        <section className="min-w-0 overflow-hidden rounded-xl border border-[#DDE4EF] bg-white/90 shadow-[0_8px_24px_rgba(31,42,68,0.05)] backdrop-blur-sm">
          <div className="flex flex-col gap-3 border-b border-[#E8EEF6] bg-[#FBFCFE] p-4 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex min-w-0 flex-col gap-3 lg:flex-row lg:items-center">
              <div className="w-full lg:w-[340px] xl:w-[420px]">
                <Input
                  className="rounded-[9px] bg-white/90 backdrop-blur-sm"
                  onChange={(e) => onSearchChange(e.target.value)}
                  onPressEnter={onSearch}
                  placeholder={searchPlaceholder}
                  prefix={<SearchOutlined className="text-gray-400" />}
                  size="large"
                  value={searchQuery}
                />
              </div>

              <div className="inline-flex w-fit flex-shrink-0 items-center gap-[2px] rounded-[9px] border border-[#D6DEEA] bg-white p-[2px] shadow-[0_4px_14px_rgba(74,85,120,0.08)]">
                {[
                  {
                    icon: <FireOutlined />,
                    label: sortMostDownloadsLabel,
                    value: 'DOWNLOAD_COUNT',
                  },
                  {
                    icon: <ClockCircleOutlined />,
                    label: sortRecentlyUpdatedLabel,
                    value: 'UPDATED_AT',
                  },
                ].map((option) => (
                  <button
                    className={`
                      flex h-8 flex-shrink-0 items-center gap-1 whitespace-nowrap rounded-[7px] px-3 text-xs font-medium
                      transition-all duration-200 ease-out
                      ${
                        sortBy === option.value
                          ? 'bg-colorPrimary text-white'
                          : 'text-gray-500 hover:bg-gray-100 hover:text-gray-700'
                      }
                    `}
                    key={option.value}
                    onClick={() => onSortChange(option.value)}
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
          </div>

          <div className="flex-1 p-4">
            {loading ? (
              <ProductMarketSkeleton count={6} />
            ) : (
              <>
                <div className="grid grid-cols-1 gap-4 animate-in fade-in duration-300 lg:grid-cols-2 xl:grid-cols-3">
                  {productCards}
                  {!hasProducts && (
                    <EmptyState className="col-span-full" description={emptyLabel} />
                  )}
                </div>
                {pagination}
              </>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}

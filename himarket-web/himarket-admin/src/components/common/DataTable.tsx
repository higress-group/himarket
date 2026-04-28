import { Pagination, Table } from 'antd';

import { SearchInput } from './SearchInput';

import type { TableProps } from 'antd';

export interface DataTablePagination {
  current: number;
  pageSize: number;
  total: number;
  onChange: (page: number, pageSize?: number) => void;
}

export interface DataTableSearch {
  placeholder?: string;
  value: string;
  onChange: (value: string) => void;
  onSearch: () => void;
}

export interface DataTableProps<T extends object> extends Omit<TableProps<T>, 'pagination'> {
  pagination?: DataTablePagination;
  search?: DataTableSearch;
  toolbarLeft?: React.ReactNode;
  toolbarRight?: React.ReactNode;
  batchActionBar?: React.ReactNode;
  wrapperClassName?: string;
}

export function DataTable<T extends object>({
  batchActionBar,
  className,
  columns,
  dataSource,
  loading,
  locale,
  pagination,
  rowClassName,
  rowKey,
  rowSelection,
  search,
  toolbarLeft,
  toolbarRight,
  wrapperClassName,
  ...rest
}: DataTableProps<T>) {
  return (
    <div>
      {/* Toolbar */}
      {(search || toolbarLeft || toolbarRight) && (
        <div className="flex items-center justify-between px-1 py-3 mb-2">
          <div className="flex items-center gap-3">
            {search && (
              <SearchInput
                onChange={search.onChange}
                onClear={() => {
                  search.onChange('');
                  search.onSearch();
                }}
                onSearch={search.onSearch}
                placeholder={search.placeholder || '搜索'}
                value={search.value}
              />
            )}
            {toolbarLeft}
          </div>
          {toolbarRight && <div className="flex items-center gap-2">{toolbarRight}</div>}
        </div>
      )}

      {/* Table card */}
      <div
        className={`bg-white rounded-xl border border-gray-200 overflow-hidden shadow-sm ${wrapperClassName || ''}`}
      >
        {batchActionBar}
        <Table<T>
          className={`product-table-optimized ${className || ''}`}
          columns={columns}
          dataSource={dataSource}
          loading={loading}
          locale={locale}
          pagination={false}
          rowClassName={
            rowClassName ?? (() => 'transition-colors duration-150 hover:bg-blue-50/30')
          }
          rowKey={rowKey}
          rowSelection={rowSelection}
          {...rest}
        />
      </div>

      {/* Pagination */}
      {pagination && pagination.total > 0 && (
        <div className="flex justify-end px-1 py-3">
          <Pagination
            current={pagination.current}
            onChange={pagination.onChange}
            pageSize={pagination.pageSize}
            pageSizeOptions={['10', '20', '50', '100']}
            showQuickJumper
            showSizeChanger
            showTotal={(total) => `共 ${total} 条`}
            total={pagination.total}
          />
        </div>
      )}
    </div>
  );
}

import { CloseOutlined, SearchOutlined } from '@ant-design/icons';
import { Button, Input, Modal, Skeleton, Switch, type ModalProps } from 'antd';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';

import McpCard from './McpCard';

import type { ICategory, IProductDetail, ISubscription } from '../../lib/apis';

interface McpModal extends ModalProps {
  categories: ICategory[];
  data: IProductDetail[];
  added: IProductDetail[];
  onFilter: (id: string) => void;
  onSearch: (categorieId: string, name: string) => void;
  mcpLoading?: boolean;
  onAdd: (product: IProductDetail) => void;
  onRemove: (product: IProductDetail) => void;
  onRemoveAll: () => void;
  subscripts: ISubscription[];
  enabled?: boolean;
  onEnabled: (enabled: boolean) => void;
  onClose: () => void;
  onQuickSubscribe?: (product: IProductDetail) => void;
}

function McpModal(props: McpModal) {
  const {
    added,
    categories,
    data,
    enabled,
    mcpLoading,
    onAdd,
    onClose,
    onEnabled,
    onFilter,
    onQuickSubscribe,
    onRemove,
    onRemoveAll,
    onSearch,
    subscripts,
    ...modalProps
  } = props;
  const { t } = useTranslation('chat');
  const [searchText, setSearchText] = useState('');

  const [active, setActive] = useState('all');

  const scbscriptsIds = useMemo(() => {
    return subscripts.map((v) => v.productId);
  }, [subscripts]);

  const addedIds = useMemo(() => {
    return added.map((v) => v.productId);
  }, [added]);

  const filteredData = useMemo(() => {
    if (active === 'added') {
      return added;
    }
    return data;
  }, [data, active, added]);
  return (
    <Modal
      {...modalProps}
      closable={false}
      footer={null}
      keyboard
      maskClosable={false}
      onCancel={onClose}
      width="min(1240px, calc(100vw - 64px))"
    >
      <div className="flex h-[min(74vh,760px)] flex-col overflow-hidden">
        <div className="flex items-start justify-between gap-4 px-1 pb-4">
          <h2 className="text-lg font-semibold text-gray-950">{t('mcpModal.title')}</h2>
          <button
            aria-label={t('close', { ns: 'common' })}
            className="flex h-9 w-9 items-center justify-center rounded-[10px] border-0 bg-transparent text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-700"
            onClick={onClose}
            type="button"
          >
            <CloseOutlined />
          </button>
        </div>

        <div className="flex min-h-0 flex-1 flex-col rounded-[20px] bg-[#F7F9FC] p-3">
          <div className="mb-3 grid grid-cols-[240px_minmax(0,1fr)] gap-4">
            <div className="flex h-12 items-center justify-between rounded-[16px] bg-white px-4 shadow-[0_8px_22px_rgba(35,52,82,0.05)]">
              <span className="text-sm font-semibold text-gray-800">{t('mcpModal.enabled')}</span>
              <Switch checked={enabled} onChange={() => onEnabled(!enabled)} />
            </div>
            <Input
              allowClear
              className="h-12 rounded-[16px] border-transparent bg-white shadow-[0_8px_22px_rgba(35,52,82,0.05)]"
              onChange={(e) => setSearchText(e.target.value)}
              onKeyDown={(evt) => {
                if (evt.code === 'Enter') {
                  onSearch(active, (evt.target as HTMLInputElement).value.trim());
                }
              }}
              placeholder={t('mcpModal.searchPlaceholder')}
              prefix={<SearchOutlined className="text-gray-400" />}
              size="large"
              value={searchText}
            />
          </div>

          <div className="grid min-h-0 flex-1 grid-cols-[240px_minmax(0,1fr)] gap-4 overflow-hidden">
            <aside className="flex min-h-0 flex-col overflow-hidden rounded-[16px] bg-white p-4 shadow-[0_12px_30px_rgba(35,52,82,0.055)]">
              <div>
                <div className="flex items-center justify-between gap-2">
                  <div className="text-xs font-medium text-gray-400">
                    {t('mcpModal.addedCount', { count: added.length })}
                  </div>
                  {active === 'added' && added.length > 0 && (
                    <button
                      className="text-xs font-medium text-gray-400 transition-colors hover:text-colorPrimary"
                      onClick={onRemoveAll}
                      type="button"
                    >
                      {t('mcpModal.removeAll')}
                    </button>
                  )}
                </div>
                <button
                  className={`
                    mt-2 flex w-full items-center justify-between rounded-[14px] px-3.5 py-3 text-sm
                    transition-all duration-200 ease-in-out active:scale-[0.98]
                    ${active === 'added' ? 'bg-[#F0F3FF] text-colorPrimary' : 'bg-[#F8FAFC] text-gray-700 hover:bg-[#F3F6FA]'}
                  `}
                  onClick={() => {
                    setActive('added');
                    onFilter('added');
                  }}
                  type="button"
                >
                  <span className="font-semibold">{t('mcpModal.addedServers')}</span>
                </button>
              </div>

              <div className="mt-5 flex min-h-0 flex-col gap-1 overflow-y-auto">
                <div className="mb-1 px-1 text-xs font-medium text-gray-400">
                  {t('mcpModal.scope')}
                </div>
                {categories.map((item) => (
                  <button
                    className={`
                        flex w-full items-center justify-between overflow-hidden text-nowrap rounded-[14px] px-3.5 py-3 text-sm
                        transition-all duration-200 ease-in-out active:scale-[0.98]
                        ${active === item.categoryId ? 'bg-[#F0F3FF] text-colorPrimary' : 'bg-transparent text-gray-600 hover:bg-[#F8FAFC] hover:text-gray-900'}
                      `}
                    key={item.categoryId}
                    onClick={() => {
                      setActive(item.categoryId);
                      onFilter(item.categoryId);
                    }}
                    type="button"
                  >
                    <span className="overflow-hidden text-ellipsis font-medium">{item.name}</span>
                  </button>
                ))}
              </div>
            </aside>

            <section
              className="min-h-0 min-w-0 overflow-hidden rounded-[16px] bg-white p-5 shadow-[0_12px_30px_rgba(35,52,82,0.055)]"
              data-sign-name="mcp-list"
            >
              <div className="h-full overflow-hidden">
                {mcpLoading ? (
                  <div className="grid h-full content-start gap-4 overflow-y-auto pr-1 lg:grid-cols-2 xl:grid-cols-3">
                    {Array.from({ length: 6 }).map((_, index) => (
                      <div
                        className="flex h-[200px] flex-col gap-4 rounded-2xl border border-[#e5e5e5] bg-white/60 p-5"
                        key={index}
                      >
                        <div className="flex items-start gap-3">
                          <Skeleton.Avatar active shape="square" size={48} />
                          <div className="flex flex-1 flex-col gap-2">
                            <Skeleton.Input
                              active
                              size="small"
                              style={{ height: 20, width: '70%' }}
                            />
                            <Skeleton.Button
                              active
                              size="small"
                              style={{ height: 24, width: 60 }}
                            />
                          </div>
                        </div>
                        <div className="flex-1">
                          <Skeleton active paragraph={{ rows: 2 }} title={false} />
                        </div>
                        <Skeleton.Button active block size="default" />
                      </div>
                    ))}
                  </div>
                ) : filteredData.length === 0 ? (
                  <Empty
                    active={active}
                    onViewAll={() => {
                      setActive('all');
                      onFilter('all');
                    }}
                  />
                ) : (
                  <div
                    className="grid h-full content-start gap-4 overflow-y-auto pr-1 lg:grid-cols-2 xl:grid-cols-3"
                    data-sign-name="mcp-card-grid"
                  >
                    {filteredData.map((item) => (
                      <McpCard
                        data={item}
                        isAdded={addedIds.includes(item.productId)}
                        isSubscribed={scbscriptsIds.includes(item.productId)}
                        key={item.productId}
                        onAdd={onAdd}
                        onQuickSubscribe={onQuickSubscribe}
                        onRemove={onRemove}
                      />
                    ))}
                  </div>
                )}
              </div>
            </section>
          </div>
        </div>
      </div>
    </Modal>
  );
}

function Empty({ active, onViewAll }: { active: string; onViewAll: () => void }) {
  const { t } = useTranslation('chat');

  return (
    <div className="flex h-full items-center justify-center">
      <div className="flex max-w-[360px] flex-col items-center gap-4 text-center">
        <div className="flex h-14 w-14 items-center justify-center rounded-[18px] border border-[#E6ECF4] bg-white/70 text-gray-400">
          <SearchOutlined />
        </div>
        <div>
          <div className="text-base font-semibold text-gray-900">{t('mcpModal.noServers')}</div>
          {active === 'added' && (
            <p className="mt-2 text-sm leading-6 text-gray-500">{t('mcpModal.addedEmptyHint')}</p>
          )}
        </div>
        {active === 'added' && (
          <Button className="rounded-[12px]" onClick={onViewAll} type="primary">
            {t('mcpModal.viewAll')}
          </Button>
        )}
      </div>
    </div>
  );
}

export default McpModal;

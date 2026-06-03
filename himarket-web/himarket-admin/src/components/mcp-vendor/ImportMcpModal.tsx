import { SearchOutlined, DownloadOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import { Modal, Input, Button, Alert, message } from 'antd';
import { useState, useCallback } from 'react';

import { ImportResultModal, type ImportResultFailure } from '@/components/common/ImportResultModal';
import { useLocale } from '@/contexts/LocaleContext';
import { apiProductApi, mcpVendorApi } from '@/lib/api';
import type { McpVendorType, RemoteMcpItemResult, VendorOption } from '@/types/mcp-vendor';
import { VENDOR_OPTIONS } from '@/types/mcp-vendor';

import RemoteMcpTable from './RemoteMcpTable';

interface ImportMcpModalProps {
  open: boolean;
  onClose: () => void;
  onImportSuccess: () => void;
}

interface ProductImportFailure {
  resourceName?: string;
  errorMessage?: string;
}

interface ProductImportResult {
  successCount?: number;
  failures?: ProductImportFailure[];
}

interface ImportResultState {
  selectedCount: number;
  successCount: number;
  failures: ImportResultFailure[];
}

export default function ImportMcpModal({ onClose, onImportSuccess, open }: ImportMcpModalProps) {
  const { t } = useLocale();
  const [step, setStep] = useState<'select' | 'list'>('select');
  const [vendorType, setVendorType] = useState<McpVendorType | null>(null);
  const [keyword, setKeyword] = useState('');
  const [items, setItems] = useState<RemoteMcpItemResult[]>([]);
  const [selectedKeys, setSelectedKeys] = useState<string[]>([]);
  const [selectedItems, setSelectedItems] = useState<RemoteMcpItemResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [importing, setImporting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [importResult, setImportResult] = useState<ImportResultState | null>(null);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });

  const selectedVendor = VENDOR_OPTIONS.find((v) => v.value === vendorType);
  const getVendorDescription = (vendor: VendorOption) => {
    switch (vendor.value) {
      case 'MODELSCOPE':
        return t('product.marketImport.vendor.modelScopeDescription');
      case 'MCP_REGISTRY':
        return t('product.marketImport.vendor.mcpRegistryDescription');
      case 'LOBEHUB':
        return t('product.marketImport.vendor.lobeHubDescription');
      default:
        return vendor.description;
    }
  };

  const resetBrowseState = useCallback(() => {
    setStep('select');
    setVendorType(null);
    setItems([]);
    setSelectedKeys([]);
    setSelectedItems([]);
    setKeyword('');
    setError(null);
    setPagination({ current: 1, pageSize: 10, total: 0 });
  }, []);

  const closeAfterImportSuccess = useCallback(() => {
    resetBrowseState();
    onClose();
  }, [onClose, resetBrowseState]);

  const handleSelectVendor = useCallback((vendor: VendorOption) => {
    setVendorType(vendor.value);
    setStep('list');
    setItems([]);
    setSelectedKeys([]);
    setSelectedItems([]);
    setKeyword('');
    setError(null);
    setPagination({ current: 1, pageSize: 10, total: 0 });
  }, []);

  const handleBack = useCallback(() => {
    resetBrowseState();
  }, [resetBrowseState]);

  const handleQuery = useCallback(
    async (page = 1, size = pagination.pageSize) => {
      if (!vendorType) return;
      setLoading(true);
      setError(null);
      try {
        const res: unknown = await mcpVendorApi.listRemoteMcpItems({
          keyword: keyword.trim() || undefined,
          page,
          size,
          vendorType,
        });
        const data =
          (
            res as {
              data?: {
                content?: unknown[];
                number?: number;
                size?: number;
                totalElements?: number;
              };
            }
          ).data ??
          (res as { content?: unknown[]; number?: number; size?: number; totalElements?: number });
        setItems((data.content ?? []) as RemoteMcpItemResult[]);
        setPagination({
          current: data.number ?? page,
          pageSize: data.size ?? size,
          total: data.totalElements ?? 0,
        });
      } catch (err: unknown) {
        const msg =
          (err as { response?: { data?: { message?: string } }; message?: string }).response?.data
            ?.message ||
          (err as { message?: string }).message ||
          t('product.marketImport.queryFailed');
        setError(msg);
        setItems([]);
      } finally {
        setLoading(false);
      }
    },
    [vendorType, keyword, pagination, t],
  );

  const handlePageChange = useCallback(
    (page: number, size: number) => {
      handleQuery(page, size);
    },
    [handleQuery],
  );

  const handleSelectionChange = useCallback(
    (keys: string[], rows: RemoteMcpItemResult[]) => {
      if (keys.length > 50) {
        message.warning(t('product.marketImport.maxSelection', { count: 50 }));
        return;
      }
      setSelectedKeys(keys);
      setSelectedItems(rows);
    },
    [t],
  );

  const handleImport = useCallback(async () => {
    if (!vendorType || selectedItems.length === 0) {
      message.warning(t('product.marketImport.selectFirst'));
      return;
    }
    setImporting(true);
    const hide = message.loading(t('product.marketImport.importing'), 0);
    try {
      const importItems = selectedItems.map((item) => ({
        description: item.description,
        resourceId: item.remoteId,
        resourceName: item.displayName || item.mcpName || item.remoteId,
      }));

      // 使用 AbortController 设置前端超时保护（5 分钟）
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 300000);

      let res: unknown;
      try {
        res = await apiProductApi.importProducts({
          items: importItems,
          productType: 'MCP_SERVER',
          source: 'EXTERNAL',
          sourceConfig: { provider: vendorType },
        });
      } finally {
        clearTimeout(timeoutId);
      }

      const result =
        (res as { data?: ProductImportResult }).data ?? (res as ProductImportResult | null);
      if (result && typeof result.successCount === 'number') {
        const failures = result.failures ?? [];
        if (failures.length > 0) {
          setImportResult({
            failures,
            selectedCount: selectedItems.length,
            successCount: result.successCount,
          });
          onImportSuccess();
          closeAfterImportSuccess();
        } else {
          message.success(t('product.marketImport.successCount', { count: result.successCount }));
          onImportSuccess();
          closeAfterImportSuccess();
        }
      } else {
        message.success(t('action.importSuccess'));
        onImportSuccess();
        closeAfterImportSuccess();
      }
    } catch (err: unknown) {
      if (
        (err as { name?: string }).name === 'AbortError' ||
        (err as { code?: string }).code === 'ECONNABORTED'
      ) {
        message.warning(t('product.marketImport.timeoutWarning'));
        onImportSuccess();
      } else {
        const msg =
          (err as { response?: { data?: { message?: string } }; message?: string }).response?.data
            ?.message ||
          (err as { message?: string }).message ||
          t('product.import.failed');
        message.error(msg);
      }
    } finally {
      hide();
      setImporting(false);
    }
  }, [closeAfterImportSuccess, selectedItems, vendorType, onImportSuccess, t]);

  const handleClose = useCallback(() => {
    resetBrowseState();
    setImportResult(null);
    onClose();
  }, [onClose, resetBrowseState]);

  const handleCloseResult = useCallback(() => {
    setImportResult(null);
    setSelectedKeys([]);
    setSelectedItems([]);
    handleQuery(pagination.current, pagination.pageSize);
  }, [handleQuery, pagination]);

  return (
    <>
      <Modal
        destroyOnHidden
        footer={
          step === 'list' && items.length > 0 ? (
            <div className="flex items-center justify-between">
              <span className="text-sm text-gray-500">
                {t('product.marketImport.selectedPrefix')}{' '}
                <span className="font-medium text-blue-600">{selectedKeys.length}</span>{' '}
                {t('product.marketImport.selectedSuffix')}
                {selectedKeys.length >= 50 && (
                  <span className="text-orange-500 ml-2">
                    {t('product.marketImport.selectionLimitReached')}
                  </span>
                )}
              </span>
              <Button
                disabled={selectedKeys.length === 0}
                icon={<DownloadOutlined />}
                loading={importing}
                onClick={handleImport}
                type="primary"
              >
                {t('product.marketImport.importSelected')}
              </Button>
            </div>
          ) : null
        }
        onCancel={handleClose}
        open={open}
        title={
          step === 'list' && selectedVendor ? (
            <div className="flex items-center gap-2">
              <Button
                className="text-gray-400 hover:text-gray-600"
                icon={<ArrowLeftOutlined />}
                onClick={handleBack}
                size="small"
                type="text"
              />
              <img
                alt={selectedVendor.label}
                className="w-5 h-5 rounded"
                onError={(e) => {
                  (e.target as HTMLImageElement).style.display = 'none';
                }}
                src={selectedVendor.iconUrl}
              />
              <span>{t('product.marketImport.fromVendor', { vendor: selectedVendor.label })}</span>
            </div>
          ) : (
            t('product.marketImport.title')
          )
        }
        width={step === 'list' ? 1000 : 640}
      >
        {step === 'select' ? (
          /* ========== Step 1: Vendor Selection Cards ========== */
          <div className="py-2">
            <p className="text-gray-500 mb-5">{t('product.marketImport.description')}</p>
            <div className="grid grid-cols-1 gap-3">
              {VENDOR_OPTIONS.map((vendor) => (
                <div
                  className="group cursor-pointer rounded-xl border-2 border-gray-100 hover:border-blue-300 p-5 transition-all duration-200 hover:shadow-md hover:bg-blue-50/30 flex items-center gap-4"
                  key={vendor.value}
                  onClick={() => handleSelectVendor(vendor)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      handleSelectVendor(vendor);
                    }
                  }}
                  role="button"
                  tabIndex={0}
                >
                  <div
                    className="w-12 h-12 rounded-xl flex items-center justify-center shrink-0 transition-transform group-hover:scale-110 overflow-hidden"
                    style={{ backgroundColor: vendor.color + '15' }}
                  >
                    <img
                      alt={vendor.label}
                      className="w-8 h-8 rounded object-contain"
                      onError={(e) => {
                        (e.target as HTMLImageElement).src = '';
                      }}
                      src={vendor.iconUrl}
                    />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="font-semibold text-gray-800 text-base">{vendor.label}</div>
                    <div className="text-sm text-gray-500 mt-0.5">
                      {getVendorDescription(vendor)}
                    </div>
                  </div>
                  <div className="text-gray-300 group-hover:text-blue-400 transition-colors text-lg">
                    →
                  </div>
                </div>
              ))}
            </div>
          </div>
        ) : (
          /* ========== Step 2: MCP List ========== */
          <div className="space-y-4">
            <div className="flex items-center gap-3">
              <Input
                allowClear
                onChange={(e) => setKeyword(e.target.value)}
                onPressEnter={() => handleQuery(1)}
                placeholder={t('product.marketImport.searchPlaceholder')}
                prefix={<SearchOutlined className="text-gray-400" />}
                style={{ flex: 1 }}
                value={keyword}
              />
              <Button loading={loading} onClick={() => handleQuery(1)} type="primary">
                {t('common.search')}
              </Button>
            </div>

            {error && (
              <Alert
                closable
                description={error}
                message={t('product.marketImport.queryFailed')}
                onClose={() => setError(null)}
                showIcon
                type="error"
              />
            )}

            <RemoteMcpTable
              items={items}
              loading={loading}
              maxSelection={50}
              onSelectionChange={handleSelectionChange}
              pagination={{
                current: pagination.current,
                onChange: handlePageChange,
                pageSize: pagination.pageSize,
                total: pagination.total,
              }}
              selectedKeys={selectedKeys}
            />

            {items.length === 0 && !loading && !error && (
              <div className="text-center py-12 text-gray-400">
                {t('product.marketImport.searchTip')}
              </div>
            )}
          </div>
        )}
      </Modal>

      <ImportResultModal
        failures={importResult?.failures ?? []}
        onClose={handleCloseResult}
        open={!!importResult}
        selectedCount={importResult?.selectedCount ?? 0}
        successCount={importResult?.successCount ?? 0}
      />
    </>
  );
}

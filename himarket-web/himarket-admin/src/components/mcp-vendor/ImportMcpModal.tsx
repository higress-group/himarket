import { SearchOutlined, DownloadOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import { Modal, Input, Button, Alert, message } from 'antd';
import { useState, useCallback } from 'react';

import { ImportResultModal, type ImportResultFailure } from '@/components/common/ImportResultModal';
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
    setStep('select');
    setVendorType(null);
    setItems([]);
    setSelectedKeys([]);
    setSelectedItems([]);
    setKeyword('');
    setError(null);
  }, []);

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
          '查询失败';
        setError(msg);
        setItems([]);
      } finally {
        setLoading(false);
      }
    },
    [vendorType, keyword, pagination],
  );

  const handlePageChange = useCallback(
    (page: number, size: number) => {
      handleQuery(page, size);
    },
    [handleQuery],
  );

  const handleSelectionChange = useCallback((keys: string[], rows: RemoteMcpItemResult[]) => {
    if (keys.length > 50) {
      message.warning('最多选择 50 条');
      return;
    }
    setSelectedKeys(keys);
    setSelectedItems(rows);
  }, []);

  const handleImport = useCallback(async () => {
    if (!vendorType || selectedItems.length === 0) {
      message.warning('请先选择要导入的 MCP');
      return;
    }
    setImporting(true);
    const hide = message.loading(
      '正在导入，请稍候（每个 MCP 需要获取详情，可能需要一些时间）...',
      0,
    );
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
        } else {
          message.success(`成功导入 ${result.successCount} 个 MCP Server`);
          onImportSuccess();
        }
      } else {
        message.success('导入完成');
        onImportSuccess();
      }
    } catch (err: unknown) {
      if (
        (err as { name?: string }).name === 'AbortError' ||
        (err as { code?: string }).code === 'ECONNABORTED'
      ) {
        message.warning('导入请求超时，但后台可能已完成导入，请刷新页面查看');
        onImportSuccess();
      } else {
        const msg =
          (err as { response?: { data?: { message?: string } }; message?: string }).response?.data
            ?.message ||
          (err as { message?: string }).message ||
          '导入失败';
        message.error(msg);
      }
    } finally {
      hide();
      setImporting(false);
    }
  }, [selectedItems, vendorType, onImportSuccess]);

  const handleClose = useCallback(() => {
    setStep('select');
    setVendorType(null);
    setItems([]);
    setSelectedKeys([]);
    setSelectedItems([]);
    setKeyword('');
    setError(null);
    setImportResult(null);
    setPagination({ current: 1, pageSize: 10, total: 0 });
    onClose();
  }, [onClose]);

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
                已选择 <span className="font-medium text-blue-600">{selectedKeys.length}</span> 项
                {selectedKeys.length >= 50 && (
                  <span className="text-orange-500 ml-2">（已达上限）</span>
                )}
              </span>
              <Button
                disabled={selectedKeys.length === 0}
                icon={<DownloadOutlined />}
                loading={importing}
                onClick={handleImport}
                type="primary"
              >
                导入选中项
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
              <span>从 {selectedVendor.label} 导入</span>
            </div>
          ) : (
            '导入 MCP Server'
          )
        }
        width={step === 'list' ? 1000 : 640}
      >
        {step === 'select' ? (
          /* ========== Step 1: Vendor Selection Cards ========== */
          <div className="py-2">
            <p className="text-gray-500 mb-5">选择 MCP 数据源，从第三方平台浏览并导入 MCP Server</p>
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
                    <div className="text-sm text-gray-500 mt-0.5">{vendor.description}</div>
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
                placeholder="搜索 MCP Server..."
                prefix={<SearchOutlined className="text-gray-400" />}
                style={{ flex: 1 }}
                value={keyword}
              />
              <Button loading={loading} onClick={() => handleQuery(1)} type="primary">
                搜索
              </Button>
            </div>

            {error && (
              <Alert
                closable
                description={error}
                message="查询失败"
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
                输入关键词搜索，或直接点击&ldquo;搜索&rdquo;浏览全部 MCP Server
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

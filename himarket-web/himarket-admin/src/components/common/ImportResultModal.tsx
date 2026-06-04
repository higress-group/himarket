import { Button, Modal } from 'antd';

import { useLocale } from '@/contexts/LocaleContext';

export interface ImportResultFailure {
  resourceName?: string;
  errorMessage?: string | null;
}

interface ImportResultFailureRow extends ImportResultFailure {
  cleanErrorMessage: string;
  failureType: string;
  key: string;
}

interface ImportResultModalProps {
  failures: ImportResultFailure[];
  onClose: () => void;
  open: boolean;
  selectedCount: number;
  successCount: number;
}

function getFailureTypeKey(errorMessage?: string | null) {
  const messageText = (errorMessage || '').toLowerCase();
  if (/already exists|duplicate|conflict|已存在|冲突/.test(messageText)) {
    return 'product.import.result.resourceConflict' as const;
  }
  if (/json|parse|protocol|connection|config|missing|配置|解析/.test(messageText)) {
    return 'product.import.result.configError' as const;
  }
  if (/not found|不存在/.test(messageText)) {
    return 'product.import.result.resourceNotFound' as const;
  }
  return 'product.import.result.failed' as const;
}

function getCleanErrorMessage(errorMessage: string | null | undefined, unknownError: string) {
  return (errorMessage || unknownError).replace(
    /^(资源冲突|配置异常|资源不存在|导入失败)[:：]\s*/,
    '',
  );
}

export function ImportResultModal({
  failures,
  onClose,
  open,
  selectedCount,
  successCount,
}: ImportResultModalProps) {
  const { t } = useLocale();
  const failureCount = failures.length;
  const title =
    successCount > 0
      ? t('product.import.result.completedWithFailures', { count: failureCount })
      : t('product.import.result.failed');
  const failureRows: ImportResultFailureRow[] = failures.map((failure, index) => {
    const failureType = t(getFailureTypeKey(failure.errorMessage));
    return {
      ...failure,
      cleanErrorMessage: getCleanErrorMessage(
        failure.errorMessage,
        t('product.import.result.unknownError'),
      ),
      failureType,
      key: `${failure.resourceName || 'unknown'}-${index}`,
    };
  });

  return (
    <Modal
      footer={
        <div className="flex justify-end gap-2">
          <Button onClick={onClose} type="primary">
            {t('common.close')}
          </Button>
        </div>
      }
      onCancel={onClose}
      open={open}
      title={title}
      width={720}
    >
      <div className="space-y-4">
        <div className="text-sm leading-6 text-gray-700">
          {t('product.import.result.summaryPrefix')}{' '}
          <span className="font-medium text-gray-900">{selectedCount}</span>{' '}
          {t('product.import.result.summarySelectedSuffix')}{' '}
          <span className="font-medium text-green-700">{successCount}</span>{' '}
          {t('product.import.result.summarySuccessSuffix')}{' '}
          <span className="font-medium text-red-700">{failureCount}</span>{' '}
          {t('product.import.result.summaryFailureSuffix')}
        </div>

        {failureCount > 0 && (
          <div>
            <div className="mb-2 flex items-center justify-between text-sm">
              <span className="font-medium text-gray-800">
                {t('product.import.result.failureItems')}
              </span>
              <span className="text-xs text-gray-500">
                {t('product.import.result.failureCount', { count: failureCount })}
              </span>
            </div>
            <div className="max-h-72 overflow-auto rounded-lg border border-gray-200">
              <table className="w-full border-collapse text-left text-xs">
                <thead className="bg-gray-50 text-gray-600">
                  <tr>
                    <th className="w-[180px] border-b border-gray-100 px-3 py-2.5 font-medium">
                      {t('product.import.resourceName')}
                    </th>
                    <th className="w-[116px] border-b border-gray-100 px-3 py-2.5 font-medium">
                      {t('product.import.result.reason')}
                    </th>
                    <th className="border-b border-gray-100 px-3 py-2.5 font-medium">
                      {t('product.import.result.detail')}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {failureRows.map((failure) => (
                    <tr className="border-b border-gray-100 last:border-b-0" key={failure.key}>
                      <td className="px-3 py-3 align-top">
                        <div className="font-medium text-gray-900">
                          {failure.resourceName || t('product.import.result.unknownResource')}
                        </div>
                      </td>
                      <td className="px-3 py-3 align-top">
                        <span className="inline-flex rounded-full bg-red-50 px-2 py-0.5 text-xs text-red-700">
                          {failure.failureType}
                        </span>
                      </td>
                      <td className="break-words px-3 py-3 font-mono leading-5 text-gray-600 align-top">
                        {failure.cleanErrorMessage}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </Modal>
  );
}

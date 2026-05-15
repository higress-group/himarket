import { Button, Modal } from 'antd';

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

function getFailureType(errorMessage?: string | null) {
  const messageText = (errorMessage || '').toLowerCase();
  if (/already exists|duplicate|conflict|已存在|冲突/.test(messageText)) {
    return '资源冲突';
  }
  if (/json|parse|protocol|connection|config|missing|配置|解析/.test(messageText)) {
    return '配置异常';
  }
  if (/not found|不存在/.test(messageText)) {
    return '资源不存在';
  }
  return '导入失败';
}

function getCleanErrorMessage(errorMessage?: string | null) {
  return (errorMessage || '未知错误').replace(
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
  const failureCount = failures.length;
  const title = successCount > 0 ? `导入完成，${failureCount} 个资源未导入` : '导入失败';
  const failureRows: ImportResultFailureRow[] = failures.map((failure, index) => {
    const failureType = getFailureType(failure.errorMessage);
    return {
      ...failure,
      cleanErrorMessage: getCleanErrorMessage(failure.errorMessage),
      failureType,
      key: `${failure.resourceName || 'unknown'}-${index}`,
    };
  });

  return (
    <Modal
      footer={
        <div className="flex justify-end gap-2">
          <Button onClick={onClose} type="primary">
            关闭
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
          已选择 <span className="font-medium text-gray-900">{selectedCount}</span> 个资源，成功{' '}
          <span className="font-medium text-green-700">{successCount}</span> 个，失败{' '}
          <span className="font-medium text-red-700">{failureCount}</span> 个
        </div>

        {failureCount > 0 && (
          <div>
            <div className="mb-2 flex items-center justify-between text-sm">
              <span className="font-medium text-gray-800">失败项</span>
              <span className="text-xs text-gray-500">{failureCount} 条</span>
            </div>
            <div className="max-h-72 overflow-auto rounded-lg border border-gray-200">
              <table className="w-full border-collapse text-left text-xs">
                <thead className="bg-gray-50 text-gray-600">
                  <tr>
                    <th className="w-[180px] border-b border-gray-100 px-3 py-2.5 font-medium">
                      资源名称
                    </th>
                    <th className="w-[116px] border-b border-gray-100 px-3 py-2.5 font-medium">
                      原因
                    </th>
                    <th className="border-b border-gray-100 px-3 py-2.5 font-medium">详情</th>
                  </tr>
                </thead>
                <tbody>
                  {failureRows.map((failure) => (
                    <tr className="border-b border-gray-100 last:border-b-0" key={failure.key}>
                      <td className="px-3 py-3 align-top">
                        <div className="font-medium text-gray-900">
                          {failure.resourceName || '未知资源'}
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

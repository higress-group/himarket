import { AlertCircle } from "lucide-react";

interface ErrorMessageProps {
  code: number;
  message: string;
  data?: Record<string, unknown>;
}

export function ErrorMessage({ code, message, data }: ErrorMessageProps) {
  return (
    <div
      className="rounded-lg border border-red-200 bg-red-50/80 px-4 py-3 text-sm"
      role="alert"
    >
      <div className="flex items-start gap-2">
        <AlertCircle size={16} className="text-red-500 mt-0.5 shrink-0" />
        <div className="min-w-0">
          <p className="font-medium text-red-700">
            错误 {code}
          </p>
          <p className="mt-0.5 text-red-600">{message}</p>
          {data && Object.keys(data).length > 0 && (
            <dl className="mt-2 space-y-1 text-xs text-red-500">
              {Object.entries(data).map(([key, value]) => (
                <div key={key} className="flex gap-1.5">
                  <dt className="font-medium shrink-0">{key}:</dt>
                  <dd className="break-all">
                    {typeof value === "string" ? value : JSON.stringify(value)}
                  </dd>
                </div>
              ))}
            </dl>
          )}
        </div>
      </div>
    </div>
  );
}

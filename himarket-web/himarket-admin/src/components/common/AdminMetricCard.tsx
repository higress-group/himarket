interface AdminMetricCardProps {
  caption?: string;
  label: string;
  value: number | string;
}

export function AdminMetricCard({ caption, label, value }: AdminMetricCardProps) {
  return (
    <article className="min-h-[92px] rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
      <div className="flex items-start justify-between gap-2">
        <span className="min-w-0 text-sm font-medium leading-5 text-gray-500">{label}</span>
        {caption && <span className="shrink-0 text-[11px] leading-5 text-gray-400">{caption}</span>}
      </div>
      <div className="mt-4 truncate text-2xl font-semibold leading-none text-gray-950 tabular-nums">
        {value}
      </div>
    </article>
  );
}

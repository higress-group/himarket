import { ArrowUpRight } from 'lucide-react';

import { ProductIconRenderer } from '../icon/ProductIconRenderer';

interface ModelCardProps {
  icon: string;
  name: string;
  description: string;
  updatedAt: string;
  tags: string[];
  onClick?: () => void;
}

export function ModelCard({ description, icon, name, onClick, tags, updatedAt }: ModelCardProps) {
  return (
    <button
      className="
        group rounded-xl border border-[#DDE4EF] bg-[linear-gradient(180deg,#FFFFFF_0%,#FBFCFF_100%)] p-4
        cursor-pointer
        transition-all duration-200 ease-out
        shadow-[0_6px_20px_rgba(31,42,68,0.05)]
        hover:-translate-y-0.5 hover:border-[#C6D1E3] hover:shadow-[0_14px_34px_rgba(31,42,68,0.09)]
        active:scale-[0.98] active:duration-150
        focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-colorPrimary/25 focus-visible:ring-offset-2
        relative
        overflow-hidden
        h-[176px]
        flex flex-col
        w-full text-left
      "
      onClick={onClick}
      type="button"
    >
      <div className="mb-3 flex items-start justify-between gap-3">
        <div className="flex min-w-0 items-center gap-3">
          <div className="flex h-11 w-11 flex-shrink-0 items-center justify-center overflow-hidden rounded-[10px] border border-[#EDF1F7] bg-[#F3F6FF]">
            <ProductIconRenderer className="h-full w-full object-cover" iconType={icon} />
          </div>
          <div className="min-w-0">
            <h3 className="truncate text-base font-bold leading-tight text-gray-950 transition-colors">
              {name}
            </h3>
            <p className="mt-1 truncate text-xs leading-snug text-gray-500">{updatedAt}</p>
          </div>
        </div>
        <span className="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-md text-gray-400 opacity-0 transition-all duration-200 group-hover:bg-[#F3F6FA] group-hover:text-gray-700 group-hover:opacity-100 group-focus-visible:bg-[#F3F6FA] group-focus-visible:text-gray-700 group-focus-visible:opacity-100">
          <ArrowUpRight aria-hidden="true" size={15} strokeWidth={2} />
        </span>
      </div>

      <div className="mb-3 flex min-h-6 flex-wrap items-center gap-2">
        {tags.slice(0, 3).map((tag) => (
          <span
            className="inline-flex min-h-6 items-center rounded-[6px] border border-[#E4EAF3] bg-[#F8FAFD] px-2 text-xs font-semibold text-[#566176]"
            key={tag}
          >
            {tag}
          </span>
        ))}
      </div>

      <p className="line-clamp-2 flex-1 text-sm leading-relaxed text-gray-600">{description}</p>
    </button>
  );
}

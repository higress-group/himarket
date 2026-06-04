import type { ReactNode } from 'react';

interface AdminPageHeaderProps {
  actions?: ReactNode;
  description: string;
  title: string;
}

export function AdminPageHeader({ actions, description, title }: AdminPageHeaderProps) {
  return (
    <section className="flex flex-col gap-4 border-b border-gray-200 pb-5 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        <h1 className="text-[28px] font-semibold leading-8 text-gray-950">{title}</h1>
        <p className="mt-2 max-w-2xl text-sm leading-6 text-gray-500">{description}</p>
      </div>
      {actions && <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>}
    </section>
  );
}

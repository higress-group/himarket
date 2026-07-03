import { InboxOutlined } from '@ant-design/icons';

import MarkdownRender from './MarkdownRender';
import { parseSkillMd } from '../lib/skillMdUtils';

interface ProductOverviewProps {
  className?: string;
  content?: string | null;
  emptyText: string;
  loading?: boolean;
  showFrontmatterTable?: boolean;
}

function classNames(...values: Array<string | undefined>) {
  return values.filter(Boolean).join(' ');
}

function renderContent(content: string, showFrontmatterTable: boolean) {
  if (!showFrontmatterTable) {
    return <MarkdownRender content={content} />;
  }

  const { body, frontmatter } = parseSkillMd(content);
  const frontmatterEntries = Object.entries(frontmatter);

  return (
    <div className="text-sm">
      {frontmatterEntries.length > 0 && (
        <table className="mb-6 w-full border-collapse text-[13px]">
          <thead>
            <tr className="bg-[#f6f8fa]">
              {frontmatterEntries.map(([key]) => (
                <th
                  className="border border-[#d0d7de] px-3 py-1.5 text-left font-semibold text-[#1f2328]"
                  key={key}
                >
                  {key}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            <tr>
              {frontmatterEntries.map(([key, value]) => (
                <td
                  className="border border-[#d0d7de] px-3 py-1.5 align-top text-[#1f2328]"
                  key={key}
                >
                  {value}
                </td>
              ))}
            </tr>
          </tbody>
        </table>
      )}
      <MarkdownRender content={body} />
    </div>
  );
}

export function ProductOverview({
  className,
  content,
  emptyText,
  loading = false,
  showFrontmatterTable = false,
}: ProductOverviewProps) {
  const rootClassName = classNames(
    'scrollbar-thin-soft overflow-y-auto',
    className ?? 'max-h-[720px] min-h-[420px] pr-2',
  );
  const hasContent = Boolean(content?.trim());

  if (loading) {
    return (
      <div className={rootClassName}>
        <div className="flex min-h-[160px] items-center justify-center py-12">
          <div className="h-6 w-6 animate-spin rounded-full border-2 border-gray-200 border-t-colorPrimary" />
        </div>
      </div>
    );
  }

  if (!hasContent || !content) {
    return (
      <div className={rootClassName}>
        <div className="flex min-h-[420px] flex-col items-center justify-center rounded-[12px] border border-dashed border-[#DDE5F0] bg-[#FBFCFE] py-16">
          <div className="mb-2 flex h-10 w-10 items-center justify-center rounded-full bg-gray-100">
            <InboxOutlined className="text-base text-gray-400" />
          </div>
          <div className="text-sm text-gray-500">{emptyText}</div>
        </div>
      </div>
    );
  }

  return <div className={rootClassName}>{renderContent(content, showFrontmatterTable)}</div>;
}

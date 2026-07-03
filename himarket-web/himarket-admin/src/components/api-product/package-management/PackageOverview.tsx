import ReactMarkdown from 'react-markdown';
import rehypeHighlight from 'rehype-highlight';
import remarkGfm from 'remark-gfm';

import { parseFrontMatter } from './frontMatter';

import 'github-markdown-css/github-markdown-light.css';
import 'highlight.js/styles/github.css';

interface PackageOverviewProps {
  content: string;
}

export function PackageOverview({ content }: PackageOverviewProps) {
  const { body, frontmatter } = parseFrontMatter(content);
  const frontmatterEntries = Object.entries(frontmatter);

  return (
    <div className="markdown-body text-sm">
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
      <ReactMarkdown rehypePlugins={[rehypeHighlight]} remarkPlugins={[remarkGfm]}>
        {body}
      </ReactMarkdown>
    </div>
  );
}

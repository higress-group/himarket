import Markdown from "react-markdown";
import rehypeHighlight from "rehype-highlight";
import remarkGfm from "remark-gfm";

interface MarkdownRendererProps {
  content: string;
}

export function MarkdownRenderer({ content }: MarkdownRendererProps) {
  return (
    <div className="p-4 overflow-auto h-full">
      <div
        className="prose prose-sm max-w-none text-gray-700
                   prose-pre:bg-gray-50 prose-pre:border prose-pre:border-gray-200/80 prose-pre:rounded-xl
                   prose-code:text-gray-700 prose-code:before:content-none prose-code:after:content-none
                   prose-headings:text-gray-800 prose-a:text-blue-600"
      >
        <Markdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeHighlight]}>
          {content}
        </Markdown>
      </div>
    </div>
  );
}

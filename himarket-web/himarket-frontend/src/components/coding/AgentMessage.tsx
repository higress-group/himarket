import Markdown from "react-markdown";
import remarkGfm from "remark-gfm";
import rehypeHighlight from "rehype-highlight";

interface AgentMessageProps {
  text: string;
  streaming?: boolean;
  variant?: "default" | "compact";
}

export function AgentMessage({
  text,
  streaming,
  variant = "default",
}: AgentMessageProps) {
  if (variant === "compact") {
    return (
      <div
        className="prose prose-sm max-w-none text-gray-600
                   prose-pre:bg-gray-50 prose-pre:border prose-pre:border-gray-200/80 prose-pre:rounded-lg
                   prose-code:text-gray-600 prose-code:before:content-none prose-code:after:content-none
                   prose-headings:text-gray-700 prose-a:text-blue-600
                   prose-p:my-1 prose-headings:my-1
                   prose-table:border-collapse prose-th:border prose-th:border-gray-200 prose-th:bg-gray-50 prose-th:px-3 prose-th:py-1.5
                   prose-td:border prose-td:border-gray-200 prose-td:px-3 prose-td:py-1.5"
      >
        <Markdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeHighlight]}>{text}</Markdown>
        {streaming && (
          <span className="inline-block w-1.5 h-3.5 bg-gray-400 animate-blink align-text-bottom ml-0.5" />
        )}
      </div>
    );
  }

  return (
    <div
      className="prose prose-sm max-w-none text-gray-700
                     prose-pre:bg-gray-50 prose-pre:border prose-pre:border-gray-200/80 prose-pre:rounded-xl
                     prose-code:text-gray-700 prose-code:before:content-none prose-code:after:content-none
                     prose-headings:text-gray-800 prose-a:text-blue-600
                     prose-table:border-collapse prose-th:border prose-th:border-gray-200 prose-th:bg-gray-50 prose-th:px-3 prose-th:py-1.5
                     prose-td:border prose-td:border-gray-200 prose-td:px-3 prose-td:py-1.5"
    >
      <Markdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeHighlight]}>{text}</Markdown>
      {streaming && (
        <span className="inline-block w-1.5 h-4 bg-gray-400 animate-blink align-text-bottom ml-0.5" />
      )}
    </div>
  );
}

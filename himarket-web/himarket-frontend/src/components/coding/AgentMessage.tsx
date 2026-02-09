import Markdown from "react-markdown";
import rehypeHighlight from "rehype-highlight";

interface AgentMessageProps {
  text: string;
  streaming?: boolean;
}

export function AgentMessage({ text, streaming }: AgentMessageProps) {
  return (
    <div
      className={`prose prose-sm max-w-none text-gray-700
                     prose-pre:bg-gray-50 prose-pre:border prose-pre:border-gray-200/80 prose-pre:rounded-xl
                     prose-code:text-gray-700 prose-code:before:content-none prose-code:after:content-none
                     prose-headings:text-gray-800 prose-a:text-blue-600
                     ${streaming ? "animate-pulse" : ""}`}
    >
      <Markdown rehypePlugins={[rehypeHighlight]}>{text}</Markdown>
    </div>
  );
}

import { useMemo, type ReactNode } from "react";
import Markdown, { type Components } from "react-markdown";
import remarkGfm from "remark-gfm";
import rehypeHighlight from "rehype-highlight";

// Matches absolute file paths like /foo/bar/baz.ts
const FILE_PATH_RE = /(?:\/[\w.@+-]+)+\.\w+/g;

interface AgentMessageProps {
  text: string;
  streaming?: boolean;
  variant?: "default" | "compact";
  onOpenFile?: (path: string) => void;
}

/** Split plain text, replacing file-path segments with clickable spans. */
function linkifyFilePaths(
  text: string,
  onOpen: (path: string) => void
): ReactNode[] {
  const parts: ReactNode[] = [];
  let lastIdx = 0;

  for (const m of text.matchAll(FILE_PATH_RE)) {
    const path = m[0];
    const start = m.index!;
    if (start > lastIdx) parts.push(text.slice(lastIdx, start));
    parts.push(
      <span
        key={start}
        role="link"
        tabIndex={0}
        className="text-blue-600 hover:underline cursor-pointer"
        onClick={e => {
          e.stopPropagation();
          onOpen(path);
        }}
        onKeyDown={e => {
          if (e.key === "Enter") onOpen(path);
        }}
      >
        {path}
      </span>
    );
    lastIdx = start + path.length;
  }

  if (lastIdx < text.length) parts.push(text.slice(lastIdx));
  return parts;
}

function useMarkdownComponents(
  onOpenFile?: (path: string) => void
): Components | undefined {
  return useMemo(() => {
    if (!onOpenFile) return undefined;

    const processChildren = (children: ReactNode): ReactNode => {
      if (typeof children === "string") {
        const parts = linkifyFilePaths(children, onOpenFile);
        return parts.length === 1 && typeof parts[0] === "string"
          ? children
          : parts;
      }
      if (Array.isArray(children)) {
        return children.map((child, i) =>
          typeof child === "string" ? (
            linkifyFilePaths(child, onOpenFile).length === 1 &&
            typeof linkifyFilePaths(child, onOpenFile)[0] === "string" ? (
              child
            ) : (
              <span key={i}>{linkifyFilePaths(child, onOpenFile)}</span>
            )
          ) : (
            child
          )
        );
      }
      return children;
    };

    return {
      p({ children }) {
        return <p>{processChildren(children)}</p>;
      },
      li({ children }) {
        return <li>{processChildren(children)}</li>;
      },
      code({ className, children, ...props }) {
        // Only process inline code (no language className = not a code block)
        const isInline = !className;
        if (
          isInline &&
          typeof children === "string" &&
          FILE_PATH_RE.test(children)
        ) {
          FILE_PATH_RE.lastIndex = 0;
          return (
            <code
              {...props}
              className="text-blue-600 hover:underline cursor-pointer"
              role="link"
              tabIndex={0}
              onClick={e => {
                e.stopPropagation();
                onOpenFile(children);
              }}
              onKeyDown={e => {
                if (e.key === "Enter") onOpenFile(children as string);
              }}
            >
              {children}
            </code>
          );
        }
        return (
          <code className={className} {...props}>
            {children}
          </code>
        );
      },
    } satisfies Components;
  }, [onOpenFile]);
}

export function AgentMessage({
  text,
  streaming,
  variant = "default",
  onOpenFile,
}: AgentMessageProps) {
  const components = useMarkdownComponents(onOpenFile);

  if (variant === "compact") {
    return (
      <div
        className="prose prose-sm max-w-none text-gray-600
                   prose-p:text-[14px] prose-p:leading-[1.75] prose-li:text-[14px]
                   prose-pre:bg-gray-50 prose-pre:border prose-pre:border-gray-200/80 prose-pre:rounded-lg
                   prose-code:text-[13px] prose-code:text-gray-600 prose-code:before:content-none prose-code:after:content-none
                   prose-headings:text-gray-700 prose-a:text-blue-600
                   prose-p:my-1.5 prose-headings:my-2
                   prose-table:border-collapse prose-th:border prose-th:border-gray-200 prose-th:bg-gray-50 prose-th:px-3 prose-th:py-1.5
                   prose-td:border prose-td:border-gray-200 prose-td:px-3 prose-td:py-1.5"
      >
        <Markdown
          remarkPlugins={[remarkGfm]}
          rehypePlugins={[rehypeHighlight]}
          components={components}
        >
          {text}
        </Markdown>
        {streaming && (
          <span className="inline-block w-1.5 h-3.5 bg-gray-400 animate-blink align-text-bottom ml-0.5" />
        )}
      </div>
    );
  }

  return (
    <div
      className="prose prose-sm max-w-none text-gray-700
                     prose-p:text-[14.5px] prose-p:leading-[1.8] prose-li:text-[14.5px]
                     prose-pre:bg-gray-50 prose-pre:border prose-pre:border-gray-200/80 prose-pre:rounded-xl
                     prose-code:text-[13px] prose-code:text-gray-700 prose-code:before:content-none prose-code:after:content-none
                     prose-headings:text-gray-800 prose-headings:tracking-tight prose-a:text-blue-600
                     prose-table:border-collapse prose-th:border prose-th:border-gray-200 prose-th:bg-gray-50 prose-th:px-3 prose-th:py-1.5
                     prose-td:border prose-td:border-gray-200 prose-td:px-3 prose-td:py-1.5"
    >
      <Markdown
        remarkPlugins={[remarkGfm]}
        rehypePlugins={[rehypeHighlight]}
        components={components}
      >
        {text}
      </Markdown>
      {streaming && (
        <span className="inline-block w-1.5 h-4 bg-gray-400 animate-blink align-text-bottom ml-0.5" />
      )}
    </div>
  );
}

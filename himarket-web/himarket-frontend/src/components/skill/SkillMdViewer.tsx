import { useState, useEffect, useRef } from "react";
import { CodeOutlined, ReadOutlined } from "@ant-design/icons";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import rehypeHighlight from "rehype-highlight";
import hljs from "highlight.js/lib/core";
import markdown from "highlight.js/lib/languages/markdown";
import yaml from "highlight.js/lib/languages/yaml";
import "highlight.js/styles/atom-one-dark.css";
import "github-markdown-css/github-markdown-light.css";

hljs.registerLanguage("markdown", markdown);
hljs.registerLanguage("yaml", yaml);

import { getSkillMdBody } from "../../lib/skillMdUtils";

type ViewMode = "rendered" | "source";

interface SkillMdViewerProps {
  document: string;
}

function SkillMdViewer({ document }: SkillMdViewerProps) {
  const [viewMode, setViewMode] = useState<ViewMode>("rendered");
  const codeRef = useRef<HTMLElement>(null);

  useEffect(() => {
    if (viewMode === "source" && codeRef.current) {
      codeRef.current.removeAttribute("data-highlighted");
      hljs.highlightElement(codeRef.current);
    }
  }, [viewMode, document]);

  if (!document) {
    return (
      <div className="bg-white/60 backdrop-blur-sm rounded-2xl border border-white/40 p-6">
        <h3 className="text-base font-semibold text-gray-900 mb-4">SKILL.md</h3>
        <p className="text-gray-400 text-sm text-center py-8">暂无内容</p>
      </div>
    );
  }

  return (
    <div className="bg-white/60 backdrop-blur-sm rounded-2xl border border-white/40 p-6">
      {/* 标题 + 切换按钮 */}
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-base font-semibold text-gray-900">SKILL.md</h3>
        <div className="flex rounded-lg border border-gray-200 overflow-hidden text-xs">
          <button
            onClick={() => setViewMode("rendered")}
            className={`flex items-center gap-1 px-3 py-1.5 transition-colors duration-200 ${
              viewMode === "rendered"
                ? "bg-purple-100 text-purple-700"
                : "bg-white text-gray-500 hover:bg-gray-50"
            }`}
          >
            <ReadOutlined />
            <span>渲染视图</span>
          </button>
          <button
            onClick={() => setViewMode("source")}
            className={`flex items-center gap-1 px-3 py-1.5 transition-colors duration-200 ${
              viewMode === "source"
                ? "bg-purple-100 text-purple-700"
                : "bg-white text-gray-500 hover:bg-gray-50"
            }`}
          >
            <CodeOutlined />
            <span>源码视图</span>
          </button>
        </div>
      </div>

      {/* 内容区域 */}
      <div className="max-h-[500px] overflow-auto rounded-lg">
        {viewMode === "rendered" ? (
          <div className="markdown-body p-4 text-sm">
            <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeHighlight]}>
              {getSkillMdBody(document)}
            </ReactMarkdown>
          </div>
        ) : (
          <pre className="m-0 rounded-lg">
            <code ref={codeRef} className="language-markdown text-xs leading-relaxed">
              {document}
            </code>
          </pre>
        )}
      </div>
    </div>
  );
}

export default SkillMdViewer;

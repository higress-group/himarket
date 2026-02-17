import { useState, useEffect } from "react";
import { Button, Tooltip } from "antd";
import { SaveOutlined } from "@ant-design/icons";
import MonacoEditor from "react-monaco-editor";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

interface SkillMdEditorProps {
  value: string;
  onChange: (value: string) => void;
  onSave: (value: string) => void;
  saving?: boolean;
}

/**
 * SKILL.md 在线编辑器组件
 * 左侧：Monaco Editor（Markdown 编辑）
 * 右侧：react-markdown 实时预览
 * 空内容时禁用保存按钮并显示提示
 *
 * Requirements: 3.1, 3.2, 3.4
 */
export default function SkillMdEditor({
  value,
  onChange,
  onSave,
  saving = false,
}: SkillMdEditorProps) {
  const [content, setContent] = useState(value);

  useEffect(() => {
    setContent(value);
  }, [value]);

  const handleEditorChange = (newValue: string) => {
    setContent(newValue);
    onChange(newValue);
  };

  const isEmpty = !content || content.trim().length === 0;

  return (
    <div className="skill-md-editor">
      <div className="flex justify-end mb-3">
        <Tooltip title={isEmpty ? "SKILL.md 内容不能为空" : ""}>
          <Button
            type="primary"
            icon={<SaveOutlined />}
            disabled={isEmpty || saving}
            loading={saving}
            onClick={() => onSave(content)}
          >
            保存
          </Button>
        </Tooltip>
      </div>

      {isEmpty && (
        <div className="mb-2 text-orange-500 text-sm">
          ⚠️ SKILL.md 内容不能为空，请输入内容后保存
        </div>
      )}

      <div className="flex gap-4" style={{ height: 600 }}>
        {/* 左侧：Monaco Editor */}
        <div className="flex-1 border rounded overflow-hidden">
          <div className="bg-gray-100 px-3 py-1.5 text-sm text-gray-600 font-medium border-b">
            编辑
          </div>
          <MonacoEditor
            language="markdown"
            theme="vs-light"
            value={content}
            onChange={handleEditorChange}
            options={{
              minimap: { enabled: false },
              wordWrap: "on",
              lineNumbers: "on",
              scrollBeyondLastLine: false,
              fontSize: 14,
              tabSize: 2,
              automaticLayout: true,
              placeholder: "请输入 SKILL.md 内容...",
            }}
          />
        </div>

        {/* 右侧：Markdown 实时预览 */}
        <div className="flex-1 border rounded overflow-hidden">
          <div className="bg-gray-100 px-3 py-1.5 text-sm text-gray-600 font-medium border-b">
            预览
          </div>
          <div
            className="p-4 overflow-auto prose prose-sm max-w-none"
            style={{ height: "calc(100% - 33px)" }}
          >
            {content && content.trim() ? (
              <ReactMarkdown remarkPlugins={[remarkGfm]}>
                {content}
              </ReactMarkdown>
            ) : (
              <div className="flex items-center justify-center h-full text-gray-400">
                <p>在左侧编辑器中输入 Markdown 内容，此处将实时预览</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

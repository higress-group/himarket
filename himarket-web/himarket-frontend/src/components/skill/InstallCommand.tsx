import { useState } from "react";
import { message } from "antd";
import { CopyOutlined, CheckOutlined } from "@ant-design/icons";
import { copyToClipboard } from "../../lib/utils";

import { parseSkillMd } from "../../lib/skillMdUtils";

interface InstallCommandProps {
  productId: string;
  skillName: string;
  document: string;
}

type CopyState = "idle" | "command" | "content";

function InstallCommand({ productId, skillName, document }: InstallCommandProps) {
  const [copiedState, setCopiedState] = useState<CopyState>("idle");

  // 优先使用 frontmatter 中的 name（kebab-case），否则将 skillName 转为 kebab-case
  const parsed = parseSkillMd(document);
  const dirName = parsed.frontmatter.name || skillName.toLowerCase().replace(/\s+/g, "-");

  const downloadUrl = `${window.location.origin}/api/skills/${productId}/download`;
  const curlCommand = `curl -o .agents/skills/${dirName}/SKILL.md ${downloadUrl}`;

  const handleCopy = async (text: string, type: CopyState, successMsg: string) => {
    try {
      await copyToClipboard(text);
      message.success(successMsg);
      setCopiedState(type);
      setTimeout(() => setCopiedState("idle"), 2000);
    } catch {
      message.error("复制失败，请手动复制");
    }
  };

  return (
    <div className="bg-white/60 backdrop-blur-sm rounded-2xl border border-white/40 p-6">
      <h3 className="text-base font-semibold text-gray-900 mb-4">安装命令</h3>

      {/* curl 命令展示 */}
      <div className="bg-gray-900 rounded-lg p-4 mb-4 relative group">
        <code className="text-green-400 text-xs leading-relaxed break-all whitespace-pre-wrap">
          {curlCommand}
        </code>
      </div>

      {/* 复制按钮区域 */}
      <div className="flex flex-col gap-2">
        <button
          onClick={() => handleCopy(curlCommand, "command", "安装命令已复制到剪贴板")}
          className="
            flex items-center justify-center gap-2 w-full px-4 py-2
            rounded-lg border border-purple-200 text-sm
            text-purple-700 bg-purple-50 hover:bg-purple-100
            transition-colors duration-200
          "
        >
          {copiedState === "command" ? (
            <CheckOutlined className="text-green-500" />
          ) : (
            <CopyOutlined />
          )}
          <span>复制命令</span>
        </button>

        <button
          onClick={() => handleCopy(document, "content", "SKILL.md 内容已复制到剪贴板")}
          className="
            flex items-center justify-center gap-2 w-full px-4 py-2
            rounded-lg border border-gray-200 text-sm
            text-gray-700 bg-gray-50 hover:bg-gray-100
            transition-colors duration-200
          "
        >
          {copiedState === "content" ? (
            <CheckOutlined className="text-green-500" />
          ) : (
            <CopyOutlined />
          )}
          <span>复制 SKILL.md 内容</span>
        </button>
      </div>
    </div>
  );
}

export default InstallCommand;

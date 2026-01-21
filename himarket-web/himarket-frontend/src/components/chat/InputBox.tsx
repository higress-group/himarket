import { useState, useRef } from "react";
import {
  SendOutlined,
  FileImageOutlined,
  VideoCameraOutlined,
  FileOutlined,
  PlusCircleOutlined,
} from "@ant-design/icons";
import { Dropdown, message } from "antd";
import type { MenuProps } from "antd";
import SendButton from "../send-button";
import { Global, Mcp } from "../icon";
import APIs, { type IProductDetail, type IAttachment } from "../../lib/apis";
import { AttachmentPreview } from "./AttachmentPreview";

type UploadedAttachment = IAttachment & { url?: string };

interface InputBoxProps {
  isLoading?: boolean;
  mcpEnabled?: boolean;
  addedMcps: IProductDetail[];
  isMcpExecuting?: boolean;
  showWebSearch: boolean;
  webSearchEnabled: boolean;
  enableMultiModal?: boolean;
  onWebSearchEnable: (enabled: boolean) => void;
  onMcpClick?: () => void;
  onSendMessage: (content: string, attachments: IAttachment[]) => void;
}

export function InputBox(props: InputBoxProps) {
  const {
    onSendMessage,
    isLoading = false,
    mcpEnabled = false,
    onMcpClick,
    addedMcps,
    isMcpExecuting = false,
    showWebSearch,
    webSearchEnabled,
    onWebSearchEnable,
    // enableMultiModal = false,
  } = props;
  const [input, setInput] = useState("");
  const [attachments, setAttachments] = useState<UploadedAttachment[]>([]);
  const [isUploading, setIsUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const currentUploadType = useRef<string>("");
  // 暂时绕过
  const enableMultiModal = true;

  const uploadItems: MenuProps["items"] = [
    ...(enableMultiModal
      ? [
          {
            key: "image",
            label: "上传图片",
            icon: <FileImageOutlined />,
          },
        ]
      : []),
    {
      key: "video",
      label: "上传视频",
      icon: <VideoCameraOutlined />,
    },
    {
      key: "text",
      label: "上传文本",
      icon: <FileOutlined />,
    },
  ];

  const handleUploadClick = ({ key }: { key: string }) => {
    currentUploadType.current = key;
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
      // Set accept attribute based on type
      if (key === "image") {
        fileInputRef.current.accept = "image/*";
      } else if (key === "video") {
        fileInputRef.current.accept = "video/*";
      } else {
        fileInputRef.current.accept =
          ".txt,.md,.html,.doc,.docx,.pdf,.xls,.xlsx,.ppt,.pptx,.csv";
      }
      fileInputRef.current.click();
    }
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    try {
      setIsUploading(true);
      const res = await APIs.uploadAttachment(file);
      if (res.code === "SUCCESS" && res.data) {
        const attachment = res.data as UploadedAttachment;
        // 为图片生成预览 URL
        if (attachment.type === "IMAGE") {
          attachment.url = URL.createObjectURL(file);
        }
        setAttachments(prev => [...prev, attachment]);
      } else {
        message.error("上传失败");
      }
    } catch (error) {
      console.error("Upload error:", error);
      message.error("上传出错");
    } finally {
      setIsUploading(false);
    }
  };

  const removeAttachment = (id: string) => {
    setAttachments(prev => {
      const target = prev.find(a => a.attachmentId === id);
      if (target?.url && target.url.startsWith("blob:")) {
        URL.revokeObjectURL(target.url);
      }
      return prev.filter(a => a.attachmentId !== id);
    });
  };

  const handleSend = () => {
    if ((input.trim() || attachments.length > 0) && !isLoading) {
      onSendMessage(input.trim(), attachments);
      setInput("");
      // 清除预览 URL
      attachments.forEach(file => {
        if (file.url && file.url.startsWith("blob:")) {
          URL.revokeObjectURL(file.url);
        }
      });
      setAttachments([]);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey && !isLoading) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div
      className="relative p-1.5 rounded-2xl flex flex-col justify-center"
      style={{
        background:
          "linear-gradient(256deg, rgba(234, 228, 248, 1) 36%, rgba(215, 229, 243, 1) 100%)",
      }}
    >
      {/* 附件预览 */}
      <AttachmentPreview
        attachments={attachments}
        onRemove={removeAttachment}
        isUploading={isUploading}
        className="mb-1"
      />

      <input
        type="file"
        ref={fileInputRef}
        onChange={handleFileChange}
        className="hidden"
      />
      {isMcpExecuting && (
        <div className="px-3 py-1 text-sm">MCP 工具执行中...</div>
      )}
      <div className="w-full h-full pb-14 p-4 bg-white/80 backdrop-blur-sm rounded-2xl">
        <textarea
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          className="w-full resize-none focus:outline-none bg-transparent"
          placeholder="输入您的问题..."
          rows={2}
        />
      </div>
      <div
        className="absolute bottom-5 flex justify-between w-full px-6 left-0"
        data-sign="tool-btns"
      >
        <div className="inline-flex gap-2">
          <Dropdown
            menu={{ items: uploadItems, onClick: handleUploadClick }}
            trigger={["click"]}
            placement="topLeft"
          >
            <div className="flex h-full gap-2 items-center justify-center px-2 rounded-lg cursor-pointer transition-all ease-linear duration-400 hover:bg-black/5">
              <PlusCircleOutlined className="text-base text-subTitle" />
            </div>
          </Dropdown>
          {showWebSearch && (
            <ToolButton
              onClick={() => onWebSearchEnable(!webSearchEnabled)}
              enabled={webSearchEnabled}
            >
              <Global
                className={`w-4 h-4 ${webSearchEnabled ? "fill-colorPrimary" : "fill-subTitle"}`}
              />
              <span className="text-sm text-subTitle">联网</span>
            </ToolButton>
          )}
          <ToolButton onClick={onMcpClick} enabled={mcpEnabled}>
            <Mcp
              className={`w-4 h-4 ${mcpEnabled ? "fill-colorPrimary" : "fill-subTitle"}`}
            />
            <span className="text-sm text-subTitle">
              MCP {addedMcps.length ? `(${addedMcps.length})` : ""}
            </span>
          </ToolButton>
        </div>
        <SendButton
          className={`w-9 h-9 ${
            input.trim() && !isLoading
              ? "bg-colorPrimary text-white hover:opacity-90"
              : "bg-colorPrimarySecondary text-colorPrimary cursor-not-allowed"
          }`}
          isLoading={isLoading}
          onClick={handleSend}
        >
          <SendOutlined className={"text-sm text-white"} />
        </SendButton>
      </div>
    </div>
  );
}

function ToolButton({
  enabled,
  children,
  onClick,
}: {
  enabled: boolean;
  children: React.ReactNode;
  onClick?: () => void;
}) {
  return (
    <div
      onClick={onClick}
      className={`flex h-full gap-2 items-center justify-center px-2 rounded-lg cursor-pointer ${enabled ? "bg-colorPrimaryBgHover" : ""}  transition-all ease-linear duration-400`}
    >
      {children}
    </div>
  );
}

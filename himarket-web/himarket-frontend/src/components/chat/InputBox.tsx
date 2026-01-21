import { useState, useRef } from "react";
import {
  SendOutlined,
  FileImageOutlined,
  FileOutlined,
  PlusOutlined
} from "@ant-design/icons";
import { Dropdown, message, Tooltip } from "antd";
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
  const [isDragging, setIsDragging] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const currentUploadType = useRef<string>("");
  // 暂时绕过
  const enableMultiModal = true;

  const uploadItems: MenuProps["items"] = [
    ...(enableMultiModal
      ? [
          {
            key: "image",
            label: (
              <Tooltip title={<span className="text-black-normal">最大 5MB，最多 10 个文件 </span>} placement="right">
                <span className="w-full inline-block">上传图片</span>
              </Tooltip>
            ),
            icon: <FileImageOutlined />,
          },
        ]
      : []),
    {
      key: "text",
      label: (
        <Tooltip
          title={
            <div className="text-black-normal">
              上传文件时支持以下格式：txt、md、html、doc、docx、pdf、xls、xlsx、ppt、pptx、csv。单次最多上传 10 个文件。表格文件大小不超过 2MB。普通文档不超过 5MB。
            </div>
          }
          placement="right"
        >
          <span className="w-full inline-block">上传文本</span>
        </Tooltip>
      ),
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
      } else {
        fileInputRef.current.accept =
          ".txt,.md,.html,.doc,.docx,.pdf,.xls,.xlsx,.ppt,.pptx,.csv";
      }
      fileInputRef.current.click();
    }
  };

  const uploadFile = async (file: File) => {
    if (attachments.length >= 10) {
      message.warning("最多支持上传 10 个文件");
      return;
    }

    const isTableFile = /\.(csv|xls|xlsx)$/i.test(file.name);
    const maxSize = isTableFile ? 2 * 1024 * 1024 : 5 * 1024 * 1024;

    if (file.size > maxSize) {
      message.error(`${isTableFile ? '表格' : '文件'}大小不能超过 ${isTableFile ? '2M' : '5M'}`);
      return;
    }

    try {
      setIsUploading(true);
      const res = await APIs.uploadAttachment(file);
      if (res.code === "SUCCESS" && res.data) {
        const uploaded = await APIs.getAttachment(res.data.attachmentId);
        const attachment = res.data as UploadedAttachment;
        // 为图片生成预览 URL
        if (attachment.type === "IMAGE") {
          attachment.url = `data:${uploaded.data.mimeType};base64,${uploaded.data.data}`;
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

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      await uploadFile(file);
    }
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
  };

  const handleDrop = async (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    const file = e.dataTransfer.files?.[0];
    if (file) {
      await uploadFile(file);
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
      className={`relative p-1.5 rounded-2xl flex flex-col justify-center transition-all duration-200 ${isDragging ? "bg-white border-2 border-dashed border-colorPrimary shadow-lg scale-[1.01]" : ""}`}
      style={{
        background: isDragging
          ? undefined
          : "linear-gradient(256deg, rgba(234, 228, 248, 1) 36%, rgba(215, 229, 243, 1) 100%)",
      }}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
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
              <PlusOutlined className="text-base text-subTitle" />
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

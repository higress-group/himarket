import { useState, useRef } from "react";
import {
  SendOutlined,
  PlusCircleOutlined,
  FileImageOutlined,
  VideoCameraOutlined,
  FileOutlined,
  LoadingOutlined,
  CloseCircleFilled,
} from "@ant-design/icons";
import { Dropdown, message } from "antd";
import type { MenuProps } from "antd";
import SendButton from "../send-button";
import { Global, Mcp, File as FileIcon } from "../icon";
import APIs, { type IProductDetail, type IAttachment } from "../../lib/apis";

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
    enableMultiModal = false,
  } = props;
  const [input, setInput] = useState("");
  const [attachments, setAttachments] = useState<IAttachment[]>([]);
  const [isUploading, setIsUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const currentUploadType = useRef<string>("");

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
        setAttachments(prev => [...prev, res.data]);
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
    setAttachments(prev => prev.filter(a => a.attachmentId !== id));
  };

  const handleSend = () => {
    if ((input.trim() || attachments.length > 0) && !isLoading) {
      onSendMessage(input.trim(), attachments);
      setInput("");
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
      {(attachments.length > 0 || isUploading) && (
        <div className="mb-1 flex items-center gap-1">
          {attachments.map(file => {
            if (file.type !== "IMAGE") {
              return (
                <div
                  className="relative group rounded-2xl p-3 bg-white/80 hover:bg-white/50 transition-colors flex items-center w-[160px] gap-2 h-full"
                  style={{
                    boxShadow: "0px 4px 12px 0px rgba(118, 94, 252, 0.15)",
                  }}
                >
                  <div
                    onClick={() => removeAttachment(file.attachmentId)}
                    className="absolute cursor-pointer hidden group-hover:block top-2 right-2 leading-none">
                    <CloseCircleFilled className="text-ring-light" />
                  </div>
                  <div
                    style={{ background: "var(--gradient-iondigo-500)" }}
                    className="flex min-w-10 w-10 h-10 items-center justify-center rounded-lg"
                  >
                    <FileIcon className="fill-indigo-500" />
                  </div>
                  <div className="flex flex-col justify-between w-20 h-10">
                    <div className="text-sm text-accent-dark font-medium text-ellipsis overflow-hidden max-w-full whitespace-nowrap">
                      {file.name}
                    </div>
                    <span className="text-xs text-accent-dark">
                      {file.name.split(".").pop()}
                    </span>
                  </div>
                </div>
              );
            }
            return (
              <div className="relative group rounded-2xl w-16 h-16 overflow-hidden">
                <div
                  onClick={() => removeAttachment(file.attachmentId)}
                  className="absolute cursor-pointer hidden group-hover:block top-2 right-2 leading-none">
                  <CloseCircleFilled className="text-ring-light" />
                </div>
                <img className="w-full h-full object-cover" />
              </div>
            );
          })}
          {isUploading && (
            <div className="flex items-center justify-center p-2 bg-gray-50 rounded-lg border border-dashed border-gray-200 min-w-[60px]">
              <LoadingOutlined className="text-colorPrimary" />
            </div>
          )}
        </div>
      )}

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

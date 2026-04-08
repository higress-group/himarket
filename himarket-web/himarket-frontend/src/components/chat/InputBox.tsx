import { SendOutlined, FileImageOutlined, FileOutlined, PlusOutlined } from '@ant-design/icons';
import { Dropdown, message, Tooltip } from 'antd';
import { useState, useRef } from 'react';

import APIs, { type IProductDetail, type IAttachment } from '../../lib/apis';
import { Global, Mcp } from '../icon';
import { AttachmentPreview } from './AttachmentPreview';
import SendButton from '../send-button';

import type { MenuProps } from 'antd';

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
  onStop?: () => void;
}

export function InputBox(props: InputBoxProps) {
  const {
    addedMcps,
    enableMultiModal = false,
    isLoading = false,
    isMcpExecuting = false,
    mcpEnabled = false,
    onMcpClick,
    onSendMessage,
    onStop,
    onWebSearchEnable,
    showWebSearch,
    webSearchEnabled,
  } = props;
  const [input, setInput] = useState('');
  const [attachments, setAttachments] = useState<UploadedAttachment[]>([]);
  const [isUploading, setIsUploading] = useState(false);
  const [isDragging, setIsDragging] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const currentUploadType = useRef<string>('');

  const uploadItems: MenuProps['items'] = [
    ...(enableMultiModal
      ? [
          {
            icon: <FileImageOutlined />,
            key: 'image',
            label: (
              <Tooltip
                placement="right"
                title={<span className="text-black-normal">最大 5MB，最多 10 个文件 </span>}
              >
                <span className="w-full inline-block">上传图片</span>
              </Tooltip>
            ),
          },
        ]
      : []),
    {
      icon: <FileOutlined />,
      key: 'text',
      label: (
        <Tooltip
          placement="right"
          title={
            <div className="text-black-normal">
              上传文件时支持以下格式：txt、md、html、doc、docx、pdf、xls、xlsx、ppt、pptx、csv。单次最多上传
              10 个文件。表格文件大小不超过 2MB。普通文档不超过 5MB。
            </div>
          }
        >
          <span className="w-full inline-block">上传文本</span>
        </Tooltip>
      ),
    },
  ];

  const handleUploadClick = ({ key }: { key: string }) => {
    currentUploadType.current = key;
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
      // Set accept attribute based on type
      if (key === 'image') {
        fileInputRef.current.accept = 'image/*';
      } else {
        fileInputRef.current.accept = '.txt,.md,.html,.doc,.docx,.pdf,.xls,.xlsx,.ppt,.pptx,.csv';
      }
      fileInputRef.current.click();
    }
  };

  const uploadFile = async (file: File) => {
    if (attachments.length >= 10) {
      message.warning('最多支持上传 10 个文件');
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
      if (res.code === 'SUCCESS' && res.data) {
        const uploaded = await APIs.getAttachment(res.data.attachmentId);
        const attachment = res.data as UploadedAttachment;
        // 为图片生成预览 URL
        if (attachment.type === 'IMAGE') {
          attachment.url = `data:${uploaded.data.mimeType};base64,${uploaded.data.data}`;
        }
        setAttachments((prev) => [...prev, attachment]);
      } else {
        message.error(res.message || '上传失败');
      }
    } catch (error: unknown) {
      console.error('Upload error:', error);
      const errMsg =
        error && typeof error === 'object' && 'response' in error
          ? (error as { response?: { data?: { message?: string } } }).response?.data?.message
          : undefined;
      message.error(errMsg || '上传出错');
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
    setAttachments((prev) => {
      const target = prev.find((a) => a.attachmentId === id);
      if (target?.url && target.url.startsWith('blob:')) {
        URL.revokeObjectURL(target.url);
      }
      return prev.filter((a) => a.attachmentId !== id);
    });
  };

  const handleSend = () => {
    if ((input.trim() || attachments.length > 0) && !isLoading) {
      onSendMessage(input.trim(), attachments);
      setInput('');
      // 清除预览 URL
      attachments.forEach((file) => {
        if (file.url && file.url.startsWith('blob:')) {
          URL.revokeObjectURL(file.url);
        }
      });
      setAttachments([]);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey && !isLoading) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div
      aria-label="拖放附件到此区域"
      className={`relative p-1.5 rounded-2xl flex flex-col justify-center transition-all duration-200 ${isDragging ? 'bg-white border-2 border-dashed border-colorPrimary shadow-lg scale-[1.01]' : ''}`}
      onDragLeave={handleDragLeave}
      onDragOver={handleDragOver}
      onDrop={handleDrop}
      role="region"
      style={{
        background: isDragging
          ? undefined
          : 'linear-gradient(256deg, rgba(234, 228, 248, 1) 36%, rgba(215, 229, 243, 1) 100%)',
      }}
    >
      {/* 附件预览 */}
      <AttachmentPreview
        attachments={attachments}
        className="mb-1"
        isUploading={isUploading}
        onRemove={removeAttachment}
      />

      <input className="hidden" onChange={handleFileChange} ref={fileInputRef} type="file" />
      {isMcpExecuting && <div className="px-3 py-1 text-sm">MCP 工具执行中...</div>}
      <div className="w-full h-full pb-14 p-4 bg-white/80 backdrop-blur-sm rounded-2xl">
        <textarea
          className="w-full resize-none focus:outline-none bg-transparent"
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="输入您的问题..."
          rows={2}
          value={input}
        />
      </div>
      <div
        className="absolute bottom-5 flex justify-between w-full px-6 left-0"
        data-sign="tool-btns"
      >
        <div className="inline-flex gap-2">
          <Dropdown
            menu={{ items: uploadItems, onClick: handleUploadClick }}
            placement="topLeft"
            trigger={['click']}
          >
            <div className="flex h-full gap-2 items-center justify-center px-2 rounded-lg cursor-pointer transition-all ease-linear duration-400 hover:bg-black/5">
              <PlusOutlined className="text-base text-subTitle" />
            </div>
          </Dropdown>
          {showWebSearch && (
            <ToolButton
              enabled={webSearchEnabled}
              onClick={() => onWebSearchEnable(!webSearchEnabled)}
            >
              <Global
                className={`w-4 h-4 ${webSearchEnabled ? 'fill-colorPrimary' : 'fill-subTitle'}`}
              />
              <span className="text-sm text-subTitle">联网</span>
            </ToolButton>
          )}
          <ToolButton enabled={mcpEnabled} onClick={onMcpClick}>
            <Mcp className={`w-4 h-4 ${mcpEnabled ? 'fill-colorPrimary' : 'fill-subTitle'}`} />
            <span className="text-sm text-subTitle">
              MCP {addedMcps.length ? `(${addedMcps.length})` : ''}
            </span>
          </ToolButton>
        </div>
        <SendButton
          className={`w-9 h-9 ${
            input.trim() && !isLoading
              ? 'bg-colorPrimary text-white hover:opacity-90'
              : isLoading
                ? 'bg-colorPrimary text-white hover:opacity-90'
                : 'bg-colorPrimarySecondary text-colorPrimary cursor-not-allowed'
          }`}
          isLoading={isLoading}
          onClick={handleSend}
          onStop={onStop}
        >
          <SendOutlined className={'text-sm text-white'} />
        </SendButton>
      </div>
    </div>
  );
}

function ToolButton({
  children,
  enabled,
  onClick,
}: {
  enabled: boolean;
  children: React.ReactNode;
  onClick?: () => void;
}) {
  return (
    <button
      className={`flex h-full gap-2 items-center justify-center px-2 rounded-lg cursor-pointer ${enabled ? 'bg-colorPrimaryBgHover' : ''}  transition-all ease-linear duration-400`}
      onClick={onClick}
      type="button"
    >
      {children}
    </button>
  );
}

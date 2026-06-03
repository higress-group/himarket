import { SendOutlined, FileImageOutlined, FileOutlined, PlusOutlined } from '@ant-design/icons';
import { Dropdown, message, Tooltip } from 'antd';
import { useState, useRef } from 'react';
import { useTranslation } from 'react-i18next';

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
    mcpEnabled = false,
    onMcpClick,
    onSendMessage,
    onStop,
    onWebSearchEnable,
    showWebSearch,
    webSearchEnabled,
  } = props;
  const { t } = useTranslation('chat');
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
                title={<span className="text-black-normal">{t('input.imageUploadHint')}</span>}
              >
                <span className="w-full inline-block">{t('input.uploadImage')}</span>
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
          title={<div className="text-black-normal">{t('input.textUploadHint')}</div>}
        >
          <span className="w-full inline-block">{t('input.uploadText')}</span>
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
      message.warning(t('input.maxFiles'));
      return;
    }

    const isTableFile = /\.(csv|xls|xlsx)$/i.test(file.name);
    const maxSize = isTableFile ? 2 * 1024 * 1024 : 5 * 1024 * 1024;

    if (file.size > maxSize) {
      message.error(
        t('input.fileTooLarge', {
          limit: isTableFile ? '2M' : '5M',
          type: isTableFile ? t('input.tableFile') : t('input.file'),
        }),
      );
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
        message.error(res.message || t('input.uploadFailed'));
      }
    } catch (error: unknown) {
      console.error('Upload error:', error);
      const errMsg =
        error && typeof error === 'object' && 'response' in error
          ? (error as { response?: { data?: { message?: string } } }).response?.data?.message
          : undefined;
      message.error(errMsg || t('input.uploadError'));
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

  const canSend = Boolean(input.trim() || attachments.length > 0);

  return (
    <div
      aria-label={t('input.dropArea')}
      className={`relative flex flex-col justify-center rounded-[16px] border bg-white/85 p-2 shadow-[0_12px_34px_rgba(37,56,88,0.06)] transition-all duration-200 focus-within:border-colorPrimary/25 focus-within:bg-white ${
        isDragging
          ? 'scale-[1.01] border-dashed border-colorPrimary ring-4 ring-colorPrimary/10'
          : 'border-[#DDE5F0]'
      }`}
      onDragLeave={handleDragLeave}
      onDragOver={handleDragOver}
      onDrop={handleDrop}
      role="region"
    >
      {/* 附件预览 */}
      <AttachmentPreview
        attachments={attachments}
        className="mb-1"
        isUploading={isUploading}
        onRemove={removeAttachment}
      />

      <input className="hidden" onChange={handleFileChange} ref={fileInputRef} type="file" />
      <div className="min-h-[80px] w-full rounded-[12px] p-3 pb-11">
        <textarea
          className="min-h-[40px] w-full resize-none bg-transparent text-[15px] leading-6 text-gray-900 placeholder:text-gray-400 focus:outline-none"
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={t('input.placeholder')}
          rows={2}
          value={input}
        />
      </div>
      <div
        className="absolute bottom-3 left-0 flex w-full justify-between px-5"
        data-sign="tool-btns"
      >
        <div className="inline-flex gap-2">
          <Dropdown
            menu={{ items: uploadItems, onClick: handleUploadClick }}
            placement="topLeft"
            trigger={['click']}
          >
            <button
              className="flex h-9 w-9 items-center justify-center rounded-[10px] text-gray-500 transition-all duration-200 hover:bg-gray-100 hover:text-gray-800 active:scale-[0.98]"
              type="button"
            >
              <PlusOutlined className="text-base text-subTitle" />
            </button>
          </Dropdown>
          {showWebSearch && (
            <ToolButton
              enabled={webSearchEnabled}
              onClick={() => onWebSearchEnable(!webSearchEnabled)}
            >
              <Global
                className={`w-4 h-4 ${webSearchEnabled ? 'fill-colorPrimary' : 'fill-subTitle'}`}
              />
              <span className="text-sm text-subTitle">{t('input.webSearch')}</span>
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
          className={`h-9 w-9 ${
            canSend && !isLoading
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
      className={`flex h-9 cursor-pointer items-center justify-center gap-2 rounded-[10px] px-3 text-gray-600 transition-all duration-200 hover:bg-gray-100 hover:text-gray-900 active:scale-[0.98] ${enabled ? 'bg-colorPrimaryBgHover text-colorPrimary' : ''}`}
      onClick={onClick}
      type="button"
    >
      {children}
    </button>
  );
}

import { CheckOutlined, CopyOutlined, DownloadOutlined, ExpandOutlined } from '@ant-design/icons';
import { type ReactNode, isValidElement, useState } from 'react';
import { useTranslation } from 'react-i18next';
import ReactMarkdown from 'react-markdown';
import rehypeHighlight from 'rehype-highlight';
import remarkGfm from 'remark-gfm';
import 'highlight.js/styles/github.css';
import 'github-markdown-css/github-markdown-light.css';
import './MarkdownRender.css';

import { copyToClipboard } from '../lib/utils';

interface MarkdownRenderProps {
  content: string;
  imageStyle?: 'default' | 'card';
  variant?: 'document' | 'chat';
}

interface CodeBlockProps {
  children: ReactNode;
  copiedLabel: string;
  copyLabel: string;
}

function getTextFromNode(node: ReactNode): string {
  if (typeof node === 'string' || typeof node === 'number') {
    return String(node);
  }

  if (Array.isArray(node)) {
    return node.map(getTextFromNode).join('');
  }

  if (isValidElement<{ children?: ReactNode }>(node)) {
    return getTextFromNode(node.props.children);
  }

  return '';
}

function CodeBlock({ children, copiedLabel, copyLabel }: CodeBlockProps) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    const text = getTextFromNode(children).replace(/\n$/, '');

    await copyToClipboard(text);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1200);
  };

  return (
    <div className="himarket-code-block">
      <button
        aria-label={copied ? copiedLabel : copyLabel}
        className="himarket-code-copy"
        onClick={handleCopy}
        title={copied ? copiedLabel : copyLabel}
        type="button"
      >
        {copied ? <CheckOutlined /> : <CopyOutlined />}
      </button>
      <pre>{children}</pre>
    </div>
  );
}

const MarkdownRender = ({
  content,
  imageStyle = 'default',
  variant = 'document',
}: MarkdownRenderProps) => {
  const { t } = useTranslation('common');
  const generatedImageLabel = t('markdown.generatedImage');
  const downloadLabel = t('markdown.download');
  const viewOriginalLabel = t('markdown.viewOriginal');

  return (
    <div
      className={`markdown-body himarket-markdown-body ${variant === 'chat' ? 'himarket-markdown-chat' : ''}`}
      style={{
        backgroundColor: 'transparent',
        color: '#24292e',
        fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif',
        fontSize: '16px',
        lineHeight: '1.5',
      }}
    >
      <ReactMarkdown
        components={{
          img: ({ alt, src }) => {
            // 标准图片渲染（用于普通 Markdown 文档）
            if (imageStyle !== 'card') {
              return <img alt={alt || ''} className="my-4 max-w-full rounded-lg" src={src} />;
            }

            // 文生图结果展示卡片 - 560px max-width, 16:10 比例
            const handleDownload = () => {
              if (src) {
                const link = document.createElement('a');
                link.href = src;
                link.download = alt || 'image.png';
                link.click();
              }
            };

            const handleViewOriginal = () => {
              if (src) {
                // 使用 Modal 或新窗口展示原图，而不是下载
                const imgWindow = window.open('', '_blank');
                if (imgWindow) {
                  imgWindow.document.write(`
                  <html>
                    <head>
                      <title>${viewOriginalLabel}</title>
                      <style>
                        body {
                          margin: 0;
                          padding: 0;
                          display: flex;
                          align-items: center;
                          justify-content: center;
                          min-height: 100vh;
                          background: #1a1a1a;
                        }
                        img {
                          max-width: 100%;
                          max-height: 100vh;
                          object-fit: contain;
                        }
                      </style>
                    </head>
                    <body>
                      <img src="${src}" alt="${alt || ''}" />
                    </body>
                  </html>
                `);
                  imgWindow.document.close();
                }
              }
            };

            return (
              <div className="my-4 w-full max-w-[560px] overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm">
                {/* 图片容器 - 16:10 比例 */}
                <button
                  className="relative flex w-full cursor-pointer items-center justify-center border-0 bg-transparent p-0"
                  onClick={handleViewOriginal}
                  style={{
                    aspectRatio: '16 / 10',
                    background: 'linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%)',
                  }}
                  type="button"
                >
                  <img
                    alt={alt || ''}
                    className="max-h-full max-w-full rounded-lg object-contain shadow-lg"
                    src={src}
                  />
                </button>

                {/* 底部信息栏 */}
                <div className="flex items-center justify-between border-t border-gray-100 px-5 py-4">
                  <div className="flex items-center gap-2 text-gray-500">
                    <svg
                      fill="none"
                      height="16"
                      stroke="currentColor"
                      strokeWidth="2"
                      viewBox="0 0 24 24"
                      width="16"
                    >
                      <rect height="18" rx="2" ry="2" width="18" x="3" y="3" />
                      <circle cx="8.5" cy="8.5" r="1.5" />
                      <polyline points="21 15 16 10 5 21" />
                    </svg>
                    <span className="text-sm">{alt || generatedImageLabel}</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <button
                      className="flex items-center gap-1.5 rounded-lg bg-gray-50 px-3 py-1.5 text-sm text-gray-600 transition-colors hover:bg-gray-100"
                      onClick={handleDownload}
                    >
                      <DownloadOutlined className="text-xs" />
                      <span>{downloadLabel}</span>
                    </button>
                    <button
                      className="flex items-center gap-1.5 rounded-lg bg-gray-50 px-3 py-1.5 text-sm text-gray-600 transition-colors hover:bg-gray-100"
                      onClick={handleViewOriginal}
                    >
                      <ExpandOutlined className="text-xs" />
                      <span>{viewOriginalLabel}</span>
                    </button>
                  </div>
                </div>
              </div>
            );
          },
          pre: ({ children }) => (
            <CodeBlock copiedLabel={t('copied')} copyLabel={t('copyCode')}>
              {children}
            </CodeBlock>
          ),
        }}
        rehypePlugins={[rehypeHighlight]}
        remarkPlugins={[remarkGfm]}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
};

export default MarkdownRender;

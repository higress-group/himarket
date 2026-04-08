import { message } from 'antd';
import * as yaml from 'js-yaml';
import React from 'react';
import SwaggerUI from 'swagger-ui-react';
import 'swagger-ui-react/swagger-ui.css';
import './SwaggerUIWrapper.css';

import { copyToClipboard } from '@/lib/utils';

interface SwaggerUIWrapperProps {
  apiSpec: string;
}

export const SwaggerUIWrapper: React.FC<SwaggerUIWrapperProps> = ({ apiSpec }) => {
  // 直接解析原始规范，不进行重新构建
  let swaggerSpec: Record<string, unknown>;

  try {
    // 尝试解析YAML格式
    try {
      swaggerSpec = yaml.load(apiSpec) as Record<string, unknown>;
    } catch {
      // 如果YAML解析失败，尝试JSON格式
      swaggerSpec = JSON.parse(apiSpec) as Record<string, unknown>;
    }

    if (!swaggerSpec || !swaggerSpec.paths) {
      throw new Error('Invalid OpenAPI specification');
    }

    // 为没有tags的操作添加默认标签，避免显示"default"
    if (swaggerSpec.paths && typeof swaggerSpec.paths === 'object') {
      Object.keys(swaggerSpec.paths).forEach((path) => {
        const pathItem = (swaggerSpec.paths as Record<string, unknown>)[path];
        if (pathItem && typeof pathItem === 'object') {
          Object.keys(pathItem).forEach((method) => {
            const operation = (pathItem as Record<string, unknown>)[method];
            if (
              operation &&
              typeof operation === 'object' &&
              !(operation as Record<string, unknown>).tags
            ) {
              (operation as Record<string, unknown>).tags = ['接口列表'];
            }
          });
        }
      });
    }
  } catch (error) {
    console.error('OpenAPI规范解析失败:', error);
    return (
      <div className="text-center text-gray-500 py-8 bg-gray-50 rounded-lg">
        <p>无法解析OpenAPI规范</p>
        <div className="text-sm text-gray-400 mt-2">请检查API配置格式是否正确</div>
        <div className="text-xs text-gray-400 mt-1">
          错误详情: {error instanceof Error ? error.message : String(error)}
        </div>
      </div>
    );
  }

  return (
    <div className="swagger-ui-wrapper">
      <SwaggerUI
        deepLinking={false}
        defaultModelExpandDepth={0}
        defaultModelsExpandDepth={0}
        displayOperationId={true}
        displayRequestDuration={true}
        docExpansion="list"
        enableCORS={true}
        filter={false}
        onComplete={() => {
          // 添加服务器复制功能
          setTimeout(() => {
            const serversContainer = document.querySelector('.swagger-ui .servers');
            if (serversContainer && !serversContainer.querySelector('.copy-server-btn')) {
              const copyBtn = document.createElement('button');
              copyBtn.className = 'copy-server-btn';
              copyBtn.innerHTML = `
                <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"/>
                </svg>
              `;
              copyBtn.title = '复制服务器地址';
              copyBtn.style.cssText = `
                position: absolute;
                right: 12px;
                top: 50%;
                transform: translateY(-50%);
                background: transparent;
                border: none;
                border-radius: 4px;
                padding: 6px 8px;
                cursor: pointer;
                color: #666;
                transition: all 0.2s;
                z-index: 10;
                display: flex;
                align-items: center;
                justify-content: center;
              `;

              copyBtn.addEventListener('click', async () => {
                const serverSelect = serversContainer.querySelector('select') as HTMLSelectElement;
                if (serverSelect && serverSelect.value) {
                  try {
                    await copyToClipboard(serverSelect.value);
                    message.success('服务器地址已复制到剪贴板', 1);
                  } catch {
                    message.error('复制失败，请手动复制');
                  }
                }
              });

              // 添加hover效果
              copyBtn.addEventListener('mouseenter', () => {
                copyBtn.style.background = '#f5f5f5';
                copyBtn.style.color = '#1890ff';
              });

              copyBtn.addEventListener('mouseleave', () => {
                copyBtn.style.background = 'transparent';
                copyBtn.style.color = '#666';
              });

              serversContainer.appendChild(copyBtn);

              // 调整服务器选择框的padding
              const serverSelect = serversContainer.querySelector('select') as HTMLSelectElement;
              if (serverSelect) {
                serverSelect.style.paddingRight = '50px';
              }
            }
          }, 1000);
        }}
        requestInterceptor={(req: { [key: string]: unknown }) => {
          return req;
        }}
        requestSnippets={{
          generators: {
            curl_bash: {
              syntax: 'bash',
              title: 'cURL (bash)',
            },
            curl_cmd: {
              syntax: 'bash',
              title: 'cURL (CMD)',
            },
            curl_powershell: {
              syntax: 'powershell',
              title: 'cURL (PowerShell)',
            },
          },
        }}
        requestSnippetsEnabled={true}
        responseInterceptor={(res: { [key: string]: unknown }) => {
          return res;
        }}
        showCommonExtensions={true}
        showMutatedRequest={true}
        showRequestHeaders={true}
        spec={swaggerSpec}
        supportedSubmitMethods={['get', 'post', 'put', 'delete', 'patch', 'head', 'options']}
        syntaxHighlight={{
          activated: true,
          theme: 'agate',
        }}
        tryItOutEnabled={true}
      />
    </div>
  );
};

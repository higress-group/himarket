import React from 'react';
import SwaggerUI from 'swagger-ui-react';
import 'swagger-ui-react/swagger-ui.css';
import './SwaggerUIWrapper.css';
import * as yaml from 'js-yaml';
import { message } from 'antd';

interface SwaggerUIWrapperProps {
  apiSpec: string;
}

export const SwaggerUIWrapper: React.FC<SwaggerUIWrapperProps> = ({ apiSpec }) => {
  // 直接解析原始规范，不进行重新构建
  let swaggerSpec: any;
  
  try {
    // 尝试解析YAML格式
    try {
      swaggerSpec = yaml.load(apiSpec);
    } catch {
      // 如果YAML解析失败，尝试JSON格式
      swaggerSpec = JSON.parse(apiSpec);
    }

    if (!swaggerSpec || !swaggerSpec.paths) {
      throw new Error('Invalid OpenAPI specification');
    }

    // 为没有tags的操作添加默认标签，避免显示"default"
    Object.keys(swaggerSpec.paths).forEach(path => {
      const pathItem = swaggerSpec.paths[path];
      Object.keys(pathItem).forEach(method => {
        const operation = pathItem[method];
        if (operation && typeof operation === 'object' && !operation.tags) {
          operation.tags = ['接口列表'];
        }
      });
    });
  } catch (error) {
    console.error('OpenAPI规范解析失败:', error);
    return (
      <div className="text-center text-gray-500 py-8 bg-gray-50 rounded-lg">
        <p>无法解析OpenAPI规范</p>
        <div className="text-sm text-gray-400 mt-2">
          请检查API配置格式是否正确
        </div>
        <div className="text-xs text-gray-400 mt-1">
          错误详情: {error instanceof Error ? error.message : String(error)}
        </div>
      </div>
    );
  }

  return (
    <div className="swagger-ui-wrapper">
      <SwaggerUI
        spec={swaggerSpec}
        docExpansion="list"
        displayRequestDuration={true}
        tryItOutEnabled={true}
        filter={false}
        defaultModelsExpandDepth={0}
        defaultModelExpandDepth={0}
        displayOperationId={true}
        supportedSubmitMethods={['get', 'post', 'put', 'delete', 'patch', 'head', 'options']}
        deepLinking={false}
        requestInterceptor={(request) => {
          console.log('Request:', request);
          return request;
        }}
        responseInterceptor={(response) => {
          console.log('Response:', response);
          return response;
        }}
        onComplete={() => {
          console.log('Swagger UI loaded');
          // 添加服务器复制功能 - 使用requestAnimationFrame优化性能
          const addCopyButton = () => {
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
              
              // 添加hover效果
              copyBtn.addEventListener('mouseenter', () => {
                copyBtn.style.background = '#f5f5f5';
                copyBtn.style.color = '#1890ff';
              });
              
              copyBtn.addEventListener('mouseleave', () => {
                copyBtn.style.background = 'transparent';
                copyBtn.style.color = '#666';
              });
              
              copyBtn.addEventListener('click', () => {
                const serverSelect = serversContainer.querySelector('select') as HTMLSelectElement;
                if (serverSelect && serverSelect.value) {
                  navigator.clipboard.writeText(serverSelect.value)
                    .then(() => {
                      message.success('服务器地址已复制到剪贴板', 1);
                    })
                    .catch(() => {
                      // 降级到传统复制方法
                      const textArea = document.createElement('textarea');
                      textArea.value = serverSelect.value;
                      document.body.appendChild(textArea);
                      textArea.select();
                      document.execCommand('copy');
                      document.body.removeChild(textArea);
                      message.success('服务器地址已复制到剪贴板', 1);
                    });
                }
              });


              serversContainer.appendChild(copyBtn);
              
              // 调整服务器选择框的padding
              const serverSelect = serversContainer.querySelector('select') as HTMLSelectElement;
              if (serverSelect) {
                serverSelect.style.paddingRight = '50px';
              }
            }
          };
          
          // 立即尝试添加按钮，如果失败则在下一帧重试
          addCopyButton();
          if (!document.querySelector('.swagger-ui .servers .copy-server-btn')) {
            requestAnimationFrame(addCopyButton);
          }
        }}
      />
    </div>
  );
};

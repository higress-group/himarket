import { message } from 'antd';
import * as yaml from 'js-yaml';
import React from 'react';
import { useTranslation } from 'react-i18next';
import SwaggerUI from 'swagger-ui-react';
import 'swagger-ui-react/swagger-ui.css';
import './SwaggerUIWrapper.css';

interface SwaggerUIWrapperProps {
  apiSpec: string;
}

export const SwaggerUIWrapper: React.FC<SwaggerUIWrapperProps> = ({ apiSpec }) => {
  const { t } = useTranslation('apiDetail');

  // Parse the raw spec directly without rebuilding it.
  let swaggerSpec: Record<string, unknown>;

  try {
    // Try YAML first, then JSON.
    try {
      swaggerSpec = yaml.load(apiSpec) as Record<string, unknown>;
    } catch {
      swaggerSpec = JSON.parse(apiSpec) as Record<string, unknown>;
    }

    if (!swaggerSpec || typeof swaggerSpec !== 'object' || !('paths' in swaggerSpec)) {
      throw new Error('Invalid OpenAPI specification');
    }

    // Add a default tag to operations without tags to avoid showing "default".
    const paths = swaggerSpec.paths as Record<string, Record<string, unknown>>;
    Object.keys(paths).forEach((path) => {
      const pathItem = paths[path];
      if (pathItem && typeof pathItem === 'object') {
        Object.keys(pathItem).forEach((method) => {
          const operation = pathItem[method];
          if (operation && typeof operation === 'object' && !('tags' in operation)) {
            (operation as Record<string, unknown>).tags = [t('restDocs.endpointList')];
          }
        });
      }
    });
  } catch (error) {
    console.error('Failed to parse OpenAPI spec:', error);
    return (
      <div className="text-center text-gray-500 py-8 bg-gray-50 rounded-lg">
        <p>{t('restDocs.parseFailedTitle')}</p>
        <div className="text-sm text-gray-400 mt-2">{t('restDocs.parseFailedHint')}</div>
        <div className="text-xs text-gray-400 mt-1">
          {t('restDocs.errorDetails')}: {error instanceof Error ? error.message : String(error)}
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
        filter={false}
        onComplete={() => {
          console.warn('Swagger UI loaded');
          // Add server copy support and retry with requestAnimationFrame if needed.
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
              copyBtn.title = t('restDocs.copyServerAddress');
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
                  navigator.clipboard
                    .writeText(serverSelect.value)
                    .then(() => {
                      message.success(t('messages.serverCopied'), 1);
                    })
                    .catch(() => {
                      const textArea = document.createElement('textarea');
                      textArea.value = serverSelect.value;
                      document.body.appendChild(textArea);
                      textArea.select();
                      document.execCommand('copy');
                      document.body.removeChild(textArea);
                      message.success(t('messages.serverCopied'), 1);
                    });
                }
              });

              serversContainer.appendChild(copyBtn);

              const serverSelect = serversContainer.querySelector('select') as HTMLSelectElement;
              if (serverSelect) {
                serverSelect.style.paddingRight = '50px';
              }
            }
          };

          addCopyButton();
          if (!document.querySelector('.swagger-ui .servers .copy-server-btn')) {
            requestAnimationFrame(addCopyButton);
          }
        }}
        requestInterceptor={(request) => {
          console.warn('Request:', request);
          return request;
        }}
        responseInterceptor={(response) => {
          console.warn('Response:', response);
          return response;
        }}
        spec={swaggerSpec}
        supportedSubmitMethods={['get', 'post', 'put', 'delete', 'patch', 'head', 'options']}
        tryItOutEnabled={true}
      />
    </div>
  );
};

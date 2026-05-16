import { ApiOutlined, CopyOutlined, DownloadOutlined, FileTextOutlined } from '@ant-design/icons';
import { Tabs, Button, message } from 'antd';
import * as yaml from 'js-yaml';
import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';

import { EmptyState } from '../components/EmptyState';
import MarkdownRender from '../components/MarkdownRender';
import { ProductDetailLayout } from '../components/ProductDetailLayout';
import { RestApiDocsViewer } from '../components/RestApiDocsViewer';
import APIs from '../lib/apis';
import { copyToClipboard } from '../lib/utils';

import type { RestApiExample } from '../components/RestApiDocsViewer';
import type { IProductDetail } from '../lib/apis';

const DEFAULT_REST_API_EXAMPLE: RestApiExample = {
  method: 'GET',
  path: '/{path}',
  serverUrl: '',
};

function stripTrailingSlash(value: string): string {
  return value.endsWith('/') ? value.slice(0, -1) : value;
}

function buildCurlCommand(example: RestApiExample): string {
  const serverUrl = stripTrailingSlash(example.serverUrl || 'https://api.example.com');
  return `curl -X ${example.method} \\
  '${serverUrl}${example.path}' \\
  -H 'Accept: application/json' \\
  -H 'Content-Type: application/json'`;
}

function ApiDetailPage() {
  const { apiProductId } = useParams();
  const [, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [apiData, setApiData] = useState<IProductDetail>();
  const [restApiExample, setRestApiExample] = useState<RestApiExample>(DEFAULT_REST_API_EXAMPLE);
  const fetchApiDetail = React.useCallback(async () => {
    setLoading(true);
    setError('');
    if (!apiProductId) return;
    try {
      const response = await APIs.getProduct({ id: apiProductId });
      if (response.code === 'SUCCESS' && response.data) {
        setApiData(response.data);

        // 提取基础URL和示例路径用于curl示例
        if (response.data.apiConfig?.spec) {
          try {
            let openApiDoc;
            try {
              openApiDoc = yaml.load(response.data.apiConfig.spec);
            } catch {
              openApiDoc = JSON.parse(response.data.apiConfig.spec);
            }

            // 提取服务器URL并处理尾部斜杠
            let serverUrl = openApiDoc?.servers?.[0]?.url || '';
            serverUrl = serverUrl ? stripTrailingSlash(serverUrl) : '';

            const nextExample: RestApiExample = {
              ...DEFAULT_REST_API_EXAMPLE,
              serverUrl,
            };

            // 提取第一个可用的路径和方法作为示例
            const paths = openApiDoc?.paths;
            if (paths && typeof paths === 'object') {
              const pathEntries = Object.entries(paths as Record<string, unknown>);
              if (pathEntries.length > 0) {
                const firstEntry = pathEntries[0];
                if (firstEntry) {
                  const [firstPath, pathMethods] = firstEntry;
                  if (pathMethods && typeof pathMethods === 'object') {
                    const methods = Object.keys(pathMethods as Record<string, unknown>);
                    if (methods.length > 0) {
                      const firstMethod = methods[0]?.toUpperCase() ?? 'GET';
                      nextExample.path = firstPath;
                      nextExample.method = firstMethod;
                    }
                  }
                }
              }
            }
            setRestApiExample(nextExample);
          } catch (error) {
            console.error('解析OpenAPI规范失败:', error);
          }
        }
      }
    } catch (error) {
      console.error('获取API详情失败:', error);
      setError('加载失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  }, [apiProductId]);

  useEffect(() => {
    if (!apiProductId) return;
    fetchApiDetail();
  }, [apiProductId, fetchApiDetail]);

  const handleRestApiExampleChange = React.useCallback((example: RestApiExample) => {
    setRestApiExample((previous) => {
      const nextExample = {
        ...example,
        serverUrl: stripTrailingSlash(example.serverUrl),
      };
      if (
        previous.method === nextExample.method &&
        previous.path === nextExample.path &&
        previous.serverUrl === nextExample.serverUrl
      ) {
        return previous;
      }
      return nextExample;
    });
  }, []);

  const handleCopyCurlCommand = async () => {
    try {
      await copyToClipboard(buildCurlCommand(restApiExample));
      message.success('cURL命令已复制到剪贴板', 1);
    } catch {
      message.error('复制失败，请手动复制');
    }
  };

  const handleDownloadSpec = (format: 'json' | 'yaml') => {
    if (!apiData?.apiConfig?.spec) return;

    try {
      const content =
        format === 'json'
          ? JSON.stringify(yaml.load(apiData.apiConfig.spec), null, 2)
          : apiData.apiConfig.spec;
      const blob = new Blob([content], {
        type: format === 'json' ? 'application/json' : 'text/yaml',
      });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${apiData.name || 'api'}-openapi.${format}`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
      message.success(`OpenAPI ${format.toUpperCase()} 文件已下载`, 1);
    } catch (err) {
      console.warn(err);
      message.error(format === 'json' ? '转换JSON格式失败' : '下载OpenAPI规范失败');
    }
  };

  const curlCommand = buildCurlCommand(restApiExample);

  const leftContent = apiData ? (
    <div className="bg-white/60 backdrop-blur-sm rounded-[10px] border border-white/40 p-6 pt-0">
      <Tabs
        defaultActiveKey="overview"
        items={[
          {
            children: apiData.document ? (
              <div className="min-h-[400px] px-4">
                <MarkdownRender content={apiData.document} />
              </div>
            ) : (
              <EmptyState description="暂无概览信息" />
            ),
            key: 'overview',
            label: (
              <span className="flex items-center gap-1.5 font-semibold">
                <FileTextOutlined className="text-sm" />
                概览
              </span>
            ),
          },
          {
            children: (
              <div>
                {apiData.apiConfig && apiData.apiConfig.spec ? (
                  <RestApiDocsViewer
                    apiSpec={apiData.apiConfig.spec}
                    onExampleChange={handleRestApiExampleChange}
                  />
                ) : (
                  <EmptyState description="暂无OpenAPI规范" />
                )}
              </div>
            ),
            key: 'openapi-spec',
            label: (
              <span className="flex items-center gap-1.5 font-semibold">
                <ApiOutlined className="text-sm" />
                OpenAPI 规范
              </span>
            ),
          },
        ]}
        size="large"
      />
    </div>
  ) : null;

  const rightContent = (
    <div className="bg-white/60 backdrop-blur-sm rounded-[10px] border border-white/40 p-6">
      <div className="mb-4 flex items-center justify-between gap-3">
        <span className="text-xs font-medium uppercase tracking-wider text-gray-500">调用示例</span>
        <div className="flex shrink-0 items-center gap-1">
          <Button
            icon={<DownloadOutlined />}
            onClick={() => handleDownloadSpec('yaml')}
            size="small"
            title="下载 YAML"
          >
            YAML
          </Button>
          <Button
            icon={<DownloadOutlined />}
            onClick={() => handleDownloadSpec('json')}
            size="small"
            title="下载 JSON"
          >
            JSON
          </Button>
        </div>
      </div>

      <div className="relative">
        <pre className="m-0 overflow-x-auto whitespace-pre-wrap rounded-[10px] border border-gray-700 bg-gray-900 p-4 pr-11 text-xs leading-6 text-gray-100">
          <code>{curlCommand}</code>
        </pre>
        <Button
          className="absolute right-2 top-2 text-gray-400 hover:text-white"
          icon={<CopyOutlined />}
          onClick={handleCopyCurlCommand}
          size="small"
          title="复制 cURL"
          type="text"
        />
      </div>
    </div>
  );

  return (
    <ProductDetailLayout
      error={error || undefined}
      headerProps={
        apiData
          ? {
              defaultIcon: '/logo.svg',
              description: apiData.description,
              icon: apiData.icon,
              name: apiData.name,
              productType: 'REST_API',
              subscribable: apiData.subscribable,
              updatedAt: apiData.updatedAt,
            }
          : undefined
      }
      leftContent={leftContent}
      loading={!apiData && !error}
      rightContent={rightContent}
    />
  );
}

export default ApiDetailPage;

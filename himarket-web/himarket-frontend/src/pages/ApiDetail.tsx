import { CopyOutlined, DownloadOutlined } from '@ant-design/icons';
import { Tabs, Space, Button, message } from 'antd';
import * as yaml from 'js-yaml';
import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';

import MarkdownRender from '../components/MarkdownRender';
import { ProductDetailLayout } from '../components/ProductDetailLayout';
import { SwaggerUIWrapper } from '../components/SwaggerUIWrapper';
import APIs from '../lib/apis';

import type { IProductDetail } from '../lib/apis';

function ApiDetailPage() {
  const { apiProductId } = useParams();
  const [, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [apiData, setApiData] = useState<IProductDetail>();
  const [baseUrl, setBaseUrl] = useState<string>('');
  const [examplePath, setExamplePath] = useState<string>('/{path}');
  const [exampleMethod, setExampleMethod] = useState<string>('GET');
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
            if (serverUrl && serverUrl.endsWith('/')) {
              serverUrl = serverUrl.slice(0, -1); // 移除末尾的斜杠
            }
            setBaseUrl(serverUrl);

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
                      setExamplePath(firstPath);
                      setExampleMethod(firstMethod);
                    }
                  }
                }
              }
            }
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

  const leftContent = apiData ? (
    <div className="bg-white/60 backdrop-blur-sm rounded-2xl border border-white/40 p-6 pt-0">
      <Tabs
        defaultActiveKey="overview"
        items={[
          {
            children: apiData.document ? (
              <div className="min-h-[400px] prose prose-lg">
                <MarkdownRender content={apiData.document} />
              </div>
            ) : (
              <div className="text-gray-500 text-center py-16">暂无概览信息</div>
            ),
            key: 'overview',
            label: '概览',
          },
          {
            children: (
              <div>
                {apiData.apiConfig && apiData.apiConfig.spec ? (
                  <SwaggerUIWrapper apiSpec={apiData.apiConfig.spec} />
                ) : (
                  <div className="text-gray-500 text-center py-16">暂无OpenAPI规范</div>
                )}
              </div>
            ),
            key: 'openapi-spec',
            label: 'OpenAPI 规范',
          },
        ]}
        size="large"
      />
    </div>
  ) : null;

  const rightContent = (
    <div className="bg-white/60 backdrop-blur-sm rounded-2xl border border-white/40 p-6">
      <h3 className="text-base font-semibold mb-4 text-gray-900">快速开始</h3>
      <Tabs
        defaultActiveKey="curl"
        items={[
          {
            children: (
              <div className="space-y-4">
                {/* cURL示例 */}
                <div className="relative">
                  <pre className="bg-gray-900 text-gray-100 p-4 rounded-xl text-xs overflow-x-auto whitespace-pre-wrap border border-gray-700">
                    <code>{`curl -X ${exampleMethod} \\
  '${baseUrl || 'https://api.example.com'}${examplePath}' \\
  -H 'Accept: application/json' \\
  -H 'Content-Type: application/json'`}</code>
                  </pre>
                  <Button
                    className="absolute top-2 right-2 text-gray-400 hover:text-white"
                    icon={<CopyOutlined />}
                    onClick={() => {
                      const curlCommand = `curl -X ${exampleMethod} \\
  '${baseUrl || 'https://api.example.com'}${examplePath}' \\
  -H 'Accept: application/json' \\
  -H 'Content-Type: application/json'`;
                      navigator.clipboard.writeText(curlCommand);
                      message.success('cURL命令已复制到剪贴板', 1);
                    }}
                    size="small"
                    type="text"
                  />
                </div>
              </div>
            ),
            key: 'curl',
            label: 'cURL',
          },
          {
            children: (
              <div className="space-y-4">
                <div className="text-xs text-gray-500 mb-3">
                  下载完整的OpenAPI规范文件，用于代码生成、API测试等场景
                </div>
                <Space className="w-full" direction="vertical">
                  <Button
                    block
                    icon={<DownloadOutlined />}
                    onClick={() => {
                      if (apiData?.apiConfig?.spec) {
                        const blob = new Blob([apiData.apiConfig.spec], { type: 'text/yaml' });
                        const url = URL.createObjectURL(blob);
                        const link = document.createElement('a');
                        link.href = url;
                        link.download = `${apiData.name || 'api'}-openapi.yaml`;
                        document.body.appendChild(link);
                        link.click();
                        document.body.removeChild(link);
                        URL.revokeObjectURL(url);
                        message.success('OpenAPI规范文件下载成功', 1);
                      }
                    }}
                    type="primary"
                  >
                    下载 YAML
                  </Button>
                  <Button
                    block
                    icon={<DownloadOutlined />}
                    onClick={() => {
                      if (apiData?.apiConfig?.spec) {
                        try {
                          const yamlDoc = yaml.load(apiData.apiConfig.spec);
                          const jsonSpec = JSON.stringify(yamlDoc, null, 2);
                          const blob = new Blob([jsonSpec], { type: 'application/json' });
                          const url = URL.createObjectURL(blob);
                          const link = document.createElement('a');
                          link.href = url;
                          link.download = `${apiData.name || 'api'}-openapi.json`;
                          document.body.appendChild(link);
                          link.click();
                          document.body.removeChild(link);
                          URL.revokeObjectURL(url);
                          message.success('OpenAPI规范文件下载成功', 1);
                        } catch (err) {
                          console.warn(err);
                          message.error('转换JSON格式失败');
                        }
                      }
                    }}
                  >
                    下载 JSON
                  </Button>
                </Space>
              </div>
            ),
            key: 'download',
            label: '下载',
          },
        ]}
      />
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

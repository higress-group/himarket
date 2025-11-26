import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { Card, Alert, Row, Col, Tabs } from "antd";
import { Layout } from "../components/Layout";
import { ProductHeader } from "../components/ProductHeader";
import { SwaggerUIWrapper } from "../components/SwaggerUIWrapper";
import ReactMarkdown from "react-markdown";
import remarkGfm from 'remark-gfm';
import 'react-markdown-editor-lite/lib/index.css';
import * as yaml from 'js-yaml';
import { Button, Typography, Space, Divider, message } from "antd";
import { CopyOutlined, RocketOutlined, DownloadOutlined } from "@ant-design/icons";
import type { IProductDetail } from "../lib/apis";
import APIs from "../lib/apis";

const { Title, Paragraph } = Typography;


function ApiDetailPage() {
  const { apiProductId } = useParams();
  const [, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [apiData, setApiData] = useState<IProductDetail>();
  const [baseUrl, setBaseUrl] = useState<string>('');
  const [examplePath, setExamplePath] = useState<string>('/{path}');
  const [exampleMethod, setExampleMethod] = useState<string>('GET');

  useEffect(() => {
    if (!apiProductId) return;
    fetchApiDetail();
  }, [apiProductId]);

  const fetchApiDetail = async () => {
    setLoading(true);
    setError('');
    if (!apiProductId) return;
    try {
      const response = await APIs.getProduct({ id: apiProductId });
      if (response.code === "SUCCESS" && response.data) {
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
              const pathEntries = Object.entries(paths);
              if (pathEntries.length > 0) {
                const [firstPath, pathMethods] = pathEntries[0];
                if (pathMethods && typeof pathMethods === 'object') {
                  const methods = Object.keys(pathMethods);
                  if (methods.length > 0) {
                    const firstMethod = methods[0].toUpperCase();
                    setExamplePath(firstPath);
                    setExampleMethod(firstMethod);
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
  };




  if (error) {
    return (
      <Layout>
        <Alert message={error} type="error" showIcon className="my-8" />
      </Layout>
    );
  }

  if (!apiData) {
    return (
      <Layout>
        <div className="flex justify-center items-center h-64">
          <div>Loading...</div>
        </div>
      </Layout>
    );
  }

  return (
    <Layout>
      <div className="mb-6">
        <ProductHeader
          name={apiData.name}
          description={apiData.description}
          icon={apiData.icon}
          defaultIcon="/logo.svg"
          updatedAt={apiData.updatedAt}
          productType="REST_API"
        />
        <hr className="border-gray-200 mt-4" />
      </div>

      {/* 主要内容区域 - 左右布局 */}
      <Row gutter={24}>
        {/* 左侧内容 */}
        <Col span={15}>
          <Card className="mb-6 rounded-lg border-gray-200">
            <Tabs
              defaultActiveKey="overview"
              items={[
                {
                  key: "overview",
                  label: "Overview",
                  children: apiData.document ? (
                    <div className="min-h-[400px]">
                      <div
                        className="prose prose-lg max-w-none"
                        style={{
                          lineHeight: '1.7',
                          color: '#374151',
                          fontSize: '16px',
                          fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif'
                        }}
                      >
                        <style>{`
                          .prose h1 {
                            color: #111827;
                            font-weight: 700;
                            font-size: 2.25rem;
                            line-height: 1.2;
                            margin-top: 0;
                            margin-bottom: 1.5rem;
                            border-bottom: 2px solid #e5e7eb;
                            padding-bottom: 0.5rem;
                          }
                          .prose h2 {
                            color: #1f2937;
                            font-weight: 600;
                            font-size: 1.875rem;
                            line-height: 1.3;
                            margin-top: 2rem;
                            margin-bottom: 1rem;
                            border-bottom: 1px solid #e5e7eb;
                            padding-bottom: 0.25rem;
                          }
                          .prose h3 {
                            color: #374151;
                            font-weight: 600;
                            font-size: 1.5rem;
                            margin-top: 1.5rem;
                            margin-bottom: 0.75rem;
                          }
                          .prose p {
                            margin-bottom: 1.25rem;
                            color: #4b5563;
                            line-height: 1.7;
                            font-size: 16px;
                          }
                          .prose code {
                            background-color: #f3f4f6;
                            border: 1px solid #e5e7eb;
                            border-radius: 0.375rem;
                            padding: 0.125rem 0.375rem;
                            font-size: 0.875rem;
                            color: #374151;
                            font-weight: 500;
                          }
                          .prose pre {
                            background-color: #1f2937;
                            border-radius: 0.5rem;
                            padding: 1.25rem;
                            overflow-x: auto;
                            margin: 1.5rem 0;
                            border: 1px solid #374151;
                          }
                          .prose pre code {
                            background-color: transparent;
                            border: none;
                            color: #f9fafb;
                            padding: 0;
                            font-size: 0.875rem;
                            font-weight: normal;
                          }
                          .prose blockquote {
                            border-left: 4px solid #3b82f6;
                            padding-left: 1rem;
                            margin: 1.5rem 0;
                            color: #6b7280;
                            font-style: italic;
                            background-color: #f8fafc;
                            padding: 1rem;
                            border-radius: 0.375rem;
                            font-size: 16px;
                          }
                          .prose ul, .prose ol {
                            margin: 1.25rem 0;
                            padding-left: 1.5rem;
                          }
                          .prose ol {
                            list-style-type: decimal;
                            list-style-position: outside;
                          }
                          .prose ul {
                            list-style-type: disc;
                            list-style-position: outside;
                          }
                          .prose li {
                            margin: 0.5rem 0;
                            color: #4b5563;
                            display: list-item;
                            font-size: 16px;
                          }
                          .prose ol li {
                            padding-left: 0.25rem;
                          }
                          .prose ul li {
                            padding-left: 0.25rem;
                          }
                          .prose table {
                            width: 100%;
                            border-collapse: collapse;
                            margin: 1.5rem 0;
                            font-size: 16px;
                          }
                          .prose th, .prose td {
                            border: 1px solid #d1d5db;
                            padding: 0.75rem;
                            text-align: left;
                          }
                          .prose th {
                            background-color: #f9fafb;
                            font-weight: 600;
                            color: #374151;
                            font-size: 16px;
                          }
                          .prose td {
                            color: #4b5563;
                            font-size: 16px;
                          }
                          .prose a {
                            color: #3b82f6;
                            text-decoration: underline;
                            font-weight: 500;
                            transition: color 0.2s;
                            font-size: inherit;
                          }
                          .prose a:hover {
                            color: #1d4ed8;
                          }
                          .prose strong {
                            color: #111827;
                            font-weight: 600;
                            font-size: inherit;
                          }
                          .prose em {
                            color: #6b7280;
                            font-style: italic;
                            font-size: inherit;
                          }
                          .prose hr {
                            border: none;
                            height: 1px;
                            background-color: #e5e7eb;
                            margin: 2rem 0;
                          }
                        `}</style>
                        <ReactMarkdown remarkPlugins={[remarkGfm]}>{apiData.document}</ReactMarkdown>
                      </div>
                    </div>
                  ) : (
                    <div className="text-gray-500 text-center py-8">
                      暂无文档内容
                    </div>
                  ),
                },
                {
                  key: "openapi-spec",
                  label: "OpenAPI Specification",
                  children: (
                    <div>
                      {apiData.apiConfig && apiData.apiConfig.spec ? (
                        <SwaggerUIWrapper apiSpec={apiData.apiConfig.spec} />
                      ) : (
                        <div className="text-gray-500 text-center py-8">
                          暂无OpenAPI规范
                        </div>
                      )}
                    </div>
                  ),
                },
              ]}
            />
          </Card>
        </Col>

        {/* 右侧内容 */}
        <Col span={9}>
          <Card
            className="rounded-lg border-gray-200"
            title={
              <Space>
                <RocketOutlined />
                <span>快速开始</span>
              </Space>
            }>
            <Space direction="vertical" className="w-full" size="middle">
              {/* cURL示例 */}
              <div>
                <Title level={5}>cURL调用示例</Title>
                <div className="bg-gray-50 p-3 rounded border relative">
                  <pre className="text-sm mb-0">
                    {`curl -X ${exampleMethod} \\
  '${baseUrl || 'https://api.example.com'}${examplePath}' \\
  -H 'Accept: application/json' \\
  -H 'Content-Type: application/json'`}
                  </pre>
                  <Button
                    type="text"
                    size="small"
                    icon={<CopyOutlined />}
                    className="absolute top-2 right-2"
                    onClick={() => {
                      const curlCommand = `curl -X ${exampleMethod} \\\n  '${baseUrl || 'https://api.example.com'}${examplePath}' \\\n  -H 'Accept: application/json' \\\n  -H 'Content-Type: application/json'`;
                      navigator.clipboard.writeText(curlCommand);
                      message.success('cURL命令已复制到剪贴板', 1);
                    }}
                  />
                </div>
              </div>

              <Divider />

              {/* 下载OAS文件 */}
              <div>
                <Title level={5}>OpenAPI规范文件</Title>
                <Paragraph type="secondary">
                  下载完整的OpenAPI规范文件，用于代码生成、API测试等场景
                </Paragraph>
                <Space>
                  <Button
                    type="primary"
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
                  >
                    下载YAML
                  </Button>
                  <Button
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
                          console.log(err)
                          message.error('转换JSON格式失败');
                        }
                      }
                    }}
                  >
                    下载JSON
                  </Button>
                </Space>
              </div>
            </Space>
          </Card>
        </Col>
      </Row>

    </Layout>
  );
}

export default ApiDetailPage; 
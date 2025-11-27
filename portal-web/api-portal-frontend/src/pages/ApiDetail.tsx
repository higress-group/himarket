import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { Card, Alert, Row, Col, Tabs } from "antd";
import { Layout } from "../components/Layout";
import { ProductHeader } from "../components/ProductHeader";
import { SwaggerUIWrapper } from "../components/SwaggerUIWrapper";
import 'react-markdown-editor-lite/lib/index.css';
import * as yaml from 'js-yaml';
import { Button, Typography, Space, Divider, message } from "antd";
import { CopyOutlined, RocketOutlined, DownloadOutlined } from "@ant-design/icons";
import type { IProductDetail } from "../lib/apis";
import APIs from "../lib/apis";
import MarkdownRender from "../components/MarkdownRender";

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

      <div className="pb-6">
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
                        <div className="prose prose-lg max-w-none">
                          <MarkdownRender content={apiData.document} />
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
      </div>
    </Layout>
  );
}

export default ApiDetailPage; 
import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Alert, Tabs, Space, Button, message } from "antd";
import { Layout } from "../components/Layout";
import { ProductHeader } from "../components/ProductHeader";
import { SwaggerUIWrapper } from "../components/SwaggerUIWrapper";
import * as yaml from 'js-yaml';
import { CopyOutlined, DownloadOutlined, ArrowLeftOutlined } from "@ant-design/icons";
import type { IProductDetail } from "../lib/apis";
import APIs from "../lib/apis";
import MarkdownRender from "../components/MarkdownRender";


function ApiDetailPage() {
  const { apiProductId } = useParams();
  const [, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [apiData, setApiData] = useState<IProductDetail>();
  const [baseUrl, setBaseUrl] = useState<string>('');
  const [examplePath, setExamplePath] = useState<string>('/{path}');
  const [exampleMethod, setExampleMethod] = useState<string>('GET');
  const navigate = useNavigate();

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
      {/* 头部 */}
      <div className="mb-8">
        {/* 返回按钮 */}
        <button
          onClick={() => navigate(-1)}
          className="
            flex items-center gap-2 mb-4 px-4 py-2 rounded-xl
            text-gray-600 hover:text-colorPrimary
            hover:bg-colorPrimaryBgHover
            transition-all duration-200
          "
        >
          <ArrowLeftOutlined />
          <span>返回</span>
        </button>

        <ProductHeader
          name={apiData.name}
          description={apiData.description}
          icon={apiData.icon}
          defaultIcon="/logo.svg"
          updatedAt={apiData.updatedAt}
          productType="REST_API"
        />
      </div>

      {/* 主要内容区域 */}
      <div className="flex flex-col lg:flex-row gap-6">
        {/* 左侧内容 */}
        <div className="w-full lg:w-[65%] order-2 lg:order-1">
          <div className="bg-white/60 backdrop-blur-sm rounded-2xl border border-white/40 p-6 pt-0">
            <Tabs
              size="large"
              defaultActiveKey="overview"
              items={[
                {
                  key: "overview",
                  label: "概览",
                  children: apiData.document ? (
                    <div className="min-h-[400px] prose prose-lg">
                      <MarkdownRender content={apiData.document} />
                    </div>
                  ) : (
                    <div className="text-gray-500 text-center py-16">
                      暂无概览信息
                    </div>
                  ),
                },
                {
                  key: "openapi-spec",
                  label: "OpenAPI 规范",
                  children: (
                    <div>
                      {apiData.apiConfig && apiData.apiConfig.spec ? (
                        <SwaggerUIWrapper apiSpec={apiData.apiConfig.spec} />
                      ) : (
                        <div className="text-gray-500 text-center py-16">
                          暂无OpenAPI规范
                        </div>
                      )}
                    </div>
                  ),
                },
              ]}
            />
          </div>
        </div>

        {/* 右侧内容 */}
        <div className="w-full lg:w-[35%] order-1 lg:order-2">
          <div className="bg-white/60 backdrop-blur-sm rounded-2xl border border-white/40 p-6">
            <h3 className="text-base font-semibold mb-4 text-gray-900">快速开始</h3>
            <Tabs
              defaultActiveKey="curl"
              items={[
                {
                  key: "curl",
                  label: "cURL",
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
                          type="text"
                          size="small"
                          icon={<CopyOutlined />}
                          className="absolute top-2 right-2 text-gray-400 hover:text-white"
                          onClick={() => {
                            const curlCommand = `curl -X ${exampleMethod} \\
  '${baseUrl || 'https://api.example.com'}${examplePath}' \\
  -H 'Accept: application/json' \\
  -H 'Content-Type: application/json'`;
                            navigator.clipboard.writeText(curlCommand);
                            message.success('cURL命令已复制到剪贴板', 1);
                          }}
                        />
                      </div>
                    </div>
                  ),
                },
                {
                  key: "download",
                  label: "下载",
                  children: (
                    <div className="space-y-4">
                      <div className="text-xs text-gray-500 mb-3">
                        下载完整的OpenAPI规范文件，用于代码生成、API测试等场景
                      </div>
                      <Space direction="vertical" className="w-full">
                        <Button
                          block
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
                                console.log(err)
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
                },
              ]}
            />
          </div>
        </div>
      </div>
    </Layout>
  );
}

export default ApiDetailPage; 
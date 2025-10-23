import { useEffect, useState, useCallback } from "react";
import { useParams } from "react-router-dom";
import api from "../lib/api";
import { Layout } from "../components/Layout";
import { ProductHeader } from "../components/ProductHeader";
import {
  Card,
  Alert,
  Button,
  message,
  Tabs,
  Row,
  Col,
  Collapse,
} from "antd";
import { CopyOutlined } from "@ant-design/icons";
import ReactMarkdown from "react-markdown";
import { ProductType } from "../types";
import type {
  Product,
  McpConfig,
  McpServerProduct,
  ApiResponse,
} from "../types";
import * as yaml from "js-yaml";
import remarkGfm from 'remark-gfm';
import 'react-markdown-editor-lite/lib/index.css'

function McpDetail() {
  const { mcpName } = useParams();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [data, setData] = useState<Product | null>(null);
  const [mcpConfig, setMcpConfig] = useState<McpConfig | null>(null);
  const [parsedTools, setParsedTools] = useState<
    Array<{
      name: string;
      description: string;
      args?: Array<{
        name: string;
        description: string;
        type: string;
        required: boolean;
        position: string;
        default?: string;
        enum?: string[];
      }>;
    }>
  >([]);
  const [httpJson, setHttpJson] = useState("");
  const [sseJson, setSseJson] = useState("");
  const [localJson, setLocalJson] = useState("");

  // 解析YAML配置的函数
  const parseYamlConfig = (
    yamlString: string
  ): {
    tools?: Array<{
      name: string;
      description: string;
      args?: Array<{
        name: string;
        description: string;
        type: string;
        required: boolean;
        position: string;
        default?: string;
        enum?: string[];
      }>;
    }>;
  } | null => {
    try {
      const parsed = yaml.load(yamlString) as {
        tools?: Array<{
          name: string;
          description: string;
          args?: Array<{
            name: string;
            description: string;
            type: string;
            required: boolean;
            position: string;
            default?: string;
            enum?: string[];
          }>;
        }>;
      };
      return parsed;
    } catch (error) {
      console.warn("解析YAML配置失败:", error);
      return null;
    }
  };

  // 格式化域名端口
  const formatDomainWithPort = (domainStr: string, protocol: string) => {
    const [host, port] = domainStr.split(':');
    if (!port) return domainStr;
    
    // 隐藏 HTTP 默认端口 80
    if (protocol === 'http' && port === '80') return host;
    // 隐藏 HTTPS 默认端口 443
    if (protocol === 'https' && port === '443') return host;
    
    return domainStr;
  };

  // 生成连接配置的函数
  const generateConnectionConfig = useCallback((
    domains: Array<{ domain: string; protocol: string }> | null | undefined,
    path: string | null | undefined,
    serverName: string,
    localConfig?: unknown,
    protocolType?: string
  ) => {
    // 互斥：优先判断本地模式
    if (localConfig) {
      const localConfigJson = JSON.stringify(localConfig, null, 2);
      setLocalJson(localConfigJson);
      setHttpJson("");
      setSseJson("");
      return;
    }

    // HTTP/SSE 模式
    if (domains && domains.length > 0 && path) {
      const domain = domains[0];
      const formattedDomain = formatDomainWithPort(domain.domain, domain.protocol);
      const baseUrl = `${domain.protocol}://${formattedDomain}`;
      let endpoint = `${baseUrl}${path}`;

      if (mcpConfig?.meta?.source === 'ADP_AI_GATEWAY' || mcpConfig?.meta?.source === 'APSARA_GATEWAY') {
        endpoint = `${baseUrl}/mcp-servers${path}`;
      }

      if (protocolType === 'SSE') {
        // 仅生成SSE配置，不追加/sse
        const sseConfig = `{
  "mcpServers": {
    "${serverName}": {
      "type": "sse",
      "url": "${endpoint}"
    }
  }
}`;
        setSseJson(sseConfig);
        setHttpJson("");
        setLocalJson("");
        return;
      } else if (protocolType === 'StreamableHTTP') {
        // 仅生成HTTP配置
        const httpConfig = `{
  "mcpServers": {
    "${serverName}": {
      "url": "${endpoint}"
    }
  }
}`;
        setHttpJson(httpConfig);
        setSseJson("");
        setLocalJson("");
        return;
      } else {
        // protocol为null或其他值：生成两种配置
        const httpConfig = `{
  "mcpServers": {
    "${serverName}": {
      "url": "${endpoint}"
    }
  }
}`;

        const sseConfig = `{
  "mcpServers": {
    "${serverName}": {
      "type": "sse",
      "url": "${endpoint}/sse"
    }
  }
}`;

        setHttpJson(httpConfig);
        setSseJson(sseConfig);
        setLocalJson("");
        return;
      }
    }

    // 无有效配置
    setHttpJson("");
    setSseJson("");
    setLocalJson("");
  }, [mcpConfig]);

  useEffect(() => {
    const fetchDetail = async () => {
      if (!mcpName) {
        return;
      }
      setLoading(true);
      setError("");
      try {
        const response: ApiResponse<Product> = await api.get(`/products/${mcpName}`);
        if (response.code === "SUCCESS" && response.data) {
          setData(response.data);

          // 处理MCP配置（统一使用新结构 mcpConfig）
          if (response.data.type === ProductType.MCP_SERVER) {
            const mcpProduct = response.data as McpServerProduct;

            if (mcpProduct.mcpConfig) {
              setMcpConfig(mcpProduct.mcpConfig);

              // 解析tools配置
              if (mcpProduct.mcpConfig.tools) {
                const parsedConfig = parseYamlConfig(
                  mcpProduct.mcpConfig.tools
                );
                if (parsedConfig && parsedConfig.tools) {
                  setParsedTools(parsedConfig.tools);
                }
              }
            }
          }
        } else {
          setError(response.message || "数据加载失败");
        }
      } catch (error) {
        console.error("API请求失败:", error);
        setError("加载失败，请稍后重试");
      } finally {
        setLoading(false);
      }
    };
    fetchDetail();
  }, [mcpName]);

  // 监听 mcpConfig 变化，重新生成连接配置
  useEffect(() => {
    if (mcpConfig) {
      generateConnectionConfig(
        mcpConfig.mcpServerConfig.domains,
        mcpConfig.mcpServerConfig.path,
        mcpConfig.mcpServerName,
        mcpConfig.mcpServerConfig.rawConfig,
(mcpConfig.meta as any)?.protocol
      );
    }
  }, [mcpConfig, generateConnectionConfig]);

  const handleCopy = async (text: string) => {
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(text);
      } else {
        // 非安全上下文降级处理
        const textarea = document.createElement("textarea");
        textarea.value = text;
        textarea.style.position = "fixed";
        document.body.appendChild(textarea);
        textarea.focus();
        textarea.select();
        document.execCommand("copy");
        document.body.removeChild(textarea);
      }
      message.success("已复制到剪贴板", 1);
    } catch {
      message.error("复制失败，请手动复制");
    }
  };


  if (error) {
    return (
      <Layout loading={loading}>
        <Alert message={error} type="error" showIcon className="my-8" />
      </Layout>
    );
  }
  if (!data) {
    return (
      <Layout loading={loading}>
        <Alert
          message="未找到相关数据"
          type="warning"
          showIcon
          className="my-8"
        />
      </Layout>
    );
  }

  const { name, description } = data;
  const hasLocalConfig = Boolean(mcpConfig?.mcpServerConfig.rawConfig);



  return (
    <Layout loading={loading}>
      <div className="mb-6">
        <ProductHeader
          name={name}
          description={description}
          icon={data.icon}
          defaultIcon="/MCP.svg"
          mcpConfig={mcpConfig}
          updatedAt={data.updatedAt}
          productType="MCP_SERVER"
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
                  children: data.document ? (
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
                        <ReactMarkdown remarkPlugins={[remarkGfm]}>{data.document}</ReactMarkdown>
                      </div>
                    </div>
                  ) : (
                    <div className="text-gray-500 text-center py-8">
                      No overview available
                    </div>
                  ),
                },
                {
                  key: "tools",
                  label: `Tools (${parsedTools.length})`,
                  children: parsedTools.length > 0 ? (
                    <div className="border border-gray-200 rounded-lg bg-gray-50">
                      {parsedTools.map((tool, idx) => (
                        <div key={idx} className={idx < parsedTools.length - 1 ? "border-b border-gray-200" : ""}>
                          <Collapse
                            ghost
                            expandIconPosition="end"
                            items={[{
                              key: idx.toString(),
                              label: tool.name,
                              children: (
                                <div className="px-4 pb-2">
                                  <div className="text-gray-600 mb-4">{tool.description}</div>
                                  
                                  {tool.args && tool.args.length > 0 && (
                                    <div>
                                      <p className="font-medium text-gray-700 mb-3">输入参数:</p>
                                      {tool.args.map((arg, argIdx) => (
                                        <div key={argIdx} className="mb-3">
                                          <div className="flex items-center mb-2">
                                            <span className="font-medium text-gray-800 mr-2">{arg.name}</span>
                                            <span className="text-xs bg-gray-200 text-gray-600 px-2 py-1 rounded mr-2">
                                              {arg.type}
                                            </span>
                                            {arg.required && (
                                              <span className="text-red-500 text-xs mr-2">*</span>
                                            )}
                                            {arg.description && (
                                              <span className="text-xs text-gray-500">
                                                {arg.description}
                                              </span>
                                            )}
                                          </div>
                                          <input
                                            type="text"
                                            placeholder={arg.description || `请输入${arg.name}`}
                                            className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                                          />
                                        </div>
                                      ))}
                                    </div>
                                  )}
                                  
                                  {(!tool.args || tool.args.length === 0) && (
                                    <div className="text-gray-500 text-sm">No parameters required</div>
                                  )}
                                </div>
                              ),
                            }]}
                          />
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="text-gray-500 text-center py-8">
                      No tools available
                    </div>
                  ),
                },
              ]}
            />
          </Card>
        </Col>

        {/* 右侧连接指导 */}
        <Col span={9}>
          {mcpConfig && (
            <Card className="mb-6 rounded-lg border-gray-200">
              <div className="mb-4">
                <h3 className="text-sm font-semibold mb-3">连接点配置</h3>
                <Tabs
                  size="small" 
                  defaultActiveKey={hasLocalConfig ? "local" : (sseJson ? "sse" : "http")}
                  items={(() => {
                    const tabs = [];
                    
                    if (hasLocalConfig) {
                      tabs.push({
                        key: "local",
                        label: "Stdio",
                        children: (
                          <div className="relative bg-gray-50 border border-gray-200 rounded-md p-3">
                            <Button
                              type="text"
                              size="small"
                              icon={<CopyOutlined />}
                              className="absolute top-2 right-2 z-10"
                              onClick={() => handleCopy(localJson)}
                            />
                            <div className="text-gray-800 font-mono text-xs overflow-x-auto">
                              <pre className="whitespace-pre-wrap">{localJson}</pre>
                            </div>
                          </div>
                        ),
                      });
                    } else {
                      if (sseJson) {
                        tabs.push({
                          key: "sse",
                          label: "SSE",
                          children: (
                            <div className="relative bg-gray-50 border border-gray-200 rounded-md p-3">
                              <Button
                                type="text"
                                size="small"
                                icon={<CopyOutlined />}
                                className="absolute top-2 right-2 z-10"
                                onClick={() => handleCopy(sseJson)}
                              />
                              <div className="text-gray-800 font-mono text-xs overflow-x-auto">
                                <pre className="whitespace-pre-wrap">{sseJson}</pre>
                              </div>
                            </div>
                          ),
                        });
                      }
                      
                      if (httpJson) {
                        tabs.push({
                          key: "http",
                          label: "Streaming HTTP",
                          children: (
                            <div className="relative bg-gray-50 border border-gray-200 rounded-md p-3">
                              <Button
                                type="text"
                                size="small"
                                icon={<CopyOutlined />}
                                className="absolute top-2 right-2 z-10"
                                onClick={() => handleCopy(httpJson)}
                              />
                              <div className="text-gray-800 font-mono text-xs overflow-x-auto">
                                <pre className="whitespace-pre-wrap">{httpJson}</pre>
                              </div>
                            </div>
                          ),
                        });
                      }
                    }
                    
                    return tabs;
                  })()}
                />
              </div>
            </Card>
          )}
        </Col>
      </Row>
    </Layout>
  );
}

export default McpDetail;

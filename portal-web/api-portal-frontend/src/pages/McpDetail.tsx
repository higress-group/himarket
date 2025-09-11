import { useEffect, useState, useCallback } from "react";
import { useParams } from "react-router-dom";
import api from "../lib/api";
import { Layout } from "../components/Layout";
import { ProductHeader } from "../components/ProductHeader";
import {
  Card,
  Descriptions,
  Alert,
  Collapse,
  Button,
  message,
  Tabs,
  Row,
  Col,
} from "antd";
import MonacoEditor from "react-monaco-editor";
import ReactMarkdown from "react-markdown";
import { ProductType } from "../types";
import type {
  Product,
  McpConfig,
  McpServerProduct,
  ApiResponse,
} from "../types";
import * as yaml from "js-yaml";
import { 
  FromTypeMap, 
  SourceMap 
} from "../lib/statusUtils";
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

  // 生成连接配置的函数
  const generateConnectionConfig = useCallback((
    domains: Array<{ domain: string; protocol: string }> | null | undefined,
    path: string | null | undefined,
    serverName: string,
    localConfig?: unknown
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
      const baseUrl = `${domain.protocol}://${domain.domain}`;
      let endpoint = `${baseUrl}${path}`;

      if (mcpConfig?.meta?.source === 'ADP_AI_GATEWAY') {
        endpoint = `${baseUrl}/mcp-servers${path}`;
      }

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
      "url": "${endpoint}/sse"
    }
  }
}`;

      setHttpJson(httpConfig);
      setSseJson(sseConfig);
      setLocalJson("");
      return;
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
        mcpConfig.mcpServerConfig.rawConfig
      );
    }
  }, [mcpConfig]);

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
      message.success("已复制到剪贴板");
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

  const { name, description, status, category, enableConsumerAuth } = data;
  const currentMcpConfig = mcpConfig;
  const hasLocalConfig = Boolean(mcpConfig?.mcpServerConfig.rawConfig);



  return (
    <Layout loading={loading}>
      <ProductHeader
        name={name}
        description={description}
        status={status}
        category={category}
        icon={data.icon || "/MCP.png"}
        defaultIcon="/MCP.png"
        enableConsumerAuth={enableConsumerAuth || undefined}
        showConsumerAuth={true}
        mcpConfig={mcpConfig}
      />

      {/* 主要内容区域 - 左右布局 */}
      <Row gutter={24}>
        {/* 左侧内容 */}
        <Col span={16}>
          <Card className="mb-6">
            <Tabs
              defaultActiveKey="overview"
              items={[
                {
                  key: "overview",
                  label: "Overview",
                  children: data.document ? (
                    <div className="prose custom-html-style h-[500px] overflow-auto">
                      <ReactMarkdown remarkPlugins={[remarkGfm]}>{data.document}</ReactMarkdown>
                    </div>
                  ) : (
                    <div className="text-gray-500 text-center py-8">
                      暂无文档内容
                    </div>
                  ),
                },
                {
                  key: "config",
                  label: "MCP Config",
                  children: currentMcpConfig ? (
                    <div>
                      <Descriptions column={1} bordered size="small">
                        <Descriptions.Item label="MCP Server Name">
                          {mcpConfig?.mcpServerName || "无"}
                        </Descriptions.Item>
                        <Descriptions.Item label="来源类型">
                          {mcpConfig?.meta?.fromType
                            ? FromTypeMap[mcpConfig.meta.fromType] ||
                              mcpConfig.meta.fromType
                            : "无"}
                        </Descriptions.Item>
                        <Descriptions.Item label="来源">
                          {mcpConfig?.meta?.source
                            ? SourceMap[mcpConfig.meta.source] ||
                              mcpConfig.meta.source
                            : "无"}
                        </Descriptions.Item>
                        <Descriptions.Item label="路径">
                          {mcpConfig?.mcpServerConfig.path || "无"}
                        </Descriptions.Item>
                        <Descriptions.Item label="运行模式">
                          {hasLocalConfig ? "Local Mode" : "SSE/HTTP Mode"}
                        </Descriptions.Item>
                        <Descriptions.Item label="域名配置">
                          {mcpConfig?.mcpServerConfig.domains &&
                          mcpConfig.mcpServerConfig.domains.length > 0
                            ? mcpConfig.mcpServerConfig.domains.map(
                                (
                                  d: { domain: string; protocol: string },
                                  i: number
                                ) => (
                                  <div key={i} className="mb-2">
                                    <div>
                                      <strong>域名/IP:</strong> {d.domain}
                                    </div>
                                    <div>
                                      <strong>协议:</strong> {d.protocol}
                                    </div>
                                  </div>
                                )
                              )
                            : "无"}
                        </Descriptions.Item>
                      </Descriptions>
                    </div>
                  ) : (
                    <div className="text-gray-500 text-center py-8">
                      暂无MCP配置信息
                    </div>
                  ),
                },
                                  {
                    key: "tools",
                    label: `Tools (${parsedTools.length})`,
                    children:
                      parsedTools.length > 0 ? (
                        <Collapse accordion>
                          {parsedTools.map((tool, idx) => (
                          <Collapse.Panel header={tool.name} key={idx}>
                            <div className="mb-2 text-gray-600">
                              {tool.description}
                            </div>
                            <div className="mb-2 font-bold">输入参数：</div>
                            <div className="space-y-2">
                              {tool.args?.map(
                                (
                                  arg: {
                                    name: string;
                                    description: string;
                                    type: string;
                                    required: boolean;
                                    position: string;
                                    default?: string;
                                    enum?: string[];
                                  },
                                  argIdx: number
                                ) => (
                                  <div
                                    key={argIdx}
                                    className="flex flex-col mb-2"
                                  >
                                    <div className="flex items-center mb-1">
                                      <span className="font-medium mr-2">
                                        {arg.name}
                                      </span>
                                      <span className="text-xs text-gray-500 mr-2">
                                        ({arg.type})
                                      </span>
                                      {arg.required && (
                                        <span className="text-red-500 text-xs">
                                          *
                                        </span>
                                      )}
                                    </div>
                                    {arg.description && (
                                      <div className="text-xs text-gray-500 mb-1">
                                        {arg.description}
                                      </div>
                                    )}
                                    <input
                                      disabled
                                      className="border rounded px-2 py-1 text-sm bg-gray-100 w-full max-w-md"
                                      placeholder={arg.default ?? ""}
                                    />
                                  </div>
                                )
                              )}
                              {(!tool.args || tool.args.length === 0) && (
                                <span className="text-gray-400">无参数</span>
                              )}
                            </div>
                          </Collapse.Panel>
                        ))}
                      </Collapse>
                    ) : (
                      <div className="text-gray-500 text-center py-8">
                        暂无工具配置
                      </div>
                    ),
                },
              ]}
            />
          </Card>
        </Col>

        {/* 右侧配置信息 */}
        <Col span={8}>

          {/* 连接配置 */}
          {mcpConfig && (
            <Card title="连接配置" className="mb-6">
              <Tabs
                defaultActiveKey={hasLocalConfig ? "local" : "sse"}
                items={
                  hasLocalConfig
                    ? [
                        {
                          key: "local",
                          label: "Local Config",
                          children: (
                            <div className="relative">
                              <div className="absolute top-2 right-2">
                                <Button
                                  size="small"
                                  onClick={() => handleCopy(localJson)}
                                >
                                  复制
                                </Button>
                              </div>
                              <div
                                style={{
                                  width: "100%",
                                  overflowX: "auto",
                                  height: "200px",
                                }}
                              >
                                <MonacoEditor
                                  language="json"
                                  theme="vs-dark"
                                  value={localJson}
                                  options={{
                                    readOnly: true,
                                    minimap: { enabled: false },
                                    scrollBeyondLastLine: false,
                                    scrollbar: {
                                      vertical: "visible",
                                      horizontal: "visible",
                                    },
                                    wordWrap: "off",
                                    lineNumbers: "on",
                                    automaticLayout: true,
                                    fontSize: 12,
                                    copyWithSyntaxHighlighting: true,
                                    contextmenu: true,
                                  }}
                                  height="200"
                                />
                              </div>
                            </div>
                          ),
                        },
                      ]
                    : [
                        {
                          key: "http",
                          label: "HTTP Config",
                          children: (
                            <div className="relative">
                              <div className="absolute top-2 right-2">
                                <Button
                                  size="small"
                                  onClick={() => handleCopy(httpJson)}
                                >
                                  复制
                                </Button>
                              </div>
                              <div
                                style={{
                                  width: "100%",
                                  overflowX: "auto",
                                  height: "200px",
                                }}
                              >
                                <MonacoEditor
                                  language="json"
                                  theme="vs-dark"
                                  value={httpJson}
                                  options={{
                                    readOnly: true,
                                    minimap: { enabled: false },
                                    scrollBeyondLastLine: false,
                                    scrollbar: {
                                      vertical: "visible",
                                      horizontal: "visible",
                                    },
                                    wordWrap: "off",
                                    lineNumbers: "on",
                                    automaticLayout: true,
                                    fontSize: 12,
                                    copyWithSyntaxHighlighting: true,
                                    contextmenu: true,
                                  }}
                                  height="200"
                                />
                              </div>
                            </div>
                          ),
                        },
                        {
                          key: "sse",
                          label: "SSE Config",
                          children: (
                            <div className="relative">
                              <div className="absolute top-2 right-2">
                                <Button
                                  size="small"
                                  onClick={() => handleCopy(sseJson)}
                                >
                                  复制
                                </Button>
                              </div>
                              <div
                                style={{
                                  width: "100%",
                                  overflowX: "auto",
                                  height: "200px",
                                }}
                              >
                                <MonacoEditor
                                  language="json"
                                  theme="vs-dark"
                                  value={sseJson}
                                  options={{
                                    readOnly: true,
                                    minimap: { enabled: false },
                                    scrollBeyondLastLine: false,
                                    scrollbar: {
                                      vertical: "visible",
                                      horizontal: "visible",
                                    },
                                    wordWrap: "off",
                                    lineNumbers: "on",
                                    automaticLayout: true,
                                    fontSize: 12,
                                    copyWithSyntaxHighlighting: true,
                                    contextmenu: true,
                                  }}
                                  height="200"
                                />
                              </div>
                            </div>
                          ),
                        },
                      ]
                }
              />
            </Card>
          )}
        </Col>
      </Row>
    </Layout>
  );
}

export default McpDetail;

import { useEffect, useState, useCallback, useMemo } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Layout } from "../components/Layout";
import { ProductHeader } from "../components/ProductHeader";
import {
  Alert, Button, message,
  Tabs, Collapse, Select,
  Spin, Tooltip,
} from "antd";
import { ArrowLeftOutlined, CopyOutlined } from "@ant-design/icons";
import { ProductType } from "../types";
import * as yaml from "js-yaml";
import type { IMCPConfig } from "../lib/apis/typing";
import type { IProductDetail } from "../lib/apis";
import APIs from "../lib/apis";
import MarkdownRender from "../components/MarkdownRender";
import { copyToClipboard } from "../lib/utils";

function McpDetail() {
  const { mcpProductId } = useParams();
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [data, setData] = useState<IProductDetail>();
  const [mcpConfig, setMcpConfig] = useState<IMCPConfig>();
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
  const [selectedDomainIndex, setSelectedDomainIndex] = useState<number>(0);

  const navigate = useNavigate();

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
    protocolType?: string,
    domainIndex: number = 0
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
    if (domains && domains.length > 0 && path && domainIndex < domains.length) {
      const domain = domains[domainIndex];
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
      if (!mcpProductId) {
        return;
      }
      setLoading(true);
      setError("");
      try {
        const response = await APIs.getProduct({ id: mcpProductId });
        if (response.code === "SUCCESS" && response.data) {
          setData(response.data);

          // 处理MCP配置（统一使用新结构 mcpConfig）
          if (response.data.type === ProductType.MCP_SERVER) {
            const mcpProduct = response.data;

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
  }, [mcpProductId]);

  // 监听 mcpConfig 变化，重新生成连接配置
  useEffect(() => {
    if (mcpConfig && data) {
      generateConnectionConfig(
        mcpConfig.mcpServerConfig.domains,
        mcpConfig.mcpServerConfig.path,
        mcpConfig.mcpServerName || data.name,
        mcpConfig.mcpServerConfig.rawConfig,
        mcpConfig.meta?.protocol,
        selectedDomainIndex
      );
    }
  }, [mcpConfig, generateConnectionConfig, selectedDomainIndex, data]);

  // 生成域名选项的函数
  const getDomainOptions = (domains: Array<{ domain: string; protocol: string; networkType?: string }>) => {
    return domains.map((domain, index) => {
      return {
        value: index,
        label: `${domain.protocol}://${domain.domain}`,
        domain: domain
      }
    })
  }

  const handleCopy = async (text: string) => {
    copyToClipboard(text).then(() => {
      message.success("已复制到剪贴板")
    });
  };


  const domainOptions = useMemo(() => {
    return getDomainOptions(mcpConfig?.mcpServerConfig?.domains || []);
  }, [mcpConfig?.mcpServerConfig?.domains]);

  if (loading) {
    return (
      <Layout>
        <div className="flex justify-center items-center h-screen">
          <Spin size="large" tip="加载中..." />
        </div>
      </Layout>
    );
  }

  if (error) {
    return (
      <Layout>
        <div className="p-8">
          <Alert message="错误" description={error} type="error" showIcon />
        </div>
      </Layout>
    );
  }

  if (!data) {
    return (
      <Layout>
        <div className="flex justify-center items-center h-screen">
          <Spin size="large" tip="加载中..." />
        </div>
      </Layout>
    );
  }

  const { name, description } = data;
  const hasLocalConfig = Boolean(mcpConfig?.mcpServerConfig.rawConfig);


  return (
    <Layout>
      {/* 头部 */}
      <div className="mb-8">
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
          name={name}
          description={description}
          icon={data.icon}
          defaultIcon="/MCP.svg"
          mcpConfig={mcpConfig}
          updatedAt={data.updatedAt}
          productType="MCP_SERVER"
        />
      </div>

      {/* 主要内容区域 - 左右布局 */}
      <div className="flex flex-col lg:flex-row gap-6">
        {/* 左侧内容 */}
        <div className="w-full lg:w-[65%] order-2 lg:order-1">
          <div className="bg-white/60 backdrop-blur-sm rounded-2xl border border-white/40 p-6">
            <Tabs
              defaultActiveKey="overview"
              // className="model-detail-tabs"
              items={[
                {
                  key: "overview",
                  label: "Overview",
                  children: data.document ? (
                    <div className="min-h-[400px] prose prose-lg">
                      <MarkdownRender content={data.document} />
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
          </div>
        </div>

        {/* 右侧连接指导 */}
        <div className="w-full lg:w-[35%] order-1 lg:order-2">
          {mcpConfig && (
            <div className="bg-white/60 backdrop-blur-sm rounded-2xl border border-white/40 p-6">
              <div className="flex items-center gap-2 mb-4">
                <h3 className="text-base font-semibold  text-gray-900">
                  连接点配置
                </h3>
                <CopyOutlined className="ml-1 text-sm text-subTitle" onClick={() => {
                  copyToClipboard(domainOptions[selectedDomainIndex].label).then(() => {
                    message.success("域名已复制");
                  });
                }} />
              </div>

              {/* 域名选择器 */}
              {mcpConfig?.mcpServerConfig?.domains && mcpConfig.mcpServerConfig.domains.length > 0 && (
                <div className="mb-2">
                  <div className="flex border border-gray-200 rounded-md overflow-hidden">
                    <div
                      className="flex-shrink-0 bg-gray-50 px-3 py-2 text-xs text-gray-600 border-r border-gray-200 flex items-center whitespace-nowrap">
                      域名
                    </div>
                    <div className="flex-1 min-w-0">
                      <Select
                        value={selectedDomainIndex}
                        onChange={setSelectedDomainIndex}
                        className="w-full"
                        placeholder="选择域名"
                        size="middle"
                        variant="borderless"
                        style={{
                          fontSize: '12px',
                          height: '100%'
                        }}
                      // options={getDomainOptions(mcpConfig.mcpServerConfig.domains)}
                      >
                        {domainOptions.map((option) => (
                          <Select.Option key={option.value} value={option.value}>
                            <Tooltip classNames={{ root: "bg-white" }} title={<span className="text-gray-900 bg-white">{option.label}</span>}>
                              <span className="text-xs text-gray-900 font-mono">
                                {option.label}
                              </span>
                            </Tooltip>
                          </Select.Option>
                        ))}
                      </Select>
                    </div>
                  </div>
                </div>
              )}

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
                        <div className="relative bg-gray-50 border border-gray-200 rounded-lg overflow-hidden">
                          <Button
                            type="text"
                            size="small"
                            icon={<CopyOutlined />}
                            className="absolute top-2 right-2 z-10 text-gray-400 hover:text-white"
                            onClick={() => handleCopy(localJson)}
                          />
                          <div className="bg-gray-800 text-gray-100 font-mono text-xs overflow-x-auto">
                            <pre className="whitespace-pre p-3">{localJson}</pre>
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
                          <div className="relative bg-gray-50 border border-gray-200 rounded-lg overflow-hidden">
                            <Button
                              type="text"
                              size="small"
                              icon={<CopyOutlined />}
                              className="absolute top-2 right-2 z-10 text-gray-400 hover:text-white"
                              onClick={() => handleCopy(sseJson)}
                            />
                            <div className="bg-gray-800 text-gray-100 font-mono text-xs overflow-x-auto">
                              <pre className="whitespace-pre p-3">{sseJson}</pre>
                            </div>
                          </div>
                        ),
                      });
                    }

                    if (httpJson) {
                      tabs.push({
                        key: "http",
                        label: "Streamable HTTP",
                        children: (
                          <div className="relative bg-gray-50 border border-gray-200 rounded-lg overflow-hidden">
                            <Button
                              type="text"
                              size="small"
                              icon={<CopyOutlined />}
                              className="absolute top-2 right-2 z-10 text-gray-400 hover:text-white"
                              onClick={() => handleCopy(httpJson)}
                            />
                            <div className="bg-gray-900  text-gray-100 font-mono text-xs overflow-x-auto">
                              <pre className="whitespace-pre p-3">{httpJson}</pre>
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
          )}
        </div>
      </div>
    </Layout>
  );
}

export default McpDetail;

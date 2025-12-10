import { Card, Tag, Tabs, Table, Collapse, Descriptions, Select } from "antd";
import { useEffect, useMemo, useState } from "react";
import type { ApiProduct } from "@/types/api-product";
import MonacoEditor from "react-monaco-editor";
import * as yaml from "js-yaml";
import { ProductTypeMap } from "@/lib/utils";

// 来源类型映射
const FromTypeMap: Record<string, string> = {
  HTTP: "HTTP转MCP",
  MCP: "MCP直接代理",
  OPEN_API: "OpenAPI转MCP",
  DIRECT_ROUTE: "直接路由",
  DATABASE: "数据库",
};

// 来源映射
const SourceMap: Record<string, string> = {
  APIG_AI: "AI网关",
  HIGRESS: "Higress",
  NACOS: "Nacos",
  APIG_API: "API网关"
};

interface ApiProductApiDocsProps {
  apiProduct: ApiProduct;
  handleRefresh: () => void;
}

export function ApiProductApiDocs({ apiProduct }: ApiProductApiDocsProps) {
  const [content, setContent] = useState("");

  // OpenAPI 端点
  const [endpoints, setEndpoints] = useState<
    Array<{
      key: string;
      method: string;
      path: string;
      description: string;
      operationId?: string;
    }>
  >([]);

  // MCP 配置解析结果
  const [mcpParsed, setMcpParsed] = useState<{
    server?: { name?: string; config?: Record<string, unknown> };
    tools?: Array<{
      name: string;
      description?: string;
      args?: Array<{
        name: string;
        description?: string;
        type?: string;
        required?: boolean;
        position?: string;
        defaultValue?: string | number | boolean | null;
        enumValues?: Array<string> | null;
      }>;
    }>;
    allowTools?: Array<string>;
  }>({});

  // MCP 连接配置JSON
  const [httpJson, setHttpJson] = useState("");
  const [sseJson, setSseJson] = useState("");
  const [localJson, setLocalJson] = useState("");
  const [selectedDomainIndex, setSelectedDomainIndex] = useState<number>(0);

  // 生成连接配置JSON
  const generateConnectionConfig = (
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
      const baseUrl = `${domain.protocol}://${domain.domain}`;
      const endpoint = `${baseUrl}${path}`;

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
  };


  useEffect(() => {
    // 设置源码内容
    if (apiProduct.apiConfig?.spec) {
      setContent(apiProduct.apiConfig.spec);
    } else if (apiProduct.mcpConfig?.tools) {
      setContent(apiProduct.mcpConfig.tools);
    } else {
      setContent("");
    }

    // 解析 OpenAPI（如有）
    if (apiProduct.apiConfig?.spec) {
      const spec = apiProduct.apiConfig.spec;
      try {
        const list: Array<{
          key: string;
          method: string;
          path: string;
          description: string;
          operationId?: string;
        }> = [];

        const lines = spec.split("\n");
        let currentPath = "";
        let inPaths = false;

        for (let i = 0; i < lines.length; i++) {
          const line = lines[i];
          const trimmedLine = line.trim();
          const indentLevel = line.length - line.trimStart().length;

          if (trimmedLine === "paths:" || trimmedLine.startsWith("paths:")) {
            inPaths = true;
            continue;
          }
          if (!inPaths) continue;

          if (
            inPaths &&
            indentLevel === 2 &&
            trimmedLine.startsWith("/") &&
            trimmedLine.endsWith(":")
          ) {
            currentPath = trimmedLine.slice(0, -1);
            continue;
          }

          if (inPaths && indentLevel === 4) {
            const httpMethods = [
              "get:",
              "post:",
              "put:",
              "delete:",
              "patch:",
              "head:",
              "options:",
            ];
            for (const method of httpMethods) {
              if (trimmedLine.startsWith(method)) {
                const methodName = method.replace(":", "").toUpperCase();
                const operationId = extractOperationId(lines, i);
                list.push({
                  key: `${methodName}-${currentPath}`,
                  method: methodName,
                  path: currentPath,
                  description: operationId || `${methodName} ${currentPath}`,
                  operationId,
                });
                break;
              }
            }
          }
        }

        setEndpoints(list.length > 0 ? list : []);
      } catch {
        setEndpoints([]);
      }
    } else {
      setEndpoints([]);
    }

    // 解析 MCP YAML（如有）
    if (apiProduct.mcpConfig?.tools) {
      try {
        const doc = yaml.load(apiProduct.mcpConfig.tools) as any;
        const toolsRaw = Array.isArray(doc?.tools) ? doc.tools : [];
        const tools = toolsRaw.map((t: any) => ({
          name: String(t?.name ?? ""),
          description: t?.description ? String(t.description) : undefined,
          args: Array.isArray(t?.args)
            ? t.args.map((a: any) => ({
                name: String(a?.name ?? ""),
                description: a?.description ? String(a.description) : undefined,
                type: a?.type ? String(a.type) : undefined,
                required: Boolean(a?.required),
                position: a?.position ? String(a.position) : undefined,
                defaultValue: a?.defaultValue ?? a?.default ?? null,
                enumValues: a?.enumValues ?? a?.enum ?? null,
              }))
            : undefined,
        }));

        setMcpParsed({
          server: doc?.server,
          tools,
          allowTools: Array.isArray(doc?.allowTools)
            ? doc.allowTools
            : undefined,
        });

        // 生成连接配置JSON
        generateConnectionConfig(
          apiProduct.mcpConfig.mcpServerConfig?.domains,
          apiProduct.mcpConfig.mcpServerConfig?.path,
          apiProduct.mcpConfig.mcpServerName || apiProduct.name,
          apiProduct.mcpConfig.mcpServerConfig?.rawConfig,
          apiProduct.mcpConfig.meta?.protocol,
          selectedDomainIndex
        );
      } catch {
        setMcpParsed({});
      }
    } else {
      setMcpParsed({});
    }
  }, [apiProduct, selectedDomainIndex]);

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

  const isOpenApi = useMemo(
    () => Boolean(apiProduct.apiConfig?.spec),
    [apiProduct]
  );
  const isMcp = useMemo(
    () => Boolean(apiProduct.mcpConfig?.tools),
    [apiProduct]
  );

  const openApiColumns = useMemo(
    () => [
      {
        title: "方法",
        dataIndex: "method",
        key: "method",
        width: 100,
        render: (method: string) => (
          <span>
            <Tag
              color={
                method === "GET"
                  ? "green"
                  : method === "POST"
                  ? "blue"
                  : method === "PUT"
                  ? "orange"
                  : method === "DELETE"
                  ? "red"
                  : "default"
              }
            >
              {method}
            </Tag>
          </span>
        ),
      },
      {
        title: "路径",
        dataIndex: "path",
        key: "path",
        width: 260,
        render: (path: string) => (
          <code className="text-sm bg-gray-100 px-2 py-1 rounded">{path}</code>
        ),
      },
    ],
    []
  );

  function extractOperationId(lines: string[], startIndex: number): string {
    const currentIndent =
      lines[startIndex].length - lines[startIndex].trimStart().length;
    for (
      let i = startIndex + 1;
      i < Math.min(startIndex + 20, lines.length);
      i++
    ) {
      const line = lines[i];
      const trimmedLine = line.trim();
      const lineIndent = line.length - line.trimStart().length;
      if (lineIndent <= currentIndent && trimmedLine !== "") break;
      if (trimmedLine.startsWith("operationId:")) {
        return trimmedLine.replace("operationId:", "").trim();
      }
    }
    return "";
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold mb-2">API配置</h1>
          <p className="text-gray-600">查看API定义和规范</p>
        </div>
      </div>

      <Tabs
        defaultActiveKey="overview"
        items={[
          {
            key: "overview",
            label: "API配置",
            children: (
              <div className="space-y-4">
                {isOpenApi && (
                  <>
                    <Descriptions
                      column={2}
                      bordered
                      size="small"
                      className="mb-4"
                    >
                      {/* 'APIG_API' | 'HIGRESS' | 'APIG_AI' */}
                      <Descriptions.Item label="API来源">
                        {SourceMap[apiProduct.apiConfig?.meta.source || '']}
                      </Descriptions.Item>
                      <Descriptions.Item label="API类型">
                        {apiProduct.apiConfig?.meta.type}
                      </Descriptions.Item>
                    </Descriptions>
                    <Table
                      columns={openApiColumns as any}
                      dataSource={endpoints}
                      rowKey="key"
                      pagination={false}
                      size="small"
                    />
                  </>
                )}

                {isMcp && (
                  <>
                    <Descriptions
                      column={2}
                      bordered
                      size="small"
                      className="mb-4"
                    >
                      <Descriptions.Item label="名称">
                        {mcpParsed.server?.name ||
                          apiProduct.mcpConfig?.meta.mcpServerName ||
                          "—"}
                      </Descriptions.Item>
                      <Descriptions.Item label="来源">
                        {apiProduct.mcpConfig?.meta.source
                          ? SourceMap[apiProduct.mcpConfig.meta.source] || apiProduct.mcpConfig.meta.source
                          : "—"}
                      </Descriptions.Item>
                      <Descriptions.Item label="来源类型">
                        {apiProduct.mcpConfig?.meta.fromType
                          ? FromTypeMap[apiProduct.mcpConfig.meta.fromType] || apiProduct.mcpConfig.meta.fromType
                          : "—"}
                      </Descriptions.Item>
                      <Descriptions.Item label="API类型">
                        {apiProduct.mcpConfig?.meta.source
                          ? ProductTypeMap[apiProduct.type] || apiProduct.type
                          : "—"}
                      </Descriptions.Item>
                    </Descriptions>
                    <div className="mb-2">
                      <span className="font-bold mr-2">工具列表：</span>
                      {/* {Array.isArray(mcpParsed.tools) && mcpParsed.tools.length > 0 ? (
                        mcpParsed.tools.map((tool, idx) => (
                          <Tag key={tool.name || idx} color="blue" className="mr-1">
                            {tool.name}
                          </Tag>
                        ))
                      ) : (
                        <span className="text-gray-400">—</span>
                      )} */}
                    </div>

                    <Collapse accordion>
                      {(mcpParsed.tools || []).map((tool, idx) => (
                        <Collapse.Panel header={tool.name} key={idx}>
                          {tool.description && (
                            <div className="mb-2 text-gray-600">
                              {tool.description}
                            </div>
                          )}
                          <div className="mb-2 font-bold">输入参数：</div>
                          <div className="space-y-2">
                            {tool.args && tool.args.length > 0 ? (
                              tool.args.map((arg, aidx) => (
                                <div key={aidx} className="flex flex-col mb-2">
                                  <div className="flex items-center mb-1">
                                    <span className="font-medium mr-2">
                                      {arg.name}
                                    </span>
                                    {arg.type && (
                                      <span className="text-xs text-gray-500 mr-2">
                                        ({arg.type})
                                      </span>
                                    )}
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
                                    placeholder={
                                      arg.defaultValue !== undefined &&
                                      arg.defaultValue !== null
                                        ? String(arg.defaultValue)
                                        : ""
                                    }
                                  />
                                  {Array.isArray(arg.enumValues) &&
                                    arg.enumValues.length > 0 && (
                                      <div className="text-xs text-gray-500 mt-1">
                                        可选值：{arg.enumValues.join(", ")}
                                      </div>
                                    )}
                                </div>
                              ))
                            ) : (
                              <span className="text-gray-400">无参数</span>
                            )}
                          </div>
                        </Collapse.Panel>
                      ))}
                    </Collapse>
                  </>
                )}
                {!isOpenApi && !isMcp && (
                  <Card>
                    <div className="text-center py-8 text-gray-500">
                      <p>暂无配置</p>
                    </div>
                  </Card>
                  )}
              </div>
            ),
          },
          ...(!isMcp ? [{
            key: "source",
            label: "OpenAPI 规范",
            children: (
              <div style={{ height: 460 }}>
                <MonacoEditor
                  language="yaml"
                  theme="vs-light"
                  value={content}
                  options={{
                    readOnly: true,
                    minimap: { enabled: true },
                    scrollBeyondLastLine: false,
                    scrollbar: { vertical: "visible", horizontal: "visible" },
                    wordWrap: "off",
                    lineNumbers: "on",
                    automaticLayout: true,
                    fontSize: 14,
                    copyWithSyntaxHighlighting: true,
                    contextmenu: true,
                  }}
                  height="100%"
                />
              </div>
            ),
          }] : []),
          ...(isMcp ? [{
            key: "mcpServerConfig",
            label: "MCP连接配置",
            children: (
              <div className="space-y-4">
                {/* 域名选择器 */}
                {apiProduct.mcpConfig?.mcpServerConfig?.domains && apiProduct.mcpConfig.mcpServerConfig.domains.length > 1 && (
                  <div className="mb-2">
                    <div className="flex items-center mb-2">
                      <span className="text-xs text-gray-900">域名</span>
                    </div>
                    <Select
                      value={selectedDomainIndex}
                      onChange={setSelectedDomainIndex}
                      className="w-full"
                      placeholder="选择域名"
                      size="middle"
                      style={{
                        borderRadius: '6px',
                        fontSize: '12px'
                      }}
                    >
                      {getDomainOptions(apiProduct.mcpConfig.mcpServerConfig.domains).map((option) => (
                        <Select.Option key={option.value} value={option.value}>
                          <span className="text-xs text-gray-900 font-mono">
                            {option.label}
                          </span>
                        </Select.Option>
                      ))}
                    </Select>
                  </div>
                )}
                
                <div className="">
                  {apiProduct.mcpConfig?.mcpServerConfig?.rawConfig ? (
                    // Local Mode - 显示本地配置
                    <div>
                      <h3 className="text-lg font-bold mb-2">Local Config</h3>
                      <MonacoEditor
                        language="json"
                        theme="vs-light"
                        value={localJson}
                        options={{
                          readOnly: true,
                          minimap: { enabled: true },
                          scrollBeyondLastLine: false,
                          scrollbar: { vertical: "visible", horizontal: "visible" },
                          wordWrap: "off",
                          lineNumbers: "on",
                          automaticLayout: true,
                          fontSize: 14,
                          copyWithSyntaxHighlighting: true,
                          contextmenu: true,
                        }}
                        height="150px"
                      />
                    </div>
                  ) : (
                    // HTTP/SSE Mode - 根据配置状态动态显示
                    <>
                      {httpJson && (
                        <div className="mt-4">
                          <h3 className="text-lg font-bold mb-2">HTTP Config</h3>
                          <MonacoEditor
                            language="json"
                            theme="vs-light"
                            value={httpJson}
                            options={{
                              readOnly: true,
                              minimap: { enabled: true },
                              scrollBeyondLastLine: false,
                              scrollbar: { vertical: "visible", horizontal: "visible" },
                              wordWrap: "off",
                              lineNumbers: "on",
                              automaticLayout: true,
                              fontSize: 14,
                              copyWithSyntaxHighlighting: true,
                              contextmenu: true,
                            }}
                            height="150px"
                          />
                        </div>
                      )}
                      {sseJson && (
                        <div className="mt-4">
                          <h3 className="text-lg font-bold mb-2">SSE Config</h3>
                          <MonacoEditor
                            language="json"
                            theme="vs-light"
                            value={sseJson}
                            options={{
                              readOnly: true,
                              minimap: { enabled: true },
                              scrollBeyondLastLine: false,
                              scrollbar: { vertical: "visible", horizontal: "visible" },
                              wordWrap: "off",
                              lineNumbers: "on",
                              automaticLayout: true,
                              fontSize: 14,
                              copyWithSyntaxHighlighting: true,
                              contextmenu: true,
                            }}
                            height="150px"
                          />
                        </div>
                      )}
                    </>
                  )}
                </div>
              </div>
            ),
          }] : [])
        ]}
      />
    </div>
  );
}

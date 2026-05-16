import { CopyOutlined, FileTextOutlined, ToolOutlined } from '@ant-design/icons';
import { Button, message, Tabs, Collapse, Select, Tooltip } from 'antd';
import * as yaml from 'js-yaml';
import { useEffect, useState, useCallback, useMemo } from 'react';
import { useParams } from 'react-router-dom';

import { EmptyState } from '../components/EmptyState';
import MarkdownRender from '../components/MarkdownRender';
import { ProductDetailLayout } from '../components/ProductDetailLayout';
import APIs from '../lib/apis';
import { copyToClipboard, formatDomainWithPort } from '../lib/utils';
import { ProductType } from '../types';

import type { IProductDetail } from '../lib/apis';
import type { IMCPConfig } from '../lib/apis/typing';

function McpDetail() {
  const { mcpProductId } = useParams();
  const [error, setError] = useState('');
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
  const [httpJson, setHttpJson] = useState('');
  const [sseJson, setSseJson] = useState('');
  const [localJson, setLocalJson] = useState('');
  const [selectedDomainIndex, setSelectedDomainIndex] = useState<number>(0);

  // 解析YAML配置的函数
  const parseYamlConfig = (
    yamlString: string,
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
      console.warn('解析YAML配置失败:', error);
      return null;
    }
  };

  // 生成连接配置的函数
  const generateConnectionConfig = useCallback(
    (
      domains: Array<{ domain: string; port?: number; protocol: string }> | null | undefined,
      path: string | null | undefined,
      serverName: string,
      localConfig?: unknown,
      protocolType?: string,
      domainIndex: number = 0,
    ) => {
      // 互斥：优先判断本地模式
      if (localConfig) {
        const localConfigJson = JSON.stringify(localConfig, null, 2);
        setLocalJson(localConfigJson);
        setHttpJson('');
        setSseJson('');
        return;
      }

      // HTTP/SSE 模式
      if (domains && domains.length > 0 && domainIndex < domains.length) {
        const domain = domains[domainIndex];
        if (!domain) return;
        const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
        const baseUrl = `${domain.protocol}://${formattedDomain}`;
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
          setHttpJson('');
          setLocalJson('');
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
          setSseJson('');
          setLocalJson('');
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
          setLocalJson('');
          return;
        }
      }

      // 无有效配置
      setHttpJson('');
      setSseJson('');
      setLocalJson('');
    },
    [],
  );

  useEffect(() => {
    const fetchDetail = async () => {
      if (!mcpProductId) {
        return;
      }
      setLoading(true);
      setError('');
      try {
        const response = await APIs.getProduct({ id: mcpProductId });
        if (response.code === 'SUCCESS' && response.data) {
          setData(response.data);

          // 处理MCP配置（统一使用新结构 mcpConfig）
          if (response.data.type === ProductType.MCP_SERVER) {
            const mcpProduct = response.data;

            if (mcpProduct.mcpConfig) {
              setMcpConfig(mcpProduct.mcpConfig);

              // 解析tools配置
              if (mcpProduct.mcpConfig.tools) {
                const parsedConfig = parseYamlConfig(mcpProduct.mcpConfig.tools);
                if (parsedConfig && parsedConfig.tools) {
                  setParsedTools(parsedConfig.tools);
                }
              }
            }
          }
        } else {
          setError(response.message || '数据加载失败');
        }
      } catch (error) {
        console.error('API请求失败:', error);
        setError('加载失败，请稍后重试');
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
        mcpConfig.protocol || mcpConfig.meta?.protocol,
        selectedDomainIndex,
      );
    }
  }, [mcpConfig, generateConnectionConfig, selectedDomainIndex, data]);

  // 生成域名选项的函数
  const getDomainOptions = (
    domains: Array<{ domain: string; port?: number; protocol: string; networkType?: string }>,
  ) => {
    return domains.map((domain, index) => {
      const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
      return {
        domain: domain,
        label: `${domain.protocol}://${formattedDomain}`,
        value: index,
      };
    });
  };

  const handleCopy = async (text: string) => {
    copyToClipboard(text).then(() => {
      message.success('已复制到剪贴板');
    });
  };

  const domainOptions = useMemo(() => {
    return getDomainOptions(mcpConfig?.mcpServerConfig?.domains || []);
  }, [mcpConfig?.mcpServerConfig?.domains]);
  const selectedDomainOption = domainOptions[selectedDomainIndex];

  const { description, name } = data || {};
  const hasLocalConfig = Boolean(mcpConfig?.mcpServerConfig.rawConfig);

  const handleCopySelectedDomain = async () => {
    if (!selectedDomainOption?.label) return;

    try {
      await copyToClipboard(selectedDomainOption.label);
      message.success('域名已复制到剪贴板');
    } catch {
      message.error('复制失败，请手动复制');
    }
  };

  const leftContent = data ? (
    <div className="rounded-xl border border-white/40 bg-white/60 p-6 backdrop-blur-sm">
      <Tabs
        defaultActiveKey="overview"
        items={[
          {
            children: data.document ? (
              <div className="min-h-[400px] px-4">
                <MarkdownRender content={data.document} />
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
            children:
              parsedTools.length > 0 ? (
                <div className="rounded-lg border border-gray-200 bg-gray-50">
                  {parsedTools.map((tool, idx) => (
                    <div
                      className={idx < parsedTools.length - 1 ? 'border-b border-gray-200' : ''}
                      key={idx}
                    >
                      <Collapse
                        expandIconPosition="end"
                        ghost
                        items={[
                          {
                            children: (
                              <div className="px-4 pb-2">
                                <div className="mb-4 text-gray-600">{tool.description}</div>

                                {tool.args && tool.args.length > 0 && (
                                  <div>
                                    <p className="mb-3 font-medium text-gray-700">输入参数:</p>
                                    {tool.args.map((arg, argIdx) => (
                                      <div className="mb-3" key={argIdx}>
                                        <div className="mb-2 flex items-center">
                                          <span className="mr-2 font-medium text-gray-800">
                                            {arg.name}
                                          </span>
                                          <span className="mr-2 rounded bg-gray-200 px-2 py-1 text-xs text-gray-600">
                                            {arg.type}
                                          </span>
                                          {arg.required && (
                                            <span className="mr-2 text-xs text-red-500">*</span>
                                          )}
                                          {arg.description && (
                                            <span className="text-xs text-gray-500">
                                              {arg.description}
                                            </span>
                                          )}
                                        </div>
                                        <input
                                          className="w-full rounded-md border border-gray-300 bg-gray-100 px-3 py-2 text-sm focus:border-transparent focus:outline-none focus:ring-2 focus:ring-blue-500"
                                          placeholder={arg.description || `请输入${arg.name}`}
                                          type="text"
                                        />
                                      </div>
                                    ))}
                                  </div>
                                )}

                                {(!tool.args || tool.args.length === 0) && (
                                  <div className="text-sm text-gray-500">
                                    No parameters required
                                  </div>
                                )}
                              </div>
                            ),
                            key: idx.toString(),
                            label: tool.name,
                          },
                        ]}
                      />
                    </div>
                  ))}
                </div>
              ) : (
                <EmptyState description="暂无工具" />
              ),
            key: 'tools',
            label: (
              <span className="flex items-center gap-1.5 font-semibold">
                <ToolOutlined className="text-sm" />
                {`工具 (${parsedTools.length})`}
              </span>
            ),
          },
        ]}
        size="large"
      />
    </div>
  ) : null;

  const rightContent = (
    <>
      {mcpConfig && (
        <div className="rounded-[10px] border border-white/40 bg-white/60 p-6 backdrop-blur-sm">
          <div className="mb-4 flex items-center gap-2">
            <span className="text-xs font-medium uppercase tracking-wider text-gray-500">
              连接点配置
            </span>
          </div>

          {/* 域名选择器 */}
          {mcpConfig?.mcpServerConfig?.domains && mcpConfig.mcpServerConfig.domains.length > 0 && (
            <div className="mb-2">
              <div className="flex overflow-hidden rounded-md border border-gray-200">
                <div className="flex flex-shrink-0 items-center whitespace-nowrap border-r border-gray-200 bg-gray-50 px-3 py-2 text-xs text-gray-600">
                  域名
                </div>
                <div className="min-w-0 flex-1">
                  <Select
                    className="w-full"
                    labelRender={() => (
                      <div className="inline-flex max-w-full items-center gap-1.5">
                        <span className="min-w-0 truncate font-mono text-xs text-gray-900">
                          {selectedDomainOption?.label || '选择域名'}
                        </span>
                        <Button
                          aria-label="复制域名"
                          disabled={!selectedDomainOption?.label}
                          icon={<CopyOutlined />}
                          onClick={(event) => {
                            event.stopPropagation();
                            handleCopySelectedDomain();
                          }}
                          onMouseDown={(event) => event.stopPropagation()}
                          size="small"
                          title="复制域名"
                          type="text"
                        />
                      </div>
                    )}
                    onChange={setSelectedDomainIndex}
                    optionLabelProp="label"
                    placeholder="选择域名"
                    size="middle"
                    style={{
                      fontSize: '12px',
                      height: '100%',
                    }}
                    value={selectedDomainIndex}
                    variant="borderless"
                  >
                    {domainOptions.map((option) => (
                      <Select.Option key={option.value} label={option.label} value={option.value}>
                        <Tooltip
                          classNames={{ root: 'bg-white' }}
                          title={<span className="bg-white text-gray-900">{option.label}</span>}
                        >
                          <span className="font-mono text-xs text-gray-900">{option.label}</span>
                        </Tooltip>
                      </Select.Option>
                    ))}
                  </Select>
                </div>
              </div>
            </div>
          )}

          <Tabs
            defaultActiveKey={hasLocalConfig ? 'local' : sseJson ? 'sse' : 'http'}
            items={(() => {
              const tabs = [];

              if (hasLocalConfig) {
                tabs.push({
                  children: (
                    <div className="relative bg-gray-50 border border-gray-200 rounded-lg overflow-hidden">
                      <Button
                        className="absolute top-2 right-2 z-10 text-gray-400 hover:text-white"
                        icon={<CopyOutlined />}
                        onClick={() => handleCopy(localJson)}
                        size="small"
                        type="text"
                      />
                      <div className="bg-gray-800 text-gray-100 font-mono text-xs overflow-x-auto">
                        <pre className="whitespace-pre p-3">{localJson}</pre>
                      </div>
                    </div>
                  ),
                  key: 'local',
                  label: 'Stdio',
                });
              } else {
                if (sseJson) {
                  tabs.push({
                    children: (
                      <div className="relative bg-gray-50 border border-gray-200 rounded-lg overflow-hidden">
                        <Button
                          className="absolute top-2 right-2 z-10 text-gray-400 hover:text-white"
                          icon={<CopyOutlined />}
                          onClick={() => handleCopy(sseJson)}
                          size="small"
                          type="text"
                        />
                        <div className="bg-gray-800 text-gray-100 font-mono text-xs overflow-x-auto">
                          <pre className="whitespace-pre p-3">{sseJson}</pre>
                        </div>
                      </div>
                    ),
                    key: 'sse',
                    label: 'SSE',
                  });
                }

                if (httpJson) {
                  tabs.push({
                    children: (
                      <div className="relative bg-gray-50 border border-gray-200 rounded-lg overflow-hidden">
                        <Button
                          className="absolute top-2 right-2 z-10 text-gray-400 hover:text-white"
                          icon={<CopyOutlined />}
                          onClick={() => handleCopy(httpJson)}
                          size="small"
                          type="text"
                        />
                        <div className="bg-gray-900  text-gray-100 font-mono text-xs overflow-x-auto">
                          <pre className="whitespace-pre p-3">{httpJson}</pre>
                        </div>
                      </div>
                    ),
                    key: 'http',
                    label: 'Streamable HTTP',
                  });
                }
              }

              return tabs;
            })()}
            size="small"
          />
        </div>
      )}
    </>
  );

  return (
    <ProductDetailLayout
      error={error || (!data ? '数据加载失败' : undefined)}
      headerProps={
        data
          ? {
              defaultIcon: '/MCP.svg',
              description: description || '',
              icon: data.icon,
              mcpConfig: mcpConfig,
              name: name || '',
              productType: 'MCP_SERVER',
              subscribable: data.subscribable,
              updatedAt: data.updatedAt,
            }
          : undefined
      }
      leftContent={leftContent}
      loading={loading}
      rightContent={rightContent}
    />
  );
}

export default McpDetail;

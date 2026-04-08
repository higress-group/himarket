import { CopyOutlined } from '@ant-design/icons';
import { Button, message, Tabs, Collapse, Select, Tooltip } from 'antd';
import * as yaml from 'js-yaml';
import { useEffect, useState, useCallback, useMemo } from 'react';
import { useParams } from 'react-router-dom';

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
      if (domains && domains.length > 0 && path && domainIndex < domains.length) {
        const domain = domains[domainIndex];
        if (!domain) {
          setHttpJson('');
          setSseJson('');
          setLocalJson('');
          return;
        }
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
        mcpConfig.meta?.protocol,
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

  const hasLocalConfig = Boolean(mcpConfig?.mcpServerConfig.rawConfig);

  const leftContent = data ? (
    <div className="bg-white/60 backdrop-blur-sm rounded-2xl border border-white/40 p-6">
      <Tabs
        defaultActiveKey="overview"
        // className="model-detail-tabs"
        items={[
          {
            children: data.document ? (
              <div className="min-h-[400px] prose prose-lg">
                <MarkdownRender content={data.document} />
              </div>
            ) : (
              <div className="text-gray-500 text-center py-8">No overview available</div>
            ),
            key: 'overview',
            label: 'Overview',
          },
          {
            children:
              parsedTools.length > 0 ? (
                <div className="border border-gray-200 rounded-lg bg-gray-50">
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
                                <div className="text-gray-600 mb-4">{tool.description}</div>

                                {tool.args && tool.args.length > 0 && (
                                  <div>
                                    <p className="font-medium text-gray-700 mb-3">输入参数:</p>
                                    {tool.args.map((arg, argIdx) => (
                                      <div className="mb-3" key={argIdx}>
                                        <div className="flex items-center mb-2">
                                          <span className="font-medium text-gray-800 mr-2">
                                            {arg.name}
                                          </span>
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
                                          className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                                          placeholder={arg.description || `请输入${arg.name}`}
                                          type="text"
                                        />
                                      </div>
                                    ))}
                                  </div>
                                )}

                                {(!tool.args || tool.args.length === 0) && (
                                  <div className="text-gray-500 text-sm">
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
                <div className="text-gray-500 text-center py-8">No tools available</div>
              ),
            key: 'tools',
            label: `Tools (${parsedTools.length})`,
          },
        ]}
      />
    </div>
  ) : null;

  const rightContent = (
    <>
      {mcpConfig && (
        <div className="bg-white/60 backdrop-blur-sm rounded-2xl border border-white/40 p-6">
          <div className="flex items-center gap-2 mb-4">
            <h3 className="text-base font-semibold  text-gray-900">连接点配置</h3>
            <CopyOutlined
              className="ml-1 text-sm text-subTitle"
              onClick={() => {
                const selectedDomain = domainOptions[selectedDomainIndex];
                if (selectedDomain) {
                  copyToClipboard(selectedDomain.label).then(() => {
                    message.success('域名已复制');
                  });
                }
              }}
            />
          </div>

          {/* 域名选择器 */}
          {mcpConfig?.mcpServerConfig?.domains && mcpConfig.mcpServerConfig.domains.length > 0 && (
            <div className="mb-2">
              <div className="flex border border-gray-200 rounded-md overflow-hidden">
                <div className="flex-shrink-0 bg-gray-50 px-3 py-2 text-xs text-gray-600 border-r border-gray-200 flex items-center whitespace-nowrap">
                  域名
                </div>
                <div className="flex-1 min-w-0">
                  <Select
                    className="w-full"
                    onChange={setSelectedDomainIndex}
                    placeholder="选择域名"
                    size="middle"
                    style={{
                      fontSize: '12px',
                      height: '100%',
                    }}
                    value={selectedDomainIndex}
                    variant="borderless"
                    // options={getDomainOptions(mcpConfig.mcpServerConfig.domains)}
                  >
                    {domainOptions.map((option) => (
                      <Select.Option key={option.value} value={option.value}>
                        <Tooltip
                          classNames={{ root: 'bg-white' }}
                          title={<span className="text-gray-900 bg-white">{option.label}</span>}
                        >
                          <span className="text-xs text-gray-900 font-mono">{option.label}</span>
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
              description: data.description ?? '',
              icon: data.icon,
              mcpConfig: mcpConfig,
              name: data.name ?? '',
              productType: 'MCP_SERVER',
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

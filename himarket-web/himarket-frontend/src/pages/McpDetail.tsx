import {
  CodeOutlined,
  CopyOutlined,
  FileTextOutlined,
  LinkOutlined,
  ToolOutlined,
} from '@ant-design/icons';
import { Button, message, Tabs, Collapse, Select, Tooltip } from 'antd';
import * as yaml from 'js-yaml';
import { useEffect, useState, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router-dom';

import { EmptyState } from '../components/EmptyState';
import MarkdownRender from '../components/MarkdownRender';
import { ProductDetailLayout } from '../components/ProductDetailLayout';
import APIs from '../lib/apis';
import { copyToClipboard, formatDomainWithPort } from '../lib/utils';
import { ProductType } from '../types';

import type { IProductDetail } from '../lib/apis';
import type { IMCPConfig } from '../lib/apis/typing';

type McpProtocolValue = 'stdio' | 'sse' | 'streamable-http';

type McpDomain = { domain: string; port?: number; protocol: string; networkType?: string };

interface McpProtocolOption {
  label: string;
  value: McpProtocolValue;
}

function hasNonEmptyRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value && typeof value === 'object' && Object.keys(value).length > 0);
}

function normalizeMcpProtocol(protocol?: string): string {
  return protocol?.replace(/[_\s-]/g, '').toUpperCase() ?? '';
}

function getMcpProtocolValue(protocol?: string): McpProtocolValue | undefined {
  const normalizedProtocol = normalizeMcpProtocol(protocol);

  if (!normalizedProtocol) {
    return undefined;
  }
  if (normalizedProtocol.includes('STDIO')) {
    return 'stdio';
  }
  if (normalizedProtocol.includes('SSE')) {
    return 'sse';
  }
  if (normalizedProtocol.includes('STREAMABLE') || normalizedProtocol.includes('HTTP')) {
    return 'streamable-http';
  }

  return undefined;
}

function getMcpProtocolOptions(config?: IMCPConfig): McpProtocolOption[] {
  if (!config) {
    return [];
  }

  const protocol =
    config.protocol || config.meta?.protocol || config.mcpServerConfig?.transportMode || '';
  const normalizedProtocol = normalizeMcpProtocol(protocol);
  const supportsSse = normalizedProtocol.includes('SSE');
  const supportsStreamable =
    normalizedProtocol.includes('STREAMABLE') || normalizedProtocol.includes('HTTP');
  const hasLocalConfig = hasNonEmptyRecord(config.mcpServerConfig?.rawConfig);
  const hasDomains = Boolean(config.mcpServerConfig?.domains?.length);
  const values: McpProtocolValue[] = [];

  if (normalizedProtocol.includes('DUAL') || (supportsSse && supportsStreamable)) {
    values.push('streamable-http', 'sse');
  } else {
    const protocolValue = getMcpProtocolValue(protocol);
    if (protocolValue) {
      values.push(protocolValue);
    }
  }

  if (values.length === 0) {
    if (hasLocalConfig) {
      values.push('stdio');
    } else if (hasDomains) {
      values.push('streamable-http', 'sse');
    }
  }

  return Array.from(new Set(values)).map((value) => ({
    label: value === 'streamable-http' ? 'Streamable HTTP' : value === 'sse' ? 'SSE' : 'Stdio',
    value,
  }));
}

function normalizeMcpPath(path?: string | null): string {
  if (!path || path === '/') {
    return '';
  }

  return path.startsWith('/') ? path : `/${path}`;
}

function appendSsePath(endpoint: string): string {
  return endpoint.endsWith('/sse') ? endpoint : `${endpoint.replace(/\/$/, '')}/sse`;
}

function buildMcpEndpoint(
  domain: McpDomain | undefined,
  path: string | null | undefined,
  protocol: McpProtocolValue,
): string {
  if (!domain || protocol === 'stdio') {
    return '';
  }

  const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
  const endpoint = `${domain.protocol}://${formattedDomain}${normalizeMcpPath(path)}`;

  return protocol === 'sse' ? appendSsePath(endpoint) : endpoint;
}

function buildMcpConfigJson({
  domain,
  localConfig,
  path,
  protocol,
  serverName,
}: {
  domain?: McpDomain;
  localConfig?: unknown;
  path?: string | null;
  protocol: McpProtocolValue;
  serverName: string;
}) {
  if (protocol === 'stdio') {
    return localConfig ? JSON.stringify(localConfig, null, 2) : '';
  }

  const endpoint = buildMcpEndpoint(domain, path, protocol);
  if (!endpoint) {
    return '';
  }

  if (protocol === 'sse') {
    return `{
  "mcpServers": {
    "${serverName}": {
      "type": "sse",
      "url": "${endpoint}"
    }
  }
}`;
  }

  return `{
  "mcpServers": {
    "${serverName}": {
      "url": "${endpoint}"
    }
  }
}`;
}

function protocolTabKey(protocol: McpProtocolValue): string {
  return protocol === 'streamable-http' ? 'http' : protocol;
}

function protocolFromTabKey(key: string): McpProtocolValue {
  return key === 'http' ? 'streamable-http' : (key as McpProtocolValue);
}

function McpDetail() {
  const { mcpProductId } = useParams();
  const { t } = useTranslation('mcpDetail');
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
  const [connectionDomainIndex, setConnectionDomainIndex] = useState<number>(0);
  const [connectionProtocol, setConnectionProtocol] = useState<McpProtocolValue>('streamable-http');
  const [configDomainIndex, setConfigDomainIndex] = useState<number>(0);
  const [configProtocol, setConfigProtocol] = useState<McpProtocolValue>('streamable-http');
  const [rightPanelTab, setRightPanelTab] = useState<'connection' | 'config'>('connection');

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
          setError(response.message || t('error.dataLoadFailed'));
        }
      } catch (error) {
        console.error('Failed to request API:', error);
        setError(t('error.loadFailed'));
      } finally {
        setLoading(false);
      }
    };
    fetchDetail();
  }, [mcpProductId, t]);

  const protocolOptions = useMemo(() => getMcpProtocolOptions(mcpConfig), [mcpConfig]);

  useEffect(() => {
    if (protocolOptions.length === 0) {
      return;
    }

    const connectionProtocolSupported = protocolOptions.some(
      (option) => option.value === connectionProtocol,
    );
    const configProtocolSupported = protocolOptions.some(
      (option) => option.value === configProtocol,
    );
    const firstProtocolOption = protocolOptions[0];
    if (!connectionProtocolSupported && firstProtocolOption) {
      setConnectionProtocol(firstProtocolOption.value);
    }
    if (!configProtocolSupported && firstProtocolOption) {
      setConfigProtocol(firstProtocolOption.value);
    }
  }, [configProtocol, connectionProtocol, protocolOptions]);

  // 生成域名选项的函数
  const getDomainOptions = (domains: McpDomain[]) => {
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
      message.success(t('message.copied'));
    });
  };

  const domainOptions = useMemo(() => {
    return getDomainOptions(mcpConfig?.mcpServerConfig?.domains || []);
  }, [mcpConfig?.mcpServerConfig?.domains]);

  useEffect(() => {
    if (connectionDomainIndex >= domainOptions.length) {
      setConnectionDomainIndex(0);
    }
    if (configDomainIndex >= domainOptions.length) {
      setConfigDomainIndex(0);
    }
  }, [configDomainIndex, connectionDomainIndex, domainOptions.length]);

  const selectedConnectionDomainOption = domainOptions[connectionDomainIndex];
  const selectedConfigDomainOption = domainOptions[configDomainIndex];
  const { description, name } = data || {};
  const hasLocalConfig = hasNonEmptyRecord(mcpConfig?.mcpServerConfig.rawConfig);
  const rawLocalConfig = hasLocalConfig ? mcpConfig?.mcpServerConfig.rawConfig : undefined;
  const configServerName = mcpConfig?.mcpServerName || data?.name || '';
  const selectedEndpoint = buildMcpEndpoint(
    selectedConnectionDomainOption?.domain,
    mcpConfig?.mcpServerConfig?.path,
    connectionProtocol,
  );

  const handleCopySelectedDomain = async () => {
    if (!selectedConnectionDomainOption?.label) return;

    try {
      await copyToClipboard(selectedConnectionDomainOption.label);
      message.success(t('message.domainCopied'));
    } catch {
      message.error(t('message.copyFailed'));
    }
  };

  const handleCopyEndpoint = async () => {
    if (!selectedEndpoint) return;

    try {
      await copyToClipboard(selectedEndpoint);
      message.success(t('message.endpointCopied'));
    } catch {
      message.error(t('message.copyFailed'));
    }
  };

  const leftContent = data ? (
    <div className="overflow-hidden rounded-[14px] border border-[#DDE5F0] bg-white/90 shadow-[0_18px_50px_rgba(15,23,42,0.05)] backdrop-blur-sm">
      <Tabs
        className="[&_.ant-tabs-content-holder]:px-5 [&_.ant-tabs-content-holder]:pb-5 [&_.ant-tabs-nav]:mb-5 [&_.ant-tabs-nav]:px-5 [&_.ant-tabs-tab]:py-4"
        defaultActiveKey="overview"
        items={[
          {
            children: data.document ? (
              <div className="scrollbar-thin-soft max-h-[720px] min-h-[420px] overflow-y-auto pr-2">
                <MarkdownRender content={data.document} />
              </div>
            ) : (
              <EmptyState description={t('empty.overview')} />
            ),
            key: 'overview',
            label: (
              <span className="flex items-center gap-1.5 font-semibold">
                <FileTextOutlined className="text-sm" />
                {t('tabs.overview')}
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
                                    <p className="mb-3 font-medium text-gray-700">
                                      {t('tools.inputParameters')}:
                                    </p>
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
                                          placeholder={
                                            arg.description ||
                                            t('tools.inputPlaceholder', { name: arg.name })
                                          }
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
                <EmptyState description={t('empty.tools')} />
              ),
            key: 'tools',
            label: (
              <span className="flex items-center gap-1.5 font-semibold">
                <ToolOutlined className="text-sm" />
                {t('tabs.toolsWithCount', { count: parsedTools.length })}
              </span>
            ),
          },
        ]}
        size="large"
      />
    </div>
  ) : null;

  const renderConfigCodeBlock = (value: string) => (
    <div className="relative overflow-hidden rounded-[12px] border border-[#172033] bg-[#111827]">
      <Button
        aria-label={t('connection.copyConfig')}
        className="absolute right-2 top-2 z-10 text-gray-400 hover:text-white"
        icon={<CopyOutlined />}
        onClick={() => handleCopy(value)}
        size="small"
        title={t('connection.copyConfig')}
        type="text"
      />
      <pre className="max-h-[260px] overflow-auto whitespace-pre p-4 pr-12 font-mono text-[12px] leading-5 text-gray-100">
        <code>{value}</code>
      </pre>
    </div>
  );

  const connectionPanel = (
    <div className="rounded-[12px] border border-[#E8EDF5] bg-[#FBFCFE] p-4">
      <div className="flex items-start gap-3">
        <div className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-[12px] bg-colorPrimaryBg text-colorPrimary">
          <LinkOutlined className="text-base" />
        </div>
        <div className="min-w-0">
          <div className="text-sm font-semibold text-gray-950">{t('connection.title')}</div>
          <p className="mt-1 text-sm leading-6 text-gray-600">{t('connection.description')}</p>
        </div>
      </div>

      <div className="mt-4 space-y-3 border-t border-[#E8EDF5] pt-3">
        <div>
          <div className="mb-2 text-xs font-semibold text-gray-500">{t('connection.protocol')}</div>
          {protocolOptions.length > 0 ? (
            <div className="overflow-hidden rounded-[10px] border border-[#DDE5F0] bg-white">
              <Select
                className="w-full"
                onChange={(value) => setConnectionProtocol(value as McpProtocolValue)}
                optionLabelProp="label"
                size="middle"
                value={connectionProtocol}
                variant="borderless"
              >
                {protocolOptions.map((option) => (
                  <Select.Option key={option.value} label={option.label} value={option.value}>
                    <span className="text-sm font-medium text-gray-900">{option.label}</span>
                  </Select.Option>
                ))}
              </Select>
            </div>
          ) : (
            <div className="rounded-[10px] border border-dashed border-[#DDE5F0] bg-white py-3 text-center text-xs text-gray-400">
              {t('connection.noProtocol')}
            </div>
          )}
        </div>

        {connectionProtocol !== 'stdio' && (
          <div>
            <div className="mb-2 text-xs font-semibold text-gray-500">{t('connection.domain')}</div>
            {domainOptions.length > 0 ? (
              <div className="flex overflow-hidden rounded-[10px] border border-[#DDE5F0] bg-white">
                <div className="min-w-0 flex-1">
                  <Select
                    className="w-full"
                    labelRender={() => (
                      <div className="inline-flex max-w-full items-center gap-1.5">
                        <span className="min-w-0 truncate font-mono text-xs text-gray-900">
                          {selectedConnectionDomainOption?.label || t('connection.selectDomain')}
                        </span>
                        <Button
                          aria-label={t('connection.copyDomain')}
                          disabled={!selectedConnectionDomainOption?.label}
                          icon={<CopyOutlined />}
                          onClick={(event) => {
                            event.stopPropagation();
                            handleCopySelectedDomain();
                          }}
                          onMouseDown={(event) => event.stopPropagation()}
                          size="small"
                          title={t('connection.copyDomain')}
                          type="text"
                        />
                      </div>
                    )}
                    onChange={setConnectionDomainIndex}
                    optionLabelProp="label"
                    placeholder={t('connection.selectDomain')}
                    size="middle"
                    value={connectionDomainIndex}
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
            ) : (
              <div className="rounded-[10px] border border-dashed border-[#DDE5F0] bg-white py-3 text-center text-xs text-gray-400">
                {t('connection.noDomain')}
              </div>
            )}
          </div>
        )}

        <div>
          <div className="mb-2 text-xs font-semibold text-gray-500">{t('connection.endpoint')}</div>
          {selectedEndpoint ? (
            <div className="flex items-center gap-2 rounded-[10px] border border-[#DDE5F0] bg-white px-3 py-2">
              <span className="min-w-0 flex-1 break-all font-mono text-xs leading-5 text-gray-900">
                {selectedEndpoint}
              </span>
              <Button
                aria-label={t('connection.copyEndpoint')}
                icon={<CopyOutlined />}
                onClick={handleCopyEndpoint}
                size="small"
                title={t('connection.copyEndpoint')}
                type="text"
              />
            </div>
          ) : (
            <div className="rounded-[10px] border border-dashed border-[#DDE5F0] bg-white py-3 text-center text-xs text-gray-400">
              {connectionProtocol === 'stdio' && hasLocalConfig
                ? t('connection.localEndpoint')
                : t('connection.noEndpoint')}
            </div>
          )}
        </div>
      </div>
    </div>
  );

  const configPanel = (
    <div className="rounded-[12px] border border-[#E8EDF5] bg-[#FBFCFE] p-4">
      {configProtocol !== 'stdio' && (
        <div className="mb-3">
          <div className="mb-2 text-xs font-semibold text-gray-500">{t('connection.domain')}</div>
          {domainOptions.length > 0 ? (
            <div className="overflow-hidden rounded-[10px] border border-[#DDE5F0] bg-white">
              <Select
                className="w-full"
                labelRender={() => (
                  <span className="block min-w-0 truncate font-mono text-xs text-gray-900">
                    {selectedConfigDomainOption?.label || t('connection.selectDomain')}
                  </span>
                )}
                onChange={setConfigDomainIndex}
                optionLabelProp="label"
                placeholder={t('connection.selectDomain')}
                size="middle"
                value={configDomainIndex}
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
          ) : (
            <div className="rounded-[10px] border border-dashed border-[#DDE5F0] bg-white py-3 text-center text-xs text-gray-400">
              {t('connection.noDomain')}
            </div>
          )}
        </div>
      )}
      <Tabs
        activeKey={protocolTabKey(configProtocol)}
        className="[&_.ant-tabs-nav]:mb-3"
        items={protocolOptions.map((option) => {
          const configValue = buildMcpConfigJson({
            domain: selectedConfigDomainOption?.domain,
            localConfig: rawLocalConfig,
            path: mcpConfig?.mcpServerConfig?.path,
            protocol: option.value,
            serverName: configServerName,
          });

          return {
            children: configValue ? (
              renderConfigCodeBlock(configValue)
            ) : (
              <div className="rounded-[10px] border border-dashed border-[#DDE5F0] bg-white py-8 text-center text-xs text-gray-400">
                {option.value === 'stdio' ? t('connection.noConfig') : t('connection.noDomain')}
              </div>
            ),
            key: protocolTabKey(option.value),
            label: option.label,
          };
        })}
        onChange={(key) => {
          setConfigProtocol(protocolFromTabKey(key));
        }}
        size="small"
      />
    </div>
  );

  const rightContent = (
    <>
      {mcpConfig && (
        <section className="rounded-[14px] border border-[#DDE5F0] bg-white/90 p-5 shadow-[0_18px_50px_rgba(15,23,42,0.05)] backdrop-blur-sm">
          <div className="mb-3 flex rounded-lg bg-gray-100 p-1">
            <button
              className={`flex flex-1 items-center justify-center gap-1.5 rounded-md py-2 text-xs transition-all ${
                rightPanelTab === 'connection'
                  ? 'bg-white font-medium text-gray-800 shadow-sm'
                  : 'text-gray-500 hover:text-gray-700'
              }`}
              onClick={() => setRightPanelTab('connection')}
              type="button"
            >
              <LinkOutlined className="text-[13px]" />
              {t('connection.infoTab')}
            </button>
            <button
              className={`flex flex-1 items-center justify-center gap-1.5 rounded-md py-2 text-xs transition-all ${
                rightPanelTab === 'config'
                  ? 'bg-white font-medium text-gray-800 shadow-sm'
                  : 'text-gray-500 hover:text-gray-700'
              }`}
              onClick={() => setRightPanelTab('config')}
              type="button"
            >
              <CodeOutlined className="text-[13px]" />
              {t('connection.configTab')}
            </button>
          </div>
          {rightPanelTab === 'connection' ? connectionPanel : configPanel}
        </section>
      )}
    </>
  );

  return (
    <ProductDetailLayout
      error={error || (!data ? t('error.dataLoadFailed') : undefined)}
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

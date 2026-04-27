import { CopyOutlined, EditOutlined, SyncOutlined } from '@ant-design/icons';
import { Button, Card, Col, Collapse, Row, Select, Spin, Tabs, Tag } from 'antd';
import { message } from 'antd';
import { useState } from 'react';

import { copyToClipboard } from '@/lib/utils';
import type { ApiProduct, ExtraParamDef, McpMetaItem } from '@/types/api-product';

import { AuthCredentialPanel } from '../display/AuthCredentialPanel';

import type { DomainOption } from '../hooks/useMcpConnectionConfig';
import type { ParsedTool } from '../hooks/useParsedMcpTools';

interface McpServerConfigPanelProps {
  apiProduct: ApiProduct;
  mcpMetaList: McpMetaItem[];
  parsedTools: ParsedTool[];
  fetchingTools: boolean;
  httpJson: string;
  sseJson: string;
  localJson: string;
  hotSseJson: string;
  hotHttpJson: string;
  domainOptions: DomainOption[];
  selectedDomainIndex: number;
  onDomainChange: (index: number) => void;
  onRefreshTools: () => void;
  onEditTools: () => void;
}

export function McpServerConfigPanel({
  apiProduct,
  domainOptions,
  fetchingTools,
  hotHttpJson,
  hotSseJson,
  httpJson,
  localJson,
  mcpMetaList,
  onDomainChange,
  onEditTools,
  onRefreshTools,
  parsedTools,
  selectedDomainIndex,
  sseJson,
}: McpServerConfigPanelProps) {
  const [mcpToolsTab, setMcpToolsTab] = useState('tools');

  const renderSandboxConfig = () => {
    const meta = mcpMetaList[0];
    if (!meta?.subscribeParams) return null;
    let sp: Record<string, unknown> = {};
    try {
      sp =
        typeof meta.subscribeParams === 'string'
          ? JSON.parse(meta.subscribeParams)
          : meta.subscribeParams;
    } catch {
      return null;
    }

    const sandboxId = sp.sandboxId || '-';
    const authType = sp.authType || 'none';
    const extraParams = sp.extraParams || {};
    const extraEntries = Object.entries(extraParams).filter(
      ([, v]) => v !== null && v !== undefined && v !== '',
    );

    let paramDefs: ExtraParamDef[] = [];
    try {
      paramDefs = meta.extraParams
        ? typeof meta.extraParams === 'string'
          ? JSON.parse(meta.extraParams)
          : meta.extraParams
        : [];
    } catch {
      /* */
    }

    return (
      <div className="mt-3 pt-3 border-t border-gray-100">
        <div className="text-xs text-gray-400 mb-2">托管配置</div>
        <div className="grid grid-cols-3 gap-x-3 gap-y-2 text-xs">
          <div>
            <div className="text-gray-400 mb-0.5">沙箱</div>
            <div
              className="font-mono text-gray-700 truncate"
              title={String((sp.sandboxName as string) || sandboxId)}
            >
              {String((sp.sandboxName as string) || sandboxId)}
            </div>
          </div>
          <div>
            <div className="text-gray-400 mb-0.5">Namespace</div>
            <div className="font-mono text-gray-700">{(sp.namespace as string) || 'default'}</div>
          </div>
          <div>
            <div className="text-gray-400 mb-0.5">鉴权类型</div>
            <div className="text-gray-700">
              {authType === 'apikey' ? <span className="text-green-600">API Key</span> : '无鉴权'}
            </div>
          </div>
        </div>
        {authType === 'apikey' && ((sp.secretName as string) || (sp.apiKey as string)) && (
          <AuthCredentialPanel apiKey={sp.apiKey as string} secretName={sp.secretName as string} />
        )}
        {extraEntries.length > 0 && (
          <div className="mt-3 rounded-lg border border-gray-200 overflow-hidden">
            <div className="px-3 py-1.5 bg-gray-50 border-b border-gray-200">
              <span className="text-xs font-medium text-gray-600">额外参数</span>
            </div>
            <div className="p-3 space-y-2.5">
              {extraEntries.map(([key, val]) => {
                const def = Array.isArray(paramDefs)
                  ? paramDefs.find((d: ExtraParamDef) => d.name === key)
                  : null;
                return (
                  <div key={key}>
                    <div className="flex items-center gap-1.5 mb-0.5">
                      <span className="text-xs font-mono text-gray-700">{key}</span>
                      {def?.required && <span className="text-red-400 text-[10px]">*</span>}
                      {def?.position && (
                        <Tag className="m-0 border-0 bg-gray-100 text-gray-500 text-[10px] leading-tight px-1.5 py-0">
                          {def.position}
                        </Tag>
                      )}
                    </div>
                    {def?.description && (
                      <div className="text-[10px] text-gray-400 mb-0.5">{def.description}</div>
                    )}
                    <div className="px-2 py-1 bg-gray-50 border border-gray-200 rounded text-xs font-mono text-gray-800 break-all">
                      {String(val)}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </div>
    );
  };

  const renderAdminConfigBlock = (json: string, extra?: React.ReactNode) => (
    <div>
      <div className="relative bg-gray-50 border border-gray-200 rounded-md p-3">
        <Button
          className="absolute top-2 right-2 z-10"
          icon={<CopyOutlined />}
          onClick={async () => {
            try {
              await copyToClipboard(json);
              message.success('已复制到剪贴板');
            } catch {
              message.error('复制失败，请手动复制');
            }
          }}
          size="small"
        />
        <div className="text-gray-800 font-mono text-xs overflow-x-auto">
          <pre className="whitespace-pre">{json}</pre>
        </div>
      </div>
      {extra}
    </div>
  );

  const meta = mcpMetaList[0];
  let extraParamDefs: ExtraParamDef[] = [];
  try {
    const raw = meta?.extraParams;
    extraParamDefs = raw ? (typeof raw === 'string' ? JSON.parse(raw) : raw) : [];
    if (!Array.isArray(extraParamDefs)) extraParamDefs = [];
  } catch {
    extraParamDefs = [];
  }

  const hasHot = !!(hotSseJson || hotHttpJson);
  const hotHostingType = mcpMetaList[0]?.endpointHostingType || '';
  const isSandboxHosted = hotHostingType === 'SANDBOX';
  const metaOrigin = (mcpMetaList[0]?.origin || '').toUpperCase();
  const isRemoteImport = metaOrigin === 'GATEWAY' || metaOrigin === 'NACOS';

  const hotTagLabel = isSandboxHosted
    ? '沙箱'
    : hotHostingType === 'NACOS'
      ? 'Nacos'
      : hotHostingType === 'GATEWAY'
        ? '网关'
        : '直连';
  const hotTagColor = isSandboxHosted ? 'green' : hotHostingType === 'DIRECT' ? 'cyan' : 'blue';

  const tabs: {
    key: string;
    label: React.ReactNode;
    children: React.ReactNode;
  }[] = [];

  if (localJson) {
    tabs.push({
      children: renderAdminConfigBlock(localJson),
      key: 'local',
      label: hasHot ? (
        <span>
          Stdio{' '}
          <Tag
            className="ml-1 mr-0"
            color="default"
            style={{ fontSize: 10, lineHeight: '16px', padding: '0 4px' }}
          >
            原始
          </Tag>
        </span>
      ) : (
        'Stdio'
      ),
    });
  }

  if (hotSseJson) {
    tabs.push({
      children: renderAdminConfigBlock(hotSseJson, renderSandboxConfig()),
      key: 'sse-hot',
      label: (
        <span>
          SSE{' '}
          <Tag
            className="ml-1 mr-0"
            color={hotTagColor}
            style={{ fontSize: 10, lineHeight: '16px', padding: '0 4px' }}
          >
            {hotTagLabel}
          </Tag>
        </span>
      ),
    });
  }
  if (sseJson && (!isRemoteImport || !hasHot) && (!hotSseJson || sseJson !== hotSseJson)) {
    tabs.push({
      children: renderAdminConfigBlock(sseJson),
      key: 'sse-cold',
      label: hasHot ? (
        <span>
          SSE{' '}
          <Tag
            className="ml-1 mr-0"
            color="default"
            style={{ fontSize: 10, lineHeight: '16px', padding: '0 4px' }}
          >
            原始
          </Tag>
        </span>
      ) : (
        'SSE'
      ),
    });
  }

  if (hotHttpJson) {
    tabs.push({
      children: renderAdminConfigBlock(hotHttpJson, renderSandboxConfig()),
      key: 'http-hot',
      label: (
        <span>
          HTTP{' '}
          <Tag
            className="ml-1 mr-0"
            color={hotTagColor}
            style={{ fontSize: 10, lineHeight: '16px', padding: '0 4px' }}
          >
            {hotTagLabel}
          </Tag>
        </span>
      ),
    });
  }
  if (httpJson && (!isRemoteImport || !hasHot) && (!hotHttpJson || httpJson !== hotHttpJson)) {
    tabs.push({
      children: renderAdminConfigBlock(httpJson),
      key: 'http-cold',
      label: hasHot ? (
        <span>
          HTTP{' '}
          <Tag
            className="ml-1 mr-0"
            color="default"
            style={{ fontSize: 10, lineHeight: '16px', padding: '0 4px' }}
          >
            原始
          </Tag>
        </span>
      ) : (
        'HTTP'
      ),
    });
  }

  return (
    <Card title="配置详情">
      <Row gutter={24}>
        <Col span={15}>
          <Card>
            <Tabs
              activeKey={mcpToolsTab}
              items={[
                {
                  children: fetchingTools ? (
                    <div className="text-center py-12">
                      <Spin tip="正在获取工具列表，请稍候..." />
                    </div>
                  ) : parsedTools.length > 0 ? (
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
                                              className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent mb-2"
                                              defaultValue={
                                                arg.default !== undefined
                                                  ? JSON.stringify(arg.default)
                                                  : ''
                                              }
                                              placeholder={arg.description || `请输入${arg.name}`}
                                              type="text"
                                            />
                                            {arg.enum && (
                                              <div className="text-xs text-gray-500">
                                                可选值:{' '}
                                                {arg.enum.map((value) => (
                                                  <code className="mr-1" key={value}>
                                                    {value}
                                                  </code>
                                                ))}
                                              </div>
                                            )}
                                          </div>
                                        ))}
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
                    <div className="text-center py-12">
                      <div className="text-gray-400 mb-3">暂无工具信息，请先获取工具列表</div>
                      <div className="text-xs text-gray-400">点击右上角「获取工具列表」按钮</div>
                    </div>
                  ),
                  key: 'tools',
                  label: `工具列表 (${parsedTools.length})`,
                },
                {
                  children:
                    extraParamDefs.length === 0 ? (
                      <div className="text-gray-400 text-center py-8">暂无部署参数</div>
                    ) : (
                      <div className="overflow-hidden rounded-lg border border-gray-200">
                        <table className="w-full text-sm">
                          <thead>
                            <tr className="bg-gray-50 text-left text-xs text-gray-500">
                              <th className="px-4 py-2.5 font-medium">参数名</th>
                              <th className="px-4 py-2.5 font-medium">必填</th>
                              <th className="px-4 py-2.5 font-medium">描述</th>
                              <th className="px-4 py-2.5 font-medium">默认值</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-gray-100">
                            {extraParamDefs.map((p: ExtraParamDef, i: number) => (
                              <tr className="hover:bg-gray-50/50 transition-colors" key={i}>
                                <td className="px-4 py-3 align-top">
                                  <div className="flex items-center gap-1.5">
                                    <code className="text-xs font-mono text-gray-800 bg-gray-100 px-1.5 py-0.5 rounded">
                                      {p.name}
                                    </code>
                                    {p.position && (
                                      <span className="text-[10px] text-gray-400 bg-gray-100 px-1.5 py-0.5 rounded">
                                        {p.position}
                                      </span>
                                    )}
                                  </div>
                                </td>
                                <td className="px-4 py-3 align-top">
                                  {p.required ? (
                                    <Tag color="red" style={{ margin: 0 }}>
                                      必填
                                    </Tag>
                                  ) : (
                                    <Tag style={{ margin: 0 }}>可选</Tag>
                                  )}
                                </td>
                                <td className="px-4 py-3 align-top">
                                  <div className="text-gray-600 text-xs leading-relaxed">
                                    {p.description || '-'}
                                  </div>
                                  {p.example !== undefined && (
                                    <div className="mt-1">
                                      <span className="text-[10px] text-gray-400">示例: </span>
                                      <code className="text-[11px] font-mono text-gray-700 bg-gray-100 px-1.5 py-0.5 rounded">
                                        {String(p.example)}
                                      </code>
                                    </div>
                                  )}
                                </td>
                                <td className="px-4 py-3 align-top">
                                  {p.default !== undefined ? (
                                    <code className="text-xs font-mono text-gray-700 bg-gray-100 px-1.5 py-0.5 rounded">
                                      {String(p.default)}
                                    </code>
                                  ) : (
                                    <span className="text-gray-300">-</span>
                                  )}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    ),
                  key: 'details',
                  label: '部署参数',
                },
              ]}
              onChange={setMcpToolsTab}
              tabBarExtraContent={
                mcpToolsTab === 'tools' ? (
                  <div className="flex gap-2">
                    <Button
                      icon={<EditOutlined />}
                      onClick={onEditTools}
                      size="small"
                      style={{ color: '#1677ff', fontSize: 12 }}
                      type="text"
                    >
                      编辑工具
                    </Button>
                    {mcpMetaList[0]?.endpointUrl && (
                      <Button
                        icon={<SyncOutlined spin={fetchingTools} />}
                        loading={fetchingTools}
                        onClick={onRefreshTools}
                        size="small"
                        style={{ color: '#1677ff', fontSize: 12 }}
                        type="text"
                      >
                        {parsedTools.length === 0 ? '获取工具列表' : '刷新工具'}
                      </Button>
                    )}
                  </div>
                ) : null
              }
            />
          </Card>
        </Col>

        <Col span={9}>
          <Card>
            <div className="mb-4">
              <h3 className="text-sm font-semibold mb-3">连接点配置</h3>

              {apiProduct.mcpConfig?.mcpServerConfig?.domains &&
                apiProduct.mcpConfig.mcpServerConfig.domains.length > 1 && (
                  <div className="mb-2">
                    <div className="flex border border-gray-200 rounded-md overflow-hidden">
                      <div className="flex-shrink-0 bg-gray-50 px-3 py-2 text-xs text-gray-600 border-r border-gray-200 flex items-center whitespace-nowrap">
                        域名
                      </div>
                      <div className="flex-1 min-w-0">
                        <Select
                          className="w-full"
                          onChange={onDomainChange}
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
                            <Select.Option key={option.value} value={option.value}>
                              <span
                                className="text-xs text-gray-900 font-mono"
                                title={option.label}
                              >
                                {option.label}
                              </span>
                            </Select.Option>
                          ))}
                        </Select>
                      </div>
                    </div>
                  </div>
                )}

              <Tabs
                defaultActiveKey={(() => {
                  if (hotSseJson) return 'sse-hot';
                  if (hotHttpJson) return 'http-hot';
                  if (localJson) return 'local';
                  if (sseJson) return 'sse-cold';
                  if (httpJson) return 'http-cold';
                  return 'local';
                })()}
                items={tabs}
                size="small"
              />
            </div>
          </Card>
        </Col>
      </Row>
    </Card>
  );
}

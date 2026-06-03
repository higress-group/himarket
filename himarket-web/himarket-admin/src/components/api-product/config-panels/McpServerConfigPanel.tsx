import { CopyOutlined } from '@ant-design/icons';
import { Button, Card, Col, Collapse, Row, Select, Tabs } from 'antd';
import { message } from 'antd';

import { useLocale } from '@/contexts/LocaleContext';
import { copyToClipboard } from '@/lib/utils';
import type { ApiProduct } from '@/types/api-product';

import type { DomainOption } from '../hooks/useMcpConnectionConfig';
import type { ParsedTool } from '../hooks/useParsedMcpTools';

interface McpServerConfigPanelProps {
  apiProduct: ApiProduct;
  parsedTools: ParsedTool[];
  httpJson: string;
  sseJson: string;
  localJson: string;
  domainOptions: DomainOption[];
  selectedDomainIndex: number;
  onDomainChange: (index: number) => void;
}

export function McpServerConfigPanel({
  apiProduct,
  domainOptions,
  httpJson,
  localJson,
  onDomainChange,
  parsedTools,
  selectedDomainIndex,
  sseJson,
}: McpServerConfigPanelProps) {
  const { t } = useLocale();
  const selectedDomainOption = domainOptions[selectedDomainIndex];

  const handleCopySelectedDomain = async () => {
    if (!selectedDomainOption?.label) return;

    try {
      await copyToClipboard(selectedDomainOption.label);
      message.success(t('common.domainCopied'));
    } catch {
      message.error(t('common.copyFailed'));
    }
  };

  const renderAdminConfigBlock = (json: string) => (
    <div>
      <div className="relative bg-gray-50 border border-gray-200 rounded-md p-3">
        <Button
          className="absolute top-2 right-2 z-10"
          icon={<CopyOutlined />}
          onClick={async () => {
            try {
              await copyToClipboard(json);
              message.success(t('common.copiedToClipboard'));
            } catch {
              message.error(t('common.copyFailed'));
            }
          }}
          size="small"
        />
        <div className="text-gray-800 font-mono text-xs overflow-x-auto">
          <pre className="whitespace-pre">{json}</pre>
        </div>
      </div>
    </div>
  );

  const tabs: {
    key: string;
    label: React.ReactNode;
    children: React.ReactNode;
  }[] = [];

  if (localJson) {
    tabs.push({
      children: renderAdminConfigBlock(localJson),
      key: 'local',
      label: 'Stdio',
    });
  }

  if (sseJson) {
    tabs.push({
      children: renderAdminConfigBlock(sseJson),
      key: 'sse',
      label: 'SSE',
    });
  }

  if (httpJson) {
    tabs.push({
      children: renderAdminConfigBlock(httpJson),
      key: 'http',
      label: 'Streamable HTTP',
    });
  }

  return (
    <Card title={t('product.linkApi.configDetail')}>
      <Row gutter={24}>
        <Col span={15}>
          <Card>
            <Tabs
              defaultActiveKey="tools"
              items={[
                {
                  children:
                    parsedTools.length > 0 ? (
                      <div className="border border-gray-200 rounded-lg bg-gray-50">
                        {parsedTools.map((tool, idx) => (
                          <div
                            className={
                              idx < parsedTools.length - 1 ? 'border-b border-gray-200' : ''
                            }
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
                                          <p className="font-medium text-gray-700 mb-3">
                                            {t('product.config.inputParameters')}:
                                          </p>
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
                                                  <span className="text-red-500 text-xs mr-2">
                                                    *
                                                  </span>
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
                                                placeholder={arg.description || arg.name}
                                                type="text"
                                              />
                                              {arg.enum && (
                                                <div className="text-xs text-gray-500">
                                                  {t('product.config.enumValues')}:{' '}
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
                      <div className="text-gray-500 text-center py-8">
                        {t('product.config.noTools')}
                      </div>
                    ),
                  key: 'tools',
                  label: t('product.config.tools', { count: parsedTools.length }),
                },
              ]}
            />
          </Card>
        </Col>

        <Col span={9}>
          <Card>
            <div className="mb-4">
              <h3 className="text-sm font-semibold mb-3">{t('product.config.connectionConfig')}</h3>

              {apiProduct.mcpConfig?.mcpServerConfig?.domains &&
                apiProduct.mcpConfig.mcpServerConfig.domains.length > 0 && (
                  <div className="mb-2">
                    <div className="flex border border-gray-200 rounded-md overflow-hidden">
                      <div className="flex-shrink-0 bg-gray-50 px-3 py-2 text-xs text-gray-600 border-r border-gray-200 flex items-center whitespace-nowrap">
                        {t('common.domain')}
                      </div>
                      <div className="flex-1 min-w-0">
                        <Select
                          className="w-full"
                          labelRender={() => (
                            <div className="inline-flex max-w-full items-center gap-1.5">
                              <span className="min-w-0 truncate font-mono text-xs text-gray-900">
                                {selectedDomainOption?.label || t('common.selectDomain')}
                              </span>
                              <Button
                                aria-label={t('product.config.copyDomain')}
                                disabled={!selectedDomainOption?.label}
                                icon={<CopyOutlined />}
                                onClick={(event) => {
                                  event.stopPropagation();
                                  handleCopySelectedDomain();
                                }}
                                onMouseDown={(event) => event.stopPropagation()}
                                size="small"
                                title={t('product.config.copyDomain')}
                                type="text"
                              />
                            </div>
                          )}
                          onChange={onDomainChange}
                          optionLabelProp="label"
                          placeholder={t('common.selectDomain')}
                          size="middle"
                          style={{
                            fontSize: '12px',
                            height: '100%',
                          }}
                          value={selectedDomainIndex}
                          variant="borderless"
                        >
                          {domainOptions.map((option) => (
                            <Select.Option
                              key={option.value}
                              label={option.label}
                              value={option.value}
                            >
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
                  if (localJson) return 'local';
                  if (sseJson) return 'sse';
                  if (httpJson) return 'http';
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

import {
  ApiOutlined,
  CodeOutlined,
  CopyOutlined,
  DownloadOutlined,
  FileTextOutlined,
} from '@ant-design/icons';
import { Tabs, Button, message, Select } from 'antd';
import * as yaml from 'js-yaml';
import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router-dom';

import { EmptyState } from '../components/EmptyState';
import MarkdownRender from '../components/MarkdownRender';
import { ProductDetailLayout } from '../components/ProductDetailLayout';
import { RestApiDocsViewer } from '../components/RestApiDocsViewer';
import APIs from '../lib/apis';
import { copyToClipboard, parseOpenAPISpec } from '../lib/utils';

import type { RestApiExample } from '../components/RestApiDocsViewer';
import type { IProductDetail } from '../lib/apis';
import type { OpenAPIEndpoint } from '../lib/utils';

const DEFAULT_REST_API_EXAMPLE: RestApiExample = {
  method: 'GET',
  path: '/{path}',
  serverUrl: '',
};

function stripTrailingSlash(value: string): string {
  return value.endsWith('/') ? value.slice(0, -1) : value;
}

function buildRequestUrl(example: RestApiExample): string {
  const serverUrl = stripTrailingSlash(example.serverUrl || 'https://api.example.com');
  return `${serverUrl}${example.path}`;
}

function buildCurlCommand(example: RestApiExample): string {
  return `curl -X ${example.method} \\
  '${buildRequestUrl(example)}' \\
  --header 'Accept: application/json' \\
  --header 'Content-Type: application/json'`;
}

function createEndpointKey(endpoint: OpenAPIEndpoint, index: number): string {
  return `${endpoint.method}:${endpoint.path}:${endpoint.operationId || index}`;
}

function ApiDetailPage() {
  const { apiProductId } = useParams();
  const { t } = useTranslation('apiDetail');
  const [, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [apiData, setApiData] = useState<IProductDetail>();
  const [selectedServerIndex, setSelectedServerIndex] = useState(0);
  const [selectedEndpointKey, setSelectedEndpointKey] = useState<string>();
  const [rightPanelTab, setRightPanelTab] = useState<'usage' | 'request'>('usage');
  const fetchApiDetail = React.useCallback(async () => {
    setLoading(true);
    setError('');
    if (!apiProductId) return;
    try {
      const response = await APIs.getProduct({ id: apiProductId });
      if (response.code === 'SUCCESS' && response.data) {
        setApiData(response.data);
      }
    } catch (error) {
      console.error('Failed to fetch API detail:', error);
      setError(t('errors.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [apiProductId, t]);

  useEffect(() => {
    if (!apiProductId) return;
    fetchApiDetail();
  }, [apiProductId, fetchApiDetail]);

  const parsedOpenApi = React.useMemo(
    () => (apiData?.apiConfig?.spec ? parseOpenAPISpec(apiData.apiConfig.spec) : null),
    [apiData?.apiConfig?.spec],
  );
  const serverOptions = parsedOpenApi?.servers ?? [];
  const endpointOptions = React.useMemo(
    () =>
      (parsedOpenApi?.endpoints ?? []).map((endpoint, index) => ({
        endpoint,
        label: `${endpoint.method.toUpperCase()} ${endpoint.path}`,
        value: createEndpointKey(endpoint, index),
      })),
    [parsedOpenApi?.endpoints],
  );

  useEffect(() => {
    setSelectedServerIndex(0);
    setSelectedEndpointKey(endpointOptions[0]?.value);
  }, [endpointOptions]);

  useEffect(() => {
    if (selectedServerIndex >= serverOptions.length) {
      setSelectedServerIndex(0);
    }
  }, [selectedServerIndex, serverOptions.length]);

  const selectedEndpointOption =
    endpointOptions.find((option) => option.value === selectedEndpointKey) || endpointOptions[0];
  const selectedServer = serverOptions[selectedServerIndex];
  const restApiExample: RestApiExample = selectedEndpointOption
    ? {
        method: selectedEndpointOption.endpoint.method,
        path: selectedEndpointOption.endpoint.path,
        serverUrl: stripTrailingSlash(selectedServer?.url || ''),
      }
    : DEFAULT_REST_API_EXAMPLE;

  const handleCopyCurlCommand = async () => {
    try {
      await copyToClipboard(buildCurlCommand(restApiExample));
      message.success(t('messages.curlCopied'), 1);
    } catch {
      message.error(t('messages.copyFailed'));
    }
  };

  const handleCopyRequestUrl = async () => {
    try {
      await copyToClipboard(buildRequestUrl(restApiExample));
      message.success(t('messages.endpointCopied'), 1);
    } catch {
      message.error(t('messages.copyFailed'));
    }
  };

  const handleDownloadSpec = (format: 'json' | 'yaml') => {
    if (!apiData?.apiConfig?.spec) return;

    try {
      const content =
        format === 'json'
          ? JSON.stringify(yaml.load(apiData.apiConfig.spec), null, 2)
          : apiData.apiConfig.spec;
      const blob = new Blob([content], {
        type: format === 'json' ? 'application/json' : 'text/yaml',
      });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${apiData.name || 'api'}-openapi.${format}`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
      message.success(t('messages.specDownloaded', { format: format.toUpperCase() }), 1);
    } catch (err) {
      console.warn(err);
      message.error(
        format === 'json' ? t('messages.jsonConvertFailed') : t('messages.specDownloadFailed'),
      );
    }
  };

  const curlCommand = buildCurlCommand(restApiExample);
  const requestUrl = buildRequestUrl(restApiExample);
  const hasOpenApiSpec = Boolean(apiData?.apiConfig?.spec);

  const methodBadge = (
    <span className="inline-flex min-w-[56px] items-center justify-center rounded-md border border-blue-200 bg-blue-50 px-2 py-1 font-mono text-xs font-bold text-blue-700">
      {restApiExample.method}
    </span>
  );

  const leftContent = apiData ? (
    <div className="overflow-hidden rounded-[14px] border border-[#DDE5F0] bg-white/90 shadow-[0_18px_50px_rgba(15,23,42,0.05)] backdrop-blur-sm">
      <Tabs
        className="[&_.ant-tabs-content-holder]:px-5 [&_.ant-tabs-content-holder]:pb-5 [&_.ant-tabs-nav]:mb-5 [&_.ant-tabs-nav]:px-5 [&_.ant-tabs-tab]:py-4"
        defaultActiveKey="overview"
        items={[
          {
            children: apiData.document ? (
              <div className="scrollbar-thin-soft max-h-[720px] min-h-[420px] overflow-y-auto pr-2">
                <MarkdownRender content={apiData.document} />
              </div>
            ) : (
              <EmptyState description={t('overview.empty')} />
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
            children: (
              <div>
                {apiData.apiConfig && apiData.apiConfig.spec ? (
                  <RestApiDocsViewer apiSpec={apiData.apiConfig.spec} />
                ) : (
                  <EmptyState description={t('openapi.empty')} />
                )}
              </div>
            ),
            key: 'openapi-spec',
            label: (
              <span className="flex items-center gap-1.5 font-semibold">
                <ApiOutlined className="text-sm" />
                {t('tabs.openapiSpec')}
              </span>
            ),
          },
        ]}
        size="large"
      />
    </div>
  ) : null;

  const usagePanel = (
    <div>
      <div className="rounded-[12px] border border-[#E8EDF5] bg-[#FBFCFE] p-4">
        <div className="flex items-start gap-3">
          <div className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-[12px] bg-colorPrimaryBg text-colorPrimary">
            <ApiOutlined className="text-base" />
          </div>
          <div className="min-w-0">
            <div className="text-sm font-semibold text-gray-950">{t('usage.title')}</div>
            <p className="mt-1 text-sm leading-6 text-gray-600">{t('usage.description')}</p>
          </div>
        </div>

        <div className="mt-4 space-y-3 border-t border-[#E8EDF5] pt-3">
          <div>
            <div className="mb-2 text-xs font-semibold text-gray-500">{t('usage.baseUrl')}</div>
            {serverOptions.length > 0 ? (
              <div className="overflow-hidden rounded-[10px] border border-[#DDE5F0] bg-white">
                <Select
                  className="w-full"
                  labelRender={() => (
                    <span className="min-w-0 truncate font-mono text-xs text-gray-900">
                      {stripTrailingSlash(selectedServer?.url || '')}
                    </span>
                  )}
                  onChange={setSelectedServerIndex}
                  optionLabelProp="label"
                  size="middle"
                  value={selectedServerIndex}
                  variant="borderless"
                >
                  {serverOptions.map((server, index) => (
                    <Select.Option key={`${server.url}-${index}`} label={server.url} value={index}>
                      <div className="py-1">
                        <div className="font-mono text-xs text-gray-900">
                          {stripTrailingSlash(server.url)}
                        </div>
                        {server.description && (
                          <div className="mt-1 text-xs text-gray-500">{server.description}</div>
                        )}
                      </div>
                    </Select.Option>
                  ))}
                </Select>
              </div>
            ) : (
              <div className="rounded-[10px] border border-dashed border-[#DDE5F0] bg-white py-3 text-center text-xs text-gray-400">
                {t('usage.noBaseUrl')}
              </div>
            )}
          </div>

          <div>
            <div className="mb-2 text-xs font-semibold text-gray-500">
              {t('usage.currentEndpoint')}
            </div>
            {endpointOptions.length > 0 ? (
              <div className="overflow-hidden rounded-[10px] border border-[#DDE5F0] bg-white">
                <Select
                  className="w-full"
                  labelRender={() => (
                    <div className="flex min-w-0 items-center gap-2">
                      {methodBadge}
                      <span className="min-w-0 flex-1 truncate font-mono text-xs text-gray-900">
                        {restApiExample.path}
                      </span>
                    </div>
                  )}
                  onChange={setSelectedEndpointKey}
                  optionLabelProp="label"
                  size="middle"
                  value={selectedEndpointOption?.value}
                  variant="borderless"
                >
                  {endpointOptions.map((option) => (
                    <Select.Option key={option.value} label={option.label} value={option.value}>
                      <div className="flex min-w-0 items-center gap-2 py-1">
                        <span className="inline-flex min-w-[56px] items-center justify-center rounded-md border border-blue-200 bg-blue-50 px-2 py-1 font-mono text-xs font-bold text-blue-700">
                          {option.endpoint.method}
                        </span>
                        <span className="min-w-0 flex-1 truncate font-mono text-xs text-gray-900">
                          {option.endpoint.path}
                        </span>
                      </div>
                    </Select.Option>
                  ))}
                </Select>
              </div>
            ) : (
              <div className="rounded-[10px] border border-dashed border-[#DDE5F0] bg-white py-3 text-center text-xs text-gray-400">
                {t('usage.noEndpoint')}
              </div>
            )}
          </div>

          <div>
            <div className="mb-2 text-xs font-semibold text-gray-500">{t('usage.requestUrl')}</div>
            <div className="flex items-center gap-2 rounded-[10px] border border-[#DDE5F0] bg-white px-3 py-2">
              <span className="min-w-0 flex-1 break-all font-mono text-xs leading-5 text-gray-900">
                {requestUrl}
              </span>
              <Button
                aria-label={t('usage.copyRequestUrl')}
                icon={<CopyOutlined />}
                onClick={handleCopyRequestUrl}
                size="small"
                title={t('usage.copyRequestUrl')}
                type="text"
              />
            </div>
          </div>

          <div>
            <div className="mb-2 text-xs font-semibold text-gray-500">{t('usage.openapi')}</div>
            <div className="grid grid-cols-2 gap-2">
              <Button
                disabled={!hasOpenApiSpec}
                icon={<DownloadOutlined />}
                onClick={() => handleDownloadSpec('yaml')}
                title={t('example.downloadYaml')}
              >
                YAML
              </Button>
              <Button
                disabled={!hasOpenApiSpec}
                icon={<DownloadOutlined />}
                onClick={() => handleDownloadSpec('json')}
                title={t('example.downloadJson')}
              >
                JSON
              </Button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );

  const requestPanel = (
    <div className="relative overflow-hidden rounded-[12px] border border-[#172033] bg-[#111827]">
      <Button
        aria-label={t('example.copyCurl')}
        className="absolute right-2 top-2 z-10 text-gray-400 hover:text-white"
        icon={<CopyOutlined />}
        onClick={handleCopyCurlCommand}
        size="small"
        title={t('example.copyCurl')}
        type="text"
      />
      <pre className="max-h-[260px] overflow-auto whitespace-pre p-4 pr-12 font-mono text-[12px] leading-5 text-gray-100">
        <code>{curlCommand}</code>
      </pre>
    </div>
  );

  const rightContent = (
    <section className="rounded-[14px] border border-[#DDE5F0] bg-white/90 p-5 shadow-[0_18px_50px_rgba(15,23,42,0.05)] backdrop-blur-sm">
      <div className="mb-3 flex rounded-lg bg-gray-100 p-1">
        <button
          className={`flex flex-1 items-center justify-center gap-1.5 rounded-md py-2 text-xs transition-all ${
            rightPanelTab === 'usage'
              ? 'bg-white font-medium text-gray-800 shadow-sm'
              : 'text-gray-500 hover:text-gray-700'
          }`}
          onClick={() => setRightPanelTab('usage')}
          type="button"
        >
          <ApiOutlined className="text-[13px]" />
          {t('usage.tab')}
        </button>
        <button
          className={`flex flex-1 items-center justify-center gap-1.5 rounded-md py-2 text-xs transition-all ${
            rightPanelTab === 'request'
              ? 'bg-white font-medium text-gray-800 shadow-sm'
              : 'text-gray-500 hover:text-gray-700'
          }`}
          onClick={() => setRightPanelTab('request')}
          type="button"
        >
          <CodeOutlined className="text-[13px]" />
          {t('example.title')}
        </button>
      </div>
      {rightPanelTab === 'usage' ? usagePanel : requestPanel}
    </section>
  );

  return (
    <ProductDetailLayout
      error={error || undefined}
      headerProps={
        apiData
          ? {
              defaultIcon: '/logo.svg',
              description: apiData.description,
              icon: apiData.icon,
              name: apiData.name,
              productType: 'REST_API',
              subscribable: apiData.subscribable,
              updatedAt: apiData.updatedAt,
            }
          : undefined
      }
      leftContent={leftContent}
      loading={!apiData && !error}
      rightContent={rightContent}
    />
  );
}

export default ApiDetailPage;

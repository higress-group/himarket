import { CopyOutlined, SearchOutlined } from '@ant-design/icons';
import { Button, Empty, Input, Segmented, Select, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { copyToClipboard, parseOpenAPISpec } from '../lib/utils';

import type { OpenAPIEndpoint, ParsedOpenAPI } from '../lib/utils';

interface RestApiDocsViewerProps {
  apiSpec: string;
  onExampleChange?: (example: RestApiExample) => void;
}

interface EndpointView extends OpenAPIEndpoint {
  key: string;
}

export interface RestApiExample {
  method: string;
  path: string;
  serverUrl: string;
}

interface ContentEntry {
  mediaType: string;
  schema?: Record<string, unknown>;
  example?: unknown;
  examples?: Record<string, unknown>;
}

interface BodyDisplay {
  example: string;
  schema: string;
}

type BodyPreviewMode = 'schema' | 'example';

const METHOD_STYLES: Record<string, string> = {
  DELETE: 'border-red-200 bg-red-50 text-red-600',
  GET: 'border-blue-200 bg-blue-50 text-blue-700',
  HEAD: 'border-gray-200 bg-gray-50 text-gray-600',
  OPTIONS: 'border-gray-200 bg-gray-50 text-gray-600',
  PATCH: 'border-cyan-200 bg-cyan-50 text-cyan-700',
  POST: 'border-emerald-200 bg-emerald-50 text-emerald-700',
  PUT: 'border-amber-200 bg-amber-50 text-amber-700',
  TRACE: 'border-gray-200 bg-gray-50 text-gray-600',
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function stringifyValue(value: unknown): string {
  if (value === undefined) return '';
  if (typeof value === 'string') return value;

  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

function createEndpointKey(endpoint: OpenAPIEndpoint, index: number): string {
  return `${endpoint.method}:${endpoint.path}:${endpoint.operationId || index}`;
}

function getMethodStyle(method: string): string {
  return METHOD_STYLES[method.toUpperCase()] || 'border-gray-200 bg-gray-50 text-gray-600';
}

function MethodBadge({ method }: { method: string }) {
  return (
    <span
      className={`inline-flex min-w-[64px] items-center justify-center rounded-md border px-2.5 py-1 text-xs font-bold tabular-nums ${getMethodStyle(
        method,
      )}`}
    >
      {method.toUpperCase()}
    </span>
  );
}

function CodeBlock({ value }: { value: string }) {
  return (
    <pre className="m-0 max-h-[320px] overflow-auto rounded-lg bg-slate-950 p-4 text-xs leading-6 text-slate-100">
      {value}
    </pre>
  );
}

function getSchemaSummary(schema?: Record<string, unknown>): string {
  if (!schema) return '-';

  const ref = schema.$ref;
  if (typeof ref === 'string') {
    return ref.split('/').pop() || ref;
  }

  const enumValues = schema.enum;
  if (Array.isArray(enumValues) && enumValues.length > 0) {
    return enumValues.map((value) => String(value)).join(' | ');
  }

  const type = schema.type;
  if (typeof type === 'string') {
    if (type === 'array' && isRecord(schema.items)) {
      return `array<${getSchemaSummary(schema.items)}>`;
    }
    return type;
  }

  const oneOf = schema.oneOf;
  if (Array.isArray(oneOf)) return 'oneOf';

  const allOf = schema.allOf;
  if (Array.isArray(allOf)) return 'allOf';

  return 'object';
}

function getContentEntries(content?: Record<string, unknown>): ContentEntry[] {
  if (!content) return [];

  return Object.entries(content).map(([mediaType, raw]) => {
    const item = isRecord(raw) ? raw : {};
    return {
      example: item.example,
      examples: isRecord(item.examples) ? item.examples : undefined,
      mediaType,
      schema: isRecord(item.schema) ? item.schema : undefined,
    };
  });
}

function getExample(entry: ContentEntry): unknown {
  if (entry.example !== undefined) return entry.example;

  const firstExample = entry.examples ? Object.values(entry.examples)[0] : undefined;
  if (isRecord(firstExample) && 'value' in firstExample) {
    return firstExample.value;
  }
  return firstExample;
}

function decodeJsonPointerSegment(segment: string): string {
  try {
    return decodeURIComponent(segment.replace(/~1/g, '/').replace(/~0/g, '~'));
  } catch {
    return segment.replace(/~1/g, '/').replace(/~0/g, '~');
  }
}

function getSchemaRefName(schema?: Record<string, unknown>): string | undefined {
  const ref = schema?.$ref;
  if (typeof ref !== 'string') return undefined;

  const refSegments = ref.split('/');
  return decodeJsonPointerSegment(refSegments[refSegments.length - 1] || ref);
}

function resolveSchemaRef(ref: string, parsed: ParsedOpenAPI): Record<string, unknown> | undefined {
  const prefix = '#/components/schemas/';
  if (!ref.startsWith(prefix)) return undefined;

  const schemaName = decodeJsonPointerSegment(ref.slice(prefix.length));
  return parsed.components?.schemas?.[schemaName];
}

function resolveSchemaValue(
  value: unknown,
  parsed: ParsedOpenAPI,
  seenRefs = new Set<string>(),
): unknown {
  if (Array.isArray(value)) {
    return value.map((item) => resolveSchemaValue(item, parsed, seenRefs));
  }

  if (!isRecord(value)) return value;

  const ref = value.$ref;
  if (typeof ref === 'string') {
    const referencedSchema = resolveSchemaRef(ref, parsed);
    if (!referencedSchema || seenRefs.has(ref)) return value;

    const nextSeenRefs = new Set(seenRefs);
    nextSeenRefs.add(ref);
    return resolveSchemaValue(referencedSchema, parsed, nextSeenRefs);
  }

  return Object.fromEntries(
    Object.entries(value).map(([key, child]) => [key, resolveSchemaValue(child, parsed, seenRefs)]),
  );
}

function omitSchemaExampleFields(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(omitSchemaExampleFields);
  if (!isRecord(value)) return value;

  return Object.fromEntries(
    Object.entries(value)
      .filter(([key]) => key !== 'example' && key !== 'examples')
      .map(([key, child]) => [key, omitSchemaExampleFields(child)]),
  );
}

function normalizeExampleValue(value: unknown): unknown {
  if (typeof value !== 'string') return value;

  const trimmed = value.trim();
  if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) return value;

  try {
    return JSON.parse(trimmed);
  } catch {
    return value;
  }
}

function getSchemaType(schema: Record<string, unknown>): string | undefined {
  const type = schema.type;
  if (typeof type === 'string') return type;
  if (Array.isArray(type)) {
    return type.find((item): item is string => typeof item === 'string' && item !== 'null');
  }
  return undefined;
}

function getPrimitiveExample(schema: Record<string, unknown>): unknown {
  const type = getSchemaType(schema);
  const format = typeof schema.format === 'string' ? schema.format : undefined;

  if (type === 'integer') return 0;
  if (type === 'number') return 0;
  if (type === 'boolean') return true;
  if (type === 'string') {
    if (format === 'date') return '2026-01-01';
    if (format === 'date-time') return '2026-01-01T00:00:00Z';
    if (format === 'email') return 'user@example.com';
    if (format === 'uri' || format === 'url') return 'https://example.com';
    if (format === 'uuid') return '00000000-0000-4000-8000-000000000000';
    return 'string';
  }
  return undefined;
}

function createExampleFromSchema(
  value: unknown,
  parsed: ParsedOpenAPI,
  seenRefs = new Set<string>(),
): unknown {
  if (!isRecord(value)) return undefined;

  const ref = value.$ref;
  if (typeof ref === 'string') {
    const referencedSchema = resolveSchemaRef(ref, parsed);
    if (!referencedSchema || seenRefs.has(ref)) return undefined;

    const nextSeenRefs = new Set(seenRefs);
    nextSeenRefs.add(ref);
    return createExampleFromSchema(referencedSchema, parsed, nextSeenRefs);
  }

  if (value.example !== undefined) return normalizeExampleValue(value.example);
  if (value.default !== undefined) return normalizeExampleValue(value.default);

  const constValue = value.const;
  if (constValue !== undefined) return constValue;

  const enumValues = value.enum;
  if (Array.isArray(enumValues) && enumValues.length > 0) return enumValues[0];

  const allOf = value.allOf;
  if (Array.isArray(allOf)) {
    const mergedExample: Record<string, unknown> = {};
    allOf.forEach((item) => {
      const itemExample = createExampleFromSchema(item, parsed, seenRefs);
      if (isRecord(itemExample)) {
        Object.assign(mergedExample, itemExample);
      }
    });
    if (Object.keys(mergedExample).length > 0) return mergedExample;
  }

  const oneOf = value.oneOf;
  if (Array.isArray(oneOf) && oneOf.length > 0) {
    return createExampleFromSchema(oneOf[0], parsed, seenRefs);
  }

  const anyOf = value.anyOf;
  if (Array.isArray(anyOf) && anyOf.length > 0) {
    return createExampleFromSchema(anyOf[0], parsed, seenRefs);
  }

  const properties = isRecord(value.properties) ? value.properties : undefined;
  if (getSchemaType(value) === 'object' || properties) {
    const objectExample: Record<string, unknown> = {};
    Object.entries(properties || {}).forEach(([name, propertySchema]) => {
      const propertyExample = createExampleFromSchema(propertySchema, parsed, seenRefs);
      objectExample[name] = propertyExample ?? null;
    });
    return objectExample;
  }

  if (getSchemaType(value) === 'array') {
    const itemExample = createExampleFromSchema(value.items, parsed, seenRefs);
    return itemExample === undefined ? [] : [itemExample];
  }

  return getPrimitiveExample(value);
}

function getBodyDisplay(entry: ContentEntry, parsed: ParsedOpenAPI): BodyDisplay {
  const example = getExample(entry);
  const schemaValue = entry.schema
    ? stringifyValue(omitSchemaExampleFields(resolveSchemaValue(entry.schema, parsed)))
    : '';

  if (example !== undefined) {
    return {
      example: stringifyValue(normalizeExampleValue(example)),
      schema: schemaValue,
    };
  }

  if (!entry.schema) return { example: '', schema: '' };

  const generatedExample = createExampleFromSchema(entry.schema, parsed);
  if (generatedExample !== undefined) {
    return {
      example: stringifyValue(generatedExample),
      schema: schemaValue,
    };
  }

  return {
    example: '',
    schema: schemaValue,
  };
}

function BodyPreview({ display }: { display: BodyDisplay }) {
  const { t } = useTranslation('apiDetail');
  const defaultMode: BodyPreviewMode = display.schema ? 'schema' : 'example';
  const [activeMode, setActiveMode] = useState<BodyPreviewMode>(defaultMode);

  useEffect(() => {
    setActiveMode(defaultMode);
  }, [defaultMode, display.example, display.schema]);

  const activeValue = activeMode === 'schema' ? display.schema : display.example;
  const emptyText = activeMode === 'schema' ? t('restDocs.noSchema') : t('restDocs.noExample');
  const copyLabel = activeMode === 'schema' ? t('restDocs.copySchema') : t('restDocs.copyExample');

  const handleCopyActiveValue = async () => {
    if (!activeValue) return;

    try {
      await copyToClipboard(activeValue);
      message.success(t('messages.copied'), 1);
    } catch {
      message.error(t('messages.copyFailed'));
    }
  };

  return (
    <div className="min-w-0">
      <div className="mb-3 flex items-center justify-between gap-3">
        <Segmented
          onChange={(value) => setActiveMode(value as BodyPreviewMode)}
          options={[
            { disabled: !display.schema, label: 'Schema', value: 'schema' },
            { disabled: !display.example, label: 'Example', value: 'example' },
          ]}
          size="small"
          value={activeMode}
        />
        <Button
          aria-label={copyLabel}
          disabled={!activeValue}
          icon={<CopyOutlined />}
          onClick={handleCopyActiveValue}
          size="small"
          title={copyLabel}
          type="text"
        />
      </div>
      {activeValue ? (
        <CodeBlock value={activeValue} />
      ) : (
        <div className="rounded-lg border border-dashed border-gray-200 bg-slate-50 px-4 py-8 text-center text-sm text-gray-500">
          {emptyText}
        </div>
      )}
    </div>
  );
}

function getResponseRows(endpoint: EndpointView) {
  return Object.entries(endpoint.responses || {}).sort(([left], [right]) =>
    left.localeCompare(right),
  );
}

function buildEndpoints(parsed: ParsedOpenAPI | null): EndpointView[] {
  return (parsed?.endpoints || []).map((endpoint, index) => ({
    ...endpoint,
    key: createEndpointKey(endpoint, index),
  }));
}

export function RestApiDocsViewer({ apiSpec, onExampleChange }: RestApiDocsViewerProps) {
  const { t } = useTranslation('apiDetail');
  const parsed = useMemo(() => parseOpenAPISpec(apiSpec), [apiSpec]);
  const endpoints = useMemo(() => buildEndpoints(parsed), [parsed]);
  const [selectedEndpointKey, setSelectedEndpointKey] = useState<string>();
  const [searchText, setSearchText] = useState('');
  const [selectedServerIndex, setSelectedServerIndex] = useState(0);

  useEffect(() => {
    setSelectedEndpointKey(endpoints[0]?.key);
    setSearchText('');
    setSelectedServerIndex(0);
  }, [endpoints]);

  const filteredEndpoints = useMemo(() => {
    const keyword = searchText.trim().toLowerCase();

    return endpoints.filter((endpoint) => {
      const keywordMatched =
        keyword.length === 0 ||
        [
          endpoint.path,
          endpoint.method,
          endpoint.summary,
          endpoint.description,
          endpoint.operationId,
          ...(endpoint.tags || []),
        ]
          .filter(Boolean)
          .join(' ')
          .toLowerCase()
          .includes(keyword);

      return keywordMatched;
    });
  }, [endpoints, searchText]);

  const selectedEndpoint =
    filteredEndpoints.find((endpoint) => endpoint.key === selectedEndpointKey) ||
    filteredEndpoints[0];

  const servers = parsed?.servers || [];
  const selectedServer = servers[selectedServerIndex];
  const requestBodyContent = selectedEndpoint
    ? getContentEntries(selectedEndpoint.requestBody?.content)
    : [];
  const responseRows = selectedEndpoint ? getResponseRows(selectedEndpoint) : [];

  const handleCopyServer = async (serverUrl: string) => {
    try {
      await copyToClipboard(serverUrl);
      message.success(t('messages.serverCopied'), 1);
    } catch {
      message.error(t('messages.copyFailed'));
    }
  };

  useEffect(() => {
    if (!selectedEndpoint) return;

    onExampleChange?.({
      method: selectedEndpoint.method,
      path: selectedEndpoint.path,
      serverUrl: selectedServer?.url || '',
    });
  }, [onExampleChange, selectedEndpoint, selectedServer?.url]);

  if (!parsed) {
    return (
      <div className="rounded-xl border border-gray-200 bg-white py-12">
        <Empty description={t('restDocs.parseFailed')} />
      </div>
    );
  }

  if (endpoints.length === 0) {
    return (
      <div className="rounded-xl border border-gray-200 bg-white py-12">
        <Empty description={t('restDocs.noEndpoints')} />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {servers.length > 0 ? (
        <div className="flex items-stretch overflow-hidden rounded-md border border-gray-200 bg-white">
          <div className="flex flex-shrink-0 items-center border-r border-gray-200 bg-gray-50 px-3 py-2 text-xs text-gray-600">
            Base URL
          </div>
          <div className="min-w-0 flex-1">
            <Select
              className="w-full"
              labelRender={() => (
                <div className="inline-flex max-w-full items-center gap-1.5">
                  <span className="min-w-0 truncate font-mono text-xs text-gray-900">
                    {selectedServer?.url || t('restDocs.selectServer')}
                  </span>
                  <Button
                    aria-label={t('restDocs.copyServerAddress')}
                    disabled={!selectedServer?.url}
                    icon={<CopyOutlined />}
                    onClick={(event) => {
                      event.stopPropagation();
                      if (selectedServer?.url) {
                        handleCopyServer(selectedServer.url);
                      }
                    }}
                    onMouseDown={(event) => event.stopPropagation()}
                    size="small"
                    title={t('restDocs.copyServerAddress')}
                    type="text"
                  />
                </div>
              )}
              onChange={setSelectedServerIndex}
              optionLabelProp="label"
              placeholder={t('restDocs.selectServer')}
              size="middle"
              style={{
                fontSize: '12px',
                height: '100%',
              }}
              value={selectedServerIndex}
              variant="borderless"
            >
              {servers.map((server, index) => (
                <Select.Option key={`${server.url}-${index}`} label={server.url} value={index}>
                  <div className="py-1">
                    <div className="font-mono text-xs text-gray-900">{server.url}</div>
                    {server.description && (
                      <div className="mt-1 text-xs text-gray-500">{server.description}</div>
                    )}
                  </div>
                </Select.Option>
              ))}
            </Select>
          </div>
        </div>
      ) : (
        <div className="rounded-md border border-dashed border-gray-200 bg-slate-50 px-4 py-3 text-sm text-gray-500">
          {t('restDocs.noServers')}
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-[340px_minmax(0,1fr)]">
        <aside className="overflow-hidden rounded-xl border border-gray-200 bg-white">
          <div className="border-b border-gray-100 bg-slate-50 p-4">
            <div>
              <h3 className="m-0 text-base font-semibold text-gray-900">
                {t('restDocs.endpointList')}
              </h3>
              <p className="m-0 mt-1 text-xs text-gray-500">{t('restDocs.endpointHint')}</p>
            </div>
            <div className="mt-4">
              <Input
                allowClear
                onChange={(event) => setSearchText(event.target.value)}
                placeholder={t('restDocs.searchPlaceholder')}
                prefix={<SearchOutlined className="text-gray-400" />}
                value={searchText}
              />
            </div>
          </div>

          <div className="max-h-[640px] space-y-2 overflow-auto p-2">
            {filteredEndpoints.length > 0 ? (
              filteredEndpoints.map((endpoint) => {
                const active = endpoint.key === selectedEndpoint?.key;
                return (
                  <button
                    aria-pressed={active}
                    className={`w-full rounded-lg border p-3 text-left transition-colors ${
                      active
                        ? 'border-blue-300 bg-blue-50'
                        : 'border-transparent bg-white hover:border-gray-200 hover:bg-gray-50'
                    }`}
                    key={endpoint.key}
                    onClick={() => setSelectedEndpointKey(endpoint.key)}
                    type="button"
                  >
                    <div className="flex items-center gap-3">
                      <MethodBadge method={endpoint.method} />
                      <span className="min-w-0 flex-1 truncate font-mono text-sm font-semibold text-gray-900">
                        {endpoint.path}
                      </span>
                    </div>
                    <div className="mt-2 line-clamp-2 text-xs leading-5 text-gray-600">
                      {endpoint.summary ||
                        endpoint.description ||
                        endpoint.operationId ||
                        t('restDocs.noDescription')}
                    </div>
                  </button>
                );
              })
            ) : (
              <Empty
                className="py-10"
                description={t('restDocs.noMatchedEndpoints')}
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              />
            )}
          </div>
        </aside>

        <section className="min-w-0 overflow-hidden rounded-xl border border-gray-200 bg-white">
          {selectedEndpoint ? (
            <>
              <div className="border-b border-gray-100 bg-slate-50 p-5">
                <div className="flex flex-wrap items-center gap-3">
                  <MethodBadge method={selectedEndpoint.method} />
                  <h3 className="m-0 min-w-0 break-all font-mono text-xl font-bold text-gray-900">
                    {selectedEndpoint.path}
                  </h3>
                </div>
                {(selectedEndpoint.summary || selectedEndpoint.description) && (
                  <p className="mt-3 max-w-[80ch] text-sm leading-6 text-gray-600">
                    {selectedEndpoint.summary || selectedEndpoint.description}
                  </p>
                )}
                <div className="mt-3 flex flex-wrap gap-2">
                  {selectedEndpoint.operationId && (
                    <span className="rounded-full bg-gray-100 px-2.5 py-1 font-mono text-xs text-gray-600">
                      operationId: {selectedEndpoint.operationId}
                    </span>
                  )}
                  {(selectedEndpoint.tags || [t('restDocs.endpointList')]).map((tag) => (
                    <span
                      className="rounded-full bg-gray-100 px-2.5 py-1 text-xs text-gray-600"
                      key={tag}
                    >
                      tag: {tag}
                    </span>
                  ))}
                </div>
              </div>

              <div className="space-y-4 p-5">
                <div className="overflow-hidden rounded-xl border border-gray-100">
                  <div className="border-b border-gray-100 px-4 py-3 text-sm font-semibold text-gray-900">
                    Parameters
                  </div>
                  {selectedEndpoint.parameters && selectedEndpoint.parameters.length > 0 ? (
                    <div className="max-w-full overflow-x-auto">
                      <table className="min-w-[620px] w-full text-left text-sm">
                        <thead className="bg-slate-50 text-xs text-gray-500">
                          <tr>
                            <th className="px-4 py-3 font-semibold">Name</th>
                            <th className="px-4 py-3 font-semibold">Description</th>
                            <th className="px-4 py-3 font-semibold">Location</th>
                            <th className="px-4 py-3 font-semibold">Schema</th>
                            <th className="px-4 py-3 font-semibold">Required</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100">
                          {selectedEndpoint.parameters.map((parameter) => (
                            <tr key={`${parameter.in}-${parameter.name}`}>
                              <td className="px-4 py-3 font-mono text-gray-900">
                                {parameter.name}
                              </td>
                              <td className="px-4 py-3 text-gray-600">
                                {parameter.description || '-'}
                              </td>
                              <td className="px-4 py-3 text-gray-600">{parameter.in}</td>
                              <td className="px-4 py-3 font-mono text-xs text-gray-700">
                                {getSchemaSummary(parameter.schema)}
                              </td>
                              <td className="px-4 py-3 text-gray-600">
                                {parameter.required ? 'true' : 'false'}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  ) : (
                    <div className="px-4 py-8 text-center text-sm text-gray-500">
                      {t('restDocs.noParameters')}
                    </div>
                  )}
                </div>

                <div className="overflow-hidden rounded-xl border border-gray-100">
                  <div className="border-b border-gray-100 px-4 py-3 text-sm font-semibold text-gray-900">
                    Request Body
                  </div>
                  {requestBodyContent.length > 0 ? (
                    <div className="space-y-3 p-4">
                      {requestBodyContent.map((entry) => {
                        const bodyDisplay = getBodyDisplay(entry, parsed);
                        const schemaName = getSchemaRefName(entry.schema);
                        return (
                          <div key={entry.mediaType}>
                            <div className="mb-2 flex flex-wrap items-center gap-2">
                              <span className="rounded-full bg-gray-100 px-2.5 py-1 font-mono text-xs text-gray-600">
                                {entry.mediaType}
                              </span>
                              {schemaName && (
                                <span className="rounded-full bg-blue-50 px-2.5 py-1 font-mono text-xs text-blue-700">
                                  schema: {schemaName}
                                </span>
                              )}
                              {selectedEndpoint.requestBody?.required && (
                                <span className="rounded-full bg-red-50 px-2.5 py-1 text-xs text-red-600">
                                  required
                                </span>
                              )}
                            </div>
                            <BodyPreview display={bodyDisplay} />
                          </div>
                        );
                      })}
                    </div>
                  ) : (
                    <div className="px-4 py-8 text-center text-sm text-gray-500">
                      {t('restDocs.noRequestBody')}
                    </div>
                  )}
                </div>

                <div className="overflow-hidden rounded-xl border border-gray-100">
                  <div className="border-b border-gray-100 px-4 py-3 text-sm font-semibold text-gray-900">
                    Responses
                  </div>
                  {responseRows.length > 0 ? (
                    <div>
                      <div className="max-w-full overflow-x-auto">
                        <table className="min-w-[520px] w-full text-left text-sm">
                          <thead className="bg-slate-50 text-xs text-gray-500">
                            <tr>
                              <th className="px-4 py-3 font-semibold">Code</th>
                              <th className="px-4 py-3 font-semibold">Description</th>
                              <th className="px-4 py-3 font-semibold">Content-Type</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-gray-100">
                            {responseRows.map(([code, response]) => {
                              const contentTypes = getContentEntries(response.content).map(
                                (entry) => entry.mediaType,
                              );
                              return (
                                <tr key={code}>
                                  <td className="px-4 py-3 font-mono text-gray-900">{code}</td>
                                  <td className="px-4 py-3 text-gray-600">
                                    {response.description || '-'}
                                  </td>
                                  <td className="px-4 py-3 font-mono text-xs text-gray-700">
                                    {contentTypes.length > 0 ? contentTypes.join(', ') : '-'}
                                  </td>
                                </tr>
                              );
                            })}
                          </tbody>
                        </table>
                      </div>
                      <div className="space-y-4 border-t border-gray-100 p-4">
                        {responseRows.flatMap(([code, response]) =>
                          getContentEntries(response.content).map((entry) => {
                            const bodyDisplay = getBodyDisplay(entry, parsed);
                            const schemaName = getSchemaRefName(entry.schema);
                            if (!bodyDisplay.schema && !bodyDisplay.example) return null;
                            return (
                              <div key={`${code}-${entry.mediaType}`}>
                                <div className="mb-2 flex flex-wrap items-center gap-2">
                                  <span className="rounded-full bg-gray-100 px-2.5 py-1 font-mono text-xs text-gray-600">
                                    {code}
                                  </span>
                                  <span className="rounded-full bg-gray-100 px-2.5 py-1 font-mono text-xs text-gray-600">
                                    {entry.mediaType}
                                  </span>
                                  {schemaName && (
                                    <span className="rounded-full bg-blue-50 px-2.5 py-1 font-mono text-xs text-blue-700">
                                      schema: {schemaName}
                                    </span>
                                  )}
                                </div>
                                <BodyPreview display={bodyDisplay} />
                              </div>
                            );
                          }),
                        )}
                      </div>
                    </div>
                  ) : (
                    <div className="px-4 py-8 text-center text-sm text-gray-500">
                      {t('restDocs.noResponses')}
                    </div>
                  )}
                </div>
              </div>
            </>
          ) : (
            <Empty className="py-16" description={t('restDocs.selectEndpoint')} />
          )}
        </section>
      </div>
    </div>
  );
}

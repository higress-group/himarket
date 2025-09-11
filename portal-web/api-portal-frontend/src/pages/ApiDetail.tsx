import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { Card, Table, Tag, Alert, Button, Modal, Space } from "antd";

import { FileTextOutlined } from "@ant-design/icons";
import { Layout } from "../components/Layout";
import { ProductHeader } from "../components/ProductHeader";
import api from "../lib/api";
import type { Product, ApiResponse, ModelApiProduct } from "../types";
import MonacoEditor from "react-monaco-editor";
import ReactMarkdown from "react-markdown";
import remarkGfm from 'remark-gfm';
import 'react-markdown-editor-lite/lib/index.css'

interface ApiEndpoint {
  key: string;
  method: string;
  path: string;
  description: string;
  operationId?: string;
  spec?: string;
}

interface ApiConfig {
  spec: string;
  meta: {
    source: string;
    type: string;
  };
}

interface UpdatedProduct extends Omit<Product, 'apiSpec'> {
  apiConfig?: ApiConfig;
  createAt: string;
  enabled: boolean;
}

function ApiDetailPage() {
  const { id } = useParams();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [apiData, setApiData] = useState<UpdatedProduct | null>(null);
  const [endpoints, setEndpoints] = useState<ApiEndpoint[]>([]);
  const [isSpecModalVisible, setIsSpecModalVisible] = useState(false);
  const [currentSpec, setCurrentSpec] = useState('');
  const [modelRoutes, setModelRoutes] = useState<Array<{ name: string; methods?: string[]; paths?: Array<{ type: string; value: string }>; ignoreUriCase?: boolean }>>([]);
  const [modelServices, setModelServices] = useState<Array<{ serviceName: string; modelName?: string | null; modelNamePattern?: string | null; protocol?: string | null; address?: string; protocols?: string[] }>>([]);

  useEffect(() => {
    if (!id) return;
    fetchApiDetail();
  }, [id]);

  const fetchApiDetail = async () => {
    setLoading(true);
    setError('');
    try {
      const response: ApiResponse<UpdatedProduct> = await api.get(`/products/${id}`);
      if (response.code === "SUCCESS" && response.data) {
        setApiData(response.data);
        // 如果是MODEL_API，准备渲染路由与服务
        if (response.data.type === 'MODEL_API' && (response.data as unknown as ModelApiProduct).modelApiConfig) {
          const m = (response.data as unknown as ModelApiProduct).modelApiConfig!;
          setModelRoutes(
            Array.isArray(m.routes) ? m.routes : []
          );
          setModelServices(
            Array.isArray(m.services) ? m.services.map(s => ({
              serviceName: s.serviceName,
              modelName: s.modelName ?? null,
              modelNamePattern: s.modelNamePattern ?? null,
              protocol: s.protocol ?? null,
              address: s.address,
              protocols: s.protocols,
            })) : []
          );
        }

        // 尝试从apiConfig.spec中解析端点信息
        if (response.data.apiConfig?.spec) {
          try {
            // 解析OpenAPI规范
            const spec = response.data.apiConfig.spec;
            const endpointsList: ApiEndpoint[] = [];
            const lines = spec.split('\n');
            let currentPath = '';
            let inPaths = false;
            for (let i = 0; i < lines.length; i++) {
              const line = lines[i];
              const trimmedLine = line.trim();
              const indentLevel = line.length - line.trimStart().length;
              if (trimmedLine === 'paths:' || trimmedLine.startsWith('paths:')) {
                inPaths = true;
                continue;
              }
              if (!inPaths) continue;
              if (inPaths && indentLevel === 2 && trimmedLine.startsWith('/') && trimmedLine.endsWith(':')) {
                currentPath = trimmedLine.slice(0, -1);
                continue;
              }
              if (inPaths && indentLevel === 4) {
                const httpMethods = ['get:', 'post:', 'put:', 'delete:', 'patch:', 'head:', 'options:'];
                for (const method of httpMethods) {
                  if (trimmedLine.startsWith(method)) {
                    const methodName = method.replace(':', '').toUpperCase();
                    const operationId = extractOperationId(lines, i);
                    endpointsList.push({
                      key: `${methodName}-${currentPath}`,
                      method: methodName,
                      path: currentPath,
                      description: operationId || `${methodName} ${currentPath}`,
                      operationId: operationId,
                      spec: spec
                    });
                    break;
                  }
                }
              }
            }
            if (endpointsList.length > 0) {
              setEndpoints(endpointsList);
            } else {
              setDefaultEndpoints();
            }
          } catch (error) {
            console.warn('解析API规范失败:', error);
            setDefaultEndpoints();
          }
        } else {
          setDefaultEndpoints();
        }
      }
    } catch (error) {
      console.error('获取API详情失败:', error);
      setError('加载失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  const extractOperationId = (lines: string[], startIndex: number): string => {
    const currentIndent = lines[startIndex].length - lines[startIndex].trimStart().length;
    for (let i = startIndex + 1; i < Math.min(startIndex + 20, lines.length); i++) {
      const line = lines[i];
      const trimmedLine = line.trim();
      const lineIndent = line.length - line.trimStart().length;
      if (lineIndent <= currentIndent && trimmedLine !== '') {
        break;
      }
      if (trimmedLine.startsWith('operationId:')) {
        return trimmedLine.replace('operationId:', '').trim();
      }
    }
    return '';
  };

  const setDefaultEndpoints = () => {
    setEndpoints([]);
  };

  const showSpecModal = (spec: string) => {
    setCurrentSpec(spec);
    setIsSpecModalVisible(true);
  };
  const columns = [
    {
      title: '方法',
      dataIndex: 'method',
      key: 'method',
      width: 100,
      render: (method: string) => (
        <Tag color={method === 'GET' ? 'green' : method === 'POST' ? 'blue' : method === 'PUT' ? 'orange' : method === 'DELETE' ? 'red' : 'default'}>
          {method}
        </Tag>
      )
    },
    {
      title: '路径',
      dataIndex: 'path',
      key: 'path',
      width: 200,
      render: (path: string) => (
        <code className="text-sm bg-gray-100 px-2 py-1 rounded">{path}</code>
      )
    },
  ];

  const providerBadge = (address?: string) => {
    if (!address) return null;
    const host = address.replace(/^https?:\/\//, '').toLowerCase();
    if (host.includes('dashscope.aliyuncs.com')) {
      return <Tag color="gold">阿里云百炼</Tag>;
    }
    return null;
  };

  if (error) {
    return (
      <Layout loading={loading}>
        <Alert message={error} type="error" showIcon className="my-8" />
      </Layout>
    );
  }

  if (!apiData) {
    return (
      <Layout loading={loading}>
        <Alert message="未找到API信息" type="warning" showIcon className="my-8" />
      </Layout>
    );
  }

  return (
    <Layout loading={loading}>
      <ProductHeader
        name={apiData.name}
        description={apiData.description}
        status={apiData.status}
        category={apiData.category}
        icon={apiData.icon || undefined}
        defaultIcon="/logo.png"
        enabled={apiData.enabled}
        showVersion={true}
        showEnabled={false}
      />

      <Card className="mb-6" title="使用指南">
        { apiData.document ? (
                <div className="prose custom-html-style h-[500px] overflow-auto">
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>{apiData.document}</ReactMarkdown>
                </div>
              ) : (
                <div className="text-gray-500 text-center py-8">
                  暂无文档内容
                </div>
              )}
      </Card>

      {apiData.apiConfig && (
        <Card 
          title={
            <div className="flex items-center justify-between">
              <span>API 端点</span>
              <Button 
                type="text" 
                icon={<FileTextOutlined />} 
                onClick={() => showSpecModal(apiData.apiConfig!.spec)}
                className="text-blue-500 hover:text-blue-700"
              >
                查看 OpenAPI 规范
              </Button>
            </div>
          } 
          className="mb-8"
        >
          <Table 
            columns={columns} 
            dataSource={endpoints}
            rowKey="key"
            pagination={false}
            size="small"
          />
        </Card>
      )}

      {apiData.type === 'MODEL_API' && (
        <>
          <Card className="mb-6" title="模型服务">
            <Table
              size="small"
              pagination={false}
              rowKey={(r) => r.serviceName}
              columns={[
                { title: '服务名', dataIndex: 'serviceName', key: 'serviceName', width: 180 },
                { title: '模型名', dataIndex: 'modelName', key: 'modelName', width: 180, render: (v: string | null) => v || '—' },
                { title: '协议', dataIndex: 'protocol', key: 'protocol', width: 140, render: (v: string | null) => (
                  v ? <Tag color="blue">{String(v).toUpperCase()}</Tag> : <span className="text-gray-400">—</span>
                ) },
                { title: '全部协议', dataIndex: 'protocols', key: 'protocols', render: (list?: string[]) => (
                  <Space wrap>
                    {(list || []).map(p => (
                      <Tag key={p} color="geekblue">{p}</Tag>
                    ))}
                  </Space>
                ) },
                { title: '地址', dataIndex: 'address', key: 'address', width: 320, render: (addr?: string) => (
                  <Space>
                    {addr ? <code className="text-sm bg-gray-100 px-2 py-1 rounded">{addr}</code> : <span className="text-gray-400">—</span>}
                    {providerBadge(addr)}
                  </Space>
                ) },
              ] as any}
              dataSource={modelServices}
            />
          </Card>

          <Card className="mb-6" title="模型路由">
            <Table
              size="small"
              pagination={false}
              rowKey={(r) => r.name}
              columns={[
                { title: '名称', dataIndex: 'name', key: 'name', width: 260 },
                { title: '方法', dataIndex: 'methods', key: 'methods', width: 180, render: (methods?: string[]) => (
                  <>
                    {(methods || []).map((m) => (
                      <Tag key={m} color="blue">{m}</Tag>
                    ))}
                  </>
                ) },
                { title: '路径', dataIndex: 'paths', key: 'paths', render: (paths?: Array<{ type: string; value: string }>) => {
                  const p = Array.isArray(paths) && paths.length > 0 ? paths[0] : undefined;
                  return p ? <code className="text-sm bg-gray-100 px-2 py-1 rounded">{p.value}</code> : <span className="text-gray-400">—</span>;
                } },
              ] as any}
              dataSource={modelRoutes}
            />
          </Card>
        </>
      )}

      {/* OpenAPI 规范模态框 */}
      <Modal
        title="OpenAPI 规范"
        open={isSpecModalVisible}
        onCancel={() => setIsSpecModalVisible(false)}
        footer={null}
        width="80%"
        style={{ top: 20 }}
        bodyStyle={{ height: '70vh', padding: 0 }}
      >
        <div style={{ height: '100%' }}>
          <MonacoEditor
            language="yaml"
            theme="vs-dark"
            value={currentSpec}
            options={{
              readOnly: true,
              minimap: { enabled: true },
              scrollBeyondLastLine: false,
              scrollbar: {
                vertical: "visible",
                horizontal: "visible",
              },
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
      </Modal>
    </Layout>
  );
}

export default ApiDetailPage; 
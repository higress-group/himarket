import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { Card, Table, Tag, Alert, Button, Modal } from "antd";

import { FileTextOutlined } from "@ant-design/icons";
import { Layout } from "../components/Layout";
import { ProductHeader } from "../components/ProductHeader";
import api from "../lib/api";
import type { Product, ApiResponse } from "../types";
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
        
        // 尝试从apiConfig.spec中解析端点信息
        if (response.data.apiConfig?.spec) {
          try {
            // 解析OpenAPI规范
            const spec = response.data.apiConfig.spec;
            const endpointsList: ApiEndpoint[] = [];
            
            // 改进的OpenAPI解析逻辑
            const lines = spec.split('\n');
            let currentPath = '';
            let inPaths = false;
            
            for (let i = 0; i < lines.length; i++) {
              const line = lines[i];
              const trimmedLine = line.trim();
              const indentLevel = line.length - line.trimStart().length;
              
              // 检测是否进入paths部分
              if (trimmedLine === 'paths:' || trimmedLine.startsWith('paths:')) {
                inPaths = true;
                continue;
              }
              
              // 如果不在paths部分，跳过
              if (!inPaths) continue;
              
              // 检测路径 - 必须是顶级缩进（通常是2个空格）
              if (inPaths && indentLevel === 2 && trimmedLine.startsWith('/') && trimmedLine.endsWith(':')) {
                currentPath = trimmedLine.slice(0, -1); // 移除末尾的冒号
                continue;
              }
              
              // 检测HTTP方法 - 必须是路径下的子级（通常是4个空格）
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
            
            console.log('解析到的端点:', endpointsList);
            if (endpointsList.length > 0) {
              setEndpoints(endpointsList);
            } else {
              console.log('未解析到端点，使用默认端点');
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
    // 从当前行开始向后查找operationId
    const currentIndent = lines[startIndex].length - lines[startIndex].trimStart().length;
    
    for (let i = startIndex + 1; i < Math.min(startIndex + 20, lines.length); i++) {
      const line = lines[i];
      const trimmedLine = line.trim();
      const lineIndent = line.length - line.trimStart().length;
      
      // 如果缩进级别小于等于当前级别，说明已经离开了当前方法块
      if (lineIndent <= currentIndent && trimmedLine !== '') {
        break;
      }
      
      // 查找operationId
      if (trimmedLine.startsWith('operationId:')) {
        return trimmedLine.replace('operationId:', '').trim();
      }
    }
    return '';
  };

  const setDefaultEndpoints = () => {
    setEndpoints([
      {
        key: "1",
        method: "GET",
        path: "/api/endpoints",
        description: "获取端点列表"
      }
    ]);
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
    // {
    //   title: '操作ID',
    //   dataIndex: 'operationId',
    //   key: 'operationId',
    //   width: 150,
    //   render: (operationId: string) => (
    //     <span className="text-sm text-gray-600">{operationId || '-'}</span>
    //   )
    // },
    // {
    //   title: '描述',
    //   dataIndex: 'description',
    //   key: 'description',
    // }
  ];



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
        // version="v1.0.0"
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

      <Card 
        title={
          <div className="flex items-center justify-between">
            <span>API 端点</span>
            {apiData.apiConfig && (
              <Button 
                type="text" 
                icon={<FileTextOutlined />} 
                onClick={() => showSpecModal(apiData.apiConfig!.spec)}
                className="text-blue-500 hover:text-blue-700"
              >
                查看 OpenAPI 规范
              </Button>
            )}
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
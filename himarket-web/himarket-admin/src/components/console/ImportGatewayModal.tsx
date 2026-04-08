import { PlusOutlined } from '@ant-design/icons';
import { Button, Table, Modal, Form, Input, message, Select } from 'antd';
import { useState } from 'react';

import { gatewayApi } from '@/lib/api';
import { getGatewayTypeLabel } from '@/lib/constant';
import type { Gateway, ApigConfig, GatewayType } from '@/types';

interface ImportGatewayModalProps {
  visible: boolean;
  gatewayType: 'APIG_API' | 'APIG_AI' | 'ADP_AI_GATEWAY' | 'APSARA_GATEWAY';
  onCancel: () => void;
  onSuccess: () => void;
}

export default function ImportGatewayModal({
  gatewayType,
  onCancel,
  onSuccess,
  visible,
}: ImportGatewayModalProps) {
  const [importForm] = Form.useForm();

  const [gatewayLoading, setGatewayLoading] = useState(false);
  const [gatewayList, setGatewayList] = useState<Gateway[]>([]);
  const [selectedGateway, setSelectedGateway] = useState<Gateway | null>(null);

  const [apigConfig, setApigConfig] = useState<ApigConfig>({
    accessKey: '',
    region: '',
    secretKey: '',
  });

  const [gatewayPagination, setGatewayPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  });

  // 监听表单中的认证方式，确保切换时联动渲染
  const authType = Form.useWatch('authType', importForm);

  // 获取网关列表
  const fetchGateways = async (values: Record<string, unknown>, page = 1, size = 10) => {
    setGatewayLoading(true);
    try {
      const res = await gatewayApi.getApigGateway({
        ...values,
        page,
        size,
      });

      setGatewayList(res.data?.content || []);
      setGatewayPagination({
        current: page,
        pageSize: size,
        total: res.data?.totalElements || 0,
      });
    } catch {
      // message.error('获取网关列表失败')
    } finally {
      setGatewayLoading(false);
    }
  };

  const fetchAdpGateways = async (values: Record<string, unknown>, page = 1, size = 50) => {
    setGatewayLoading(true);
    try {
      const res = await gatewayApi.getAdpGateways({ ...values, page, size });
      setGatewayList(res.data?.content || []);
      setGatewayPagination({
        current: page,
        pageSize: size,
        total: res.data?.totalElements || 0,
      });
    } catch {
      // message.error('获取网关列表失败')
    } finally {
      setGatewayLoading(false);
    }
  };

  const fetchApsaraGateways = async (values: Record<string, unknown>, page = 1, size = 50) => {
    setGatewayLoading(true);
    try {
      const res = await gatewayApi.getApsaraGateways({ ...values, page, size });
      setGatewayList(res.data?.content || []);
      setGatewayPagination({
        current: page,
        pageSize: size,
        total: res.data?.totalElements || 0,
      });
    } finally {
      setGatewayLoading(false);
    }
  };

  // 处理网关选择
  const handleGatewaySelect = (gateway: Gateway) => {
    setSelectedGateway(gateway);
  };

  // 处理网关分页变化
  const handleGatewayPaginationChange = (page: number, pageSize: number) => {
    const values = importForm.getFieldsValue();
    const data = JSON.parse(sessionStorage.getItem('importFormConfig') || '{}');
    const config = JSON.stringify(values) === '{}' ? data : values;
    if (gatewayType === 'APSARA_GATEWAY') {
      fetchApsaraGateways({ ...config, gatewayType }, page, pageSize);
    } else if (gatewayType === 'ADP_AI_GATEWAY') {
      fetchAdpGateways({ ...config, gatewayType }, page, pageSize);
    } else {
      fetchGateways({ ...config, gatewayType }, page, pageSize);
    }
  };

  // 处理导入
  const handleImport = () => {
    if (!selectedGateway) {
      message.warning('请选择一个Gateway');
      return;
    }
    const payload: Record<string, unknown> = {
      ...selectedGateway,
      gatewayType: gatewayType,
    };
    if (gatewayType === 'ADP_AI_GATEWAY') {
      payload.adpAIGatewayConfig = apigConfig;
    } else if (gatewayType === 'APSARA_GATEWAY') {
      payload.apsaraGatewayConfig = apigConfig;
    } else {
      payload.apigConfig = apigConfig;
    }
    gatewayApi
      .importGateway(payload)
      .then(() => {
        message.success('导入成功！');
        handleCancel();
        onSuccess();
      })
      .catch(() => {
        // message.error(error.response?.data?.message || '导入失败！')
      });
  };

  // 重置弹窗状态
  const handleCancel = () => {
    setSelectedGateway(null);
    setGatewayList([]);
    setGatewayPagination({ current: 1, pageSize: 10, total: 0 });
    importForm.resetFields();
    onCancel();
  };

  return (
    <Modal footer={null} onCancel={handleCancel} open={visible} title="导入网关实例" width={600}>
      <Form form={importForm} layout="vertical" preserve={false}>
        {gatewayList.length === 0 && ['APIG_API', 'APIG_AI'].includes(gatewayType) && (
          <div className="mb-4">
            <h3 className="text-lg font-medium mb-3">认证信息</h3>
            <Form.Item
              label="Region"
              name="region"
              rules={[{ message: '请输入region', required: true }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              label="Access Key"
              name="accessKey"
              rules={[{ message: '请输入accessKey', required: true }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              label="Secret Key"
              name="secretKey"
              rules={[{ message: '请输入secretKey', required: true }]}
            >
              <Input.Password />
            </Form.Item>
            <Button
              loading={gatewayLoading}
              onClick={() => {
                importForm.validateFields().then((values) => {
                  setApigConfig(values);
                  sessionStorage.setItem('importFormConfig', JSON.stringify(values));
                  fetchGateways({ ...values, gatewayType: gatewayType });
                });
              }}
              type="primary"
            >
              获取网关列表
            </Button>
          </div>
        )}

        {['ADP_AI_GATEWAY'].includes(gatewayType) && gatewayList.length === 0 && (
          <div className="mb-4">
            <h3 className="text-lg font-medium mb-3">认证信息</h3>
            <Form.Item
              label="服务地址"
              name="baseUrl"
              rules={[
                { message: '请输入服务地址', required: true },
                { message: '必须以 http:// 或 https:// 开头', pattern: /^https?:\/\//i },
              ]}
            >
              <Input placeholder="如：http://apigateway.example.com 或者 http://10.236.6.144" />
            </Form.Item>
            <Form.Item
              initialValue={80}
              label="端口"
              name="port"
              rules={[
                { message: '请输入端口号', required: true },
                {
                  validator: (_, value) => {
                    if (value === undefined || value === null || value === '')
                      return Promise.resolve();
                    const n = Number(value);
                    return n >= 1 && n <= 65535
                      ? Promise.resolve()
                      : Promise.reject(new Error('端口范围需在 1-65535'));
                  },
                },
              ]}
            >
              <Input placeholder="如：8080" type="text" />
            </Form.Item>
            <Form.Item
              initialValue="Seed"
              label="认证方式"
              name="authType"
              rules={[{ message: '请选择认证方式', required: true }]}
            >
              <Select>
                <Select.Option value="Seed">Seed</Select.Option>
                <Select.Option value="Header">固定Header</Select.Option>
              </Select>
            </Form.Item>
            {authType === 'Seed' && (
              <Form.Item
                label="Seed"
                name="authSeed"
                rules={[{ message: '请输入Seed', required: true }]}
              >
                <Input placeholder="通过configmap获取" />
              </Form.Item>
            )}
            {authType === 'Header' && (
              <Form.Item label="Headers">
                <Form.List initialValue={[{ key: '', value: '' }]} name="authHeaders">
                  {(fields, { add, remove }) => (
                    <>
                      {fields.map(({ key, name, ...restField }) => (
                        <div
                          key={key}
                          style={{ alignItems: 'center', display: 'flex', marginBottom: 8 }}
                        >
                          <Form.Item
                            {...restField}
                            name={[name, 'key']}
                            rules={[{ message: '请输入Header名称', required: true }]}
                            style={{ flex: 1, marginBottom: 0, marginRight: 8 }}
                          >
                            <Input placeholder="Header名称，如：X-Auth-Token" />
                          </Form.Item>
                          <Form.Item
                            {...restField}
                            name={[name, 'value']}
                            rules={[{ message: '请输入Header值', required: true }]}
                            style={{ flex: 1, marginBottom: 0, marginRight: 8 }}
                          >
                            <Input placeholder="Header值" />
                          </Form.Item>
                          {fields.length > 1 && (
                            <Button
                              danger
                              onClick={() => remove(name)}
                              style={{ marginBottom: 0 }}
                              type="text"
                            >
                              删除
                            </Button>
                          )}
                        </div>
                      ))}
                      <Form.Item style={{ marginBottom: 0 }}>
                        <Button
                          block
                          icon={<PlusOutlined />}
                          onClick={() => add({ key: '', value: '' })}
                          type="dashed"
                        >
                          添加Header
                        </Button>
                      </Form.Item>
                    </>
                  )}
                </Form.List>
              </Form.Item>
            )}
            <Button
              loading={gatewayLoading}
              onClick={() => {
                importForm.validateFields().then((values) => {
                  // 处理认证参数
                  const processedValues = { ...values };

                  // 根据认证方式设置相应的参数
                  if (values.authType === 'Seed') {
                    processedValues.authSeed = values.authSeed;
                    delete processedValues.authHeaders;
                  } else if (values.authType === 'Header') {
                    processedValues.authHeaders = values.authHeaders;
                    delete processedValues.authSeed;
                  }

                  setApigConfig(processedValues);
                  sessionStorage.setItem('importFormConfig', JSON.stringify(processedValues));
                  fetchAdpGateways({ ...processedValues, gatewayType: gatewayType });
                });
              }}
              type="primary"
            >
              获取网关列表
            </Button>
          </div>
        )}

        {gatewayList.length === 0 && gatewayType === 'APSARA_GATEWAY' && (
          <div className="mb-4">
            <h3 className="text-lg font-medium mb-3">认证信息</h3>
            <Form.Item
              label="RegionId"
              name="regionId"
              rules={[{ message: '请输入RegionId', required: true }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              label="AccessKeyId"
              name="accessKeyId"
              rules={[{ message: '请输入AccessKeyId', required: true }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              label="AccessKeySecret"
              name="accessKeySecret"
              rules={[{ message: '请输入AccessKeySecret', required: true }]}
            >
              <Input.Password />
            </Form.Item>
            <Form.Item label="SecurityToken" name="securityToken">
              <Input />
            </Form.Item>
            <Form.Item
              label="Domain"
              name="domain"
              rules={[{ message: '请输入Domain', required: true }]}
            >
              <Input placeholder="csb-cop-api-biz.inter.envXX.example.com" />
            </Form.Item>
            <Form.Item
              initialValue="csb2"
              label="Product"
              name="product"
              rules={[{ message: '请输入Product', required: true }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              initialValue="2023-02-06"
              label="Version"
              name="version"
              rules={[{ message: '请输入Version', required: true }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              label="x-acs-organizationid"
              name="xAcsOrganizationId"
              rules={[{ message: '请输入组织ID' }]}
            >
              <Input />
            </Form.Item>
            <Form.Item label="x-acs-caller-sdk-source" name="xAcsCallerSdkSource">
              <Input />
            </Form.Item>
            <Form.Item label="x-acs-resourcegroupid" name="xAcsResourceGroupId">
              <Input />
            </Form.Item>
            <Form.Item label="x-acs-caller-type" name="xAcsCallerType">
              <Input />
            </Form.Item>
            <div className="flex gap-2">
              <Button
                onClick={() => {
                  try {
                    const raw = localStorage.getItem('apsaraImportConfig');
                    if (!raw) {
                      message.info('暂无历史参数');
                      return;
                    }
                    const data = JSON.parse(raw);
                    importForm.setFieldsValue(data);
                    message.success('已填充上次参数');
                  } catch {
                    message.error('读取历史参数失败');
                  }
                }}
              >
                使用上次配置
              </Button>
              <Button
                onClick={() => {
                  importForm.validateFields().then((values) => {
                    setApigConfig(values);
                    sessionStorage.setItem('importFormConfig', JSON.stringify(values));
                    localStorage.setItem('apsaraImportConfig', JSON.stringify(values));
                    // APSARA 需要远程拉取列表
                    fetchApsaraGateways({ ...values, gatewayType: 'APSARA_GATEWAY' });
                  });
                }}
                type="primary"
              >
                获取网关列表
              </Button>
            </div>
          </div>
        )}

        {gatewayList.length > 0 && (
          <div className="mb-4">
            <h3 className="text-lg font-medium mb-3">选择网关实例</h3>
            <Table
              columns={[
                { dataIndex: 'gatewayId', title: 'ID' },
                {
                  dataIndex: 'gatewayType',
                  render: (gatewayType: string) => getGatewayTypeLabel(gatewayType as GatewayType),
                  title: '类型',
                },
                { dataIndex: 'gatewayName', title: '名称' },
              ]}
              dataSource={gatewayList}
              pagination={{
                current: gatewayPagination.current,
                onChange: handleGatewayPaginationChange,
                pageSize: gatewayPagination.pageSize,
                showQuickJumper: true,
                showSizeChanger: true,
                showTotal: (total) => `共 ${total} 条`,
                total: gatewayPagination.total,
              }}
              rowKey="gatewayId"
              rowSelection={{
                onChange: (_selectedRowKeys, selectedRows) => {
                  const selected = selectedRows[0];
                  if (selected) {
                    handleGatewaySelect(selected);
                  }
                },
                selectedRowKeys: selectedGateway ? [selectedGateway.gatewayId] : [],
                type: 'radio',
              }}
              size="small"
            />
          </div>
        )}

        {selectedGateway && (
          <div className="text-right">
            <Button onClick={handleImport} type="primary">
              完成导入
            </Button>
          </div>
        )}
      </Form>
    </Modal>
  );
}

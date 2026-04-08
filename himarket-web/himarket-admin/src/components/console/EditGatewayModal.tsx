import { PlusOutlined } from '@ant-design/icons';
import { Modal, Form, Input, message, Select, Button, Switch } from 'antd';
import { useEffect, useState } from 'react';

import { gatewayApi } from '@/lib/api';
import { getGatewayTypeLabel } from '@/lib/constant';
import type { Gateway, GatewayType } from '@/types';

interface EditGatewayModalProps {
  visible: boolean;
  gateway: Gateway | null;
  onCancel: () => void;
  onSuccess: () => void;
}

export default function EditGatewayModal({
  gateway,
  onCancel,
  onSuccess,
  visible,
}: EditGatewayModalProps) {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const [updateAuth, setUpdateAuth] = useState(false); // 是否更新认证信息

  // 监听表单中的认证方式（ADP 网关使用）
  const authType = Form.useWatch('authType', form);

  // 表单初始化
  useEffect(() => {
    if (visible && gateway) {
      // 重置更新认证信息状态
      setUpdateAuth(false);

      const formValues: Record<string, string> = {
        gatewayName: gateway.gatewayName,
      };

      // 不初始化认证配置字段，因为默认折叠不修改
      form.setFieldsValue(formValues);
    }
  }, [visible, gateway, form]);

  // 处理提交
  const handleSubmit = async () => {
    if (!gateway) return;

    try {
      const values = await form.validateFields();
      setSubmitting(true);

      const payload: Record<string, unknown> = {
        gatewayName: values.gatewayName,
        gatewayType: gateway.gatewayType,
      };

      // 只有在用户选择更新认证信息时，才构建认证配置对象
      if (updateAuth) {
        if (gateway.gatewayType === 'APIG_API' || gateway.gatewayType === 'APIG_AI') {
          payload.apigConfig = {
            accessKey: values.accessKey,
            region: values.region,
            secretKey: values.secretKey,
          };
        } else if (gateway.gatewayType === 'HIGRESS') {
          payload.higressConfig = {
            address: values.address,
            gatewayAddress: values.gatewayAddress,
            password: values.password,
            username: values.username,
          };
        } else if (gateway.gatewayType === 'ADP_AI_GATEWAY') {
          const adpAIGatewayConfig: Record<string, unknown> = {
            authType: values.authType,
            baseUrl: values.baseUrl,
            port: values.port,
          };
          if (values.authType === 'Seed') {
            adpAIGatewayConfig.authSeed = values.authSeed;
          } else if (values.authType === 'Header') {
            adpAIGatewayConfig.authHeaders = values.authHeaders;
          }
          payload.adpAIGatewayConfig = adpAIGatewayConfig;
        } else if (gateway.gatewayType === 'APSARA_GATEWAY') {
          payload.apsaraGatewayConfig = {
            accessKeyId: values.accessKeyId,
            accessKeySecret: values.accessKeySecret,
            domain: values.domain,
            product: values.product,
            regionId: values.regionId,
            version: values.version,
            xAcsOrganizationId: values.xAcsOrganizationId,
            ...(values.securityToken && { securityToken: values.securityToken }),
            ...(values.xAcsCallerSdkSource && { xAcsCallerSdkSource: values.xAcsCallerSdkSource }),
            ...(values.xAcsResourceGroupId && { xAcsResourceGroupId: values.xAcsResourceGroupId }),
            ...(values.xAcsCallerType && { xAcsCallerType: values.xAcsCallerType }),
          };
        }
      }

      await gatewayApi.updateGateway(gateway.gatewayId, payload);
      message.success('更新成功');
      form.resetFields();
      onSuccess();
    } catch (_error) {
      // 错误已在拦截器中处理
    } finally {
      setSubmitting(false);
    }
  };

  // 处理取消
  const handleCancel = () => {
    form.resetFields();
    setUpdateAuth(false);
    onCancel();
  };

  // 渲染配置字段（根据网关类型）
  const renderConfigFields = () => {
    if (!gateway) return null;

    switch (gateway.gatewayType as GatewayType) {
      case 'APIG_API':
      case 'APIG_AI':
        return (
          <>
            <Form.Item
              label="Region"
              name="region"
              rules={[{ message: '请输入Region', required: updateAuth }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              label="Access Key"
              name="accessKey"
              rules={[{ message: '请输入Access Key', required: updateAuth }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              label="Secret Key"
              name="secretKey"
              rules={[{ message: '请输入Secret Key', required: updateAuth }]}
            >
              <Input.Password />
            </Form.Item>
          </>
        );

      case 'HIGRESS':
        return (
          <>
            <Form.Item
              label="Console地址"
              name="address"
              rules={[
                { message: '请输入Console地址', required: updateAuth },
                { message: '必须以 http:// 或 https:// 开头', pattern: /^https?:\/\//i },
              ]}
            >
              <Input placeholder="如：http://console.higress.io" />
            </Form.Item>
            <Form.Item
              label="用户名"
              name="username"
              rules={[{ message: '请输入用户名', required: updateAuth }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              label="密码"
              name="password"
              rules={[{ message: '请输入密码', required: updateAuth }]}
            >
              <Input.Password />
            </Form.Item>
            <Form.Item
              label="Gateway地址"
              name="gatewayAddress"
              rules={[{ message: '必须以 http:// 或 https:// 开头', pattern: /^https?:\/\//i }]}
            >
              <Input placeholder="如：http://gateway.higress.io（可选）" />
            </Form.Item>
          </>
        );

      case 'ADP_AI_GATEWAY':
        return (
          <>
            <Form.Item
              label="服务地址"
              name="baseUrl"
              rules={[
                { message: '请输入服务地址', required: updateAuth },
                { message: '必须以 http:// 或 https:// 开头', pattern: /^https?:\/\//i },
              ]}
            >
              <Input placeholder="如：http://apigateway.example.com 或者 http://10.236.6.144" />
            </Form.Item>
            <Form.Item
              label="端口"
              name="port"
              rules={[
                { message: '请输入端口号', required: updateAuth },
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
              rules={[{ message: '请选择认证方式', required: updateAuth }]}
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
                rules={[{ message: '请输入Seed', required: updateAuth }]}
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
          </>
        );

      case 'APSARA_GATEWAY':
        return (
          <>
            <Form.Item
              label="RegionId"
              name="regionId"
              rules={[{ message: '请输入RegionId', required: updateAuth }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              label="AccessKeyId"
              name="accessKeyId"
              rules={[{ message: '请输入AccessKeyId', required: updateAuth }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              label="AccessKeySecret"
              name="accessKeySecret"
              rules={[{ message: '请输入AccessKeySecret', required: updateAuth }]}
            >
              <Input.Password />
            </Form.Item>
            <Form.Item label="SecurityToken" name="securityToken">
              <Input placeholder="可选" />
            </Form.Item>
            <Form.Item
              label="Domain"
              name="domain"
              rules={[{ message: '请输入Domain', required: updateAuth }]}
            >
              <Input placeholder="csb-cop-api-biz.inter.envXX.example.com" />
            </Form.Item>
            <Form.Item
              label="Product"
              name="product"
              rules={[{ message: '请输入Product', required: updateAuth }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              label="Version"
              name="version"
              rules={[{ message: '请输入Version', required: updateAuth }]}
            >
              <Input />
            </Form.Item>
            <Form.Item label="x-acs-organizationid" name="xAcsOrganizationId">
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
          </>
        );

      default:
        return null;
    }
  };

  return (
    <Modal
      footer={[
        <Button key="cancel" onClick={handleCancel}>
          取消
        </Button>,
        <Button key="submit" loading={submitting} onClick={handleSubmit} type="primary">
          保存
        </Button>,
      ]}
      onCancel={handleCancel}
      open={visible}
      title="编辑网关"
      width={600}
    >
      <Form form={form} layout="vertical">
        {/* 基本信息 */}
        <div className="mb-6">
          <h3 className="text-lg font-medium mb-4">基本信息</h3>
          <Form.Item label="网关类型">
            <Input disabled value={getGatewayTypeLabel(gateway?.gatewayType as GatewayType)} />
          </Form.Item>
          <Form.Item label="网关ID">
            <Input disabled value={gateway?.gatewayId} />
          </Form.Item>
          <Form.Item
            label="网关名称"
            name="gatewayName"
            rules={[{ message: '请输入网关名称', required: true }]}
          >
            <Input />
          </Form.Item>
        </div>

        {/* 认证配置 */}
        <div>
          <h3 className="text-lg font-medium mb-4">认证信息</h3>
          <div className="flex items-center mb-4">
            <Switch
              checked={updateAuth}
              onChange={(checked) => {
                setUpdateAuth(checked);
                // 如果取消更新，清空认证字段
                if (!checked) {
                  const authFields = [
                    'region',
                    'accessKey',
                    'secretKey',
                    'address',
                    'username',
                    'password',
                    'gatewayAddress',
                    'baseUrl',
                    'port',
                    'authType',
                    'authSeed',
                    'authHeaders',
                    'regionId',
                    'accessKeyId',
                    'accessKeySecret',
                    'securityToken',
                    'domain',
                    'product',
                    'version',
                    'xAcsOrganizationId',
                    'xAcsCallerSdkSource',
                    'xAcsResourceGroupId',
                    'xAcsCallerType',
                  ];
                  const resetFields: Record<string, undefined> = {};
                  authFields.forEach((field) => {
                    resetFields[field] = undefined;
                  });
                  form.setFieldsValue(resetFields);
                }
              }}
            />
            <span className="text-sm ml-2">更新认证信息</span>
          </div>
          {updateAuth && (
            <div className="border rounded-lg p-4 bg-gray-50">{renderConfigFields()}</div>
          )}
        </div>
      </Form>
    </Modal>
  );
}

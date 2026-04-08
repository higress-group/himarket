import {
  SaveOutlined,
  PlusOutlined,
  DeleteOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons';
import {
  Card,
  Form,
  Input,
  Switch,
  Button,
  Divider,
  Space,
  Table,
  Modal,
  message,
  Tabs,
} from 'antd';
import { useState, useMemo } from 'react';

import { portalApi } from '@/lib/api';
import type {
  Portal,
  PortalDomainConfig,
  ThirdPartyAuthConfig,
  OidcConfig,
  OAuth2Config,
} from '@/types';
import { AuthenticationType } from '@/types';

import { ThirdPartyAuthManager } from './ThirdPartyAuthManager';

interface PortalSettingsProps {
  portal: Portal;
  onRefresh?: () => void;
}

export function PortalSettings({ onRefresh, portal }: PortalSettingsProps) {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [domainModalVisible, setDomainModalVisible] = useState(false);
  const [domainForm] = Form.useForm();
  const [domainLoading, setDomainLoading] = useState(false);

  // 本地OIDC配置状态，避免频繁刷新
  // local的有点问题，一切tab就坏了

  const handleSave = async () => {
    try {
      setLoading(true);
      const values = await form.validateFields();

      await portalApi.updatePortal(portal.portalId, {
        description: portal.description, // 保持现有描述不变
        name: portal.name, // 保持现有名称不变
        portalDomainConfig: portal.portalDomainConfig,
        portalSettingConfig: {
          ...portal.portalSettingConfig,
          autoApproveDevelopers: values.autoApproveDevelopers,
          autoApproveSubscriptions: values.autoApproveSubscriptions,
          builtinAuthEnabled: values.builtinAuthEnabled,
          frontendRedirectUrl: values.frontendRedirectUrl,
          oidcAuthEnabled: values.oidcAuthEnabled,
        },
        portalUiConfig: portal.portalUiConfig,
      });

      message.success('Portal设置保存成功');
      onRefresh?.();
    } catch (_error) {
      message.error('保存Portal设置失败');
    } finally {
      setLoading(false);
    }
  };

  const handleSettingUpdate = async (key: string, value: unknown) => {
    try {
      await portalApi.updatePortal(portal.portalId, {
        ...portal,
        portalSettingConfig: {
          ...portal.portalSettingConfig,
          [key]: value,
        },
      });
      message.success('设置已更新');
      onRefresh?.();
    } catch (_error) {
      message.error('设置更新失败');
    }
  };

  const handleAddDomain = () => {
    setDomainModalVisible(true);
    domainForm.resetFields();
  };

  const handleDomainModalOk = async () => {
    try {
      setDomainLoading(true);
      const values = await domainForm.validateFields();

      const newDomain = {
        domain: values.domain,
        type: 'CUSTOM',
      };

      await portalApi.bindDomain(portal.portalId, newDomain);
      message.success('域名绑定成功');
      onRefresh?.();
      setDomainModalVisible(false);
    } catch (_error) {
      message.error('绑定域名失败');
    } finally {
      setDomainLoading(false);
    }
  };

  const handleDomainModalCancel = () => {
    setDomainModalVisible(false);
    domainForm.resetFields();
  };

  const handleDeleteDomain = async (domain: string) => {
    Modal.confirm({
      cancelText: '取消',
      content: `确定要解绑域名 "${domain}" 吗？此操作不可恢复。`,
      icon: <ExclamationCircleOutlined />,
      okText: '确认解绑',
      okType: 'danger',
      async onOk() {
        try {
          await portalApi.unbindDomain(portal.portalId, domain);
          message.success('域名解绑成功');
          onRefresh?.();
        } catch (_error) {
          message.error('解绑域名失败');
        }
      },
      title: '确认解绑',
    });
  };

  // 合并OIDC和OAuth2配置用于统一显示
  const thirdPartyAuthConfigs = useMemo((): ThirdPartyAuthConfig[] => {
    const configs: ThirdPartyAuthConfig[] = [];

    // 添加OIDC配置
    if (portal.portalSettingConfig?.oidcConfigs) {
      portal.portalSettingConfig.oidcConfigs.forEach((oidcConfig) => {
        configs.push({
          ...oidcConfig,
          type: AuthenticationType.OIDC,
        });
      });
    }

    // 添加OAuth2配置
    if (portal.portalSettingConfig?.oauth2Configs) {
      portal.portalSettingConfig.oauth2Configs.forEach((oauth2Config) => {
        configs.push({
          ...oauth2Config,
          type: AuthenticationType.OAUTH2,
        });
      });
    }

    return configs;
  }, [portal.portalSettingConfig?.oidcConfigs, portal.portalSettingConfig?.oauth2Configs]);

  // 第三方认证配置保存函数
  const handleSaveThirdPartyAuth = async (configs: ThirdPartyAuthConfig[]) => {
    try {
      // 分离OIDC和OAuth2配置，去掉type字段
      const oidcConfigs = configs
        .filter((config) => config.type === AuthenticationType.OIDC)
        .map((config) => {
          const {
            type: _type /* eslint-disable-line @typescript-eslint/no-unused-vars */,
            ...oidcConfig
          } = config as OidcConfig & {
            type: AuthenticationType.OIDC;
          };
          return oidcConfig;
        });

      const oauth2Configs = configs
        .filter((config) => config.type === AuthenticationType.OAUTH2)
        .map((config) => {
          const {
            type: _type /* eslint-disable-line @typescript-eslint/no-unused-vars */,
            ...oauth2Config
          } = config as OAuth2Config & {
            type: AuthenticationType.OAUTH2;
          };
          return oauth2Config;
        });

      const updateData = {
        ...portal,
        portalSettingConfig: {
          ...portal.portalSettingConfig,
          oauth2Configs: oauth2Configs,
          // 直接保存分离的配置数组
          oidcConfigs: oidcConfigs,
        },
      };

      await portalApi.updatePortal(portal.portalId, updateData);

      onRefresh?.();
    } catch (_error) {
      throw _error;
    }
  };

  // 域名列表数据
  const domains = portal.portalDomainConfig || [];

  // 域名表格列定义
  const domainColumns = [
    {
      dataIndex: 'domain',
      key: 'domain',
      title: '域名',
    },
    {
      dataIndex: 'type',
      key: 'type',
      render: (type: string) => (type === 'DEFAULT' ? '默认域名' : '自定义域名'),
      title: '类型',
    },
    {
      key: 'action',
      render: (_: unknown, record: PortalDomainConfig) =>
        record.type === 'CUSTOM' && (
          <Button
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleDeleteDomain(record.domain)}
            type="text"
          />
        ),
      title: '操作',
    },
  ];

  const tabItems = [
    {
      children: (
        <div className="space-y-6">
          {/* 基本安全设置 */}
          <div className="grid grid-cols-2 gap-6">
            <Form.Item label="账号密码登录" name="builtinAuthEnabled" valuePropName="checked">
              <Switch onChange={(checked) => handleSettingUpdate('builtinAuthEnabled', checked)} />
            </Form.Item>
            {/* <Form.Item
              name="oidcAuthEnabled"
              label="OIDC认证"
              valuePropName="checked"
            >
              <Switch 
                onChange={(checked) => handleSettingUpdate('oidcAuthEnabled', checked)}
              />
            </Form.Item> */}
            <Form.Item label="开发者自动审批" name="autoApproveDevelopers" valuePropName="checked">
              <Switch
                onChange={(checked) => handleSettingUpdate('autoApproveDevelopers', checked)}
              />
            </Form.Item>
            <Form.Item label="订阅自动审批" name="autoApproveSubscriptions" valuePropName="checked">
              <Switch
                onChange={(checked) => handleSettingUpdate('autoApproveSubscriptions', checked)}
              />
            </Form.Item>
          </div>

          {/* 第三方认证管理 */}
          <Divider />
          <ThirdPartyAuthManager
            configs={thirdPartyAuthConfigs}
            onSave={handleSaveThirdPartyAuth}
          />
        </div>
      ),
      key: 'auth',
      label: '安全设置',
    },
    {
      children: (
        <div>
          <div className="flex justify-between items-center mb-4">
            <div>
              <h3 className="text-lg font-medium">域名列表</h3>
              <p className="text-sm text-gray-500">管理Portal的域名配置</p>
            </div>
            <Button icon={<PlusOutlined />} onClick={handleAddDomain} type="primary">
              绑定域名
            </Button>
          </div>
          <Table
            columns={domainColumns}
            dataSource={domains}
            pagination={false}
            rowKey="domain"
            size="small"
          />
        </div>
      ),
      key: 'domain',
      label: '域名管理',
    },
  ];

  return (
    <div className="p-6 space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold mb-2">Portal设置</h1>
          <p className="text-gray-600">配置Portal的基本设置和高级选项</p>
        </div>
        <Space>
          <Button icon={<SaveOutlined />} loading={loading} onClick={handleSave} type="primary">
            保存设置
          </Button>
        </Space>
      </div>

      <Form
        form={form}
        initialValues={{
          autoApproveDevelopers: portal.portalSettingConfig?.autoApproveDevelopers,
          autoApproveSubscriptions: portal.portalSettingConfig?.autoApproveSubscriptions,
          builtinAuthEnabled: portal.portalSettingConfig?.builtinAuthEnabled,
          frontendRedirectUrl: portal.portalSettingConfig?.frontendRedirectUrl,
          oidcAuthEnabled: portal.portalSettingConfig?.oidcAuthEnabled,
          portalDomainConfig: portal.portalDomainConfig,
          portalSettingConfig: portal.portalSettingConfig,
        }}
        layout="vertical"
      >
        <Card>
          <Tabs defaultActiveKey="auth" items={tabItems} type="card" />
        </Card>
      </Form>

      {/* 域名绑定模态框 */}
      <Modal
        cancelText="取消"
        confirmLoading={domainLoading}
        okText="绑定"
        onCancel={handleDomainModalCancel}
        onOk={handleDomainModalOk}
        open={domainModalVisible}
        title="绑定域名"
      >
        <Form form={domainForm} layout="vertical">
          <Form.Item
            label="域名"
            name="domain"
            rules={[
              { message: '请输入域名', required: true },
              {
                message: '请输入有效的域名格式',
                pattern:
                  /^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$/,
              },
            ]}
          >
            <Input placeholder="example.com" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

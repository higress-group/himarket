import { Card, Form, Switch, Divider, message } from 'antd';
import { useMemo } from 'react';

import { useLocale } from '@/contexts/LocaleContext';
import { portalApi } from '@/lib/api';
import type { Portal, ThirdPartyAuthConfig, OidcConfig, OAuth2Config } from '@/types';
import { AuthenticationType } from '@/types';

import { ThirdPartyAuthManager } from './ThirdPartyAuthManager';

interface PortalSecurityProps {
  portal: Portal;
  onRefresh?: () => void;
}

export function PortalSecurity({ onRefresh, portal }: PortalSecurityProps) {
  const { t } = useLocale();
  const [form] = Form.useForm();

  const handleSave = async () => {
    try {
      const values = await form.validateFields();

      await portalApi.updatePortal(portal.portalId, {
        description: portal.description,
        name: portal.name,
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

      message.success(t('portal.security.saveSuccess'));
      onRefresh?.();
    } catch (_error) {
      message.error(t('portal.security.saveFailed'));
    }
  };

  const handleSettingUpdate = () => {
    // 立即更新配置
    handleSave();
  };

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

  return (
    <div className="p-6 space-y-6">
      <div>
        <h1 className="text-2xl font-bold mb-2">{t('portal.security.title')}</h1>
        <p className="text-gray-600">{t('portal.security.description')}</p>
      </div>

      <Form
        form={form}
        initialValues={{
          autoApproveDevelopers: portal.portalSettingConfig?.autoApproveDevelopers,
          autoApproveSubscriptions: portal.portalSettingConfig?.autoApproveSubscriptions,
          builtinAuthEnabled: portal.portalSettingConfig?.builtinAuthEnabled,
          frontendRedirectUrl: portal.portalSettingConfig?.frontendRedirectUrl,
          oidcAuthEnabled: portal.portalSettingConfig?.oidcAuthEnabled,
        }}
        layout="vertical"
      >
        <Card>
          <div className="space-y-6">
            {/* 基本安全配置标题 */}
            <h3 className="text-lg font-medium">{t('portal.security.basic')}</h3>

            {/* 基本安全设置内容 */}
            <div className="grid grid-cols-2 gap-6">
              <Form.Item
                label={t('portal.security.accountLogin')}
                name="builtinAuthEnabled"
                valuePropName="checked"
              >
                <Switch onChange={() => handleSettingUpdate()} />
              </Form.Item>
              <Form.Item
                label={t('portal.security.developerApproval')}
                name="autoApproveDevelopers"
                valuePropName="checked"
              >
                <Switch onChange={() => handleSettingUpdate()} />
              </Form.Item>
              <Form.Item
                label={t('portal.security.subscriptionApproval')}
                name="autoApproveSubscriptions"
                valuePropName="checked"
              >
                <Switch onChange={() => handleSettingUpdate()} />
              </Form.Item>
            </div>

            <Divider />

            {/* 第三方认证管理器 - 内部已有标题，不需要重复添加 */}
            <ThirdPartyAuthManager
              configs={thirdPartyAuthConfigs}
              onSave={handleSaveThirdPartyAuth}
            />
          </div>
        </Card>
      </Form>
    </div>
  );
}

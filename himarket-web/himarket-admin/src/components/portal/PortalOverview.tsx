import {
  ApiOutlined,
  CopyOutlined,
  EditOutlined,
  LinkOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Button, Card, message } from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { StatusIndicator } from '@/components/common';
import { useLocale } from '@/contexts/LocaleContext';
import { apiProductApi, portalApi } from '@/lib/api';
import { copyToClipboard } from '@/lib/utils';
import type { Portal } from '@/types';

import type { ReactNode } from 'react';

interface PortalOverviewProps {
  portal: Portal;
  onEdit?: () => void;
}

function InfoItem({
  children,
  label,
  wide,
}: {
  children: ReactNode;
  label: string;
  wide?: boolean;
}) {
  return (
    <div className={wide ? 'md:col-span-2' : undefined}>
      <dt className="text-xs font-medium text-gray-500">{label}</dt>
      <dd className="mt-1 min-w-0 text-sm leading-6 text-gray-900">{children}</dd>
    </div>
  );
}

function PortalMetricCard({
  icon,
  label,
  onClick,
  value,
}: {
  icon: ReactNode;
  label: string;
  onClick: () => void;
  value: ReactNode;
}) {
  return (
    <button
      className="rounded-lg border border-gray-200 bg-white p-4 text-left shadow-sm transition-all duration-150 hover:border-blue-200 hover:shadow-md"
      onClick={onClick}
      type="button"
    >
      <div className="text-sm text-gray-500">{label}</div>
      <div className="mt-3 flex items-end gap-2">
        <span className="text-xl text-blue-500">{icon}</span>
        <span className="text-2xl font-semibold leading-none text-blue-600">{value}</span>
      </div>
    </button>
  );
}

export function PortalOverview({ onEdit, portal }: PortalOverviewProps) {
  const navigate = useNavigate();
  const { t } = useLocale();
  const [apiCount, setApiCount] = useState(0);
  const [developerCount, setDeveloperCount] = useState(0);

  useEffect(() => {
    if (!portal.portalId) return;

    portalApi
      .getDeveloperList(portal.portalId, {
        page: 1,
        size: 10,
      })
      .then((res: unknown) => {
        const data = (res as { data?: { totalElements?: number } }).data;
        setDeveloperCount(data?.totalElements ?? 0);
      });
    apiProductApi
      .getApiProducts({
        page: 1,
        portalId: portal.portalId,
        size: 10,
      })
      .then((res: unknown) => {
        const data = (res as { data?: { totalElements?: number } }).data;
        setApiCount(data?.totalElements ?? 0);
      });
  }, [portal.portalId]);

  const primaryDomain =
    portal.portalDomainConfig?.[portal.portalDomainConfig.length - 1]?.domain || '';

  return (
    <div className="space-y-6 p-6">
      <div>
        <h1 className="mb-2 text-2xl font-bold">{t('portal.overview.title')}</h1>
        <p className="text-gray-600">{t('portal.overview.description')}</p>
      </div>

      <Card
        className="rounded-lg border border-gray-200 shadow-sm"
        extra={
          onEdit && (
            <Button icon={<EditOutlined />} onClick={onEdit} type="primary">
              {t('portal.overview.edit')}
            </Button>
          )
        }
        title={t('portal.overview.basicInfo')}
      >
        <dl className="grid gap-x-8 gap-y-5 md:grid-cols-2">
          <InfoItem label={t('portal.overview.name')}>{portal.name}</InfoItem>
          <InfoItem label="Portal ID">
            <div className="flex min-w-0 items-center gap-2">
              <span className="truncate text-gray-700">{portal.portalId}</span>
              <CopyOutlined
                className="ml-1 cursor-pointer text-gray-400 transition-colors hover:text-blue-600"
                onClick={async () => {
                  try {
                    await copyToClipboard(portal.portalId);
                    message.success(t('portal.overview.copied'));
                  } catch {
                    message.error(t('common.copyFailed'));
                  }
                }}
                style={{ fontSize: '12px' }}
              />
            </div>
          </InfoItem>

          <InfoItem label={t('portal.overview.domain')}>
            {primaryDomain ? (
              <div className="flex min-w-0 items-center gap-2">
                <LinkOutlined className="text-blue-500" />
                <a
                  className="truncate text-blue-600 hover:underline"
                  href={`http://${primaryDomain}`}
                  rel="noopener noreferrer"
                  target="_blank"
                >
                  {primaryDomain}
                </a>
              </div>
            ) : (
              <span className="text-gray-400">-</span>
            )}
          </InfoItem>
          <InfoItem label={t('portal.overview.accountLogin')}>
            <StatusIndicator
              tone={portal.portalSettingConfig?.builtinAuthEnabled ? 'success' : 'neutral'}
            >
              {portal.portalSettingConfig?.builtinAuthEnabled
                ? t('portal.overview.enabled')
                : t('portal.overview.disabled')}
            </StatusIndicator>
          </InfoItem>

          <InfoItem label={t('portal.overview.developerAutoApproval')}>
            <StatusIndicator
              tone={portal.portalSettingConfig?.autoApproveDevelopers ? 'success' : 'neutral'}
            >
              {portal.portalSettingConfig?.autoApproveDevelopers
                ? t('portal.overview.enabled')
                : t('portal.overview.disabled')}
            </StatusIndicator>
          </InfoItem>
          <InfoItem label={t('portal.overview.subscriptionAutoApproval')}>
            <StatusIndicator
              tone={portal.portalSettingConfig?.autoApproveSubscriptions ? 'success' : 'neutral'}
            >
              {portal.portalSettingConfig?.autoApproveSubscriptions
                ? t('portal.overview.enabled')
                : t('portal.overview.disabled')}
            </StatusIndicator>
          </InfoItem>

          <InfoItem label={t('portal.overview.portalDescription')} wide>
            <span className="text-gray-700">{portal.description || '-'}</span>
          </InfoItem>
        </dl>
      </Card>

      <div className="grid gap-4 md:grid-cols-2">
        <PortalMetricCard
          icon={<UserOutlined />}
          label={t('portal.overview.registeredDevelopers')}
          onClick={() => {
            navigate(`/portals/${portal.portalId}?tab=developers`);
          }}
          value={developerCount}
        />
        <PortalMetricCard
          icon={<ApiOutlined />}
          label={t('portal.overview.publishedApis')}
          onClick={() => {
            navigate(`/portals/${portal.portalId}?tab=published-apis`);
          }}
          value={apiCount}
        />
      </div>
    </div>
  );
}

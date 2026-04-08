import {
  UserOutlined,
  ApiOutlined,
  LinkOutlined,
  CheckCircleFilled,
  MinusCircleFilled,
  EditOutlined,
  CopyOutlined,
} from '@ant-design/icons';
import { Card, Row, Col, Button, message } from 'antd';
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

import { portalApi, apiProductApi } from '@/lib/api';
import { copyToClipboard } from '@/lib/utils';
import type { Portal } from '@/types';

interface PortalOverviewProps {
  portal: Portal;
  onEdit?: () => void;
}

export function PortalOverview({ onEdit, portal }: PortalOverviewProps) {
  const navigate = useNavigate();
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
  }, [portal.portalId]); // 只依赖portalId，而不是整个portal对象

  return (
    <div className="p-6 space-y-6">
      <div>
        <h1 className="text-2xl font-bold mb-2">概览</h1>
        <p className="text-gray-600">Portal概览</p>
      </div>

      {/* 基本信息 */}
      <Card
        extra={
          onEdit && (
            <Button icon={<EditOutlined />} onClick={onEdit} type="primary">
              编辑
            </Button>
          )
        }
        title="基本信息"
      >
        <div>
          <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
            <span className="text-xs text-gray-600">Portal名称:</span>
            <span className="col-span-2 text-xs text-gray-900">{portal.name}</span>
            <span className="text-xs text-gray-600">Portal ID:</span>
            <div className="col-span-2 flex items-center gap-2">
              <span className="text-xs text-gray-700">{portal.portalId}</span>
              <CopyOutlined
                className="text-gray-400 hover:text-colorPrimary cursor-pointer transition-colors ml-1"
                onClick={async () => {
                  try {
                    await copyToClipboard(portal.portalId);
                    message.success('Portal ID已复制');
                  } catch {
                    message.error('复制失败，请手动复制');
                  }
                }}
                style={{ fontSize: '12px' }}
              />
            </div>
          </div>

          <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
            <span className="text-xs text-gray-600">域名:</span>
            <div className="col-span-2 flex items-center gap-2">
              <LinkOutlined className="text-colorPrimary" />
              <a
                className="text-xs text-colorPrimary hover:underline"
                href={`http://${portal.portalDomainConfig?.[portal.portalDomainConfig.length - 1]?.domain}`}
                rel="noopener noreferrer"
                target="_blank"
              >
                {portal.portalDomainConfig?.[portal.portalDomainConfig.length - 1]?.domain}
              </a>
            </div>
            <span className="text-xs text-gray-600">账号密码登录:</span>
            <div className="col-span-2 flex items-center">
              {portal.portalSettingConfig?.builtinAuthEnabled ? (
                <CheckCircleFilled className="text-green-500 mr-2" style={{ fontSize: '10px' }} />
              ) : (
                <MinusCircleFilled className="text-gray-400 mr-2" style={{ fontSize: '10px' }} />
              )}
              <span className="text-xs text-gray-900">
                {portal.portalSettingConfig?.builtinAuthEnabled ? '已启用' : '已停用'}
              </span>
            </div>
          </div>

          <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
            <span className="text-xs text-gray-600">开发者自动审批:</span>
            <div className="col-span-2 flex items-center">
              {portal.portalSettingConfig?.autoApproveDevelopers ? (
                <CheckCircleFilled className="text-green-500 mr-2" style={{ fontSize: '10px' }} />
              ) : (
                <MinusCircleFilled className="text-gray-400 mr-2" style={{ fontSize: '10px' }} />
              )}
              <span className="text-xs text-gray-900">
                {portal.portalSettingConfig?.autoApproveDevelopers ? '已启用' : '已停用'}
              </span>
            </div>
            <span className="text-xs text-gray-600">订阅自动审批:</span>
            <div className="col-span-2 flex items-center">
              {portal.portalSettingConfig?.autoApproveSubscriptions ? (
                <CheckCircleFilled className="text-green-500 mr-2" style={{ fontSize: '10px' }} />
              ) : (
                <MinusCircleFilled className="text-gray-400 mr-2" style={{ fontSize: '10px' }} />
              )}
              <span className="text-xs text-gray-900">
                {portal.portalSettingConfig?.autoApproveSubscriptions ? '已启用' : '已停用'}
              </span>
            </div>
          </div>

          <div className="grid grid-cols-6 gap-8 items-start pt-2 pb-2">
            <span className="text-xs text-gray-600">描述:</span>
            <span className="col-span-5 text-xs text-gray-900 leading-relaxed">
              {portal.description || '-'}
            </span>
          </div>
        </div>
      </Card>

      {/* 统计数据 */}
      <Row gutter={[16, 16]}>
        <Col lg={12} sm={12} xs={24}>
          <Card
            className="cursor-pointer hover:shadow-md transition-shadow"
            onClick={() => {
              navigate(`/portals/${portal.portalId}?tab=developers`);
            }}
          >
            <div className="flex flex-col gap-2 text-subTitle">
              <div>注册开发者</div>
              <div className="flex items-center gap-2">
                <UserOutlined className="text-xl text-colorPrimary" />
                <div className="text-colorPrimary text-2xl">{developerCount}</div>
              </div>
            </div>
          </Card>
        </Col>
        <Col lg={12} sm={12} xs={24}>
          <Card
            className="cursor-pointer hover:shadow-md transition-shadow"
            onClick={() => {
              navigate(`/portals/${portal.portalId}?tab=published-apis`);
            }}
          >
            <div className="flex flex-col gap-2 text-subTitle">
              <div>已发布的API</div>
              <div className="flex items-center gap-2">
                <ApiOutlined className="text-xl text-colorPrimary" />
                <div className="text-colorPrimary text-2xl">{apiCount}</div>
              </div>
            </div>
          </Card>
        </Col>
      </Row>
    </div>
  );
}

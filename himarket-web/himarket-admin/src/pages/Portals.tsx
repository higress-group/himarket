import { PlusOutlined, MoreOutlined, LinkOutlined } from '@ant-design/icons';
import {
  Button,
  Card,
  Dropdown,
  Modal,
  Form,
  Input,
  message,
  Tooltip,
  Pagination,
  Skeleton,
  Empty,
} from 'antd';
import { useState, useCallback, memo, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';

import { AdminPageHeader, StatusIndicator } from '@/components/common';
import { useLocale } from '@/contexts/LocaleContext';
import type { ApiResponse, PaginatedResponse, Portal } from '@/types';

import { portalApi } from '../lib/api';

import type { MenuProps } from 'antd';

function renderPortalValue(status: 'success' | 'warning' | 'neutral', text: string) {
  return (
    <StatusIndicator className="text-[11px]" iconSize={11} tone={status}>
      {text}
    </StatusIndicator>
  );
}

// 优化的Portal卡片组件
const PortalCard = memo(
  ({
    fetchPortals,
    onNavigate,
    portal,
  }: {
    portal: Portal;
    onNavigate: (id: string) => void;
    fetchPortals: () => void;
  }) => {
    const { t } = useLocale();

    const handleCardClick = useCallback(() => {
      onNavigate(portal.portalId);
    }, [portal.portalId, onNavigate]);

    const handleLinkClick = useCallback((e: React.MouseEvent) => {
      e.stopPropagation();
    }, []);

    const primaryDomain =
      portal.portalDomainConfig?.[portal.portalDomainConfig.length - 1]?.domain || '';

    const dropdownItems: MenuProps['items'] = [
      {
        danger: true,
        key: 'delete',
        label: t('page.portal.delete'),
        onClick: (e) => {
          e?.domEvent?.stopPropagation(); // 阻止事件冒泡
          Modal.confirm({
            content: t('page.portal.deleteConfirm'),
            onOk: () => {
              return handleDeletePortal(portal.portalId);
            },
            title: t('page.portal.deleteTitle'),
          });
        },
      },
    ];

    const handleDeletePortal = useCallback(
      (portalId: string) => {
        return portalApi
          .deletePortal(portalId)
          .then(() => {
            message.success(t('page.portal.deleteSuccess'));
            fetchPortals();
          })
          .catch((error) => {
            message.error(error?.response?.data?.message || t('page.portal.deleteFailed'));
            throw error;
          });
      },
      [fetchPortals, t],
    );

    return (
      <Card
        className="
          group relative cursor-pointer overflow-hidden rounded-lg
          border border-gray-200 bg-white shadow-sm
          transition-all duration-150
          hover:-translate-y-0.5 hover:border-blue-200 hover:bg-white hover:shadow-md
          active:scale-[0.99]
        "
        hoverable
        onClick={handleCardClick}
      >
        <div className="mb-4 flex items-start justify-between gap-4">
          <div className="min-w-0">
            <Tooltip placement="topLeft" title={portal.title}>
              <h3 className="mb-1 truncate text-xl font-bold text-gray-800">{portal.title}</h3>
            </Tooltip>
            <Tooltip placement="topLeft" title={portal.description}>
              <p className="line-clamp-2 text-sm leading-5 text-gray-500">
                {portal.description || t('page.portal.noDescription')}
              </p>
            </Tooltip>
          </div>
          <Dropdown menu={{ items: dropdownItems }} trigger={['click']}>
            <Button
              className="-mr-2 shrink-0 text-gray-500 hover:bg-gray-100"
              icon={<MoreOutlined />}
              onClick={(e) => e.stopPropagation()}
              type="text"
            />
          </Dropdown>
        </div>

        <div className="space-y-4">
          <div className="flex items-center gap-2 rounded-md border border-gray-100 bg-gray-50/80 px-3 py-2 transition-colors duration-150 group-hover:border-blue-200 group-hover:bg-blue-50/50">
            <span className="shrink-0 text-[11px] font-medium text-gray-500">
              {t('page.portal.visitAddress')}
            </span>
            <span className="h-3 w-px shrink-0 bg-gray-200" />
            <LinkOutlined className="h-4 w-4 shrink-0 text-blue-500 transition-colors duration-150 group-hover:text-blue-600" />
            {primaryDomain ? (
              <Tooltip color="#000" placement="top" title={primaryDomain}>
                <a
                  className="truncate text-sm font-medium text-blue-600 hover:text-blue-700"
                  href={`http://${primaryDomain}`}
                  onClick={handleLinkClick}
                  rel="noopener noreferrer"
                  target="_blank"
                >
                  {primaryDomain}
                </a>
              </Tooltip>
            ) : (
              <span className="truncate text-sm text-gray-400">{t('page.portal.noDomain')}</span>
            )}
          </div>

          <div className="grid grid-cols-3 gap-2">
            <div className="min-h-[54px] rounded-md border border-gray-100 bg-white px-2.5 py-2 shadow-[0_1px_2px_rgba(15,23,42,0.03)] transition-colors duration-150 group-hover:border-gray-200">
              <span className="block text-[11px] font-medium leading-4 text-gray-500">
                {t('page.portal.statAccountLogin')}
              </span>
              <div className="mt-1">
                {renderPortalValue(
                  portal.portalSettingConfig?.builtinAuthEnabled ? 'success' : 'warning',
                  portal.portalSettingConfig?.builtinAuthEnabled
                    ? t('page.portal.statSupported')
                    : t('page.portal.statUnsupported'),
                )}
              </div>
            </div>

            <div className="min-h-[54px] rounded-md border border-gray-100 bg-white px-2.5 py-2 shadow-[0_1px_2px_rgba(15,23,42,0.03)] transition-colors duration-150 group-hover:border-gray-200">
              <span className="block text-[11px] font-medium leading-4 text-gray-500">
                {t('page.portal.statDeveloperApproval')}
              </span>
              <div className="mt-1">
                {renderPortalValue(
                  portal.portalSettingConfig?.autoApproveDevelopers ? 'success' : 'warning',
                  portal.portalSettingConfig?.autoApproveDevelopers
                    ? t('page.portal.statAuto')
                    : t('page.portal.statManual'),
                )}
              </div>
            </div>

            <div className="min-h-[54px] rounded-md border border-gray-100 bg-white px-2.5 py-2 shadow-[0_1px_2px_rgba(15,23,42,0.03)] transition-colors duration-150 group-hover:border-gray-200">
              <span className="block text-[11px] font-medium leading-4 text-gray-500">
                {t('page.portal.statSubscriptionApproval')}
              </span>
              <div className="mt-1">
                {renderPortalValue(
                  portal.portalSettingConfig?.autoApproveSubscriptions ? 'success' : 'warning',
                  portal.portalSettingConfig?.autoApproveSubscriptions
                    ? t('page.portal.statAuto')
                    : t('page.portal.statManual'),
                )}
              </div>
            </div>
          </div>
        </div>
      </Card>
    );
  },
);

PortalCard.displayName = 'PortalCard';

export default function Portals() {
  const navigate = useNavigate();
  const { locale, t } = useLocale();
  const [portals, setPortals] = useState<Portal[]>([]);
  const [loading, setLoading] = useState<boolean>(true); // 初始状态为 loading
  const [error, setError] = useState<string | null>(null);
  const [isModalVisible, setIsModalVisible] = useState<boolean>(false);
  const [form] = Form.useForm();
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 12,
    total: 0,
  });
  const lastAutoFetchKeyRef = useRef('');

  const fetchPortals = useCallback(
    (page = 1, size = 12) => {
      setLoading(true);
      portalApi
        .getPortals({ page, size })
        .then((res) => {
          const r = res as unknown as ApiResponse<PaginatedResponse<Portal>>;
          const list = r?.data?.content || [];
          const portals: Portal[] = list.map((item) => ({
            adminId: item.adminId,
            description: item.description,
            name: item.name,
            portalDomainConfig: item.portalDomainConfig || [],
            portalId: item.portalId,
            portalSettingConfig: item.portalSettingConfig,
            portalUiConfig: item.portalUiConfig,
            title: item.name,
          }));
          setPortals(portals);
          setPagination({
            current: page,
            pageSize: size,
            total: r?.data?.totalElements || 0,
          });
        })
        .catch((err: Error) => {
          setError(err?.message || t('page.portal.loadFailed'));
        })
        .finally(() => {
          setLoading(false);
        });
    },
    [t],
  );

  useEffect(() => {
    const key = '1-12';
    if (lastAutoFetchKeyRef.current === key) {
      return;
    }

    lastAutoFetchKeyRef.current = key;
    setError(null);
    fetchPortals(1, 12);
  }, [fetchPortals]);

  // 处理分页变化
  const handlePaginationChange = (page: number, pageSize: number) => {
    fetchPortals(page, pageSize);
  };

  const handleCreatePortal = useCallback(() => {
    setIsModalVisible(true);
  }, []);

  const handleModalOk = useCallback(async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);

      const newPortal = {
        description: values.description,
        name: values.name,
        title: values.title,
      };

      await portalApi.createPortal(newPortal);
      message.success(t('page.portal.createSuccess'));
      setIsModalVisible(false);
      form.resetFields();

      fetchPortals();
    } catch {
      // message.error(error?.message || "创建失败");
    } finally {
      setLoading(false);
    }
  }, [fetchPortals, form, t]);

  const handleModalCancel = useCallback(() => {
    setIsModalVisible(false);
    form.resetFields();
  }, [form]);

  const handlePortalClick = useCallback(
    (portalId: string) => {
      navigate(`/portals/${portalId}`);
    },
    [navigate],
  );

  return (
    <div className="space-y-6">
      <AdminPageHeader
        actions={
          <Button icon={<PlusOutlined />} onClick={handleCreatePortal} type="primary">
            {t('page.portal.create')}
          </Button>
        }
        description={t('page.portal.description')}
        title={t('page.portal.title')}
      />
      {error && (
        <div className="rounded-lg border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">
          {error}
        </div>
      )}

      {loading ? (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: pagination.pageSize || 12 }).map((_, index) => (
            <div
              className="h-full rounded-lg border border-gray-200 bg-white p-4 shadow-sm"
              key={index}
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <div className="mb-2 flex items-center justify-between">
                    <Skeleton.Input active size="small" style={{ width: 120 }} />
                    <Skeleton.Input active size="small" style={{ width: 32 }} />
                  </div>
                  <Skeleton.Input active size="small" style={{ marginBottom: 12, width: '100%' }} />
                  <Skeleton.Input active size="small" style={{ marginBottom: 8, width: '80%' }} />
                  <div className="flex items-center justify-between">
                    <Skeleton.Input active size="small" style={{ width: 60 }} />
                    <Skeleton.Input active size="small" style={{ width: 80 }} />
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <>
          {portals.length > 0 ? (
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
              {portals.map((portal) => (
                <PortalCard
                  fetchPortals={() => fetchPortals(pagination.current, pagination.pageSize)}
                  key={portal.portalId}
                  onNavigate={handlePortalClick}
                  portal={portal}
                />
              ))}
            </div>
          ) : (
            <div className="rounded-lg border border-gray-200 bg-white py-14">
              <Empty description={t('page.portal.empty')} />
            </div>
          )}

          {pagination.total > 0 && (
            <div className="mt-6 flex justify-end px-1">
              <Pagination
                current={pagination.current}
                onChange={handlePaginationChange}
                pageSize={pagination.pageSize}
                pageSizeOptions={['6', '12', '24', '48']}
                showQuickJumper
                showSizeChanger
                showTotal={(total) =>
                  locale === 'zh-CN' ? `共 ${total} 条` : `${total} item${total === 1 ? '' : 's'}`
                }
                total={pagination.total}
              />
            </div>
          )}
        </>
      )}

      <Modal
        confirmLoading={loading}
        onCancel={handleModalCancel}
        onOk={handleModalOk}
        open={isModalVisible}
        title={t('page.portal.formTitle')}
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            label={t('page.portal.fieldName')}
            name="name"
            rules={[{ message: t('page.portal.placeholderName'), required: true }]}
          >
            <Input placeholder={t('page.portal.placeholderName')} />
          </Form.Item>

          {/* <Form.Item
            name="title"
            label="标题"
            rules={[{ required: true, message: "请输入Portal标题" }]}
          >
            <Input placeholder="请输入Portal标题" />
          </Form.Item> */}

          <Form.Item
            label={t('page.portal.fieldDescription')}
            name="description"
            rules={[{ message: t('page.portal.placeholderDescription') }]}
          >
            <Input.TextArea placeholder={t('page.portal.placeholderDescription')} rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

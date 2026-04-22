import {
  InfoCircleOutlined,
  SettingOutlined,
  FileTextOutlined,
  CloudServerOutlined,
  ApiOutlined,
  DatabaseOutlined,
  CheckCircleFilled,
} from '@ant-design/icons';
import { Modal, Form, Button, Space, message } from 'antd';
import { useState, useMemo } from 'react';

import { apiProductApi, mcpServerApi } from '@/lib/api';

import BasicInfoStep from './steps/BasicInfoStep';
import GatewayImportStep from './steps/GatewayImportStep';
import McpConfigStep from './steps/McpConfigStep';
import NacosImportStep from './steps/NacosImportStep';
import SandboxDeployStep from './steps/SandboxDeployStep';
import ServiceIntroStep from './steps/ServiceIntroStep';

import type { McpStepWizardProps, McpCreationFormData } from './types';

/** 步骤导航项 */
interface NavItem {
  key: number;
  label: string;
  icon: React.ReactNode;
  desc: string;
}

/** 每个步骤对应的必填字段名（用于前进校验） */
const STEP_FIELDS: Record<string, string[][]> = {
  gateway: [
    ['name', 'description', 'mcpName'], // 基础信息
    [], // 选择网关 MCP
    [], // 服务介绍
  ],
  manual: [
    ['name', 'description', 'mcpName'], // 基础信息
    ['connectionConfig'], // MCP 配置
    [], // 服务介绍
    [], // 沙箱部署
  ],
  nacos: [
    ['name', 'description', 'mcpName'], // 基础信息
    [], // 选择 Nacos MCP
    [], // 服务介绍
  ],
};

/** manual 模式导航项（含沙箱步骤） */
const MANUAL_NAV_FULL: NavItem[] = [
  { desc: '产品与 MCP 元信息', icon: <InfoCircleOutlined />, key: 0, label: '基础信息' },
  { desc: '协议与连接方式', icon: <SettingOutlined />, key: 1, label: 'MCP 配置' },
  { desc: 'Markdown 文档', icon: <FileTextOutlined />, key: 2, label: '服务介绍' },
  { desc: '沙箱与参数配置', icon: <CloudServerOutlined />, key: 3, label: '沙箱部署' },
];

/** manual 模式导航项（不含沙箱步骤） */
const MANUAL_NAV_SHORT: NavItem[] = [
  { desc: '产品与 MCP 元信息', icon: <InfoCircleOutlined />, key: 0, label: '基础信息' },
  { desc: '协议与连接方式', icon: <SettingOutlined />, key: 1, label: 'MCP 配置' },
  { desc: 'Markdown 文档', icon: <FileTextOutlined />, key: 2, label: '服务介绍' },
];

const GATEWAY_NAV: NavItem[] = [
  { desc: '产品与 MCP 元信息', icon: <InfoCircleOutlined />, key: 0, label: '基础信息' },
  { desc: '从网关导入 MCP Server', icon: <ApiOutlined />, key: 1, label: '选择网关 MCP' },
  { desc: 'Markdown 文档', icon: <FileTextOutlined />, key: 2, label: '服务介绍' },
];

const NACOS_NAV: NavItem[] = [
  { desc: '产品与 MCP 元信息', icon: <InfoCircleOutlined />, key: 0, label: '基础信息' },
  { desc: '从 Nacos 导入 MCP Server', icon: <DatabaseOutlined />, key: 1, label: '选择 Nacos MCP' },
  { desc: 'Markdown 文档', icon: <FileTextOutlined />, key: 2, label: '服务介绍' },
];

/** 根据 mode 和 sandboxRequired 获取标题 */
function getTitle(mode: McpStepWizardProps['mode']): string {
  switch (mode) {
    case 'manual':
      return '手动创建 MCP';
    case 'gateway':
      return '从网关导入 MCP';
    case 'nacos':
      return '从 Nacos 导入 MCP';
  }
}

export function McpStepWizard({ mode, onCancel, onSuccess, visible }: McpStepWizardProps) {
  const [form] = Form.useForm<McpCreationFormData>();
  const [currentStep, setCurrentStep] = useState(0);
  const [completedSteps, setCompletedSteps] = useState<Set<number>>(new Set());
  const [submitting, setSubmitting] = useState(false);

  // Watch sandboxRequired to dynamically show/hide step 4 in manual mode
  const sandboxRequired = Form.useWatch('sandboxRequired', form);

  /** Dynamic nav items based on mode + sandboxRequired */
  const navItems = useMemo<NavItem[]>(() => {
    if (mode === 'gateway') return GATEWAY_NAV;
    if (mode === 'nacos') return NACOS_NAV;
    // manual mode: show sandbox step only when sandboxRequired is on
    return sandboxRequired ? MANUAL_NAV_FULL : MANUAL_NAV_SHORT;
  }, [mode, sandboxRequired]);

  /** Dynamic step fields for validation */
  const stepFields = useMemo<string[][]>(() => {
    const fields = STEP_FIELDS[mode] as string[][];
    if (mode === 'manual' && !sandboxRequired) {
      // Remove sandbox step fields (last entry) when not required
      return fields.slice(0, 3);
    }
    return fields;
  }, [mode, sandboxRequired]);

  const isLastStep = currentStep === navItems.length - 1;

  /** Validate current step fields and navigate forward */
  const handleNext = async () => {
    try {
      await form.validateFields(stepFields[currentStep]);
      setCompletedSteps((prev) => new Set(prev).add(currentStep));
      setCurrentStep(currentStep + 1);
    } catch {
      /* validation failed, stay on current step */
    }
  };

  /** Navigate backward */
  const handlePrev = () => {
    if (currentStep > 0) {
      setCurrentStep(currentStep - 1);
    }
  };

  /** Navigate to a specific step (validate if going forward) */
  const navigateTo = async (target: number) => {
    if (target > currentStep) {
      try {
        await form.validateFields(stepFields[currentStep]);
        setCompletedSteps((prev) => new Set(prev).add(currentStep));
      } catch {
        return;
      }
    }
    setCurrentStep(target);
  };

  /** Submit — create product + save MCP meta based on mode */
  const handleSubmit = async () => {
    try {
      await form.validateFields(stepFields[currentStep]);
      setCompletedSteps((prev) => new Set(prev).add(currentStep));
      const values = form.getFieldsValue(true) as McpCreationFormData;

      setSubmitting(true);

      // Pre-check: 产品名称重复校验
      try {
        const checkRes = await apiProductApi.getApiProducts({
          name: values.name,
          page: 0,
          size: 1,
          type: 'MCP_SERVER',
        });
        const checkData = checkRes as { data?: { content?: Array<{ name?: string }> } };
        const existing = checkData.data?.content || [];
        if (existing.length > 0 && existing.some((p) => p.name === values.name)) {
          message.error(`产品名称「${values.name}」已存在，请更换名称`);
          return;
        }
      } catch {
        // 查询失败不阻塞提交，依赖后端校验
      }

      // Step 1: Create MCP_SERVER product
      // Assemble icon: backend expects { type, value }
      let iconParam: { type: string; value: string } | undefined;
      if (values.iconUrl) {
        iconParam = { type: 'URL', value: values.iconUrl };
      } else if (values.icon && typeof values.icon === 'string') {
        iconParam = { type: 'BASE64', value: values.icon };
      }

      const productRes = await apiProductApi.createApiProduct({
        autoApprove: values.autoApprove,
        categories: values.categories,
        description: values.description,
        icon: iconParam,
        name: values.name,
        type: 'MCP_SERVER',
      });
      const productData = productRes as { data?: { productId?: string }; productId?: string };
      const productId = productData.data?.productId ?? productData.productId;
      if (!productId) throw new Error('创建产品失败，未返回 productId');

      // Step 2: Save MCP meta — 失败时回滚已创建的 Product
      try {
        if (mode === 'manual') {
          await mcpServerApi.saveMeta({
            authType: values.authType,
            connectionConfig: values.connectionConfig ?? '{}',
            description: values.description,
            displayName: values.name,
            extraParams: values.extraParams ? JSON.stringify(values.extraParams) : undefined,
            mcpName: values.mcpName,
            namespace: values.namespace,
            origin: 'ADMIN',
            paramValues: values.paramValues ? JSON.stringify(values.paramValues) : undefined,
            productId: productId,
            protocolType: values.protocolType ?? 'sse',
            repoUrl: values.repoUrl,
            resourceSpec: values.sandboxRequired
              ? JSON.stringify({
                  cpuLimit: values.cpuLimit,
                  cpuRequest: values.cpuRequest,
                  ephemeralStorage: values.ephemeralStorage,
                  memoryLimit: values.memoryLimit,
                  memoryRequest: values.memoryRequest,
                  preset: values.resourcePreset || 'small',
                })
              : undefined,
            sandboxId: values.sandboxId,
            sandboxRequired: values.sandboxRequired,
            serviceIntro: values.serviceIntro,
            sourceType: 'ADMIN',
            tags: values.tags ? JSON.stringify(values.tags) : undefined,
            transportType: values.transportType,
          });
        } else if (mode === 'gateway') {
          await mcpServerApi.saveMeta({
            connectionConfig: '{}',
            description: values.description,
            displayName: values.name,
            gatewayId: values.gatewayId,
            mcpName: values.mcpName,
            origin: 'GATEWAY',
            productId: productId,
            protocolType: 'sse',
            refConfig: values.gatewayRefConfig
              ? JSON.stringify(values.gatewayRefConfig)
              : undefined,
            serviceIntro: values.serviceIntro,
            sourceType: 'GATEWAY',
            tags: values.tags ? JSON.stringify(values.tags) : undefined,
          });
        } else if (mode === 'nacos') {
          await mcpServerApi.saveMeta({
            connectionConfig: '{}',
            description: values.description,
            displayName: values.name,
            mcpName: values.mcpName,
            nacosId: values.nacosId,
            origin: 'NACOS',
            productId: productId,
            protocolType: 'sse',
            refConfig: values.nacosRefConfig ? JSON.stringify(values.nacosRefConfig) : undefined,
            serviceIntro: values.serviceIntro,
            sourceType: 'NACOS',
            tags: values.tags ? JSON.stringify(values.tags) : undefined,
          });
        }
      } catch (metaErr: unknown) {
        // saveMeta 失败，回滚已创建的 Product 避免脏数据
        try {
          await apiProductApi.deleteApiProduct(productId);
        } catch {
          console.warn('[McpStepWizard] 回滚 Product 失败，productId:', productId);
        }
        throw metaErr;
      }

      // Success: close wizard and refresh list
      onSuccess();
    } catch (err: unknown) {
      // Show error but keep form data
      const e = err as {
        errorFields?: unknown;
        message?: string;
        response?: { data?: { message?: string } };
      };
      if (e.errorFields) {
        // Form validation error — already shown inline
        return;
      }
      message.error(e.response?.data?.message || e.message || '创建失败，请重试');
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancel = () => {
    form.resetFields();
    form.setFieldsValue({ sandboxRequired: true });
    setCurrentStep(0);
    setCompletedSteps(new Set());
    onCancel();
  };

  /** Render content for each step */
  const renderStepContent = () => {
    if (mode === 'manual') {
      switch (currentStep) {
        case 0:
          return <BasicInfoStep mode={mode} />;
        case 1:
          return <McpConfigStep />;
        case 2:
          return <ServiceIntroStep />;
        case 3:
          return <SandboxDeployStep />;
        default:
          return null;
      }
    }

    if (mode === 'gateway') {
      switch (currentStep) {
        case 0:
          return <BasicInfoStep mode={mode} />;
        case 1:
          return <GatewayImportStep />;
        case 2:
          return <ServiceIntroStep />;
        default:
          return null;
      }
    }

    if (mode === 'nacos') {
      switch (currentStep) {
        case 0:
          return <BasicInfoStep mode={mode} />;
        case 1:
          return <NacosImportStep />;
        case 2:
          return <ServiceIntroStep />;
        default:
          return null;
      }
    }

    return null;
  };

  return (
    <Modal
      closable={false}
      destroyOnClose
      footer={null}
      maskClosable={false}
      onCancel={handleCancel}
      open={visible}
      styles={{
        body: { padding: 0 },
        header: { display: 'none' },
      }}
      width={820}
    >
      <div className="flex" style={{ minHeight: 540 }}>
        {/* 左侧步骤导航 */}
        <div className="w-52 bg-gray-50/80 border-r border-gray-100 p-5 flex flex-col flex-shrink-0">
          <div className="mb-6">
            <h3 className="text-base font-semibold text-gray-800 leading-tight">
              {getTitle(mode)}
            </h3>
            <p className="text-xs text-gray-400 mt-1.5 leading-relaxed">
              {mode === 'manual'
                ? '手动填写 MCP 信息'
                : mode === 'gateway'
                  ? '从网关导入 MCP'
                  : '从 Nacos 导入 MCP'}
            </p>
          </div>

          <nav className="flex-1">
            {navItems.map((item, idx) => {
              const isActive = currentStep === item.key;
              const isDone = completedSteps.has(item.key);
              const isLast = idx === navItems.length - 1;
              return (
                <div
                  className="flex gap-3"
                  key={item.key}
                  onClick={() => navigateTo(item.key)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      navigateTo(item.key);
                    }
                  }}
                  role="button"
                  tabIndex={0}
                >
                  {/* 圆点 + 连线 */}
                  <div className="flex flex-col items-center flex-shrink-0">
                    <div
                      className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-medium transition-all duration-200 cursor-pointer ${
                        isActive
                          ? 'bg-blue-500 text-white shadow-sm shadow-blue-200'
                          : isDone
                            ? 'bg-green-500 text-white'
                            : 'bg-gray-200 text-gray-500'
                      }`}
                    >
                      {isDone && !isActive ? <CheckCircleFilled className="text-xs" /> : idx + 1}
                    </div>
                    {!isLast && (
                      <div
                        className={`w-0.5 flex-1 my-1 min-h-[28px] rounded-full transition-colors duration-300 ${
                          isDone ? 'bg-green-300' : 'bg-gray-200'
                        }`}
                      />
                    )}
                  </div>
                  {/* 文字 */}
                  <div className="pt-0.5 pb-4 cursor-pointer">
                    <div
                      className={`text-sm leading-tight ${
                        isActive
                          ? 'font-semibold text-gray-900'
                          : isDone
                            ? 'font-medium text-gray-700'
                            : 'text-gray-500'
                      }`}
                    >
                      {item.label}
                    </div>
                    <div className="text-[11px] text-gray-400 mt-0.5 leading-tight">
                      {item.desc}
                    </div>
                  </div>
                </div>
              );
            })}
          </nav>
        </div>

        {/* 右侧内容区域 */}
        <div className="flex-1 flex flex-col min-w-0">
          {/* 顶部标题栏 */}
          <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
            <div className="flex items-center gap-2">
              <span className="text-gray-400">{navItems[currentStep]?.icon}</span>
              <span className="text-sm font-medium text-gray-800">
                {navItems[currentStep]?.label}
              </span>
              <span className="text-xs text-gray-400">— {navItems[currentStep]?.desc}</span>
            </div>
            <Button
              className="text-gray-400 hover:text-gray-600"
              onClick={handleCancel}
              size="small"
              type="text"
            >
              ✕
            </Button>
          </div>

          {/* 表单内容 */}
          <div className="flex-1 overflow-auto px-6 py-5">
            <Form form={form} layout="vertical" requiredMark>
              {/* 保持 sandboxRequired 字段始终挂载，避免 useWatch 在步骤切换后丢失值 */}
              <Form.Item hidden name="sandboxRequired">
                <input type="hidden" />
              </Form.Item>
              {renderStepContent()}
            </Form>
          </div>

          {/* 底部操作栏 */}
          <div className="flex items-center justify-between px-6 py-3 border-t border-gray-100">
            <div>
              {currentStep > 0 ? (
                <Button disabled={submitting} onClick={handlePrev}>
                  上一步
                </Button>
              ) : (
                <span />
              )}
            </div>
            <Space>
              <Button disabled={submitting} onClick={handleCancel}>
                取消
              </Button>
              {isLastStep ? (
                <Button loading={submitting} onClick={handleSubmit} type="primary">
                  保存配置
                </Button>
              ) : (
                <Button disabled={submitting} onClick={handleNext} type="primary">
                  下一步
                </Button>
              )}
            </Space>
          </div>
        </div>
      </div>
    </Modal>
  );
}

export default McpStepWizard;

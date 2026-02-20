import { useState } from "react";
import { Form, Input, Switch, Select } from "antd";

// ============ 类型定义 ============

export interface CustomModelFormData {
  baseUrl: string;
  apiKey: string;
  modelId: string;
  modelName: string;
  protocolType: "openai" | "anthropic" | "gemini";
}

export interface CustomModelFormProps {
  /** 当前选中的 provider 是否支持自定义模型 */
  supportsCustomModel: boolean;
  /** 表单值变化时回调，enabled=false 时 data 为 null */
  onChange?: (enabled: boolean, data: CustomModelFormData | null) => void;
}

// ============ 常量 ============

const PROTOCOL_OPTIONS = [
  { value: "openai", label: "OpenAI" },
  { value: "anthropic", label: "Anthropic" },
  { value: "gemini", label: "Gemini" },
];

const URL_PATTERN = /^https?:\/\/.+/;

// ============ 组件 ============

export function CustomModelForm({ supportsCustomModel, onChange }: CustomModelFormProps) {
  const [enabled, setEnabled] = useState(false);
  const [form] = Form.useForm<CustomModelFormData>();

  if (!supportsCustomModel) {
    return null;
  }

  const handleSwitchChange = (checked: boolean) => {
    setEnabled(checked);
    if (!checked) {
      form.resetFields();
      onChange?.(false, null);
    }
  };

  const handleValuesChange = () => {
    if (!enabled) return;
    // 仅在校验通过时回调有效数据
    form
      .validateFields()
      .then((values) => {
        onChange?.(true, {
          ...values,
          modelName: values.modelName || values.modelId,
          protocolType: values.protocolType || "openai",
        });
      })
      .catch(() => {
        // 校验未通过，传递 null 表示数据不完整
        onChange?.(true, null);
      });
  };

  return (
    <div className="w-full">
      {/* 开关 */}
      <div className="flex items-center justify-center gap-2 mb-3">
        <Switch
          size="small"
          checked={enabled}
          onChange={handleSwitchChange}
        />
        <span className="text-sm text-gray-600">使用自定义模型</span>
      </div>

      {/* 配置表单 */}
      {enabled && (
        <Form
          form={form}
          layout="vertical"
          size="small"
          initialValues={{ protocolType: "openai" }}
          onValuesChange={handleValuesChange}
          className="w-full"
        >
          <Form.Item
            name="baseUrl"
            label="模型接入点 URL"
            rules={[
              { required: true, message: "请输入模型接入点 URL" },
              {
                pattern: URL_PATTERN,
                message: "请输入合法的 URL（以 http:// 或 https:// 开头）",
              },
            ]}
          >
            <Input placeholder="https://api.example.com/v1" />
          </Form.Item>

          <Form.Item
            name="apiKey"
            label="API Key"
            rules={[{ required: true, message: "请输入 API Key" }]}
          >
            <Input.Password placeholder="sk-..." />
          </Form.Item>

          <Form.Item
            name="modelId"
            label="模型 ID"
            rules={[{ required: true, message: "请输入模型 ID" }]}
          >
            <Input placeholder="gpt-4o" />
          </Form.Item>

          <Form.Item
            name="modelName"
            label="模型显示名称"
          >
            <Input placeholder="留空则使用模型 ID" />
          </Form.Item>

          <Form.Item
            name="protocolType"
            label="协议类型"
          >
            <Select options={PROTOCOL_OPTIONS} />
          </Form.Item>
        </Form>
      )}
    </div>
  );
}

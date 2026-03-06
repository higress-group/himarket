import { useEffect } from "react";
import { Form, Input, Select } from "antd";

// ============ 类型定义 ============

export interface CustomModelFormData {
  baseUrl: string;
  apiKey: string;
  modelId: string;
  modelName: string;
  protocolType: "openai" | "anthropic" | "gemini";
}

export interface CustomModelFormProps {
  /** 是否显示表单（由外部模式状态控制） */
  enabled: boolean;
  /** 表单值变化时回调，data 为 null 表示数据不完整 */
  onChange?: (data: CustomModelFormData | null) => void;
}

// ============ 常量 ============

const PROTOCOL_OPTIONS = [
  { value: "openai", label: "OpenAI" },
  { value: "anthropic", label: "Anthropic" },
  { value: "gemini", label: "Gemini" },
];

const URL_PATTERN = /^https?:\/\/.+/;

// ============ 组件 ============

export function CustomModelForm({ enabled, onChange }: CustomModelFormProps) {
  const [form] = Form.useForm<CustomModelFormData>();

  // enabled 变为 false 时重置表单
  useEffect(() => {
    if (!enabled) {
      form.resetFields();
      onChange?.(null);
    }
  }, [enabled]);

  if (!enabled) {
    return null;
  }

  const handleValuesChange = () => {
    form
      .validateFields()
      .then((values) => {
        onChange?.({
          ...values,
          modelName: values.modelName || values.modelId,
          protocolType: values.protocolType || "openai",
        });
      })
      .catch(() => {
        onChange?.(null);
      });
  };

  return (
    <div className="w-full">
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
    </div>
  );
}

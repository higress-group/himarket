import { Form, Input, Select } from 'antd';
import { useEffect } from 'react';

// ============ 类型定义 ============

export interface CustomModelFormData {
  baseUrl: string;
  apiKey: string;
  modelId: string;
  modelName: string;
  protocolType: 'openai' | 'anthropic' | 'gemini';
}

export interface CustomModelFormProps {
  /** 是否显示表单（由外部模式状态控制） */
  enabled: boolean;
  /** 表单值变化时回调，data 为 null 表示数据不完整 */
  onChange?: (data: CustomModelFormData | null) => void;
}

// ============ 常量 ============

const PROTOCOL_OPTIONS = [
  { label: 'OpenAI', value: 'openai' },
  { label: 'Anthropic', value: 'anthropic' },
  { label: 'Gemini', value: 'gemini' },
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
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
          protocolType: values.protocolType || 'openai',
        });
      })
      .catch(() => {
        onChange?.(null);
      });
  };

  return (
    <div className="w-full">
      <Form
        className="w-full"
        form={form}
        initialValues={{ protocolType: 'openai' }}
        layout="vertical"
        onValuesChange={handleValuesChange}
        size="small"
      >
        <Form.Item
          label="模型接入点 URL"
          name="baseUrl"
          rules={[
            { message: '请输入模型接入点 URL', required: true },
            {
              message: '请输入合法的 URL（以 http:// 或 https:// 开头）',
              pattern: URL_PATTERN,
            },
          ]}
        >
          <Input placeholder="https://api.example.com/v1" />
        </Form.Item>

        <Form.Item
          label="API Key"
          name="apiKey"
          rules={[{ message: '请输入 API Key', required: true }]}
        >
          <Input.Password placeholder="sk-..." />
        </Form.Item>

        <Form.Item
          label="模型 ID"
          name="modelId"
          rules={[{ message: '请输入模型 ID', required: true }]}
        >
          <Input placeholder="gpt-4o" />
        </Form.Item>

        <Form.Item label="模型显示名称" name="modelName">
          <Input placeholder="留空则使用模型 ID" />
        </Form.Item>

        <Form.Item label="协议类型" name="protocolType">
          <Select options={PROTOCOL_OPTIONS} />
        </Form.Item>
      </Form>
    </div>
  );
}

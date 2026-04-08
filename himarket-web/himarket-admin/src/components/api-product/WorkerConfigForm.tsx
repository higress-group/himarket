import { Form, Select, Tag } from 'antd';

/**
 * Worker configuration form component
 * Contains tags input for WORKER type products
 */
export default function WorkerConfigForm() {
  return (
    <Form.Item
      label="Worker 标签"
      name={['feature', 'workerConfig', 'tags']}
      tooltip="为 Worker 添加分类标签，便于开发者搜索和筛选"
    >
      <Select
        mode="tags"
        placeholder="输入标签后按回车添加"
        style={{ width: '100%' }}
        tagRender={({ closable, label, onClose }) => (
          <Tag closable={closable} color="blue" onClose={onClose} style={{ marginInlineEnd: 4 }}>
            {label}
          </Tag>
        )}
        tokenSeparators={[',']}
      />
    </Form.Item>
  );
}

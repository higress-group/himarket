import { Form, Select, Tag } from 'antd';

/**
 * 技能配置表单组件
 * 包含技能标签多选输入，用于 AGENT_SKILL 类型产品的配置
 */
export default function SkillConfigForm() {
  return (
    <Form.Item
      label="技能标签"
      name={['feature', 'skillConfig', 'skillTags']}
      tooltip="为技能添加分类标签，便于开发者搜索和筛选"
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

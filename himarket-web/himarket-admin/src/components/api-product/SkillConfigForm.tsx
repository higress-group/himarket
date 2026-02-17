import { useState, useEffect } from "react";
import { Form, Select, Collapse, Tag } from "antd";

const { Panel } = Collapse;

interface SkillConfigFormProps {
  initialExpanded?: boolean;
}

/**
 * 技能配置表单组件
 * 包含技能标签多选输入，用于 AGENT_SKILL 类型产品的配置
 */
export default function SkillConfigForm({ initialExpanded = false }: SkillConfigFormProps) {
  const [activeKey, setActiveKey] = useState<string[]>([]);

  useEffect(() => {
    setActiveKey(initialExpanded ? ['1'] : []);
  }, [initialExpanded]);

  return (
    <Collapse
      ghost
      activeKey={activeKey}
      onChange={(keys) => setActiveKey(keys as string[])}
      style={{ marginBottom: 16 }}
    >
      <Panel header="技能配置" key="1" forceRender>
        <Form.Item
          label="技能标签"
          name={['skillConfig', 'skillTags']}
          tooltip="为技能添加分类标签，便于开发者搜索和筛选"
        >
          <Select
            mode="tags"
            placeholder="输入标签后按回车添加"
            style={{ width: '100%' }}
            tokenSeparators={[',']}
            tagRender={({ label, closable, onClose }) => (
              <Tag
                color="blue"
                closable={closable}
                onClose={onClose}
                style={{ marginInlineEnd: 4 }}
              >
                {label}
              </Tag>
            )}
          />
        </Form.Item>
      </Panel>
    </Collapse>
  );
}

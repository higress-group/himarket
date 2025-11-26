import { useState, useEffect } from "react";
import { Form, Input, InputNumber, Collapse, Row, Col } from "antd";

const { Panel } = Collapse;

interface ModelFeatureFormProps {
  initialExpanded?: boolean;
}

export default function ModelFeatureForm({ initialExpanded = false }: ModelFeatureFormProps) {
  const [activeKey, setActiveKey] = useState<string[]>(initialExpanded ? ['1'] : []);
  
  const tooltipStyle = {
    overlayInnerStyle: {
      backgroundColor: '#000',
      color: '#fff',
    }
  };

  useEffect(() => {
    if (initialExpanded) {
      setActiveKey(['1']);
    }
  }, [initialExpanded]);

  return (
    <Collapse 
      ghost 
      activeKey={activeKey} 
      onChange={(keys) => setActiveKey(keys as string[])}
      style={{ marginBottom: 16 }}
    >
      <Panel header="模型参数" key="1">
        <Row gutter={16}>
          <Col span={8}>
            <Form.Item 
              label="Model" 
              name={['feature', 'modelFeature', 'model']}
              tooltip={{ title: "模型名称，如 qwen-max", ...tooltipStyle }}
              style={{ marginBottom: 0 }}
            >
              <Input placeholder="qwen-max" size="small" />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item 
              label="Max Tokens" 
              name={['feature', 'modelFeature', 'maxTokens']}
              tooltip={{ title: "1-100000", ...tooltipStyle }}
              style={{ marginBottom: 0 }}
            >
              <InputNumber 
                min={1} 
                max={100000} 
                style={{ width: '100%' }}
                placeholder="5000"
                size="small"
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item 
              label="Temperature" 
              name={['feature', 'modelFeature', 'temperature']}
              tooltip={{ title: "0.0-2.0", ...tooltipStyle }}
              style={{ marginBottom: 0 }}
            >
              <InputNumber 
                min={0} 
                max={2} 
                step={0.1}
                style={{ width: '100%' }}
                placeholder="0.9"
                size="small"
              />
            </Form.Item>
          </Col>
        </Row>
      </Panel>
    </Collapse>
  );
}


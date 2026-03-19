import { Form, Input, InputNumber, Switch, Row, Col, Divider } from "antd";

const tooltipStyle = {
  overlayInnerStyle: {
    backgroundColor: '#000',
    color: '#fff',
  }
};

export default function ModelFeatureForm() {
  return (
    <>
      <Divider orientation="left" style={{ marginTop: 0, marginBottom: 16 }}>模型参数</Divider>
      <Row gutter={16}>
        <Col span={24}>
          <Form.Item
            label="Model"
            name={['feature', 'modelFeature', 'model']}
            tooltip={{ title: "模型名称，如 qwen-max", ...tooltipStyle }}
            rules={[{ required: true, message: '请输入模型名称' }]}
          >
            <Input placeholder="qwen-max" />
          </Form.Item>
        </Col>
      </Row>
      <Row gutter={16}>
        <Col span={12}>
          <Form.Item
            label="Max Tokens"
            name={['feature', 'modelFeature', 'maxTokens']}
            tooltip={{ title: "1-8192", ...tooltipStyle }}
          >
            <InputNumber
              min={1}
              max={8192}
              style={{ width: '100%' }}
              placeholder="5000"
            />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item
            label="Temperature"
            name={['feature', 'modelFeature', 'temperature']}
            tooltip={{ title: "0.0-2.0", ...tooltipStyle }}
          >
            <InputNumber
              min={0}
              max={2}
              step={0.1}
              style={{ width: '100%' }}
              placeholder="0.9"
            />
          </Form.Item>
        </Col>
      </Row>
      <Row gutter={16}>
        <Col span={8}>
          <Form.Item
            label="Web Search"
            name={['feature', 'modelFeature', 'webSearch']}
            tooltip={{ title: "是否启用网络搜索能力", ...tooltipStyle }}
            valuePropName="checked"
            initialValue={true}
          >
            <Switch />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item
            label="Enable Thinking"
            name={['feature', 'modelFeature', 'enableThinking']}
            tooltip={{ title: "是否启用深度思考", ...tooltipStyle }}
            valuePropName="checked"
            initialValue={false}
          >
            <Switch />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item
            label="Enable MultiModal"
            name={['feature', 'modelFeature', 'enableMultiModal']}
            tooltip={{ title: "支持多模态", ...tooltipStyle }}
            valuePropName="checked"
            initialValue={false}
          >
            <Switch />
          </Form.Item>
        </Col>
      </Row>
    </>
  );
}

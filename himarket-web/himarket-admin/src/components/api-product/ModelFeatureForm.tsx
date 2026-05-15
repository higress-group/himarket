import { Form, Input, InputNumber, Switch, Row, Col, Divider } from 'antd';

const tooltipStyle = {
  overlayInnerStyle: {
    backgroundColor: '#000',
    color: '#fff',
  },
};

interface ModelFeatureFormProps {
  initialExpanded?: boolean;
}

export default function ModelFeatureForm({
  initialExpanded: _initialExpanded,
}: ModelFeatureFormProps) {
  return (
    <>
      <Divider style={{ marginBottom: 16, marginTop: 0 }} titlePlacement="left">
        模型参数
      </Divider>
      <Row gutter={16}>
        <Col span={24}>
          <Form.Item
            label="模型名称"
            name={['feature', 'modelFeature', 'model']}
            rules={[{ message: '请输入模型名称', required: true }]}
            tooltip={{ title: '模型名称，如 qwen-max', ...tooltipStyle }}
          >
            <Input placeholder="qwen-max" />
          </Form.Item>
        </Col>
      </Row>
      <Row gutter={16}>
        <Col span={12}>
          <Form.Item
            label="最大输出 Tokens"
            name={['feature', 'modelFeature', 'maxTokens']}
            tooltip={{ title: '单次响应最多输出的 Tokens 数量，范围 1-8192', ...tooltipStyle }}
          >
            <InputNumber max={8192} min={1} placeholder="5000" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item
            label="温度"
            name={['feature', 'modelFeature', 'temperature']}
            tooltip={{ title: '控制输出随机性，数值越高结果越发散，范围 0.0-2.0', ...tooltipStyle }}
          >
            <InputNumber max={2} min={0} placeholder="0.9" step={0.1} style={{ width: '100%' }} />
          </Form.Item>
        </Col>
      </Row>
      <Row gutter={16}>
        <Col span={8}>
          <Form.Item
            initialValue={true}
            label="联网搜索"
            name={['feature', 'modelFeature', 'webSearch']}
            tooltip={{ title: '是否启用网络搜索能力', ...tooltipStyle }}
            valuePropName="checked"
          >
            <Switch />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item
            initialValue={false}
            label="深度思考"
            name={['feature', 'modelFeature', 'enableThinking']}
            tooltip={{ title: '是否启用深度思考', ...tooltipStyle }}
            valuePropName="checked"
          >
            <Switch />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item
            initialValue={false}
            label="多模态"
            name={['feature', 'modelFeature', 'enableMultiModal']}
            tooltip={{ title: '支持多模态', ...tooltipStyle }}
            valuePropName="checked"
          >
            <Switch />
          </Form.Item>
        </Col>
      </Row>
    </>
  );
}

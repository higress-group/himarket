import { Form, Input, InputNumber, Switch, Row, Col, Divider } from 'antd';

import { useLocale } from '@/contexts/LocaleContext';

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
  const { t } = useLocale();

  return (
    <>
      <Divider style={{ marginBottom: 16, marginTop: 0 }} titlePlacement="left">
        {t('product.modelForm.title')}
      </Divider>
      <Row gutter={16}>
        <Col span={24}>
          <Form.Item
            label={t('product.modelForm.modelName')}
            name={['feature', 'modelFeature', 'model']}
            rules={[{ message: t('product.modelForm.modelNameRequired'), required: true }]}
            tooltip={{ title: t('product.modelForm.modelNameTooltip'), ...tooltipStyle }}
          >
            <Input placeholder="qwen-max" />
          </Form.Item>
        </Col>
      </Row>
      <Row gutter={16}>
        <Col span={12}>
          <Form.Item
            label={t('product.modelForm.maxTokens')}
            name={['feature', 'modelFeature', 'maxTokens']}
            tooltip={{ title: t('product.modelForm.maxTokensTooltip'), ...tooltipStyle }}
          >
            <InputNumber max={8192} min={1} placeholder="5000" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item
            label={t('product.modelForm.temperature')}
            name={['feature', 'modelFeature', 'temperature']}
            tooltip={{ title: t('product.modelForm.temperatureTooltip'), ...tooltipStyle }}
          >
            <InputNumber max={2} min={0} placeholder="0.9" step={0.1} style={{ width: '100%' }} />
          </Form.Item>
        </Col>
      </Row>
      <Row gutter={16}>
        <Col span={8}>
          <Form.Item
            initialValue={true}
            label={t('product.modelForm.webSearch')}
            name={['feature', 'modelFeature', 'webSearch']}
            tooltip={{ title: t('product.modelForm.webSearchTooltip'), ...tooltipStyle }}
            valuePropName="checked"
          >
            <Switch />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item
            initialValue={false}
            label={t('product.modelForm.thinking')}
            name={['feature', 'modelFeature', 'enableThinking']}
            tooltip={{ title: t('product.modelForm.thinkingTooltip'), ...tooltipStyle }}
            valuePropName="checked"
          >
            <Switch />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item
            initialValue={false}
            label={t('product.modelForm.multimodal')}
            name={['feature', 'modelFeature', 'enableMultiModal']}
            tooltip={{ title: t('product.modelForm.multimodalTooltip'), ...tooltipStyle }}
            valuePropName="checked"
          >
            <Switch />
          </Form.Item>
        </Col>
      </Row>
    </>
  );
}

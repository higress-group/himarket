import { LinkOutlined } from '@ant-design/icons';
import { Card, Button, Modal, Form, Select, message, Descriptions, Empty } from 'antd';
import { useState, useEffect } from 'react';

import { nacosApi, apiProductApi } from '@/lib/api';
import type { ApiProduct } from '@/types/api-product';
import type { NacosInstance } from '@/types/gateway';

interface ApiProductLinkNacosProps {
  apiProduct: ApiProduct;
  handleRefresh: () => void;
}

export function ApiProductLinkNacos({ apiProduct, handleRefresh }: ApiProductLinkNacosProps) {
  const isWorker = apiProduct.type === 'WORKER';
  const [modalVisible, setModalVisible] = useState(false);
  const [form] = Form.useForm();
  const [nacosInstances, setNacosInstances] = useState<NacosInstance[]>([]);
  const [namespaces, setNamespaces] = useState<unknown[]>([]);
  const [nacosLoading, setNacosLoading] = useState(false);
  const [nsLoading, setNsLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  // 当前关联的 Nacos 信息（Skill / Worker）
  const nacosConfig = isWorker ? apiProduct.workerConfig : apiProduct.skillConfig;
  const currentNacosId = nacosConfig?.nacosId;
  const currentNamespace = nacosConfig?.namespace || 'public';

  // 查找当前关联的 Nacos 实例名称
  const [currentNacosName, setCurrentNacosName] = useState<string>('');

  useEffect(() => {
    if (currentNacosId) {
      nacosApi
        .getNacos({ page: 1, size: 1000 })
        .then((res: unknown) => {
          const data = res as Record<string, unknown>;
          const list = ((data.data as Record<string, unknown>)?.content || []) as NacosInstance[];
          setNacosInstances(list);
          const found = list.find((n: NacosInstance) => n.nacosId === currentNacosId);
          setCurrentNacosName(found?.nacosName || currentNacosId);
        })
        .catch(() => {});
    }
  }, [currentNacosId]);

  const fetchNacosInstances = async () => {
    setNacosLoading(true);
    try {
      const res = await nacosApi.getNacos({ page: 1, size: 1000 });
      setNacosInstances(res.data?.content || []);
    } catch {
    } finally {
      setNacosLoading(false);
    }
  };

  const handleNacosChange = async (nacosId: string) => {
    form.setFieldValue('namespace', undefined);
    setNamespaces([]);
    setNsLoading(true);
    try {
      const res = await nacosApi.getNamespaces(nacosId, { page: 1, size: 1000 });
      setNamespaces(res.data?.content || []);
    } catch {
    } finally {
      setNsLoading(false);
    }
  };

  const openModal = () => {
    fetchNacosInstances();
    if (currentNacosId) {
      form.setFieldsValue({ nacosId: currentNacosId, namespace: currentNamespace });
      handleNacosChange(currentNacosId);
    }
    setModalVisible(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (isWorker) {
        await apiProductApi.updateWorkerNacos(apiProduct.productId, {
          nacosId: values.nacosId,
          namespace: values.namespace,
        });
      } else {
        await apiProductApi.updateSkillNacos(apiProduct.productId, {
          nacosId: values.nacosId,
          namespace: values.namespace,
        });
      }
      message.success('Nacos 关联已更新');
      setModalVisible(false);
      handleRefresh();
    } catch {
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-1">Link Nacos</h1>
      <p className="text-gray-600 mb-6">
        管理该{isWorker ? ' Worker ' : ' Skill '}关联的 Nacos 实例和命名空间
      </p>

      {currentNacosId ? (
        <Card>
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label="Nacos 实例">{currentNacosName}</Descriptions.Item>
            <Descriptions.Item label="命名空间">{currentNamespace || 'public'}</Descriptions.Item>
            <Descriptions.Item label="Nacos ID">{currentNacosId}</Descriptions.Item>
          </Descriptions>
          <div className="mt-4">
            <Button icon={<LinkOutlined />} onClick={openModal} type="primary">
              切换关联
            </Button>
          </div>
        </Card>
      ) : (
        <Card>
          <Empty description="尚未关联 Nacos 实例" image={Empty.PRESENTED_IMAGE_SIMPLE}>
            <Button icon={<LinkOutlined />} onClick={openModal} type="primary">
              关联 Nacos
            </Button>
          </Empty>
        </Card>
      )}

      <Modal
        cancelText="取消"
        confirmLoading={saving}
        okText="确认"
        onCancel={() => setModalVisible(false)}
        onOk={handleSave}
        open={modalVisible}
        title="关联 Nacos 实例"
      >
        <Form form={form} layout="vertical">
          <Form.Item
            label="Nacos 实例"
            name="nacosId"
            rules={[{ message: '请选择 Nacos 实例', required: true }]}
          >
            <Select
              loading={nacosLoading}
              onChange={handleNacosChange}
              options={nacosInstances.map((n) => ({
                label: `${n.nacosName}${n.isDefault ? ' (默认)' : ''}`,
                value: n.nacosId,
              }))}
              placeholder="选择 Nacos 实例"
            />
          </Form.Item>
          <Form.Item
            label="命名空间"
            name="namespace"
            rules={[{ message: '请选择命名空间', required: true }]}
          >
            <Select
              loading={nsLoading}
              options={namespaces.map((item: unknown) => {
                const ns = item as Record<string, string>;
                return {
                  label: ns.namespaceName || ns.namespaceId || 'public',
                  value: ns.namespaceId || '',
                };
              })}
              placeholder="选择命名空间"
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

import { useState, useEffect, useCallback } from 'react';
import { Button, Card, message, Modal, Input, Tag, Spin, Empty, Descriptions } from 'antd';
import {
  CloudServerOutlined,
  DeleteOutlined,
  PlusOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ExclamationCircleOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { getK8sClusters, registerK8sConfig, removeK8sConfig, type K8sClusterInfo } from '@/lib/k8sApi';

const { TextArea } = Input;

export default function K8sCluster() {
  const [cluster, setCluster] = useState<K8sClusterInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [modalVisible, setModalVisible] = useState(false);
  const [kubeconfig, setKubeconfig] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const fetchCluster = useCallback(async () => {
    try {
      setLoading(true);
      const res = await getK8sClusters();
      const list = Array.isArray(res.data) ? res.data : [];
      // 当前阶段只支持唯一集群
      setCluster(list.length > 0 ? list[0] : null);
    } catch {
      message.error('获取 K8s 集群信息失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchCluster();
  }, [fetchCluster]);

  const handleRegister = async () => {
    if (!kubeconfig.trim()) {
      message.warning('请输入 kubeconfig 内容');
      return;
    }
    try {
      setSubmitting(true);
      await registerK8sConfig(kubeconfig.trim());
      message.success('K8s 集群配置成功');
      setModalVisible(false);
      setKubeconfig('');
      fetchCluster();
    } catch {
      // 错误已由拦截器处理
    } finally {
      setSubmitting(false);
    }
  };

  const handleRemove = () => {
    if (!cluster) return;
    Modal.confirm({
      title: '确认删除',
      icon: <ExclamationCircleOutlined />,
      content: `确定要删除集群「${cluster.clusterName}」的配置吗？删除后 K8s 沙箱运行时将不可用。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await removeK8sConfig(cluster.configId);
          message.success('集群配置已删除');
          setCluster(null);
        } catch {
          // 错误已由拦截器处理
        }
      },
    });
  };

  const formatTime = (dateStr: string) => {
    try {
      return new Date(dateStr).toLocaleString('zh-CN');
    } catch {
      return dateStr;
    }
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-800 flex items-center gap-2">
            <CloudServerOutlined /> K8s 集群配置
          </h1>
          <p className="text-gray-500 mt-1">
            配置 K8s 集群连接信息，启用沙箱运行时为用户提供隔离的运行环境
          </p>
        </div>
        <div className="flex gap-2">
          <Button icon={<ReloadOutlined />} onClick={fetchCluster} loading={loading}>
            刷新
          </Button>
          {!cluster && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalVisible(true)}>
              配置集群
            </Button>
          )}
        </div>
      </div>

      {loading ? (
        <div className="flex justify-center py-20">
          <Spin size="large" />
        </div>
      ) : cluster ? (
        <Card>
          <Descriptions
            title={
              <span className="flex items-center gap-2">
                <CloudServerOutlined />
                {cluster.clusterName}
                {cluster.connected ? (
                  <Tag icon={<CheckCircleOutlined />} color="success">已连接</Tag>
                ) : (
                  <Tag icon={<CloseCircleOutlined />} color="error">连接失败</Tag>
                )}
              </span>
            }
            extra={
              <Button danger icon={<DeleteOutlined />} onClick={handleRemove}>
                删除配置
              </Button>
            }
            column={1}
            bordered
          >
            <Descriptions.Item label="集群地址">{cluster.serverUrl}</Descriptions.Item>
            <Descriptions.Item label="配置 ID">{cluster.configId}</Descriptions.Item>
            <Descriptions.Item label="注册时间">{formatTime(cluster.registeredAt)}</Descriptions.Item>
            <Descriptions.Item label="连接状态">
              {cluster.connected ? (
                <span className="text-green-600">正常</span>
              ) : (
                <span className="text-red-500">无法连接，请检查集群状态或 kubeconfig 是否过期</span>
              )}
            </Descriptions.Item>
          </Descriptions>
        </Card>
      ) : (
        <Card>
          <Empty
            image={<CloudServerOutlined style={{ fontSize: 64, color: '#d9d9d9' }} />}
            description={
              <div>
                <p className="text-gray-500 text-base">尚未配置 K8s 集群</p>
                <p className="text-gray-400 text-sm mt-1">
                  配置后，用户可在前台选择 K8s 沙箱运行时启动 CLI Agent
                </p>
              </div>
            }
          >
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalVisible(true)}>
              配置集群
            </Button>
          </Empty>
        </Card>
      )}

      <Modal
        title="配置 K8s 集群"
        open={modalVisible}
        onCancel={() => { setModalVisible(false); setKubeconfig(''); }}
        onOk={handleRegister}
        okText="提交"
        cancelText="取消"
        confirmLoading={submitting}
        width={640}
      >
        <p className="text-gray-500 mb-3">
          请粘贴 kubeconfig 文件内容（YAML 格式），系统将验证集群连接后保存配置。
        </p>
        <TextArea
          rows={14}
          value={kubeconfig}
          onChange={(e) => setKubeconfig(e.target.value)}
          placeholder={`apiVersion: v1\nkind: Config\nclusters:\n- cluster:\n    server: https://your-k8s-api-server:6443\n    certificate-authority-data: ...\n  name: my-cluster\n...`}
          style={{ fontFamily: 'monospace', fontSize: 12 }}
        />
      </Modal>
    </div>
  );
}

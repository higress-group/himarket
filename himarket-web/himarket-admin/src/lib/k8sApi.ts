import api from './api';

export interface K8sClusterInfo {
  configId: string;
  clusterName: string;
  serverUrl: string;
  connected: boolean;
  registeredAt: string;
}

/** 获取已注册的 K8s 集群列表 */
export const getK8sClusters = async (): Promise<{ data: K8sClusterInfo[] }> => {
  return await api.get('/k8s/clusters');
};

/** 注册 kubeconfig */
export const registerK8sConfig = async (kubeconfig: string): Promise<{ data: { configId: string } }> => {
  return await api.post('/k8s/config', { kubeconfig });
};

/** 删除集群配置 */
export const removeK8sConfig = async (configId: string): Promise<void> => {
  return await api.delete(`/k8s/config/${configId}`);
};

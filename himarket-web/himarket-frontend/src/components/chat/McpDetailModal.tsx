import { useEffect, useState } from "react";
import { Modal, Spin, Tag, Button, message, Tabs } from "antd";
import {
  AppstoreOutlined, ThunderboltOutlined,
  LinkOutlined,
} from "@ant-design/icons";
import APIs from "../../lib/apis";
import type { IProductDetail, IMcpMeta } from "../../lib/apis/product";
import { hasAvailableEndpoint } from "../../lib/utils/mcpUtils";
import { ProductIconRenderer } from "../icon/ProductIconRenderer";
import { getIconString, parseMetaIcon } from "../../lib/iconUtils";

interface McpDetailModalProps {
  open: boolean;
  product: IProductDetail | null;
  onClose: () => void;
  /** 订阅成功后的回调（刷新外层订阅列表） */
  onSubscribed?: () => void;
}

function McpDetailModal({ open, product, onClose, onSubscribed }: McpDetailModalProps) {
  const [meta, setMeta] = useState<IMcpMeta | null>(null);
  const [loading, setLoading] = useState(false);

  // 订阅相关状态
  const [subscribed, setSubscribed] = useState(false);
  const [subscribing, setSubscribing] = useState(false);
  const [unsubscribing, setUnsubscribing] = useState(false);

  // 加载 meta + 订阅状态
  useEffect(() => {
    if (!open || !product) {
      setMeta(null); setSubscribed(false); return;
    }
    setLoading(true);
    const pid = product.productId;

    Promise.all([
      APIs.getProductMcpMeta(pid).catch(() => null),
      APIs.getProductSubscriptionStatus(pid).catch(() => null),
    ]).then(([metaRes, subStatus]) => {
      if (metaRes?.code === "SUCCESS" && metaRes.data?.length > 0) setMeta(metaRes.data[0]);
      if (subStatus?.hasSubscription) setSubscribed(true);
    }).finally(() => setLoading(false));
  }, [open, product]);

  if (!product) return null;

  const displayName = meta?.displayName || meta?.mcpName || product.name;
  const description = meta?.description || product.description || "";
  const protocolType = meta?.protocolType || "";

  // 冷数据：从 product.mcpConfig.mcpServerConfig 解析连接配置
  const coldConfigs: Array<{ protocol: string; url: string }> = (() => {
    try {
      const cfg = product.mcpConfig?.mcpServerConfig;
      if (!cfg) return [];
      const parsed = typeof cfg === "string" ? JSON.parse(cfg) : cfg;
      if (Array.isArray(parsed)) return parsed;
      return [];
    } catch { return []; }
  })();

  // 热数据：从 meta.endpointUrl 获取连接配置
  const hotEndpointUrl = meta?.endpointUrl || "";
  const hotEndpointProtocol = meta?.endpointProtocol?.toLowerCase() || "";

  const protocols = protocolType ? protocolType.toLowerCase().split(",").map(p => p.trim()).filter(Boolean) : [];

  // 订阅（走正常产品订阅流程）
  const handleSubscribe = async () => {
    if (!product) return;
    setSubscribing(true);
    try {
      const consumerRes = await APIs.getPrimaryConsumer();
      if (consumerRes.code !== "SUCCESS" || !consumerRes.data) {
        message.error("获取消费者信息失败"); return;
      }
      await APIs.subscribeProduct(consumerRes.data.consumerId, product.productId);
      setSubscribed(true);
      message.success("订阅成功");
      onSubscribed?.();
    } catch { message.error("订阅失败"); }
    finally { setSubscribing(false); }
  };

  // 取消订阅（走正常产品取消订阅流程）
  const handleUnsubscribe = async () => {
    if (!product) return;
    setUnsubscribing(true);
    try {
      const consumerRes = await APIs.getPrimaryConsumer();
      if (consumerRes.code !== "SUCCESS" || !consumerRes.data) {
        message.error("获取消费者信息失败"); return;
      }
      await APIs.unsubscribeProduct(consumerRes.data.consumerId, product.productId);
      setSubscribed(false);
      message.success("已取消订阅");
      onSubscribed?.();
    } catch { message.error("取消订阅失败"); }
    finally { setUnsubscribing(false); }
  };

  // 构建连接配置 tab（冷热数据合并：相同协议优先用热数据）
  const buildTabItems = () => {
    const items: { key: string; label: React.ReactNode; children: React.ReactNode }[] = [];
    const hasHot = hotEndpointUrl && subscribed;
    const hotProto = hotEndpointProtocol || "sse";

    // 收集冷数据按协议分组
    const coldByProto: Record<string, string> = {};
    coldConfigs.forEach(cfg => {
      const proto = cfg.protocol?.toLowerCase() || "sse";
      if (cfg.url) coldByProto[proto] = cfg.url;
    });

    // 需要展示的协议集合（冷 + 热合并）
    const allProtos = new Set<string>(Object.keys(coldByProto));
    if (hasHot) allProtos.add(hotProto);

    allProtos.forEach(proto => {
      const isHot = hasHot && proto === hotProto;
      const url = isHot ? hotEndpointUrl : coldByProto[proto];
      if (!url) return;

      items.push({
        key: `${proto}`,
        label: proto.toUpperCase(),
        children: (
          <div className="text-xs font-mono text-gray-500 bg-gray-50 rounded-lg p-2 break-all">
            {url}
          </div>
        ),
      });
    });

    // 如果没有任何数据，根据 protocols 生成占位 tab
    if (items.length === 0) {
      protocols.forEach(proto => {
        items.push({
          key: `placeholder-${proto}`,
          label: proto.toUpperCase(),
          children: <p className="text-xs text-gray-400 py-2">暂无连接配置</p>,
        });
      });
    }

    return items;
  };

  return (
    <Modal open={open} onCancel={onClose} footer={null} width={520} destroyOnClose closable title={null}>
      {loading ? (
        <div className="flex justify-center py-12"><Spin size="large" /></div>
      ) : (
        <div className="space-y-4">
          {/* 紧凑 Header */}
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-purple-100 to-indigo-100 flex items-center justify-center flex-shrink-0 overflow-hidden">
              {meta?.icon || product.icon ? (
                <ProductIconRenderer className="w-full h-full object-cover" iconType={getIconString(parseMetaIcon(meta?.icon) || product.icon)} />
              ) : (
                <AppstoreOutlined className="text-purple-500 text-lg" />
              )}
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-base font-semibold text-gray-900 truncate">{displayName}</div>
              {meta?.mcpName && <div className="text-[11px] text-gray-400 font-mono truncate">{meta.mcpName}</div>}
            </div>
          </div>

          {description && <p className="text-xs text-gray-500 leading-relaxed">{description}</p>}

          {/* 连接配置区域 */}
          <div>
            <h3 className="text-sm font-semibold text-gray-900 mb-2 flex items-center gap-1.5">
              <LinkOutlined className="text-green-500" />连接配置
            </h3>
            {protocols.length === 0 && coldConfigs.length === 0 ? (
              <div className="text-xs text-gray-400 text-center py-4">暂无连接配置信息</div>
            ) : (
              <div>
                <div className="mb-2 flex items-center gap-1.5">
                  {protocols.map(p => (
                    <Tag key={p} className="m-0 border-0 bg-gray-100 text-gray-600 text-xs">{p.toUpperCase()}</Tag>
                  ))}
                  {subscribed && <Tag color="green" className="m-0 border-0">已订阅</Tag>}
                </div>
                <Tabs size="small" defaultActiveKey={buildTabItems()[0]?.key} items={buildTabItems()} />
                {/* 订阅/取消订阅按钮 */}
                <div className="mt-2">
                  {subscribed ? (
                    <Button size="small" danger loading={unsubscribing} onClick={handleUnsubscribe} block>取消订阅</Button>
                  ) : hasAvailableEndpoint(meta) ? (
                    <Button type="primary" size="small" icon={<ThunderboltOutlined />} loading={subscribing} onClick={handleSubscribe} block>订阅</Button>
                  ) : null}
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </Modal>
  );
}

export default McpDetailModal;

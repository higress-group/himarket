import { useState, useEffect, useCallback } from "react";
import { ArrowLeftOutlined, CloudServerOutlined } from "@ant-design/icons";
import { Button } from "antd";
import { useNavigate } from "react-router-dom";
import { Layout } from "../components/Layout";
import { McpCard } from "../components/square/McpCard";
import { useAuth } from "../hooks/useAuth";
import APIs from "../lib/apis";
import { getIconString, parseMetaIcon } from "../lib/iconUtils";
import type { IProductDetail, IMcpMeta } from "../lib/apis/product";
import { getProductMcpMetaBatch, getProductMcpMetaBatchPublic } from "../lib/apis/product";
import dayjs from "dayjs";
import { CardGridSkeleton } from "../components/loading";

interface McpProductItem {
  product: IProductDetail;
  meta: IMcpMeta | null;
}

function collectProtocols(product: IProductDetail, meta: IMcpMeta | null): string[] {
  const set = new Set<string>();
  if (meta?.protocolType) {
    meta.protocolType.split(",").map(p => p.trim().toUpperCase()).filter(Boolean).forEach(p => set.add(p));
  }
  const coldProto = product.mcpConfig?.meta?.protocol;
  if (coldProto) {
    coldProto.split(",").map((p: string) => p.trim().toUpperCase()).filter(Boolean).forEach((p: string) => set.add(p));
  }
  if (meta?.endpointProtocol) {
    meta.endpointProtocol.split(",").map(p => p.trim().toUpperCase()).filter(Boolean).forEach(p => set.add(p));
  }
  if (product.mcpConfig?.mcpServerConfig?.rawConfig && Object.keys(product.mcpConfig.mcpServerConfig.rawConfig).length > 0) {
    set.add("STDIO");
  }
  return Array.from(set);
}

function countTools(product: IProductDetail, meta: IMcpMeta | null): number {
  const src = meta?.toolsConfig || product.mcpConfig?.tools;
  if (!src) return 0;
  try {
    const parsed = typeof src === "string" ? JSON.parse(src) : src;
    if (Array.isArray(parsed)) return parsed.length;
    return parsed?.tools?.length || 0;
  } catch { return 0; }
}

function parseTags(meta: IMcpMeta | null): string[] {
  if (!meta?.tags) return [];
  try {
    const parsed = JSON.parse(meta.tags);
    return Array.isArray(parsed) ? parsed : meta.tags.split(",").map((t: string) => t.trim()).filter(Boolean);
  } catch {
    return meta.tags.split(",").map((t: string) => t.trim()).filter(Boolean);
  }
}

function MyMcp() {
  const navigate = useNavigate();
  const { isLoggedIn } = useAuth();

  const [mcpItems, setMcpItems] = useState<McpProductItem[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchMyMcps = useCallback(async () => {
    setLoading(true);
    try {
      const consumerRes = await APIs.getPrimaryConsumer();
      if (consumerRes.code !== "SUCCESS" || !consumerRes.data) {
        setMcpItems([]);
        return;
      }
      const subRes = await APIs.getConsumerSubscriptions(consumerRes.data.consumerId, { size: 200 });
      if (subRes.code !== "SUCCESS" || !subRes.data?.content) {
        setMcpItems([]);
        return;
      }
      const mcpSubs = subRes.data.content.filter(
        s => s.status === 'APPROVED' && s.productType === 'MCP_SERVER'
      );
      const productIds = mcpSubs.map(s => s.productId);
      if (productIds.length === 0) {
        setMcpItems([]);
        return;
      }
      const fetchFn = isLoggedIn ? getProductMcpMetaBatch : getProductMcpMetaBatchPublic;
      const metaRes = await fetchFn(productIds);
      const metaList = metaRes.code === "SUCCESS" ? (metaRes.data || []) : [];
      const metaByProduct = new Map<string, IMcpMeta>();
      for (const meta of metaList) {
        if (!metaByProduct.has(meta.productId)) {
          metaByProduct.set(meta.productId, meta);
        }
      }
      const items: McpProductItem[] = mcpSubs.map(sub => {
        const meta = metaByProduct.get(sub.productId);
        const product: IProductDetail = {
          productId: sub.productId,
          name: sub.productName || meta?.displayName || meta?.mcpName || "",
          description: meta?.description || "",
          type: "MCP_SERVER",
          icon: meta?.icon ? (() => { try { return JSON.parse(meta.icon); } catch { return undefined; } })() : undefined,
          createAt: sub.createAt || "",
        } as IProductDetail;
        return { product, meta: meta || null };
      });
      setMcpItems(items);
    } catch {
      // 未登录或获取失败
    } finally {
      setLoading(false);
    }
  }, [isLoggedIn]);

  useEffect(() => {
    if (isLoggedIn) fetchMyMcps();
  }, [fetchMyMcps, isLoggedIn]);

  return (
    <Layout>
      <div className="flex flex-col h-[calc(100vh-96px)] overflow-auto scrollbar-hide">
        {/* 顶部区域 */}
        <div className="flex-shrink-0 px-6 py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <Button
                type="text"
                icon={<ArrowLeftOutlined />}
                onClick={() => navigate("/mcp")}
                className="rounded-xl"
              />
              <span className="text-lg font-medium text-gray-900">我的 MCP</span>
              {mcpItems.length > 0 && (
                <span className="text-sm text-gray-400">共 {mcpItems.length} 个已订阅</span>
              )}
            </div>
          </div>
        </div>

        {/* 内容区域 */}
        <div className="flex-1 px-4 pt-2 pb-4 flex-shrink-0">
          <div className="pb-4">
            {loading ? (
              <CardGridSkeleton count={4} columns={{ sm: 1, md: 2, lg: 4 }} />
            ) : mcpItems.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-24 text-gray-400">
                <CloudServerOutlined className="text-5xl mb-4 text-gray-300" />
                <span className="text-sm">暂无已订阅的 MCP</span>
                <span className="text-xs mt-1 text-gray-300">去 MCP 广场浏览并订阅</span>
              </div>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6 max-w-[1600px] mx-auto">
                {mcpItems.map((item) => {
                  const displayName = item.meta?.displayName || item.meta?.mcpName || item.product.name;
                  return (
                    <McpCard
                      key={item.product.productId}
                      icon={getIconString(parseMetaIcon(item.meta?.icon) || item.product.icon, displayName)}
                      name={displayName}
                      mcpName={item.meta?.mcpName}
                      description={item.meta?.description || item.product.description}
                      releaseDate={dayjs(item.product.createAt).format("YYYY-MM-DD")}
                      protocols={collectProtocols(item.product, item.meta)}
                      toolCount={countTools(item.product, item.meta)}
                      tags={parseTags(item.meta)}
                      categoryName={item.product.categories?.[0]?.name}
                      subscribed={true}
                      isLoggedIn={isLoggedIn}
                      onClick={() => navigate(`/mcp/${item.product.productId}`)}
                    />
                  );
                })}
              </div>
            )}
          </div>
        </div>
      </div>
    </Layout>
  );
}

export default MyMcp;

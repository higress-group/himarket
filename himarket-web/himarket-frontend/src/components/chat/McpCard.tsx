import { Button, Popover, Skeleton, Divider } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import APIs, { type IProductDetail, type IMcpTool } from '../../lib/apis';
import { getIconString } from '../../lib/iconUtils';
import { More } from '../icon';
import { ProductIconRenderer } from '../icon/ProductIconRenderer';

interface McpCardProps {
  data: IProductDetail;
  isSubscribed?: boolean;
  isAdded?: boolean;
  onAdd?: (product: IProductDetail) => void;
  onRemove?: (product: IProductDetail) => void;
  onQuickSubscribe?: (product: IProductDetail) => void;
  onShowMore?: (product: IProductDetail) => void;
  moreLoading?: boolean;
}

function McpCard(props: McpCardProps) {
  const {
    data,
    isAdded = false,
    isSubscribed = false,
    onAdd,
    onQuickSubscribe,
    onRemove,
    onShowMore,
  } = props;
  const { t } = useTranslation('chat');

  const [toolsLoading, setToolsLoading] = useState(false);
  const [tools, setTools] = useState<IMcpTool[]>([]);
  const [popoverVisible, setPopoverVisible] = useState(false);

  // 加载工具列表
  const loadTools = async () => {
    if (tools.length > 0) return; // 已加载过则不重复加载

    setToolsLoading(true);
    try {
      const resp = await APIs.getMcpTools({ productId: data.productId });
      if (resp.data?.tools) {
        setTools(resp.data.tools);
      }
    } catch (error) {
      console.error('Failed to load MCP tools:', error);
    } finally {
      setToolsLoading(false);
    }
  };

  // 当 Popover 打开时加载工具列表
  const handleVisibleChange = (visible: boolean) => {
    setPopoverVisible(visible);
    if (visible) {
      loadTools();
      onShowMore?.(data);
    }
  };

  const handleAdd = () => {
    if (isAdded) {
      onRemove?.(data);
    } else {
      onAdd?.(data);
    }
  };

  const handleQuickSubscribe = () => {
    onQuickSubscribe?.(data);
  };

  return (
    <div
      className="
        bg-white/60 backdrop-blur-sm rounded-2xl p-5
        border border-[#e5e5e5]
        cursor-pointer
        transition-all duration-300 ease-in-out
        hover:bg-white hover:shadow-md hover:scale-[1.02] hover:border-colorPrimary/30
        active:scale-[0.98]
        relative overflow-hidden group
        h-[200px] flex flex-col gap-4
      "
    >
      {/* 上部：Logo、名称和状态 */}
      <div className="flex gap-3 items-start">
        <div className="w-12 h-12 rounded-[14px] border border-white/60 bg-white/70 flex items-center justify-center flex-shrink-0 overflow-hidden">
          <ProductIconRenderer
            className="w-full h-full object-cover"
            iconType={getIconString(data.icon, data.name)}
          />
        </div>
        <div className="flex w-full h-full justify-between">
          <div className="flex h-full flex-col justify-between">
            <h3 className="font-medium text-base  truncate">{data.name}</h3>
            <div>
              <span
                className={`text-xs px-2 py-1 rounded-lg ${
                  isSubscribed || data.subscribable === false
                    ? 'bg-colorPrimaryBgHover text-colorPrimary'
                    : 'bg-gray-100 text-gray-600'
                }`}
              >
                {isSubscribed
                  ? t('mcpCard.subscribed')
                  : data.subscribable === false
                    ? t('mcpCard.noSubscriptionRequired')
                    : t('mcpCard.notSubscribed')}
              </span>
            </div>
          </div>
          <Popover
            content={
              <div className="w-80 max-h-96 overflow-y-auto">
                {toolsLoading ? (
                  // 骨架屏
                  <div className="space-y-3">
                    <Skeleton.Input active size="small" style={{ width: 100 }} />
                    {[1, 2, 3].map((i) => (
                      <div key={i}>
                        <Skeleton active paragraph={{ rows: 2 }} title={{ width: '60%' }} />
                        {i < 3 && <Divider style={{ margin: '12px 0' }} />}
                      </div>
                    ))}
                  </div>
                ) : (
                  <div>
                    {/* 顶部标题 */}
                    <div className="font-medium text-base mb-3">
                      {t('mcpCard.toolsWithCount', { count: tools.length })}
                    </div>

                    {/* 工具列表 */}
                    {tools.length === 0 ? (
                      <div className="text-sm text-gray-400">{t('mcpCard.noTools')}</div>
                    ) : (
                      <div className="space-y-3">
                        {tools.map((tool, index) => (
                          <div key={tool.name}>
                            <div className="space-y-1">
                              <div className="font-medium text-sm text-gray-900">{tool.name}</div>
                              <div className="text-xs text-gray-500 leading-relaxed">
                                {tool.description || t('mcpCard.noDescription')}
                              </div>
                            </div>
                            {index < tools.length - 1 && <Divider style={{ margin: '12px 0' }} />}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            }
            onOpenChange={handleVisibleChange}
            open={popoverVisible}
            placement="bottom"
            trigger={'click'}
          >
            <div
              onClick={(e) => e.stopPropagation()}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.stopPropagation();
                }
              }}
              role="button"
              tabIndex={0}
            >
              <More className="fill-mainTitle" />
            </div>
          </Popover>
        </div>
      </div>

      {/* 中部：描述 */}
      <div className="flex-1 overflow-hidden">
        <p className="text-sm text-colorTextSecondaryCustom line-clamp-2">
          {data.description || t('mcpCard.noDescription')}
        </p>
      </div>

      {/* 下部：按钮区域 */}
      <div className="flex gap-2">
        {isSubscribed || data.subscribable === false ? (
          <Button block onClick={handleAdd} type={isAdded ? 'default' : 'primary'}>
            {isAdded ? t('mcpCard.remove') : t('mcpCard.add')}
          </Button>
        ) : (
          <div className="flex gap-2 justify-between w-full">
            <Button className="flex-1" onClick={handleQuickSubscribe}>
              {t('mcpCard.quickSubscribe')}
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}

export default McpCard;

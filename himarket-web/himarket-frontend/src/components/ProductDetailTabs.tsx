import { Tabs } from 'antd';

import type { TabsProps } from 'antd';
import type { CSSProperties, ReactNode } from 'react';

const PRODUCT_DETAIL_TABS_CARD_CLASS =
  'overflow-hidden rounded-[14px] border border-[#DDE5F0] bg-white/90 shadow-[0_18px_50px_rgba(15,23,42,0.05)] backdrop-blur-sm';

const PRODUCT_DETAIL_TABS_NAV_CLASS =
  '[&_.ant-tabs-nav]:mb-5 [&_.ant-tabs-nav]:px-5 [&_.ant-tabs-tab]:py-4';

const PRODUCT_DETAIL_TABS_CONTENT_CLASS =
  '[&_.ant-tabs-content-holder]:px-5 [&_.ant-tabs-content-holder]:pb-5';

function classNames(...values: Array<string | undefined>) {
  return values.filter(Boolean).join(' ');
}

interface ProductDetailTabLabelProps {
  children: ReactNode;
  icon: ReactNode;
}

interface ProductDetailTabsProps extends Omit<TabsProps, 'className' | 'size'> {
  cardClassName?: string;
  contentPadded?: boolean;
  style?: CSSProperties;
  tabsClassName?: string;
}

export function ProductDetailTabLabel({ children, icon }: ProductDetailTabLabelProps) {
  return (
    <span className="flex items-center gap-1.5 font-semibold">
      <span className="inline-flex text-sm">{icon}</span>
      <span>{children}</span>
    </span>
  );
}

export function ProductDetailTabs({
  cardClassName,
  contentPadded = true,
  style,
  tabsClassName,
  ...tabsProps
}: ProductDetailTabsProps) {
  return (
    <div className={classNames(PRODUCT_DETAIL_TABS_CARD_CLASS, cardClassName)} style={style}>
      <Tabs
        {...tabsProps}
        className={classNames(
          PRODUCT_DETAIL_TABS_NAV_CLASS,
          contentPadded ? PRODUCT_DETAIL_TABS_CONTENT_CLASS : undefined,
          tabsClassName,
        )}
        size="large"
      />
    </div>
  );
}

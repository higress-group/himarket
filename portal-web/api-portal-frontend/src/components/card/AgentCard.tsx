import React from 'react';
import styles from './AgentCard.module.css';
import commonStyles from './CommonCard.module.css';
import CommonCard from './CommonCard';
import ArrowIcon from './ArrowIcon';

interface AgentCardProps {
  onClick?: () => void;
}

// 图标配置数据
const ICON_CONFIGS = [
  {
    className: 'icon1',
    src: 'https://img.alicdn.com/imgextra/i4/6000000007431/O1CN01uEwB3b24lSGtXHUT1_!!6000000007431-2-gg_dtc.png',
  },
  {
    className: 'icon2',
    src: 'https://img.alicdn.com/imgextra/i4/6000000005762/O1CN016vnqPJ1sR3Emi7EAd_!!6000000005762-2-gg_dtc.png',
  },
  {
    className: 'icon3',
    src: 'https://img.alicdn.com/imgextra/i1/6000000005201/O1CN01Zr8fcn1oI72ptwPCR_!!6000000005201-2-gg_dtc.png',
  },
  {
    className: 'icon4',
    src: 'https://img.alicdn.com/imgextra/i4/6000000003603/O1CN01BPAzi01cUE8Er8PgP_!!6000000003603-2-gg_dtc.png',
  },
  {
    className: 'icon5',
    src: 'https://img.alicdn.com/imgextra/i4/6000000006949/O1CN01KerPWm21ChMsC3GSz_!!6000000006949-2-gg_dtc.png',
  },
  {
    className: 'icon6',
    src: 'https://img.alicdn.com/imgextra/i2/6000000004304/O1CN01joOQVX1hfHm2AXuE6_!!6000000004304-2-gg_dtc.png',
  },
  {
    className: 'icon7',
    src: 'https://img.alicdn.com/imgextra/i1/6000000004165/O1CN01LpQ1C81gdcijtUvAU_!!6000000004165-2-gg_dtc.png',
  },
] as const;

const AgentCard: React.FC<AgentCardProps> = ({ onClick }) => {
  return (
    <CommonCard to="/agents">
      <div className={styles.agentCard} onClick={onClick}>
        <span className={styles.title}>
          <span className={styles.titleLabel}>智能体</span>
        </span>
        <div className={styles.iconList}>
          {ICON_CONFIGS.map((icon, index) => (
            <img
              key={index}
              className={styles[icon.className]}
              src={icon.src}
              alt=""
            />
          ))}
        </div>
        <div className={styles.rightSection}>
          <img
            className={styles.rightImage}
            src="https://img.alicdn.com/imgextra/i1/6000000005513/O1CN01C0pNK11qb0cLK9qrg_!!6000000005513-2-gg_dtc.png"
          />
        </div>
        <div className={styles.topGradient}></div>

        <div className={styles.bottomGradient}></div>
        <img
          className={styles.bottomLeftImage}
          src="https://img.alicdn.com/imgextra/i2/O1CN01qh9GBE1Np5wSrTpt2_!!6000000001618-2-tps-1296-766.png"
        />
        <div className={styles.bottomRightSection}>
          <img
            className={styles.bottomRightImage}
            src="https://img.alicdn.com/imgextra/i3/6000000001400/O1CN01EnLOVN1MDFbBnNJY4_!!6000000001400-2-gg_dtc.png"
          />
        </div>
        <div className={styles.topRightIcon}>
          <ArrowIcon className={commonStyles.arrowRightIcon} />
        </div>
      </div>
    </CommonCard>
  );
};

export default AgentCard;

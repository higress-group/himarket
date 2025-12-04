import React from 'react';
import CommonCard from './CommonCard';
import ArrowIcon from './ArrowIcon';
import styles from './FunModelCard.module.css';

interface FunModelCardProps {
  onClick?: () => void;
  onFunArtClick?: () => void;
}

const ICON_CONFIGS = [
  'https://img.alicdn.com/imgextra/i3/6000000004413/O1CN01mY4qbQ1iTCwdu1xQ0_!!6000000004413-2-gg_dtc.png',
  'https://img.alicdn.com/imgextra/i1/6000000007756/O1CN01qXlLKd27AIzcNSce9_!!6000000007756-2-gg_dtc.png',
  'https://img.alicdn.com/imgextra/i4/6000000000894/O1CN013OnMGA1ITVBo2tTqq_!!6000000000894-2-gg_dtc.png',
  'https://img.alicdn.com/imgextra/i4/6000000001253/O1CN01c10HT01L7vNRhNe7e_!!6000000001253-2-gg_dtc.png',
  'https://img.alicdn.com/imgextra/i3/6000000005946/O1CN01c4YKsX1tnK77nd2CK_!!6000000005946-2-gg_dtc.png',
  'https://img.alicdn.com/imgextra/i3/6000000004993/O1CN01eHdhnH1mkqfB47O09_!!6000000004993-2-gg_dtc.png',
  'https://img.alicdn.com/imgextra/i1/6000000002871/O1CN01njTQ7A1X4yDAydYnu_!!6000000002871-2-gg_dtc.png',
];

const FunModelCard: React.FC<FunModelCardProps> = ({ onClick, onFunArtClick }) => {
  return (
    <CommonCard to="models">
      <div className={styles.funModelCardContainer}>
        <div className={styles.funModelCard} onClick={onClick}>
          <div className={styles.topContainer}>
            <div className={styles.serviceLabel}>
              <span>模型市场</span>
            </div>
            <div className={styles.backgroundImageContainer}>
              <img
                className={styles.gifIndex}
                src="https://img.alicdn.com/imgextra/i2/O1CN01t68fd21bJP9qjFONX_!!6000000003444-1-tps-1000-563.gif"
                alt=""
              />
            </div>
            <div className={styles.topRightIcon}>
              <ArrowIcon className={styles.arrowRightIcon} />
            </div>
            <div className={styles.iconList}>
              {ICON_CONFIGS?.map((src, index) => {
                const ICON_SPACING = 38;
                const RIGHT_START = 20;
                const LEFT_START = 138;
                const isRightSide = index < 3;

                return (
                  <img
                    key={index}
                    src={src}
                    alt=""
                    style={{
                      zIndex: 100 - index,
                      top: '76px',
                      ...(isRightSide
                        ? { right: `${RIGHT_START + index * ICON_SPACING}px` }
                        : { left: `${LEFT_START - (index - 3) * ICON_SPACING}px` }),
                    }}
                  />
                );
              })}
            </div>
          </div>
          <div
            className={styles.bottomContainer}
            onClick={(e) => {
              e.stopPropagation();
              e.preventDefault();
              onFunArtClick && onFunArtClick();
            }}
          >
            <ArrowIcon className={styles.bottomRightIcon} />
            <div className={styles.bottomBlueBlur}></div>
            <div className={styles.bottomBackground}>
              <div className={styles.blurredWhiteBar}></div>
              <div className={styles.bottomGradientOverlay}></div>
              <div className={styles.blurredWhiteBarLarge}></div>
            </div>
          </div>
        </div>
      </div>
    </CommonCard>
  );
};

export default FunModelCard;

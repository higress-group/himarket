import React from 'react';
import styles from './FunAppCenterCard.module.css';
import commonStyles from './CommonCard.module.css';
import CommonCard from './CommonCard';
import ArrowIcon from './ArrowIcon';
import ChatThum from "../../assets/chat-thum.svg";
import ChatThumCard from "../../assets/chat-thum-card.svg";

interface FunFlowCardProps {
  onClick?: () => void;
}

// 图标配置数据
// const ICON_CONFIGS = [
//   'https://img.alicdn.com/imgextra/i3/6000000007136/O1CN015mUAQm22aLQqbP12V_!!6000000007136-2-gg_dtc.png',
//   'https://img.alicdn.com/imgextra/i4/6000000001401/O1CN01NDpafA1MDhzjo5NVR_!!6000000001401-2-gg_dtc.png',
//   'https://img.alicdn.com/imgextra/i1/6000000000695/O1CN01KIhdsk1H0MN9M86NJ_!!6000000000695-2-gg_dtc.png',
//   'https://img.alicdn.com/imgextra/i4/6000000008073/O1CN01DiO6rW29VUXuMi1wu_!!6000000008073-2-gg_dtc.png',
//   'https://img.alicdn.com/imgextra/i4/O1CN01VQpE0i1D2PgaAhmKx_!!6000000000158-2-tps-96-96.png',
//   'https://img.alicdn.com/imgextra/i3/6000000001505/O1CN01bQepAJ1MzLBZTnhZo_!!6000000001505-2-gg_dtc.png',
//   'https://img.alicdn.com/imgextra/i2/6000000003138/O1CN01DoOMCz1Z3Fxe5l2rL_!!6000000003138-2-gg_dtc.png',
// ] as const;

const FunAppCenterCard: React.FC<FunFlowCardProps> = ({ onClick }) => {
  return (
    <CommonCard to="/chat">
      <div className={styles.funFlowCard} onClick={onClick}>
        <span className={styles.title}>
          <span className={styles.titleLabel}>体验中心</span>
        </span>
        <div className={styles.content}>
          <div className={styles.iconsRow}>
          </div>
          <div className={styles.imageSection}>
            <img
              className={styles.leftImage}
              src={ChatThumCard}
            />
            <img
              className={styles.rightImage}
              src={ChatThum}
            />
          </div>
        </div>
        <div className={styles.topRightIcon}>
          <ArrowIcon className={commonStyles.arrowRightIcon} />
        </div>
      </div>
    </CommonCard>
  );
};

export default FunAppCenterCard;

import React from 'react';
import styles from './CommonCard.module.css';

interface FunModelCardProps {
  onClick?: () => void;
  onFunArtClick?: () => void;
  children?: React.ReactNode;
}

const CommonCard: React.FC<FunModelCardProps> = ({ children }) => {
  return (
    <div className={styles.commonCardFloatingContainer}>
      <div className={styles.commonCardContainer}>
        <div className={styles.animationContainer}>
          <div className={styles.square}></div>
          <div className={styles.testCircle}></div>
          <div className={styles.testCircle2}></div>
          <div className={styles.testCircle3}></div>
        </div>
        <div className={styles.contentContainer}>{children}</div>
      </div>
    </div>
  );
};

export default CommonCard;

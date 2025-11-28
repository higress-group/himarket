interface SendButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  isLoading: boolean;
}

const SendButton: React.FC<SendButtonProps> = ({
  isLoading,
  className = '',
  children,
  ...props
}) => {
  return (
    <>
      <style>{`
        /* 1. 旋转动画 */
        @keyframes spin-tail {
          0% { transform: rotate(0deg); }
          100% { transform: rotate(360deg); }
        }

        /* 2. 拖尾线圈核心样式 */
        .loading-tail-ring {
          /* 使用 conic-gradient 制作从透明到当前颜色的渐变 */
          background: conic-gradient(from 0deg, transparent 0%, currentColor 100%);
          
          /* 使用 mask 挖空中间，形成线条 */
          /* 注意：calc(100% - 3px) 中的 3px 就是线条的粗细 */
          -webkit-mask: radial-gradient(closest-side, transparent calc(100% - 3px), black calc(100% - 3px));
          mask: radial-gradient(closest-side, transparent calc(100% - 3px), black calc(100% - 3px));
          
          /* 应用动画 */
          animation: spin-tail 1s linear infinite;
        }
      `}</style>

      <button
        type="button"
        disabled={isLoading}
        // 这里的 className 允许你传入 w-*, h-*, text-*, bg-* 等 tailwind 类
        // 默认添加了 relative, flex 等布局属性
        className={`relative flex items-center justify-center rounded-full transition-all active:scale-95 ${className}`}
        {...props}
      >
        {isLoading ? (
          /* Loading 状态：内容被替换为 Spinner */
          <div className="absolute inset-0 w-full h-full flex items-center justify-center">

            {/* A. 外圈：流星拖尾线 */}
            {/* absolute inset-0 撑满按钮 */}
            <div className="loading-tail-ring absolute inset-0 rounded-full" />

            {/* B. 中间：圆角正方形 */}
            {/* w-1/3 表示宽度是按钮的 33% */}
            <div className="relative bg-current w-1/3 h-1/3 rounded-[20%]" />

          </div>
        ) : (
          /* 非 Loading 状态：显示原本的内容 (图标/文字) */
          <div className="flex items-center justify-center w-full h-full">
            {children}
          </div>
        )}
      </button>
    </>
  );
};

export default SendButton;;
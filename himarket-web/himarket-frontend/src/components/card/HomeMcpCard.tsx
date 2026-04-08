import { Button } from 'antd';

import Circle from './circle';
import More from '../../assets/more.svg';
import { ArrowRight } from '../icon';
import CommonCard from './CommonCard';

function HomeMCPCard() {
  return (
    <CommonCard to="/mcp">
      <div
        className="absolute w-full h-full z-[1] animate-[fadeIn_0.8s_ease-out_0.2s_both]"
        style={{
          background: 'linear-gradient(324deg, #C6D1FF 0%, #E1E7FF 21%, #FFFFFF 100%)',
        }}
      />
      <div className="h-full relative z-[3] flex flex-col justify-between p-6">
        <div className="flex flex-col gap-4">
          <div className="font-medium animate-[fadeInLeft_0.6s_ease-out_0.3s_both]">MCP 市场</div>
          <div className="flex pl-3">
            {['⏰', '🌦️', '🌏', '💱'].map((emoji, index) => (
              <Circle
                className="w-12 h-12 text-[20px] animate-[fadeInScale_0.5s_ease-out_both] group-hover:scale-110 group-hover:-translate-y-1 transition-all duration-300"
                key={emoji}
                style={{
                  animationDelay: `${0.4 + index * 0.08}s`,
                  marginLeft: -12,
                }}
              >
                {emoji}
              </Circle>
            ))}
            <img
              alt=""
              className="animate-[fadeInScale_0.5s_ease-out_both] group-hover:scale-110 group-hover:-translate-y-1 transition-all duration-300"
              src={More}
              style={{
                animationDelay: '0.72s',
                marginLeft: -12,
              }}
            />
          </div>
        </div>
        <div className="absolute w-[120%] top-[26%] left-[-14%] scale-[.6] -rotate-[20deg] animate-[slideInRotateLeft_0.8s_ease-out_0.6s_both] transition-all duration-500">
          <ProductCard
            data={{
              description:
                'Time 是一个提供时间和时区转换功能的 MCP 服务，使 LLM 能够获取当前时间信息并使用 IANA 时区名称执行时区转换，自动检测系统时区。',
              icon: '⏰',
              name: 'Time',
            }}
            isAdded={true}
            isSubscribed={true}
          />
        </div>
        <div className="absolute w-[120%] top-[46%] left-[6%] scale-[.6] rotate-[15deg] animate-[slideInRotate_0.8s_ease-out_0.75s_both] transition-all duration-500">
          <ProductCard
            data={{
              description: '支持查询中国十大银行的外汇牌价，以及汇率查询',
              icon: '💱',
              name: 'Exchange Rate Query',
            }}
            isAdded={false}
            isSubscribed={true}
          />
        </div>
        <div className="animate-[scaleIn_0.5s_ease-out_0.9s_both]">
          <Circle className="w-8 h-8 inline-flex group-hover:w-auto group-hover:px-4 transition-all duration-500 ease-out group-hover:bg-black group-hover:border-transparent">
            <span className="max-w-0 overflow-hidden opacity-0 group-hover:max-w-xs group-hover:opacity-100 group-hover:mr-2 transition-all duration-500 ease-out text-white whitespace-nowrap text-sm font-medium">
              立即体验
            </span>
            <ArrowRight className="fill-mainTitle group-hover:fill-white transition-colors duration-500 ease-out" />
          </Circle>
        </div>
      </div>
    </CommonCard>
  );
}

function ProductCard(props: {
  isSubscribed: boolean;
  isAdded: boolean;
  data: {
    name: string;
    description: string;
    icon: string;
  };
}) {
  const { data, isAdded, isSubscribed } = props;
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
        <div className="w-14 h-14 text-[40px]">{data.icon}</div>
        <div className="flex w-full h-full justify-between">
          <div className="flex h-full flex-col justify-between">
            <h3 className="font-medium text-base  truncate">{data.name}</h3>
            <div>
              <span
                className={`text-xs px-2 py-1 rounded-lg ${
                  isSubscribed
                    ? 'bg-colorPrimaryBgHover text-colorPrimary'
                    : 'bg-gray-100 text-gray-600'
                }`}
              >
                {isSubscribed ? '已订阅' : '未订阅'}
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* 中部：描述 */}
      <div className="flex-1 overflow-hidden">
        <p className="text-sm text-colorTextSecondaryCustom line-clamp-2">
          {data.description || '暂无描述'}
        </p>
      </div>

      {/* 下部：按钮区域 */}
      <div className="flex gap-2">
        {isSubscribed ? (
          <Button block type={isAdded ? 'default' : 'primary'}>
            {isAdded ? '取消添加' : '添加'}
          </Button>
        ) : (
          <div className="flex gap-2 justify-between w-full">
            <Button className="flex-1">快速订阅</Button>
          </div>
        )}
      </div>
    </div>
  );
}

export default HomeMCPCard;

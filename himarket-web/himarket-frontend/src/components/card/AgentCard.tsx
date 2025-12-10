import Person1 from "../../assets/person1.svg";
import Person2 from "../../assets/person2.svg";
import Person3 from "../../assets/person3.svg";
import Person4 from "../../assets/person4.svg";
import More from "../../assets/more.svg";
import Circle from "./circle";
import { ArrowRight } from "../icon";
import CommonCard from "./CommonCard";

function HomeAgentCard() {
  return (
    <CommonCard to="/agents">
      <div
        className="absolute w-full h-full z-[1] animate-[fadeIn_0.8s_ease-out_0.2s_both]"
        style={{
          background: "linear-gradient(324deg, #C6C8FF 0%, #E1E2FF 21%, #FFFFFF 99%)",
        }}
      />
      <div className="h-full relative z-[3] flex flex-col justify-between p-6">
        <div className="h-full relative flex flex-col gap-4">
          <div className="font-medium animate-[fadeInLeft_0.6s_ease-out_0.3s_both]">智能体市场</div>
          <div className="flex pl-3">
            {[
              Person1, Person2, Person3, Person4, More,
            ].map((img, index) => (
              <img
                key={index}
                style={{
                  marginLeft: -12,
                  animationDelay: `${0.4 + index * 0.08}s`
                }}
                src={img}
                className="animate-[fadeInScale_0.5s_ease-out_both] group-hover:scale-110 group-hover:-translate-y-1 transition-all duration-300"
              />
            ))}
          </div>
          <div className="flex flex-col gap-8 mt-[20%]">
            <div className="flex justify-end">
              <TextTooltip className="flex-1 max-w-[90%] animate-[fadeInRight_0.7s_ease-out_0.65s_both] group-hover:translate-x-2 group-hover:scale-105 transition-all duration-300" style={{ boxShadow: "0px 4px 8px 0px rgba(24, 101, 255, 0.16)" }} placement="r" classNames={{ root: "bg-colorPrimaryBorderHover" }}>
                <span className="text-white text-xs">帮我生成一个三日杭州旅行攻略</span>
              </TextTooltip>
            </div>
            <div className="flex justify-start">
              <TextTooltip className="flex-1 max-w-[90%] animate-[fadeInLeft_0.7s_ease-out_0.8s_both] group-hover:-translate-x-2 group-hover:scale-105 transition-all duration-300" style={{ boxShadow: "0px 8px 24px 0px rgba(71, 71, 235, 0.08)" }} placement="l" classNames={{ root: "bg-[#F9FAFB]" }}>
                <div className="flex flex-col gap-2 w-full p-2">
                  <span className="text-colorPrimaryBorderHover text-xs">
                    正在为您生成旅行计划
                  </span>
                  <div className="w-full h-2 rounded group-hover:w-3/4 transition-all duration-300" style={{ background: "linear-gradient(90deg, rgba(224, 231, 255, 0.7) 0%, rgba(224, 231, 255, 0.2) 100%)" }}></div>
                  <div className="w-full h-2 rounded group-hover:w-2/3 transition-all duration-300 delay-75" style={{ background: "linear-gradient(90deg, rgba(224, 231, 255, 0.7) 0%, rgba(224, 231, 255, 0.2) 100%)" }}></div>
                </div>
              </TextTooltip>
            </div>
          </div>
          <div className="-z-10 flex-1 absolute w-full h-full animate-[fadeIn_1s_ease-out_0.5s_both]">
            <div className="absolute bottom-0 h-3/4 left-[-20%] w-[140%]">
              <div className="h-full grid grid-cols-3 grid-rows-3 gap-2 opacity-15">
                {Array.from({ length: 9 }).map((_v, i) => (
                  <div key={i} className="bg-white rounded-lg" />
                ))}
              </div>
            </div>
          </div>
        </div>
        <div className="animate-[scaleIn_0.5s_ease-out_0.95s_both]">
          <Circle className="w-8 h-8 inline-flex group-hover:w-auto group-hover:px-4 transition-all duration-500 ease-out group-hover:bg-black group-hover:border-transparent">
            <span className="max-w-0 overflow-hidden opacity-0 group-hover:max-w-xs group-hover:opacity-100 group-hover:mr-2 transition-all duration-500 ease-out text-white whitespace-nowrap text-sm font-medium">
              立即体验
            </span>
            <ArrowRight className="fill-mainTitle group-hover:fill-white transition-colors duration-500 ease-out" />
          </Circle>
        </div>
      </div>
    </CommonCard>
  )
}


function TextTooltip({ className = "", placement, style, classNames = { root: "" }, children }: React.PropsWithChildren<{ style?: React.CSSProperties; classNames?: { root: string }; placement: "l" | "r"; className?: string }>) {
  const p = placement === "r" ? "-right-1" : "-left-1"
  return (
    <div style={style} className={`flex items-center relative rounded-lg p-2  ${classNames.root} ${className}`}>
      {children}
      <div className={`
        absolute w-3 h-3 rounded-[2px] top-1/2 -translate-y-1/2 ${p} rotate-45 ${classNames.root}
        `}></div>
    </div>
  )
}

export default HomeAgentCard;
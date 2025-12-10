import HomeModelCardBg from "../../assets/home-model-card-bg.svg";
import DeepSeek from "../../assets/deepseek.svg";
import Qwen from "../../assets/qwen.svg";
import ChatGPT from "../../assets/chatgpt.svg";
import Empty from "../../assets/empty.svg";
import More from "../../assets/more.svg";
import Circle from "./circle";
import { ArrowRight } from "../icon";
import CommonCard from "./CommonCard";

function HomeModelCard() {
  return (
    <CommonCard to="/models">
      <div
        className="h-full"
        style={{
          backgroundImage: `url(${HomeModelCardBg})`, backgroundSize: "cover",
        }}
      >
        <div
          className="absolute w-full h-full z-[1] animate-[fadeIn_0.8s_ease-out_0.2s_both] group-hover:opacity-60 transition-opacity duration-500"
          style={{
            background: "linear-gradient(306deg, #0D53FF 1%, rgba(80, 98, 244, 0.7) 59%, rgba(99, 102, 241, 0.09) 98%)",
            opacity: .4,
            mixBlendMode: "multiply"
          }}
        />
        <div className="h-full relative z-[3] flex flex-col justify-between p-6">
          <div className="flex flex-col gap-4">
            <div className="font-medium animate-[fadeInLeft_0.6s_ease-out_0.3s_both]">模型市场</div>
            <div className="flex pl-3">
              {[
                Qwen, DeepSeek, ChatGPT, Empty, More,
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
      </div>
    </CommonCard>
  )
}

export default HomeModelCard
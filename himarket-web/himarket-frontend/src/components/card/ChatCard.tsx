import ChatThum from "../../assets/chat-thum.svg";
import ChatThumCard from "../../assets/chat-thum-card.svg";
import Circle from "./circle";
import { ArrowRight } from "../icon";
import CommonCard from "./CommonCard";

function HomeChatCard() {
  return (
    <CommonCard to="/chats">
      <div
        className="absolute w-full h-full z-[1] animate-[fadeIn_0.8s_ease-out_0.2s_both]"
        style={{
          background: "linear-gradient(324deg, #C6CFFF 0%, #E1E6FF 29%, #FFFFFF 100%)",
        }}
      />
      <div className="h-full relative z-[3] flex flex-col justify-between p-6">
        <div className="flex flex-col gap-4">
          <div className="font-medium animate-[fadeInLeft_0.6s_ease-out_0.3s_both]">HiChat</div>
          <div className="mt-[10%]">
            <img className="absolute w-[150%] max-w-[150%] animate-[fadeInScale_0.8s_ease-out_0.5s_both] group-hover:scale-105 transition-transform duration-500" src={ChatThum} />
            <img className="absolute z-[2] bottom-[12%] left-[20%] animate-[slideInUp_0.7s_ease-out_0.7s_both] group-hover:bottom-[10%] group-hover:left-[22%] transition-all duration-500" src={ChatThumCard} />
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
    </CommonCard>
  )
}

export default HomeChatCard;

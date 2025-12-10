import More from "../../assets/more.svg";
import Circle from "./circle";
import { ArrowRight, Type, Image, SquarePlay, AudioLines } from "../icon";
import CommonCard from "./CommonCard";

const code = [
  {
    "api_name": "Qwen-Text-Generation",
    "type": "Text Generation",
    "protocol": "HTTPS",
    "domain": "qwen-text.aliyuncs.com",
    "model_service": "qwen-max"
  },
  {
    "api_name": "DeepSeek-Coder-API",
    "type": "Text Generation",
    "protocol": "HTTPS",
    "domain": "deepseek-coder.api.deepseek.com",
    "model_service": "deepseek-coder-33b-instruct"
  },
  {
    "api_name": "Stable-Diffusion-Image-Gen",
    "type": "Image Generation",
    "protocol": "HTTPS",
    "domain": "sd-image-gen.ai-gateway.example.com",
    "model_service": "stabilityai/stable-diffusion-xl-base-1.0"
  },
  {
    "api_name": "Qwen-VL-Multimodal",
    "type": "Multimodal Understanding and Generation",
    "protocol": "HTTPS",
    "domain": "qwen-vl.aliyuncs.com",
    "model_service": "qwen-vl-plus"
  },
  {
    "api_name": "Open-Sora-Video-Gen",
    "type": "Video Generation",
    "protocol": "HTTPS",
    "domain": "video-gen.open-sora.ai",
    "model_service": "open-sora-1.0"
  }
]

function HomeAPICard() {
  return (
    <CommonCard to="/apis">
      <div
        className="absolute w-full h-full z-[1] animate-[fadeIn_0.8s_ease-out_0.2s_both]"
        style={{
          background: "linear-gradient(324deg, #C6D9FF 0%, #E1EBFF 21%, #FFFFFF 99%)",
        }}
      />
      <div className="absolute w-full h-full z-[2] left-[-10%] animate-[fadeIn_1s_ease-out_0.5s_both]">
        <pre className="text-white/60 text-xs px-2">
          {JSON.stringify(code, null, 2)}
        </pre>
      </div>
      <div className="h-full relative z-[3] flex flex-col justify-between p-6">
        <div className="h-full relative flex flex-col gap-4">
          <div className="font-medium animate-[fadeInLeft_0.6s_ease-out_0.3s_both]">API 市场</div>
          <div className="flex pl-3">
            {
              [
                <Type />,
                <Image />,
                <SquarePlay />,
                <AudioLines />,
              ].map((icon, i) => (
                <Circle
                  className="w-12 h-12 animate-[fadeInScale_0.5s_ease-out_both] group-hover:scale-110 group-hover:-translate-y-1 transition-all duration-300"
                  style={{
                    marginLeft: -12,
                    animationDelay: `${0.4 + i * 0.08}s`
                  }}
                  key={i}>
                  {icon}
                </Circle>
              ))
            }
            <img
              src={More}
              style={{
                marginLeft: -12,
                animationDelay: '0.72s'
              }}
              className="animate-[fadeInScale_0.5s_ease-out_both] group-hover:scale-110 group-hover:-translate-y-1 transition-all duration-300"
            />
          </div>
          <div className="flex-1 w-full relative mt-[10%]">
            <Terminal className="w-3/4 animate-[slideInUp_0.7s_ease-out_0.65s_both] group-hover:scale-105 transition-transform duration-500">
              <pre className="text-white/60 max-h-24 overflow-hidden text-[6px]">
                {JSON.stringify(code, null, 2)}
              </pre>
            </Terminal>
            <div
              className="h-24 w-3/4 rounded-md absolute border left-[20%] border-white z-[-1] top-[20%] animate-[fadeIn_0.8s_ease-out_0.8s_both] group-hover:left-[24%] group-hover:top-[24%] transition-all duration-500"
              style={{
                background: "linear-gradient(313deg, rgba(255, 255, 255, 0.75) 0%, rgba(255, 255, 255, 0) 85%)",
                backdropFilter: "blur(10px)"
              }}>

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

function Terminal(props: React.PropsWithChildren<{ className?: string }>) {
  const { className, children } = props;
  return (
    <div className={`rounded-md overflow-hidden ${className}`}>
      <div className="px-3 bg-white h-3 w-full flex items-center gap-1">
        <div className="w-[6px] h-[6px] rounded-full bg-[#EF4444]"></div>
        <div className="w-[6px] h-[6px] rounded-full bg-[#EAB308]"></div>
        <div className="w-[6px] h-[6px] rounded-full bg-[#22C55E]"></div>
      </div>
      <div className="min-h-20" style={{ background: "linear-gradient(138deg, rgba(82, 82, 82, 0.8) 0%, rgba(31, 31, 31, 0.95) 79%)" }}>
        {children}
      </div>
    </div>
  )
}

export default HomeAPICard;
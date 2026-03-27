import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { StarFilled } from "@ant-design/icons";
import APIs from "../../lib/apis";
import type { IProductDetail } from "../../lib/apis";

interface RelatedSkillsProps {
  currentProductId: string;
}

function RelatedSkills({ currentProductId }: RelatedSkillsProps) {
  const navigate = useNavigate();
  const [skills, setSkills] = useState<IProductDetail[]>([]);

  useEffect(() => {
    const fetchRelated = async () => {
      try {
        const resp = await APIs.getProducts({
          type: "AGENT_SKILL",
          size: 6,
        });
        if (resp.code === "SUCCESS" && resp.data) {
          const filtered = resp.data.content
            .filter((s) => s.productId !== currentProductId)
            .slice(0, 3);
          setSkills(filtered);
        }
      } catch {
        // 静默失败
      }
    };
    fetchRelated();
  }, [currentProductId]);

  if (skills.length === 0) return null;

  return (
    <div className="bg-white/60 backdrop-blur-sm rounded-2xl border border-white/40 p-5">
      <h3 className="text-base font-semibold text-gray-900 mb-4">相关技能</h3>
      <div className="space-y-2">
        {skills.map((skill) => (
          <button
            key={skill.productId}
            onClick={() => navigate(`/skills/${skill.productId}`)}
            className="
              flex items-center gap-3 w-full px-3 py-2.5
              rounded-lg border border-gray-100
              bg-white hover:bg-purple-50 hover:border-purple-200
              transition-colors duration-200 text-left
            "
          >
            <div className="w-8 h-8 rounded-lg flex-shrink-0 flex items-center justify-center bg-gradient-to-br from-purple-50 to-indigo-50 border border-purple-200">
              {skill.icon?.value ? (
                <img src={skill.icon.value} alt="" className="w-5 h-5 rounded" />
              ) : (
                <span className="text-base">⚡</span>
              )}
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-sm font-medium text-gray-800 truncate">{skill.name}</div>
              <div className="text-xs text-gray-400 truncate">
                {skill.skillConfig?.skillTags?.[0] || "技能"}
              </div>
            </div>
            {skill.skillConfig?.downloadCount != null && (
              <div className="flex items-center gap-1 text-xs text-amber-500 flex-shrink-0">
                <StarFilled className="text-[10px]" />
                <span>{skill.skillConfig.downloadCount.toLocaleString()}</span>
              </div>
            )}
          </button>
        ))}
      </div>
    </div>
  );
}

export default RelatedSkills;

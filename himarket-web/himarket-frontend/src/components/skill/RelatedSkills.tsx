import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { StarFilled, ThunderboltOutlined } from "@ant-design/icons";
import APIs from "../../lib/apis";
import type { IProductDetail } from "../../lib/apis";

interface RelatedSkillsProps {
  currentProductId: string;
  currentSkillTags?: string[];
}

function RelatedSkills({ currentProductId, currentSkillTags }: RelatedSkillsProps) {
  const navigate = useNavigate();
  const [skills, setSkills] = useState<IProductDetail[]>([]);

  useEffect(() => {
    const fetchRelated = async () => {
      try {
        const resp = await APIs.getProducts({
          type: "AGENT_SKILL",
          size: 100,  // 获取更多用于筛选
        });
        if (resp.code === "SUCCESS" && resp.data) {
          const allSkills = resp.data.content.filter(
            (s) => s.productId !== currentProductId
          );

          // 如果有标签，基于标签匹配筛选
          if (currentSkillTags && currentSkillTags.length > 0) {
            const scored = allSkills.map((skill) => {
              const skillTags = skill.skillConfig?.skillTags || [];
              // 计算标签匹配分数
              const matchCount = skillTags.filter((tag) =>
                currentSkillTags.includes(tag)
              ).length;
              return { skill, matchCount };
            });

            // 按匹配分数排序，取匹配最高的，相同分数则随机
            scored.sort((a, b) => {
              if (b.matchCount !== a.matchCount) {
                return b.matchCount - a.matchCount;
              }
              return Math.random() - 0.5;
            });

            // 如果有匹配，返回匹配的；否则返回随机的
            const matchedSkills = scored.filter((s) => s.matchCount > 0);
            const result = matchedSkills.length > 0
              ? matchedSkills.slice(0, 3).map((s) => s.skill)
              : allSkills.sort(() => Math.random() - 0.5).slice(0, 3);

            setSkills(result);
          } else {
            // 没有标签时，随机显示
            setSkills(allSkills.sort(() => Math.random() - 0.5).slice(0, 3));
          }
        }
      } catch {
        // 静默失败
      }
    };
    fetchRelated();
  }, [currentProductId, currentSkillTags]);

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
            <div className="w-8 h-8 rounded-lg flex-shrink-0 flex items-center justify-center bg-gray-50 border border-gray-200">
              {skill.icon?.value ? (
                <img src={skill.icon.value} alt="" className="w-5 h-5 rounded" />
              ) : (
                <ThunderboltOutlined className="text-base" />
              )}
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-sm font-medium text-gray-800 truncate">{skill.name}</div>
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

import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Layout } from "../components/Layout";
import { Alert, Spin, Tag } from "antd";
import { ArrowLeftOutlined } from "@ant-design/icons";
import type { IProductDetail } from "../lib/apis";
import type { ISkillConfig } from "../lib/apis/typing";
import APIs from "../lib/apis";
import { getSkillMdBody } from "../lib/skillMdUtils";
import MarkdownRender from "../components/MarkdownRender";
import InstallCommand from "../components/skill/InstallCommand";
import SkillMdViewer from "../components/skill/SkillMdViewer";

function SkillDetail() {
  const { skillProductId } = useParams<{ skillProductId: string }>();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [data, setData] = useState<IProductDetail>();
  const [skillConfig, setSkillConfig] = useState<ISkillConfig>();

  useEffect(() => {
    const fetchDetail = async () => {
      if (!skillProductId) return;
      setLoading(true);
      setError("");
      try {
        const response = await APIs.getProduct({ id: skillProductId });
        if (response.code === "SUCCESS" && response.data) {
          setData(response.data);
          if (response.data.skillConfig) {
            setSkillConfig(response.data.skillConfig);
          }
        } else {
          setError(response.message || "数据加载失败");
        }
      } catch (err) {
        console.error("API请求失败:", err);
        setError("加载失败，请稍后重试");
      } finally {
        setLoading(false);
      }
    };
    fetchDetail();
  }, [skillProductId]);

  if (loading) {
    return (
      <Layout>
        <div className="flex justify-center items-center h-screen">
          <Spin size="large" tip="加载中..." />
        </div>
      </Layout>
    );
  }

  if (error || !data) {
    return (
      <Layout>
        <div className="p-8">
          <Alert message="错误" description={error || "技能不存在"} type="error" showIcon />
        </div>
      </Layout>
    );
  }

  const { name, description } = data;
  const skillTags = skillConfig?.skillTags || [];

  return (
    <Layout>
      {/* 头部 */}
      <div className="mb-8">
        <button
          onClick={() => navigate(-1)}
          className="
            flex items-center gap-2 mb-4 px-4 py-2 rounded-xl
            text-gray-600 hover:text-colorPrimary
            hover:bg-colorPrimaryBgHover
            transition-all duration-200
          "
        >
          <ArrowLeftOutlined />
          <span>返回</span>
        </button>

        {/* 技能名称、描述、标签 */}
        <div className="flex items-center gap-4 mb-3">
          <div className="w-16 h-16 rounded-xl flex-shrink-0 flex items-center justify-center bg-gradient-to-br from-purple-50 to-indigo-50 border border-purple-200">
            <span className="text-3xl">⚡</span>
          </div>
          <div className="flex-1 min-w-0">
            <h1 className="text-xl font-semibold text-gray-900 mb-1">{name}</h1>
            {data.updatedAt && (
              <div className="text-sm text-gray-400">
                {new Date(data.updatedAt).toLocaleDateString("zh-CN", {
                  year: "numeric",
                  month: "2-digit",
                  day: "2-digit",
                }).replace(/\//g, ".")} updated
              </div>
            )}
          </div>
        </div>

        <p className="text-gray-600 text-sm leading-relaxed mb-3">{description}</p>

        {skillTags.length > 0 && (
          <div className="flex flex-wrap gap-2">
            {skillTags.map((tag) => (
              <Tag key={tag} color="purple">{tag}</Tag>
            ))}
          </div>
        )}
      </div>

      {/* 主要内容区域 - 左右布局 */}
      <div className="flex flex-col lg:flex-row gap-6">
        {/* 左侧：SKILL.md 渲染内容 */}
        <div className="w-full lg:w-[65%] order-2 lg:order-1">
          <div className="bg-white/60 backdrop-blur-sm rounded-2xl border border-white/40 p-6">
            <h3 className="text-base font-semibold text-gray-900 mb-4">使用说明</h3>
            {data.document ? (
              <div className="min-h-[400px] prose prose-lg">
                <MarkdownRender content={getSkillMdBody(data.document)} />
              </div>
            ) : (
              <div className="text-gray-500 text-center py-16">
                暂无使用说明
              </div>
            )}
          </div>
        </div>

        {/* 右侧：安装命令 & SKILL.md 源码查看（占位） */}
        <div className="w-full lg:w-[35%] order-1 lg:order-2 space-y-6">
          {/* InstallCommand 组件 */}
          <InstallCommand
            productId={skillProductId!}
            skillName={name}
            document={data.document || ""}
          />

          {/* SkillMdViewer 组件 */}
          <SkillMdViewer document={data.document || ""} />
        </div>
      </div>
    </Layout>
  );
}

export default SkillDetail;

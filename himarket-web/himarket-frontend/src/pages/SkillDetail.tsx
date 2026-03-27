import { useEffect, useState, useCallback, useRef } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Layout } from "../components/Layout";
import { Alert, Spin, Tag, Button } from "antd";
import { ArrowLeftOutlined, DownloadOutlined } from "@ant-design/icons";
import Editor from "@monaco-editor/react";
import type { IProductDetail } from "../lib/apis";
import type { ISkillConfig } from "../lib/apis/typing";
import type { SkillFileTreeNode, SkillFileContent } from "../lib/apis/cliProvider";
import APIs from "../lib/apis";
import { getSkillFiles, getSkillFileContent, getSkillPackageUrl } from "../lib/apis/cliProvider";
import { parseSkillMd } from "../lib/skillMdUtils";
import MarkdownRender from "../components/MarkdownRender";
import SkillFileTree from "../components/skill/SkillFileTree";
import RelatedSkills from "../components/skill/RelatedSkills";

function inferLanguage(path: string): string {
  const ext = path.split(".").pop()?.toLowerCase() ?? "";
  const map: Record<string, string> = {
    ts: "typescript", tsx: "typescript", js: "javascript", jsx: "javascript",
    py: "python", java: "java", go: "go", rs: "rust", cpp: "cpp", c: "c",
    sh: "shell", bash: "shell", yaml: "yaml", yml: "yaml", json: "json",
    toml: "ini", xml: "xml", html: "html", css: "css", md: "markdown",
  };
  return map[ext] ?? "plaintext";
}

function SkillDetail() {
  const { skillProductId } = useParams<{ skillProductId: string }>();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [data, setData] = useState<IProductDetail>();
  const [skillConfig, setSkillConfig] = useState<ISkillConfig>();

  const [fileTree, setFileTree] = useState<SkillFileTreeNode[]>([]);
  const [selectedFilePath, setSelectedFilePath] = useState<string | undefined>();
  const [fileContent, setFileContent] = useState<SkillFileContent | null>(null);
  const [fileLoading, setFileLoading] = useState(false);
  const [treeWidth, setTreeWidth] = useState(224);
  const isDragging = useRef(false);

  const handleDragStart = (e: React.MouseEvent) => {
    e.preventDefault();
    isDragging.current = true;
    const startX = e.clientX;
    const startWidth = treeWidth;
    const onMove = (ev: MouseEvent) => {
      if (!isDragging.current) return;
      setTreeWidth(Math.min(520, Math.max(160, startWidth + ev.clientX - startX)));
    };
    const onUp = () => {
      isDragging.current = false;
      window.removeEventListener("mousemove", onMove);
      window.removeEventListener("mouseup", onUp);
    };
    window.addEventListener("mousemove", onMove);
    window.addEventListener("mouseup", onUp);
  };

  useEffect(() => {
    const fetchDetail = async () => {
      if (!skillProductId) return;
      setLoading(true);
      setError("");
      try {
        const [productRes, filesRes] = await Promise.all([
          APIs.getProduct({ id: skillProductId }),
          getSkillFiles(skillProductId).catch(() => null),
        ]);
        if (productRes.code === "SUCCESS" && productRes.data) {
          setData(productRes.data);
          if (productRes.data.skillConfig) {
            setSkillConfig(productRes.data.skillConfig);
          }
        } else {
          setError(productRes.message || "数据加载失败");
        }
        if (filesRes?.code === "SUCCESS" && Array.isArray(filesRes.data) && filesRes.data.length > 0) {
          setFileTree(filesRes.data);
          // 默认选中并加载 SKILL.md
          if (skillProductId) {
            setSelectedFilePath("SKILL.md");
            setFileLoading(true);
            getSkillFileContent(skillProductId, "SKILL.md")
              .then((r) => { if (r.code === "SUCCESS" && r.data) setFileContent(r.data); })
              .catch(() => {})
              .finally(() => setFileLoading(false));
          }
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

  const handleSelectFile = useCallback(async (path: string) => {
    if (!skillProductId) return;
    setSelectedFilePath(path);
    setFileLoading(true);
    try {
      const res = await getSkillFileContent(skillProductId, path);
      if (res.code === "SUCCESS" && res.data) {
        setFileContent(res.data);
      }
    } catch {
      setFileContent(null);
    } finally {
      setFileLoading(false);
    }
  }, [skillProductId]);

  const handleDownload = useCallback(async () => {
    if (!skillProductId) return;
    const url = fileTree.length > 0
      ? getSkillPackageUrl(skillProductId)
      : `/api/v1/skills/${skillProductId}/download`;
    try {
      const headers: Record<string, string> = {};
      const token = localStorage.getItem("access_token");
      if (token) {
        headers["Authorization"] = `Bearer ${token}`;
      }
      const res = await fetch(url, { headers });
      if (!res.ok) throw new Error("下载失败");
      const blob = await res.blob();
      const blobUrl = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = blobUrl;
      // 从 Content-Disposition 取文件名，fallback 用 skill 名
      const disposition = res.headers.get("Content-Disposition") ?? "";
      const match = disposition.match(/filename\*?=(?:UTF-8'')?["']?([^"';\n]+)/i);
      a.download = match ? decodeURIComponent(match[1]) : (data?.name ?? "skill") + (fileTree.length > 0 ? ".zip" : ".md");
      a.click();
      URL.revokeObjectURL(blobUrl);
    } catch {
      // 静默失败，浏览器会有提示
    }
  }, [skillProductId, fileTree, data]);

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
  const hasFiles = fileTree.length > 0;

  const countFiles = (nodes: SkillFileTreeNode[]): number =>
    nodes.reduce((acc, n) => acc + (n.type === "directory" ? countFiles(n.children ?? []) : 1), 0);

  const renderFilePreview = () => {
    if (!selectedFilePath) {
      return (
        <div className="text-gray-400 text-center py-16 text-sm">点击左侧文件查看内容</div>
      );
    }
    if (fileLoading) {
      return <div className="flex justify-center py-16"><Spin /></div>;
    }
    if (!fileContent) {
      return <div className="text-gray-400 text-center py-16 text-sm">加载失败</div>;
    }
    if (fileContent.encoding === "base64") {
      return (
        <div className="text-gray-400 text-center py-16 text-sm">二进制文件，不支持预览</div>
      );
    }
    if (selectedFilePath.endsWith(".md")) {
      const { frontmatter, body } = parseSkillMd(fileContent.content);
      const fmEntries = Object.entries(frontmatter);
      return (
        <div className="p-8 overflow-auto h-full">
          {fmEntries.length > 0 && (
            <table className="mb-6 w-full text-[13px] border-collapse">
              <thead>
                <tr className="bg-[#f6f8fa]">
                  {fmEntries.map(([k]) => (
                    <th key={k} className="border border-[#d0d7de] px-3 py-1.5 text-left font-semibold text-[#1f2328]">{k}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                <tr>
                  {fmEntries.map(([k, v]) => (
                    <td key={k} className="border border-[#d0d7de] px-3 py-1.5 text-[#1f2328] align-top">{v}</td>
                  ))}
                </tr>
              </tbody>
            </table>
          )}
          <div className="markdown-body">
            <MarkdownRender content={body} />
          </div>
        </div>
      );
    }
    return (
      <div style={{ minHeight: 400 }}>
        <Editor
          height={Math.max(400, (fileContent.content.split("\n").length + 2) * 19)}
          path={selectedFilePath}
          language={inferLanguage(selectedFilePath)}
          value={fileContent.content}
          options={{ readOnly: true, minimap: { enabled: false }, scrollBeyondLastLine: false }}
          theme="vs"
        />
      </div>
    );
  };

  return (
    <Layout>
      <div className="mb-8">
        <button
          onClick={() => navigate(-1)}
          className="flex items-center gap-2 mb-4 px-4 py-2 rounded-xl text-gray-600 hover:text-colorPrimary hover:bg-colorPrimaryBgHover transition-all duration-200"
        >
          <ArrowLeftOutlined />
          <span>返回</span>
        </button>

        <div className="flex items-center gap-4 mb-3">
          <div className="w-16 h-16 rounded-xl flex-shrink-0 flex items-center justify-center bg-gradient-to-br from-purple-50 to-indigo-50 border border-purple-200">
            <span className="text-3xl">⚡</span>
          </div>
          <div className="flex-1 min-w-0">
            <h1 className="text-xl font-semibold text-gray-900 mb-1">{name}</h1>
            {data.updatedAt && (
              <div className="text-sm text-gray-400">
                {new Date(data.updatedAt).toLocaleDateString("zh-CN", {
                  year: "numeric", month: "2-digit", day: "2-digit",
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

      <div className="flex flex-col lg:flex-row gap-6">
        {/* 左侧：GitHub 风格文件树 + 预览 */}
        <div className="w-full lg:w-[65%] order-2 lg:order-1">
          {hasFiles ? (
            <div className="bg-white rounded-2xl border border-gray-200 overflow-hidden flex shadow-sm" style={{ height: 800 }}>
              {/* 文件树列 */}
              <div className="flex-shrink-0 border-r border-gray-200 bg-gray-50 flex flex-col overflow-hidden" style={{ width: treeWidth }}>
                <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200 flex-shrink-0">
                  <span className="text-xs font-medium text-gray-500 uppercase tracking-wide">Files</span>
                  <span className="text-xs text-gray-400">{countFiles(fileTree)}</span>
                </div>
                <div className="flex-1 overflow-y-auto overflow-x-hidden py-2 px-1">
                  <SkillFileTree
                    nodes={fileTree}
                    selectedPath={selectedFilePath}
                    onSelect={handleSelectFile}
                  />
                </div>
              </div>
              {/* 拖拽分隔条 */}
              <div
                onMouseDown={handleDragStart}
                className="w-1 flex-shrink-0 cursor-col-resize hover:bg-blue-200 transition-colors bg-transparent"
              />
              {/* 文件预览列 */}
              <div className="flex-1 min-w-0 flex flex-col overflow-hidden bg-white">
                {selectedFilePath && (
                  <div className="flex items-center justify-between px-5 py-3 border-b border-gray-200 flex-shrink-0">
                    <span className="text-sm font-medium text-gray-700 truncate">{selectedFilePath}</span>
                    <span className="text-xs text-gray-400 flex-shrink-0 ml-2">readonly</span>
                  </div>
                )}
                <div className="flex-1 overflow-auto">
                  {renderFilePreview()}
                </div>
              </div>
            </div>
          ) : (
            <div className="bg-white rounded-2xl border border-gray-200 p-6 shadow-sm">
              <div className="text-gray-500 text-center py-16">暂无文件</div>
            </div>
          )}
        </div>

        {/* 右侧：下载 + 相关推荐 */}
        <div className="w-full lg:w-[35%] order-1 lg:order-2 space-y-6">
          <div className="bg-white/60 backdrop-blur-sm rounded-2xl border border-white/40 p-4">
            <Button
              type="primary"
              icon={<DownloadOutlined />}
              onClick={handleDownload}
              block
            >
              下载 Skill 包
            </Button>
          </div>

          <RelatedSkills currentProductId={skillProductId!} />
        </div>
      </div>
    </Layout>
  );
}

export default SkillDetail;

import { useState, useEffect, useCallback, useRef } from "react";
import { SearchOutlined, PlusOutlined } from "@ant-design/icons";
import { Trans } from 'react-i18next';
import { Input, Select, message, Pagination, Button } from "antd";
import { useNavigate } from "react-router-dom";
import { useTranslation } from 'react-i18next';
import { Layout } from "../components/Layout";
import { CategoryMenu } from "../components/square/CategoryMenu";
import { ModelCard } from "../components/square/ModelCard";
import { SkillCard } from "../components/square/SkillCard";
import { EmptyState } from "../components/EmptyState";
import { LoginPrompt } from "../components/LoginPrompt";
import { CreateSkillModal } from "../components/skill/CreateSkillModal";
import { useAuth } from "../hooks/useAuth";
import { useDebounce } from "../hooks/useDebounce";
import { WorkerCard } from "../components/square/WorkerCard";
import APIs, { type ICategory } from "../lib/apis";
import { listDeveloperSkills, type DeveloperSkillResult, type SkillListType } from "../lib/apis/developerSkill";
import { getIconString } from "../lib/iconUtils";
import type { IProductDetail } from "../lib/apis/product";
import dayjs from "dayjs";
import BackToTopButton from "../components/scroll-to-top";
import { CardGridSkeleton } from "../components/loading";

function Square(props: { activeType: string }) {
  const { activeType } = props;
  const navigate = useNavigate();
  const { t } = useTranslation('square');
  const { isLoggedIn } = useAuth();
  const [loginPromptOpen, setLoginPromptOpen] = useState(false);
  const [createSkillOpen, setCreateSkillOpen] = useState(false);

  // Skill 标签页：all / personal / official（仅 AGENT_SKILL 时生效）
  const [skillTab, setSkillTab] = useState<SkillListType>("all");
  const [developerSkills, setDeveloperSkills] = useState<DeveloperSkillResult[]>([]);
  const isSkillPersonalView = activeType === "AGENT_SKILL" && skillTab !== "all";

  const [activeCategory, setActiveCategory] = useState("all");
  const [searchQuery, setSearchQuery] = useState("");
  const [products, setProducts] = useState<IProductDetail[]>([]);
  const [categories, setCategories] = useState<Array<{ id: string; name: string; count: number }>>([]);
  const [loading, setLoading] = useState(true);
  const [categoriesLoading, setCategoriesLoading] = useState(true);
  const [sortBy, setSortBy] = useState<string>("DOWNLOAD_COUNT");

  const showSortControl = activeType === 'AGENT_SKILL' || activeType === 'WORKER';

  // 分页相关状态
  const [currentPage, setCurrentPage] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const PAGE_SIZE = 12;

  // 滚动容器 ref，供 BackToTopButton 使用
  const scrollContainerRef = useRef<HTMLDivElement>(null);

  // activeType 切换时立即重置全部状态，避免旧数据闪烁
  useEffect(() => {
    setProducts([]);
    setCategories([]);
    setLoading(true);
    setCategoriesLoading(true);
    setSearchQuery("");
    setCurrentPage(1);
    setSortBy("DOWNLOAD_COUNT");
    setActiveCategory("all");
    setTotalElements(0);
    setSkillTab("all");
    setDeveloperSkills([]);

    const fetchCategories = async () => {
      try {
        const productType = activeType;
        const response = await APIs.getCategoriesByProductType({ productType });

        if (response.code === "SUCCESS" && response.data?.content) {
          const categoryList = response.data.content.map((cat: ICategory) => ({
            id: cat.categoryId,
            name: cat.name,
            count: 0,
          }));

          if (categoryList.length > 0) {
            setCategories([
              { id: "all", name: t('allCategory'), count: 0 },
              ...categoryList
            ]);
          } else {
            setCategories([]);
          }
        }
      } catch (error) {
        console.error("Failed to fetch categories:", error);
        message.error(t('fetchCategoriesFailed'));
      } finally {
        setCategoriesLoading(false);
      }
    };

    fetchCategories();
  }, [activeType]);

  // 获取产品列表
  const fetchProducts = useCallback(async (searchText?: string, page?: number) => {
    setLoading(true);
    try {
      const productType = activeType;
      const categoryIds = activeCategory === "all" ? undefined : [activeCategory];
      const name = (searchText ?? "").trim() || undefined;
      // page 从 0 开始，currentPage 从 1 开始
      const pageIndex = (page ?? currentPage);

      const response = await APIs.getProducts({
        type: productType,
        categoryIds,
        name,
        page: pageIndex,
        size: PAGE_SIZE,
        sortBy: showSortControl ? sortBy : undefined,
      });
      if (response.code === "SUCCESS" && response.data?.content) {
        setProducts(response.data.content);
        setTotalElements(response.data.totalElements);
      }
    } catch (error) {
      console.error("Failed to fetch products:", error);
      message.error(t('fetchProductsFailed'));
    } finally {
      setLoading(false);
    }
  }, [activeType, activeCategory, currentPage, sortBy, showSortControl]);

  // 获取开发者个人/官方 Skill（personal / official 标签页）
  const fetchDeveloperSkills = useCallback(async (tab: SkillListType) => {
    if (activeType !== "AGENT_SKILL" || tab === "all") return;
    setLoading(true);
    try {
      const resp = await listDeveloperSkills({ type: tab });
      if (resp.code === "SUCCESS" && resp.data) {
        setDeveloperSkills(resp.data);
      }
    } catch (error) {
      console.error("Failed to fetch developer skills:", error);
      message.error(t('fetchProductsFailed'));
    } finally {
      setLoading(false);
    }
  }, [activeType]);

  useEffect(() => {
    fetchProducts(searchQuery);
  }, [activeType, activeCategory, currentPage, sortBy]);

  useEffect(() => {
    if (activeType === "AGENT_SKILL" && skillTab !== "all") {
      fetchDeveloperSkills(skillTab);
    }
  }, [skillTab, activeType]);

  // Debounce 自动搜索：输入停顿 300ms 后自动触发搜索并重置分页
  useDebounce(searchQuery, 300, (debouncedValue) => {
    setCurrentPage(1);
    fetchProducts(debouncedValue, 1);
  });

  const handlePageChange = (page: number) => {
    setCurrentPage(page);
  };

  const handleSearchChange = (value: string) => {
    setSearchQuery(value);
  };

  // 即时搜索：搜索按钮和回车键
  const handleSearch = () => {
    setCurrentPage(1);
    fetchProducts(searchQuery, 1);
  };

  const filteredModels = products;

  // 根据产品类型获取引导语
  const getSlogan = (): { title: string; subtitleKey: string } | null => {
    switch (activeType) {
      case 'AGENT_SKILL':
        return { title: t('skillMarketTitle'), subtitleKey: 'skillMarketSubtitle' };
      case 'WORKER':
        return { title: t('workerMarketTitle'), subtitleKey: 'workerMarketSubtitle' };
      default:
        return null;
    }
  };

  const handleTryNow = (product: IProductDetail) => {
    if (!isLoggedIn) {
      setLoginPromptOpen(true);
      return;
    }
    navigate("/chat", { state: { selectedProduct: product } });
  };

  const handleViewDetail = (product: IProductDetail) => {
    switch (product.type) {
      case "MODEL_API":
        navigate(`/models/${product.productId}`);
        break;
      case "MCP_SERVER":
        navigate(`/mcp/${product.productId}`);
        break;
      case "AGENT_API":
        navigate(`/agents/${product.productId}`);
        break;
      case "REST_API":
        navigate(`/apis/${product.productId}`);
        break;
      case "AGENT_SKILL":
        navigate(`/skills/${product.productId}`);
        break;
      case "WORKER":
        navigate(`/workers/${product.productId}`);
        break;
      default:
        console.log(t('unknownProductType'), product.type);
    }
  };

  const slogan = getSlogan();

  return (
    <Layout>
      <div className="flex flex-col h-[calc(100vh-96px)] overflow-auto scrollbar-hide" ref={scrollContainerRef}>
        {/* 引导语 */}
        {slogan && (
            <div className="text-center py-6">
              <h1 className="text-4xl font-bold mb-3">{slogan.title}</h1>
              <p className="text-gray-500 text-base flex items-baseline justify-center gap-0">
                <Trans
                  t={t}
                  i18nKey={slogan.subtitleKey}
                  values={{ count: totalElements }}
                  components={{
                    1: <span className="text-4xl font-extrabold text-blue-500 mx-1 tabular-nums leading-none relative -top-[2px]" />
                  }}
                />
              </p>
            </div>
        )}

        {/* 搜索区域 */}
        <div className="flex-shrink-0">
          <div className="flex flex-col gap-3 px-6 py-4">

            {/* 工具栏：类型 + 排序 + 搜索 + 创建按钮，整合为一行 */}
            <div className="flex items-center gap-3 flex-wrap">
              {/* Skill 类型下拉（仅 AGENT_SKILL 显示） */}
              {activeType === "AGENT_SKILL" && (
                <Select
                  value={skillTab}
                  onChange={(val) => {
                    if ((val === "personal" || val === "official") && !isLoggedIn) {
                      setLoginPromptOpen(true);
                      return;
                    }
                    setSkillTab(val);
                  }}
                  size="middle"
                  style={{ width: 140 }}
                  options={[
                    { value: "all", label: t("skillTabAll", "全部 Skill") },
                    { value: "personal", label: t("skillTabPersonal", "个人 Skill") },
                    { value: "official", label: t("skillTabOfficial", "平台 Skill") },
                  ]}
                />
              )}

              {/* 排序下拉（SKILL/WORKER 时显示） */}
              {showSortControl && (
                <Select
                  value={sortBy}
                  onChange={(val) => { setSortBy(val); setCurrentPage(1); }}
                  size="middle"
                  style={{ width: 130 }}
                  options={[
                    { value: "DOWNLOAD_COUNT", label: t("sortMostDownloads", "最多下载") },
                    { value: "UPDATED_AT", label: t("sortRecentlyUpdated", "最近更新") },
                  ]}
                />
              )}

              {/* 搜索框 */}
              <Input
                placeholder={t("searchPlaceholder")}
                value={searchQuery}
                onChange={(e) => handleSearchChange(e.target.value)}
                onPressEnter={handleSearch}
                allowClear
                size="middle"
                prefix={<SearchOutlined className="text-gray-400" />}
                style={{ maxWidth: 320 }}
                className="flex-1 min-w-[180px]"
              />

              {/* 创建 Skill 按钮 */}
              {activeType === "AGENT_SKILL" && isLoggedIn && (
                <Button
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={() => setCreateSkillOpen(true)}
                  className="ml-auto"
                >
                  {t("createSkill", "创建 Skill")}
                </Button>
              )}
            </div>

            {/* 分类菜单（仅 all 标签页显示） */}
            {!isSkillPersonalView && (
              <div className="flex-1 min-w-0">
                <CategoryMenu
                  categories={categories}
                  activeCategory={activeCategory}
                  onSelectCategory={setActiveCategory}
                  loading={categoriesLoading}
                />
              </div>
            )}
          </div>
        </div>

        {/* 内容区域：Grid 卡片展示 */}
        <div className="flex-1 px-4 pt-4 pb-4 flex-shrink-0">
          <div className="pb-4">
            {loading ? (
              <CardGridSkeleton count={8} />
            ) : (
              <>
                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6 max-w-[1600px] mx-auto animate-in fade-in duration-300">
                  {/* personal / official 标签页：渲染 developerSkills */}
                  {isSkillPersonalView ? (
                    developerSkills.length > 0 ? developerSkills.map((skill) => (
                      <SkillCard
                        key={skill.productId}
                        name={skill.name}
                        description={skill.description ?? ""}
                        releaseDate={skill.createdAt ? dayjs(skill.createdAt).format("YYYY-MM-DD HH:mm:ss") : ""}
                        skillTags={skill.tags}
                        onClick={() => navigate(`/skills/${skill.productId}`)}
                        sourceTag={skill.isOfficial ? "official" : "personal"}
                        developerUsername={skill.developerUsername || (skill.isOfficial ? "admin" : undefined)}
                        isOwner={skill.isOwner}
                        onEdit={skill.isOwner ? () => navigate(`/skills/${skill.productId}`) : undefined}
                        onDelete={skill.isOwner ? () => fetchDeveloperSkills(skillTab) : undefined}
                        productId={skill.productId}
                      />
                    )) : (
                      <EmptyState productType={activeType} />
                    )
                  ) : (
                    /* all 标签页：渲染公开产品列表 */
                    filteredModels.map((product) => (
                      product.type === 'AGENT_SKILL' ? (
                        <SkillCard
                          key={product.productId}
                          name={product.name}
                          description={product.description}
                          releaseDate={dayjs(product.createAt).format("YYYY-MM-DD HH:mm:ss")}
                          skillTags={product.skillConfig?.skillTags}
                          downloadCount={product.skillConfig?.downloadCount}
                          sourceTag={product.developerId ? "personal" : "official"}
                          developerUsername={product.developerUsername || (product.developerId ? undefined : "admin")}
                          onClick={() => handleViewDetail(product)}
                        />
                      ) : product.type === 'WORKER' ? (
                        <WorkerCard
                          key={product.productId}
                          name={product.name}
                          description={product.description}
                          releaseDate={dayjs(product.createAt).format("YYYY-MM-DD HH:mm:ss")}
                          workerTags={product.workerConfig?.tags}
                          downloadCount={product.workerConfig?.downloadCount}
                          onClick={() => handleViewDetail(product)}
                        />
                      ) : (
                        <ModelCard
                          key={product.productId}
                          icon={getIconString(product.icon, product.name)}
                          name={product.name}
                          description={product.description}
                          releaseDate={dayjs(product.createAt).format("YYYY-MM-DD HH:mm:ss")}
                          onClick={() => handleViewDetail(product)}
                          onTryNow={activeType === "MODEL_API" ? () => handleTryNow(product) : undefined}
                        />
                      )
                    ))
                  )}
                  {!loading && !isSkillPersonalView && filteredModels.length === 0 && (
                    <EmptyState productType={activeType} />
                  )}
                </div>

                {/* 分页组件（仅 all 标签页） */}
                {!loading && !isSkillPersonalView && totalElements > PAGE_SIZE && (
                  <div className="flex justify-center mt-8 mb-4">
                    <Pagination
                      current={currentPage}
                      pageSize={PAGE_SIZE}
                      total={totalElements}
                      onChange={handlePageChange}
                      showSizeChanger={false}
                      showQuickJumper
                    />
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      </div>
      <BackToTopButton container={scrollContainerRef.current!} />
      <LoginPrompt
        open={loginPromptOpen}
        onClose={() => setLoginPromptOpen(false)}
        contextMessage={t('loginPromptContext')}
      />
      <CreateSkillModal
        open={createSkillOpen}
        onClose={() => setCreateSkillOpen(false)}
        onCreated={() => {
          setSkillTab("personal");
          fetchDeveloperSkills("personal");
        }}
      />
    </Layout>
  );
}

export default Square;

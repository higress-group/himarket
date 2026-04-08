interface Category {
  id: string;
  name: string;
  count?: number;
}

interface CategoryMenuProps {
  categories: Category[];
  activeCategory: string;
  onSelectCategory: (categoryId: string) => void;
  loading?: boolean;
}

export function CategoryMenu({
  activeCategory,
  categories,
  loading = false,
  onSelectCategory,
}: CategoryMenuProps) {
  // 没有分类数据且不在加载中时不渲染任何内容
  if (!loading && categories.length === 0) {
    return null;
  }

  return (
    <div className="overflow-hidden">
      {loading ? (
        <div className="flex items-center justify-center gap-2 flex-wrap">
          {/* 骨架药丸，与真实分类按钮布局一致，避免高度跳动 */}
          {Array.from({ length: 5 }).map((_, i) => (
            <div
              className="h-8 rounded-full bg-gray-200 animate-pulse"
              key={i}
              style={{ width: `${60 + i * 12}px` }}
            />
          ))}
        </div>
      ) : (
        <div className="flex items-center justify-center gap-2 flex-wrap animate-in fade-in duration-200">
          {categories.map((category) => {
            const isActive = category.id === activeCategory;
            return (
              <button
                className={`
                  px-4 py-1.5 rounded-full cursor-pointer whitespace-nowrap
                  transition-all duration-300 ease-in-out text-sm font-medium
                  ${
                    isActive
                      ? 'bg-gray-900 text-white shadow-md'
                      : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                  }
                `}
                key={category.id}
                onClick={() => onSelectCategory(category.id)}
                type="button"
              >
                <span>{category.name}</span>
                {category.count !== undefined && category.count > 0 && (
                  <span
                    className={`
                      ml-1.5 text-xs px-1.5 py-0.5 rounded-full
                      ${isActive ? 'bg-white/20 text-white' : 'bg-gray-200 text-gray-600'}
                    `}
                  >
                    {category.count}
                  </span>
                )}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

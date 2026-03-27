import { Spin } from "antd";

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

export function CategoryMenu({ categories, activeCategory, onSelectCategory, loading = false }: CategoryMenuProps) {
  // 没有分类数据时不渲染任何内容
  if (!loading && categories.length === 0) {
    return null;
  }

  return (
    <div className="overflow-hidden">
      {loading ? (
        <div className="flex items-center justify-center py-4">
          <Spin />
        </div>
      ) : (
        <div className="flex items-center justify-center gap-2 flex-wrap">
          {categories.map((category) => {
            const isActive = category.id === activeCategory;
            return (
              <div
                key={category.id}
                onClick={() => onSelectCategory(category.id)}
                className={`
                  px-4 py-1.5 rounded-full cursor-pointer whitespace-nowrap
                  transition-all duration-300 ease-in-out text-sm font-medium
                  ${isActive
                    ? "bg-gray-900 text-white shadow-md"
                    : "bg-gray-100 text-gray-700 hover:bg-gray-200"
                  }
                `}
              >
                <span>{category.name}</span>
                {category.count !== undefined && category.count > 0 && (
                  <span
                    className={`
                      ml-1.5 text-xs px-1.5 py-0.5 rounded-full
                      ${isActive ? "bg-white/20 text-white" : "bg-gray-200 text-gray-600"}
                    `}
                  >
                    {category.count}
                  </span>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

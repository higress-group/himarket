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
  return (
    <div className="w-64 bg-white/40 backdrop-blur-xl rounded-lg flex flex-col overflow-hidden">
      {loading ? (
        <div className="flex items-center justify-center py-8">
          <Spin />
        </div>
      ) : (
        <div className="flex-1 overflow-y-auto p-4 space-y-1">
          {categories.map((category) => {
            const isActive = category.id === activeCategory;
            return (
              <div
                key={category.id}
                onClick={() => onSelectCategory(category.id)}
                className={`
                  px-4 py-2.5 rounded-xl cursor-pointer
                  transition-all duration-300 ease-in-out
                  ${
                    isActive
                      ? "bg-colorPrimary text-white shadow-md"
                      : "text-gray-700 hover:bg-colorPrimaryBgHover hover:text-colorPrimary"
                  }
                `}
              >
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium">{category.name}</span>
                  {category.count !== undefined && category.count > 0 && (
                    <span
                      className={`
                        text-xs px-2 py-0.5 rounded-full
                        ${isActive ? "bg-white/20" : "bg-gray-100 text-gray-600"}
                      `}
                    >
                      {category.count}
                    </span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

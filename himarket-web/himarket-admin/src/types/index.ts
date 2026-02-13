// 导出所有类型定义
export * from "./portal";
export * from "./api-product";
export * from "./gateway";
export * from "./subscription";
export * from "./consumer";
export * from "./logCollector";

// 通用API响应类型
export interface ApiResponse<T = unknown> {
  code: string;
  message: string;
  data: T;
}

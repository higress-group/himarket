import * as product from "./product";
import * as consumer from "./consumer";
import * as developer from "./developer";
import * as category from "./category";
import * as chat from "./chat";


const APIs = {
  ...product,
  ...consumer,
  ...developer,
  ...category,
  ...chat,
}
export default APIs;

// 也可以单独导出，方便按需引入
export * from "./product";
export * from "./consumer";
export * from "./developer";
export * from "./category";
export * from "./chat";
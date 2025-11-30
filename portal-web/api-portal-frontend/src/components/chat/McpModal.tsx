import { Modal, Switch, type ModalProps } from "antd";
import type { ICategory, IProductDetail } from "../../lib/apis";

interface McpModal extends ModalProps {
  categories: ICategory[];
  data: IProductDetail[];
  onFilter: (id: string) => void;
}

function McpModal(props: McpModal) {
  const { data, categories, onFilter, ...modalProps } = props;

  return (
    <Modal
      width={window.innerWidth * 0.8}
      footer={null}
      {...modalProps}
    >
      <div className="flex p-2 gap-2 items-center h-full">
        <div className="h-full flex-1" data-sign-name="sidebar">
          <div className="flex flex-col gap-3">
            <div className="flex flex-col gap-5">
              <div
                className={`flex items-center bg-white rounded-lg border-[4px] border-colorPrimaryBgHover/50
            transition-all duration-200 ease-in-out hover:bg-gray-50 hover:shadow-md hover:scale-[1.02] active:scale-95 text-nowrap overflow-hidden
            w-full px-5 py-2 justify-between `}
              >
                <div className="flex w-full justify-between items-center gap-2">
                  <span className="text-sm font-medium">MCP: 启用</span>
                  <Switch />
                </div>
              </div>
              <button
                className={`flex items-center bg-white rounded-lg transition-all duration-200 ease-in-out hover:bg-colorPrimaryBgHover hover:shadow-md hover:scale-[1.02] active:scale-95 text-nowrap overflow-hidden w-full px-5 py-2 justify-between `}
              >
                已添加 Server (0)
              </button>
            </div>
            <div className="border-t border-gray-200"></div>
            <div className="flex flex-col gap-2">
              {
                categories.map((item) => (
                  <button
                    key={item.categoryId}
                    className={`flex items-center bg-white rounded-lg transition-all duration-200 ease-in-out hover:bg-colorPrimaryBgHover hover:shadow-md hover:scale-[1.02] active:scale-95 text-nowrap overflow-hidden w-full px-5 py-2 justify-between `}
                    onClick={() => onFilter(item.categoryId)}
                  >
                    {item.name}
                  </button>
                ))
              }
            </div>
          </div>
        </div>
        <div className="h-full flex-[5]">
          <div></div>
          <div>
            {data.map((item) => (
              <div key={item.productId}>{item.name}</div>
            ))}
          </div>
        </div>
      </div>
    </Modal>
  )
}

export default McpModal;
import { useMemo, useState } from "react";
import { Modal, Switch, Input, Skeleton, type ModalProps, Button } from "antd";
import { CloseOutlined, DeleteOutlined, SearchOutlined } from "@ant-design/icons";
import type { ICategory, IProductDetail, ISubscription } from "../../lib/apis";
import McpCard from "./McpCard";

interface McpModal extends ModalProps {
  categories: ICategory[];
  data: IProductDetail[];
  added: IProductDetail[];
  onFilter: (id: string) => void;
  onSearch: (categorieId: string, name: string) => void;
  mcpLoading?: boolean;
  onAdd: (product: IProductDetail) => void;
  onRemove: (product: IProductDetail) => void;
  onRemoveAll: () => void;
  subscripts: ISubscription[];
  enabled?: boolean;
  onEnabled: (enabled: boolean) => void;
  onClose: () => void;
  onQuickSubscribe?: (product: IProductDetail) => void;
}

function McpModal(props: McpModal) {
  const {
    data, categories, onFilter,
    onSearch, mcpLoading, added,
    onAdd, subscripts, onEnabled,
    enabled, onRemove, onClose,
    onQuickSubscribe, onRemoveAll,
    ...modalProps
  } = props;
  const [searchText, setSearchText] = useState("");

  const [active, setActive] = useState("all");

  const scbscriptsIds = useMemo(() => {
    return subscripts.map(v => v.productId);
  }, [subscripts]);

  const addedIds = useMemo(() => {
    return added.map(v => v.productId);
  }, [added]);

  const filteredData = useMemo(() => {
    if (active === "added") {
      return added
    }
    return data;
  }, [data, active, added]);

  return (
    <Modal
      width={window.innerWidth * 0.9}
      height={window.innerHeight * 0.8}
      closable={false}
      footer={null}
      {...modalProps}
    >
      <div className="flex p-2 gap-2 h-[70vh]">
        <div className="flex-1 flex flex-col overflow-y-auto" data-sign-name="sidebar">
          <div className="flex px-1 flex-col gap-3">
            <div className="flex flex-col gap-5">
              <div
                className={`flex items-center bg-white rounded-lg border-[4px] border-colorPrimaryBgHover/50
            transition-all duration-200 ease-in-out hover:bg-gray-50 hover:shadow-md hover:scale-[1.02] active:scale-95 text-nowrap overflow-hidden
            w-full px-5 py-2 justify-between`}
              >
                <div className="flex w-full justify-between items-center gap-2">
                  <span className="text-sm font-medium">MCP: 启用</span>
                  <Switch checked={enabled} onChange={() => onEnabled(!enabled)} />
                </div>
              </div>
              <button
                onClick={() => {
                  setActive("added");
                  onFilter("added");
                }}
                className={`
                  flex items-center  rounded-lg 
                  transition-all duration-200 ease-in-out 
                  hover:bg-colorPrimaryBgHover hover:shadow-md hover:scale-[1.02] 
                  active:scale-95 text-nowrap overflow-hidden w-full px-5 py-2 justify-between
                  ${active === "added" ? "bg-colorPrimaryBgHover shadow-md scale-[1.02]" : "bg-white"}
                `}
              >
                已添加 Server
              </button>
            </div>
            <div className="border-t border-gray-200"></div>
            <div className="flex flex-col gap-2">
              {
                categories.map((item) => (
                  <button
                    key={item.categoryId}
                    className={`
                      flex items-center rounded-lg 
                      transition-all duration-200 ease-in-out
                       hover:bg-colorPrimaryBgHover hover:shadow-md 
                       hover:scale-[1.02] active:scale-95 text-nowrap 
                       overflow-hidden w-full px-5 py-2 justify-between 
                       ${active === item.categoryId ? "bg-colorPrimaryBgHover shadow-md scale-[1.02]" : "bg-white"}
                    `}
                    onClick={() => {
                      setActive(item.categoryId)
                      onFilter(item.categoryId)
                    }}
                  >
                    {item.name}
                  </button>
                ))
              }
            </div>
          </div>
        </div>

        <div className="flex-[5] flex flex-col gap-4 overflow-hidden" data-sign-name="mcp-list">
          <div className="flex flex-col gap-2">
            <div className="flex w-full gap-4 justify-between">
              <Input
                placeholder="搜索 MCP Server..."
                prefix={<SearchOutlined />}
                value={searchText}
                onChange={(e) => setSearchText(e.target.value)}
                allowClear
                size="large"
                onKeyDown={(evt) => {
                  if (evt.code === "Enter") {
                    onSearch(active, (evt.target as HTMLInputElement).value.trim())
                  }
                }}
              />
              <div onClick={onClose} className="flex h-full items-center justify-center cursor-pointer">
                <CloseOutlined />
              </div>
            </div>
            {
              active === "added" && filteredData.length > 0 && (
                <span>已添加 {added.length} / 10</span>
              )
            }
          </div>
          {
            mcpLoading ? (
              <div className="grid grid-cols-3 gap-4 content-start overflow-y-auto p-1">
                {Array.from({ length: 6 }).map((_, index) => (
                  <div
                    key={index}
                    className="bg-white/60 backdrop-blur-sm rounded-2xl p-5 border border-[#e5e5e5] h-[200px] flex flex-col gap-4"
                  >
                    {/* 上部：Logo、名称和��态 */}
                    <div className="flex gap-3 items-start">
                      <Skeleton.Avatar active size={56} shape="square" />
                      <div className="flex-1 flex flex-col gap-2">
                        <Skeleton.Input active size="small" style={{ width: '70%', height: 20 }} />
                        <Skeleton.Button active size="small" style={{ width: 60, height: 24 }} />
                      </div>
                    </div>

                    {/* 中部：描述 */}
                    <div className="flex-1">
                      <Skeleton active paragraph={{ rows: 2 }} title={false} />
                    </div>

                    {/* 下部：按钮区域 */}
                    <Skeleton.Button active block size="default" />
                  </div>
                ))}
              </div>
            ) : (
              filteredData.length === 0 ? (
                <Empty onViewAll={() => {
                  setActive("all");
                  onFilter("all");
                }} active={active} />
              ) : (
                <div className="grid grid-cols-3 gap-4 content-start overflow-y-auto p-1 flex-1" data-sign-name="mcp-card-grid">
                  {
                    filteredData.map((item) => (
                      <McpCard
                        key={item.productId} data={item}
                        isAdded={addedIds.includes(item.productId)}
                        onAdd={onAdd}
                        onRemove={onRemove}
                        isSubscribed={scbscriptsIds.includes(item.productId)}
                        onQuickSubscribe={onQuickSubscribe}
                      />
                    ))}
                </div>
              )
            )
          }
          {
            active === "added" && filteredData.length > 0 && (
              <Button onClick={onRemoveAll} block size="large">
                <DeleteOutlined />
                批量取消添加
              </Button>
            )
          }
        </div>
      </div>
    </Modal>
  )
}


function Empty({ active, onViewAll }: { active: string; onViewAll: () => void }) {
  return (
    <div className="grid grid-cols-3 grid-rows-3 gap-4 content-start overflow-y-auto p-1 flex-1 relative">
      <div className="absolute z-20 top-[50%] left-[50%] translate-x-[-50%] translate-y-[-50%] flex flex-col gap-4 justify-center">
        <div className="">
          <div className="text-center text-lg">暂无 MCP Server...</div>
          {active === "added" && (
            <span>您可以从全部 MCP Server 中选择并添加您需要的 Server</span>
          )}
        </div>
        <div className="flex justify-center">
          <Button type="primary" onClick={onViewAll}>预览全部 Server</Button>
        </div>
      </div>
      <div className="absolute w-full h-full z-10" style={{ background: "linear-gradient(326deg, #FFFFFF 18%, rgba(255, 255, 255, 0) 81%)" }}></div>
      {
        Array.from({ length: 9 }).map((_, index) => (
          <div
            key={index}
            className="bg-[#F9FAFB] backdrop-blur-sm rounded-2xl p-5  flex flex-col gap-4"
          >
          </div>
        ))
      }
    </div>
  )
}

export default McpModal;
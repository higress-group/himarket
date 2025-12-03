import { Table, Button, Space, Typography, Input, Pagination, type TableColumnType, Select } from "antd";
import { DeleteOutlined, PlusOutlined, SearchOutlined, ReloadOutlined, EditOutlined } from "@ant-design/icons";
import { Layout } from "../components/Layout";
import { useEffect, useState, useCallback } from "react";
import { getConsumers, deleteConsumer, createConsumer } from "../lib/api";
import { message, Modal } from "antd";
import { Link, useSearchParams } from "react-router-dom";
import { formatDateTime } from "../lib/utils";
import APIs, { type IConsumer, type IGetPrimaryConsumerResp } from "../lib/apis";

const { Title } = Typography;

function ConsumersPage() {
  const [searchParams] = useSearchParams();
  const productId = searchParams.get('productId');

  const [consumers, setConsumers] = useState<IConsumer[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [searchInput, setSearchInput] = useState(""); // 输入框的值
  const [searchName, setSearchName] = useState(""); // 实际搜索的值
  const [addModalOpen, setAddModalOpen] = useState(false);
  const [addLoading, setAddLoading] = useState(false);
  const [addForm, setAddForm] = useState({ name: '', description: '' });
  const [refreshIndex, setRefreshIndex] = useState(0);
  const [primaryConsumer, setPrimaryConsumer] = useState<IGetPrimaryConsumerResp>();

  const [consumersForSelect, setConsumersForSelect] = useState<IConsumer[]>([]);
  const [showModifyPrimaryConsumerModal, setShowModifyPrimaryConsumerModal] = useState(false);
  const [selectedPrimaryConsumer, setSelectedPrimaryConsumer] = useState("");


  const fetchConsumers = useCallback(async (searchKeyword?: string, targetPage?: number) => {
    setLoading(true);
    try {
      const res = await getConsumers(
        { name: searchKeyword || '' },
        { page: targetPage || page, size: pageSize }
      );
      setConsumers(res.data?.content || []);
      setTotal(res.data?.totalElements || 0);
    } catch {
      // message.error("获取消费者列表失败");
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, refreshIndex]); // 不依赖 searchName

  const fetchConsumersForSelect = async (searchKeyword?: string, targetPage?: number, size = 100, isRefresh = false) => {
    try {
      const res = await APIs.getConsumers(
        {
          name: searchKeyword || '',
          page: targetPage,
          size: size
        },

      );
      if (res?.data?.content) {
        if (searchKeyword || isRefresh) {
          setConsumersForSelect(res.data.content)
        } else {
          setConsumersForSelect(v => [...v, ...res.data.content]);
        }
      }
    } catch {
      // message.error("获取消费者列表失败");
    }
  }; // 不依赖 searchName


  const getPrimaryConsumer = () => {
    APIs.getPrimaryConsumer().then(({ data }) => {
      if (data) {
        setPrimaryConsumer(data)
      }
    })
  }

  // 初始加载和分页变化时调用
  useEffect(() => {
    fetchConsumers(searchName);
  }, [page, pageSize, fetchConsumers, searchName]); // 包含fetchConsumers以确保初始加载

  // 处理搜索
  const handleSearch = useCallback(async (searchValue?: string) => {
    const actualSearchValue = searchValue !== undefined ? searchValue : searchInput;
    setSearchName(actualSearchValue);
    setPage(1);
    // 直接调用API，不依赖状态变化
    await fetchConsumers(actualSearchValue, 1);
  }, [searchInput, fetchConsumers]);

  const handleDelete = (record: IConsumer) => {
    Modal.confirm({
      title: `确定要删除消费者「${record.name}」吗？`,
      onOk: async () => {
        try {
          await deleteConsumer(record.consumerId);
          message.success("删除成功");
          await fetchConsumers(searchName); // 使用当前搜索条件重新加载
        } catch {
          // message.error("删除失败");
        }
      },
    });
  };

  const handleAdd = async () => {
    if (!addForm.name.trim()) {
      message.warning('请输入消费者名称');
      return;
    }
    setAddLoading(true);
    try {
      await createConsumer({ name: addForm.name, description: addForm.description });
      message.success('新增成功');
      setAddModalOpen(false);
      setAddForm({ name: '', description: '' });
      await fetchConsumers(searchName); // 使用当前搜索条件重新加载
    } catch {
      // message.error('新增失败');
    } finally {
      setAddLoading(false);
    }
  };

  const handleConfirmModifyPrimaryConsumer = () => {
    APIs.putPrimaryConsumer(selectedPrimaryConsumer)
      .then(({ code }) => {
        if (code === "SUCCESS") {
          message.success("修改成功！")
          setShowModifyPrimaryConsumerModal(false);
          getPrimaryConsumer();
        }
      }).catch(() => {
        message.error("修改失败，请重试")
      })
  }

  const columns: TableColumnType<IConsumer>[] = [
    {
      title: '消费者',
      dataIndex: 'name',
      key: 'name',
      width: "20%",
      render: (name: string, record) => (
        <div className="flex gap-2 items-center">
          <div className="font-medium">{name}</div>
          {record.consumerId === primaryConsumer?.consumerId && (
            <div
              onClick={() => {
                setShowModifyPrimaryConsumerModal(true);
                fetchConsumersForSelect(undefined, 1, 1000, true);
              }}
              className="px-2 py-1 gap-2 cursor-pointer rounded-md bg-black/70 text-white">
              <span> 默认消费者 </span>
              <EditOutlined />
            </div>
          )}
        </div>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createAt',
      key: 'createAt',
      width: "20%",
      render: (date: string) => date ? formatDateTime(date) : '-',
    },
    {
      title: "描述",
      dataIndex: "description",
      key: "description",
      width: "30%",
      render: (description: string) => description || '-',
    },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, record: IConsumer) => (
        <Space>
          <Link to={`/consumers/${record.consumerId}`}>
            <Button
              className="rounded-lg text-colorPrimary"
            >
              查看详情
            </Button>
          </Link>
          <Button
            disabled={record.consumerId === primaryConsumer?.consumerId}
            className="rounded-lg" icon={<DeleteOutlined className={record.consumerId === primaryConsumer?.consumerId ? "" : "text-[#EF4444]"} />}
            onClick={() => handleDelete(record)}>
          </Button>
        </Space>
      ),
    },
  ];

  useEffect(() => {
    getPrimaryConsumer();
  }, []);

  return (
    <Layout>
      <div className="w-full ">

        {/* 主内容区域 - glass-morphism 风格 */}
        <div className="min-h-[calc(100vh-96px)] bg-white backdrop-blur-xl rounded-2xl shadow-xs border border-white/40 p-6">
          <div className="mb-5">
            <Title level={2} className="text-gray-900">
              {productId ? '产品订阅管理' : '消费者管理'}
            </Title>
          </div>
          {/* 搜索和新增按钮 */}
          <div className="mb-4 flex justify-between items-center">
            <div className="flex gap-2 items-center">
              {!productId && (
                <Button
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={() => setAddModalOpen(true)}
                  className="rounded-lg"
                >
                  新增消费者
                </Button>
              )}
              <Input
                placeholder="搜索消费者..."
                prefix={<SearchOutlined className="text-gray-400" />}
                value={searchInput}
                onChange={e => setSearchInput(e.target.value)}
                onPressEnter={() => handleSearch()}
                allowClear
                className="w-80 rounded-lg"
                style={{
                  backgroundColor: "rgba(255, 255, 255, 0.6)",
                  backdropFilter: "blur(10px)",
                }}
              />
            </div>
            <div>
              <Button className="rounded-lg" icon={<ReloadOutlined />} onClick={() => setRefreshIndex(v => v + 1)} />
            </div>
          </div>

          {/* 表格 */}
          <div className="overflow-hidden rounded-lg border border-[#e5e5e5]">
            <Table
              columns={columns}
              dataSource={consumers}
              rowKey="consumerId"
              loading={loading}
              pagination={false}
            />
          </div>
          <div className="flex w-full justify-end items-center p-3">
            <Pagination
              {...{
                total,
                current: page,
                pageSize,
                showSizeChanger: true,
                showQuickJumper: true,
                showTotal: (total) => `共 ${total} 条`,
                onChange: (p, ps) => {
                  setPage(p);
                  setPageSize(ps);
                },
              }}
            />
          </div>
        </div>

        {/* 新增消费者模态框 */}
        <Modal
          title="新增消费者"
          open={addModalOpen}
          onCancel={() => { setAddModalOpen(false); setAddForm({ name: '', description: '' }); }}
          onOk={handleAdd}
          confirmLoading={addLoading}
          okText="提交"
          cancelText="取消"
        >
          <div className="mb-4">
            <Input
              placeholder="消费者名称"
              value={addForm.name}
              maxLength={50}
              onChange={e => setAddForm(f => ({ ...f, name: e.target.value }))}
              disabled={addLoading}
            />
          </div>
          <div>
            <Input.TextArea
              placeholder="描述（可选），长度限制64"
              value={addForm.description}
              maxLength={64}
              onChange={e => setAddForm(f => ({ ...f, description: e.target.value }))}
              disabled={addLoading}
              rows={3}
            />
          </div>
        </Modal>
      </div>
      <Modal
        // title="修改默认消费者"
        width={400}
        open={showModifyPrimaryConsumerModal}
        onCancel={() => setShowModifyPrimaryConsumerModal(false)}
        footer={null}
      >
        <div className="flex w-full justify-center flex-col gap-4 pt-2">
          <div className="font-bold text-lg">切换默认消费者</div>
          <div>
            <Select
              defaultValue={primaryConsumer?.consumerId}
              style={{ width: "100%" }}
              options={consumersForSelect.map(v => ({ label: v.name, value: v.consumerId }))}
              filterOption={(input, option) => {
                return (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
              }}
              showSearch
              onChange={setSelectedPrimaryConsumer}
            />
          </div>
          <div className="flex gap-2 justify-end">
            <Button type="primary" onClick={handleConfirmModifyPrimaryConsumer}>确认</Button>
            <Button
              onClick={() => setShowModifyPrimaryConsumerModal(false)}
            >取消</Button>
          </div>
        </div>
      </Modal>
    </Layout>
  );
}

export default ConsumersPage; 
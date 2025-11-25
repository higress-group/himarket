import { Table, Button, Space, Typography, Input } from "antd";
import { DeleteOutlined, EyeOutlined, PlusOutlined, SearchOutlined } from "@ant-design/icons";
import { Layout } from "../components/Layout";
import { useEffect, useState, useCallback } from "react";
import { getConsumers, deleteConsumer, createConsumer } from "../lib/api";
import { message, Modal } from "antd";
import { Link, useSearchParams } from "react-router-dom";
import { formatDateTime } from "../lib/utils";
import type { Consumer } from "../types/consumer";
import "../styles/table.css";

const { Title } = Typography;

function ConsumersPage() {
  const [searchParams] = useSearchParams();
  const productId = searchParams.get('productId');

  const [consumers, setConsumers] = useState<Consumer[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [searchInput, setSearchInput] = useState(""); // 输入框的值
  const [searchName, setSearchName] = useState(""); // 实际搜索的值
  const [addModalOpen, setAddModalOpen] = useState(false);
  const [addLoading, setAddLoading] = useState(false);
  const [addForm, setAddForm] = useState({ name: '', description: '' });

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
  }, [page, pageSize]); // 不依赖 searchName

  // 初始加载和分页变化时调用
  useEffect(() => {
    fetchConsumers(searchName);
  }, [page, pageSize, fetchConsumers]); // 包含fetchConsumers以确保初始加载

  // 处理搜索
  const handleSearch = useCallback(async (searchValue?: string) => {
    const actualSearchValue = searchValue !== undefined ? searchValue : searchInput;
    setSearchName(actualSearchValue);
    setPage(1);
    // 直接调用API，不依赖状态变化
    await fetchConsumers(actualSearchValue, 1);
  }, [searchInput, fetchConsumers]);

  const handleDelete = (record: Consumer) => {
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

  const columns = [
    {
      title: '消费者',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record: Consumer) => (
        <div>
          <div className="font-medium">{name}</div>
          <div className="text-xs text-gray-400">{record.description}</div>
        </div>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createAt',
      key: 'createAt',
      render: (date: string) => date ? formatDateTime(date) : '-',
    },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, record: Consumer) => (
        <Space>
          <Link to={`/consumers/${record.consumerId}`}>
            <Button
              type="link"
              icon={<EyeOutlined />}
            >
              查看详情
            </Button>
          </Link>
          <Button type="link" danger icon={<DeleteOutlined />} onClick={() => handleDelete(record)}>
            删除
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Layout>
      <div className="w-full">
        {/* 页面标题 */}
        <div className="mb-6">
          <Title level={2} className="mb-0 text-gray-900">
            {productId ? '产品订阅管理' : '消费者'}
          </Title>
        </div>

        {/* 主内容区域 - glass-morphism 风格 */}
        <div className="bg-white/40 backdrop-blur-xl rounded-2xl shadow-lg border border-white/40 p-6">
          {/* 搜索和新增按钮 */}
          <div className="mb-4 flex justify-between items-center">
            <Input
              placeholder="搜索消费者..."
              prefix={<SearchOutlined className="text-gray-400" />}
              value={searchInput}
              onChange={e => setSearchInput(e.target.value)}
              onPressEnter={() => handleSearch()}
              allowClear
              className="w-80 rounded-xl"
              style={{
                backgroundColor: "rgba(255, 255, 255, 0.6)",
                backdropFilter: "blur(10px)",
              }}
            />
            {!productId && (
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => setAddModalOpen(true)}
                className="rounded-full"
              >
                新增消费者
              </Button>
            )}
          </div>

          {/* 表格 */}
          <Table
            columns={columns}
            dataSource={consumers}
            rowKey="consumerId"
            loading={loading}
            pagination={{
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
    </Layout>
  );
}

export default ConsumersPage; 
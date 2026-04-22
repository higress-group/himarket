import {
  DeleteOutlined,
  PlusOutlined,
  SearchOutlined,
  ReloadOutlined,
  EditOutlined,
} from '@ant-design/icons';
import {
  Table,
  Button,
  Space,
  Typography,
  Input,
  Pagination,
  type TableColumnType,
  Select,
} from 'antd';
import { message, Modal } from 'antd';
import { useEffect, useState, useCallback } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

import { Layout } from '../components/Layout';
import { getConsumers, deleteConsumer, createConsumer } from '../lib/apis';
import APIs, { type IConsumer, type IGetPrimaryConsumerResp } from '../lib/apis';
import { formatDateTime } from '../lib/utils';

const { Title } = Typography;

function ConsumersPage() {
  const [searchParams] = useSearchParams();
  const productId = searchParams.get('productId');

  const [consumers, setConsumers] = useState<IConsumer[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [searchInput, setSearchInput] = useState(''); // 输入框的值
  const [searchName, setSearchName] = useState(''); // 实际搜索的值
  const [addModalOpen, setAddModalOpen] = useState(false);
  const [addLoading, setAddLoading] = useState(false);
  const [addForm, setAddForm] = useState({ description: '', name: '' });
  const [primaryConsumer, setPrimaryConsumer] = useState<IGetPrimaryConsumerResp>();

  const [consumersForSelect, setConsumersForSelect] = useState<IConsumer[]>([]);
  const [showModifyPrimaryConsumerModal, setShowModifyPrimaryConsumerModal] = useState(false);
  const [selectedPrimaryConsumer, setSelectedPrimaryConsumer] = useState('');

  const fetchConsumers = useCallback(
    async (searchKeyword?: string, targetPage?: number) => {
      setLoading(true);
      try {
        const res = await getConsumers({
          name: searchKeyword || '',
          page: targetPage || page,
          size: pageSize,
        });
        setConsumers(res.data?.content || []);
        setTotal(res.data?.totalElements || 0);
      } catch {
        // message.error("获取消费者列表失败");
      } finally {
        setLoading(false);
      }
    },
    [page, pageSize],
  );

  const fetchConsumersForSelect = async (
    searchKeyword?: string,
    targetPage?: number,
    size = 100,
    isRefresh = false,
  ) => {
    try {
      const res = await APIs.getConsumers({
        name: searchKeyword || '',
        page: targetPage,
        size: size,
      });
      if (res?.data?.content) {
        if (searchKeyword || isRefresh) {
          setConsumersForSelect(res.data.content);
        } else {
          setConsumersForSelect((v) => [...v, ...res.data.content]);
        }
      }
    } catch {
      // message.error("获取消费者列表失败");
    }
  }; // 不依赖 searchName

  const getPrimaryConsumer = () => {
    APIs.getPrimaryConsumer().then(({ data }) => {
      if (data) {
        setPrimaryConsumer(data);
      }
    });
  };

  // 初始加载和分页变化时调用
  useEffect(() => {
    fetchConsumers(searchName);
  }, [page, pageSize, fetchConsumers, searchName]); // 包含fetchConsumers以确保初始加载

  // 处理搜索
  const handleSearch = useCallback(
    async (searchValue?: string) => {
      const actualSearchValue = searchValue !== undefined ? searchValue : searchInput;
      setSearchName(actualSearchValue);
      setPage(1);
      // 直接调用API，不依赖状态变化
      await fetchConsumers(actualSearchValue, 1);
    },
    [searchInput, fetchConsumers],
  );

  const handleDelete = (record: IConsumer) => {
    Modal.confirm({
      onOk: async () => {
        try {
          await deleteConsumer(record.consumerId);
          message.success('删除成功');
          await fetchConsumers(searchName); // 使用当前搜索条件重新加载
        } catch {
          // message.error("删除失败");
        }
      },
      title: `确定要删除消费者「${record.name}」吗？`,
    });
  };

  const handleAdd = async () => {
    if (!addForm.name.trim()) {
      message.warning('请输入消费者名称');
      return;
    }
    setAddLoading(true);
    try {
      await createConsumer({ description: addForm.description, name: addForm.name });
      message.success('新增成功');
      setAddModalOpen(false);
      setAddForm({ description: '', name: '' });
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
        if (code === 'SUCCESS') {
          message.success('修改成功！');
          setShowModifyPrimaryConsumerModal(false);
          getPrimaryConsumer();
        }
      })
      .catch(() => {
        message.error('修改失败，请重试');
      });
  };

  const columns: TableColumnType<IConsumer>[] = [
    {
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record) => (
        <div className="flex gap-2 items-center">
          <div className="font-medium">{name}</div>
          {record.consumerId === primaryConsumer?.consumerId && (
            <div
              className="px-2 py-1 gap-2 cursor-pointer rounded-md bg-black/70 text-white"
              onClick={() => {
                setShowModifyPrimaryConsumerModal(true);
                fetchConsumersForSelect(undefined, 1, 1000, true);
              }}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  setShowModifyPrimaryConsumerModal(true);
                  fetchConsumersForSelect(undefined, 1, 1000, true);
                }
              }}
              role="button"
              tabIndex={0}
            >
              <span> 默认消费者 </span>
              <EditOutlined />
            </div>
          )}
        </div>
      ),
      title: '消费者',
      width: '20%',
    },
    {
      dataIndex: 'createAt',
      key: 'createAt',
      render: (date: string) => (date ? formatDateTime(date) : '-'),
      title: '创建时间',
      width: '20%',
    },
    {
      dataIndex: 'description',
      key: 'description',
      render: (description: string) => description || '-',
      title: '描述',
      width: '30%',
    },
    {
      key: 'action',
      render: (_: unknown, record: IConsumer) => (
        <Space>
          <Link to={`/consumers/${record.consumerId}`}>
            <Button className="rounded-lg text-colorPrimary">查看详情</Button>
          </Link>
          <Button
            className="rounded-lg"
            disabled={record.consumerId === primaryConsumer?.consumerId}
            icon={
              <DeleteOutlined
                className={
                  record.consumerId === primaryConsumer?.consumerId ? '' : 'text-[#EF4444]'
                }
              />
            }
            onClick={() => handleDelete(record)}
          ></Button>
        </Space>
      ),
      title: '操作',
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
            <Title className="text-gray-900" level={2}>
              {productId ? '产品订阅管理' : '消费者管理'}
            </Title>
          </div>
          {/* 搜索和新增按钮 */}
          <div className="mb-4 flex justify-between items-center">
            <div className="flex gap-2 items-center">
              {!productId && (
                <Button
                  className="rounded-lg"
                  icon={<PlusOutlined />}
                  onClick={() => setAddModalOpen(true)}
                  type="primary"
                >
                  新增消费者
                </Button>
              )}
              <Input
                allowClear
                className="w-80 rounded-lg"
                onChange={(e) => setSearchInput(e.target.value)}
                onPressEnter={() => handleSearch()}
                placeholder="搜索消费者..."
                prefix={<SearchOutlined className="text-gray-400" />}
                style={{
                  backdropFilter: 'blur(10px)',
                  backgroundColor: 'rgba(255, 255, 255, 0.6)',
                }}
                value={searchInput}
              />
            </div>
            <div>
              <Button
                className="rounded-lg"
                icon={<ReloadOutlined />}
                onClick={() => fetchConsumers(searchName)}
              />
            </div>
          </div>

          {/* 表格 */}
          <div className="overflow-hidden rounded-lg border border-[#e5e5e5]">
            <Table
              columns={columns}
              dataSource={consumers}
              loading={loading}
              pagination={false}
              rowKey="consumerId"
            />
          </div>
          <div className="flex w-full justify-end items-center p-3">
            <Pagination
              {...{
                current: page,
                onChange: (p, ps) => {
                  setPage(p);
                  setPageSize(ps);
                },
                pageSize,
                showQuickJumper: true,
                showSizeChanger: true,
                showTotal: (total) => `共 ${total} 条`,
                total,
              }}
            />
          </div>
        </div>

        {/* 新增消费者模态框 */}
        <Modal
          cancelText="取消"
          confirmLoading={addLoading}
          okText="提交"
          onCancel={() => {
            setAddModalOpen(false);
            setAddForm({ description: '', name: '' });
          }}
          onOk={handleAdd}
          open={addModalOpen}
          title="新增消费者"
        >
          <div className="mb-4">
            <Input
              disabled={addLoading}
              maxLength={50}
              onChange={(e) => setAddForm((f) => ({ ...f, name: e.target.value }))}
              placeholder="消费者名称"
              value={addForm.name}
            />
          </div>
          <div>
            <Input.TextArea
              disabled={addLoading}
              maxLength={64}
              onChange={(e) => setAddForm((f) => ({ ...f, description: e.target.value }))}
              placeholder="描述（可选），长度限制64"
              rows={3}
              value={addForm.description}
            />
          </div>
        </Modal>
      </div>
      <Modal
        footer={null}
        onCancel={() => setShowModifyPrimaryConsumerModal(false)}
        open={showModifyPrimaryConsumerModal}
        // title="修改默认消费者"
        width={400}
      >
        <div className="flex w-full justify-center flex-col gap-4 pt-2">
          <div className="font-bold text-lg">切换默认消费者</div>
          <div>
            <Select
              defaultValue={primaryConsumer?.consumerId}
              filterOption={(input, option) => {
                return (option?.label ?? '').toLowerCase().includes(input.toLowerCase());
              }}
              onChange={setSelectedPrimaryConsumer}
              options={consumersForSelect.map((v) => ({ label: v.name, value: v.consumerId }))}
              showSearch
              style={{ width: '100%' }}
            />
          </div>
          <div className="flex gap-2 justify-end">
            <Button onClick={handleConfirmModifyPrimaryConsumer} type="primary">
              确认
            </Button>
            <Button onClick={() => setShowModifyPrimaryConsumerModal(false)}>取消</Button>
          </div>
        </div>
      </Modal>
    </Layout>
  );
}

export default ConsumersPage;

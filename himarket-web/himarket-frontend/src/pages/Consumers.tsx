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
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router-dom';

import { Layout } from '../components/Layout';
import { getConsumers, deleteConsumer, createConsumer } from '../lib/apis';
import APIs, { type IConsumer, type IGetPrimaryConsumerResp } from '../lib/apis';
import { formatDateTime } from '../lib/utils';

const { Title } = Typography;

function ConsumersPage() {
  const { t } = useTranslation(['consumer', 'common']);
  const [searchParams] = useSearchParams();
  const productId = searchParams.get('productId');

  const [consumers, setConsumers] = useState<IConsumer[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [searchInput, setSearchInput] = useState('');
  const [searchName, setSearchName] = useState('');
  const [addModalOpen, setAddModalOpen] = useState(false);
  const [addLoading, setAddLoading] = useState(false);
  const [addForm, setAddForm] = useState({ description: '', name: '' });
  const [refreshIndex, setRefreshIndex] = useState(0);
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
        // message.error('Failed to fetch consumers');
      } finally {
        setLoading(false);
      }
    },
    [page, pageSize],
  ); // refreshIndex is intentionally excluded to prevent unnecessary re-fetches

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
      // message.error('Failed to fetch consumers');
    }
  };

  const getPrimaryConsumer = () => {
    APIs.getPrimaryConsumer().then(({ data }) => {
      if (data) {
        setPrimaryConsumer(data);
      }
    });
  };

  useEffect(() => {
    fetchConsumers(searchName);
  }, [page, pageSize, fetchConsumers, refreshIndex, searchName]);

  const handleSearch = useCallback(
    async (searchValue?: string) => {
      const actualSearchValue = searchValue !== undefined ? searchValue : searchInput;
      setSearchName(actualSearchValue);
      setPage(1);
      await fetchConsumers(actualSearchValue, 1);
    },
    [searchInput, fetchConsumers],
  );

  const handleDelete = (record: IConsumer) => {
    Modal.confirm({
      onOk: async () => {
        try {
          await deleteConsumer(record.consumerId);
          message.success(t('deleteSuccess'));
          await fetchConsumers(searchName);
        } catch {
          // message.error('Delete failed');
        }
      },
      title: t('deleteConfirm', { name: record.name }),
    });
  };

  const handleAdd = async () => {
    if (!addForm.name.trim()) {
      message.warning(t('nameRequired'));
      return;
    }
    setAddLoading(true);
    try {
      await createConsumer({ description: addForm.description, name: addForm.name });
      message.success(t('addSuccess'));
      setAddModalOpen(false);
      setAddForm({ description: '', name: '' });
      await fetchConsumers(searchName);
    } catch {
      // message.error('Add failed');
    } finally {
      setAddLoading(false);
    }
  };

  const handleConfirmModifyPrimaryConsumer = () => {
    APIs.putPrimaryConsumer(selectedPrimaryConsumer)
      .then(({ code }) => {
        if (code === 'SUCCESS') {
          message.success(t('updateSuccess'));
          setShowModifyPrimaryConsumerModal(false);
          getPrimaryConsumer();
        }
      })
      .catch(() => {
        message.error(t('updateFailed'));
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
            <button
              className="px-2 py-1 gap-2 cursor-pointer rounded-md bg-black/70 text-white flex items-center"
              onClick={() => {
                setShowModifyPrimaryConsumerModal(true);
                fetchConsumersForSelect(undefined, 1, 1000, true);
              }}
              type="button"
            >
              <span>{t('defaultConsumer')}</span>
              <EditOutlined />
            </button>
          )}
        </div>
      ),
      title: t('columns.consumer'),
      width: '20%',
    },
    {
      dataIndex: 'createAt',
      key: 'createAt',
      render: (date: string) => (date ? formatDateTime(date) : '-'),
      title: t('columns.createdAt'),
      width: '20%',
    },
    {
      dataIndex: 'description',
      key: 'description',
      render: (description: string) => description || '-',
      title: t('columns.description'),
      width: '30%',
    },
    {
      key: 'action',
      render: (_: unknown, record: IConsumer) => (
        <Space>
          <Link to={`/consumers/${record.consumerId}`}>
            <Button className="rounded-lg text-colorPrimary">{t('viewDetails')}</Button>
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
      title: t('columns.action'),
    },
  ];

  useEffect(() => {
    getPrimaryConsumer();
  }, []);

  return (
    <Layout>
      <div className="w-full ">
        <div className="min-h-[calc(100vh-96px)] bg-white backdrop-blur-xl rounded-2xl shadow-xs border border-white/40 p-6">
          <div className="mb-5">
            <Title className="text-gray-900" level={2}>
              {productId ? t('productSubscriptionsTitle') : t('listTitle')}
            </Title>
          </div>
          <div className="mb-4 flex justify-between items-center">
            <div className="flex gap-2 items-center">
              {!productId && (
                <Button
                  className="rounded-lg"
                  icon={<PlusOutlined />}
                  onClick={() => setAddModalOpen(true)}
                  type="primary"
                >
                  {t('addConsumer')}
                </Button>
              )}
              <Input
                allowClear
                className="w-80 rounded-lg"
                onChange={(e) => setSearchInput(e.target.value)}
                onPressEnter={() => handleSearch()}
                placeholder={t('searchPlaceholder')}
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
                onClick={() => setRefreshIndex((v) => v + 1)}
              />
            </div>
          </div>

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
                showTotal: (total) => t('total', { total }),
                total,
              }}
            />
          </div>
        </div>

        <Modal
          cancelText={t('common:cancel')}
          confirmLoading={addLoading}
          okText={t('modal.submit')}
          onCancel={() => {
            setAddModalOpen(false);
            setAddForm({ description: '', name: '' });
          }}
          onOk={handleAdd}
          open={addModalOpen}
          title={t('modal.addTitle')}
        >
          <div className="mb-4">
            <Input
              disabled={addLoading}
              maxLength={50}
              onChange={(e) => setAddForm((f) => ({ ...f, name: e.target.value }))}
              placeholder={t('modal.namePlaceholder')}
              value={addForm.name}
            />
          </div>
          <div>
            <Input.TextArea
              disabled={addLoading}
              maxLength={64}
              onChange={(e) => setAddForm((f) => ({ ...f, description: e.target.value }))}
              placeholder={t('modal.descriptionPlaceholder')}
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
        width={400}
      >
        <div className="flex w-full justify-center flex-col gap-4 pt-2">
          <div className="font-bold text-lg">{t('primaryConsumer.title')}</div>
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
              {t('common:confirm')}
            </Button>
            <Button onClick={() => setShowModifyPrimaryConsumerModal(false)}>
              {t('common:cancel')}
            </Button>
          </div>
        </div>
      </Modal>
    </Layout>
  );
}

export default ConsumersPage;

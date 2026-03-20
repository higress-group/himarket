import React, { useState, useEffect } from 'react';
import { Table, Card, Space, Button, Input, message } from 'antd';
import type { TablePaginationConfig, FilterValue, SorterResult } from 'antd/es/table/interface';
import { SearchOutlined, SyncOutlined } from '@ant-design/icons';
import { productSummaryApi } from '../lib/api';

const ProductSummary: React.FC = () => {
  const [data, setData] = useState<any[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  });
  const [filters, setFilters] = useState({
    name: '',
  });

  // 获取产品统计列表
  const fetchData = async (params: any = {}) => {
    try {
      setLoading(true);
      const response = await productSummaryApi.getProductSummaryList({
        page: (params.current || pagination.current) - 1, // 后端页码从0开始
        size: params.pageSize || pagination.pageSize,
        name: filters.name,
        sort: params.sort && params.sortOrder ? `${params.sort},${params.sortOrder}` : undefined,
      });
      setData(response.data.content || []);
      setPagination({
        ...pagination,
        current: response.data.number + 1, // 前端页码从1开始
        pageSize: response.data.size,
        total: response.data.totalElements,
      });
    } catch (error) {
      console.error('获取产品统计列表失败:', error);
      message.error('获取产品统计列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const handleTableChange = (pagination: TablePaginationConfig, _: Record<string, FilterValue | null>, sorter: SorterResult<any> | SorterResult<any>[]) => {
    if (Array.isArray(sorter)) {
      // 多列排序
      const firstSorter = sorter[0];
      if (firstSorter && firstSorter.field) {
        fetchData({
          current: pagination.current,
          pageSize: pagination.pageSize,
          sort: firstSorter.field as string,
          sortOrder: firstSorter.order === 'ascend' ? 'asc' : 'desc',
        });
      } else {
        fetchData({
          current: pagination.current,
          pageSize: pagination.pageSize,
        });
      }
    } else {
      // 单列排序
      if (sorter && sorter.field) {
        fetchData({
          current: pagination.current,
          pageSize: pagination.pageSize,
          sort: sorter.field as string,
          sortOrder: sorter.order === 'ascend' ? 'asc' : 'desc',
        });
      } else {
        fetchData({
          current: pagination.current,
          pageSize: pagination.pageSize,
        });
      }
    }
  };



  const syncProductSummary = async () => {
    try {
      await productSummaryApi.syncProductSummary();
      message.success('所有产品统计数据同步成功');
      fetchData();
    } catch (error) {
      console.error('同步产品统计数据失败:', error);
      message.error('同步产品统计数据失败');
    }
  };

  const columns = [
    {
      title: '产品ID',
      dataIndex: 'productId',
      key: 'productId',
    },
    {
      title: '产品名称',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '产品类型',
      dataIndex: 'type',
      key: 'type',
    },
    {
      title: '订阅数',
      dataIndex: 'subscriptionCount',
      key: 'subscriptionCount',
      sorter: true,
    },
    {
      title: '使用次数',
      dataIndex: 'usageCount',
      key: 'usageCount',
      sorter: true,
    },
    {
      title: '点赞数',
      dataIndex: 'likesCount',
      key: 'likesCount',
      sorter: true,
    },
  ];

  return (
    <div className="p-6 bg-white">
      <Card title="产品统计列表">
        <Space className="mb-4" wrap>
          <Input
            placeholder="搜索产品名称"
            prefix={<SearchOutlined />}
            style={{ width: 250 }}
            onChange={(e) => setFilters({ ...filters, name: e.target.value })}
            onPressEnter={() => fetchData()}
          />

          <Button type="primary" icon={<SearchOutlined />} onClick={() => fetchData()}>
            搜索
          </Button>
          <Button icon={<SyncOutlined />} onClick={syncProductSummary}>
            同步所有
          </Button>
        </Space>

        <Table
          columns={columns}
          dataSource={data}
          loading={loading}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => `共 ${total} 条`,
          }}
          onChange={handleTableChange}
          rowKey="productId"
        />
      </Card>
    </div>
  );
};

export default ProductSummary;
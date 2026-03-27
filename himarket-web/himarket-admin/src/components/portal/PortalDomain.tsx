import {Card, Button, Table, Modal, Form, Input, Select, message, Space} from 'antd'
import {PlusOutlined, ExclamationCircleOutlined} from '@ant-design/icons'
import {useState} from 'react'
import {Portal} from '@/types'
import {portalApi} from '@/lib/api'

interface PortalDomainProps {
    portal: Portal
    onRefresh?: () => void
}

export function PortalDomain({portal, onRefresh}: PortalDomainProps) {
    const [domainModalVisible, setDomainModalVisible] = useState(false)
    const [domainForm] = Form.useForm()
    const [domainLoading, setDomainLoading] = useState(false)

    const handleAddDomain = () => {
        setDomainModalVisible(true)
    }

    const handleDomainModalOk = async () => {
        try {
            setDomainLoading(true)
            const values = await domainForm.validateFields()
            
            await portalApi.bindDomain(portal.portalId, {
                domain: values.domain,
                type: 'CUSTOM',
                protocol: values.protocol
            })
            
            message.success('域名绑定成功')
            setDomainModalVisible(false)
            domainForm.resetFields()
            onRefresh?.()
        } catch (error) {
            message.error('绑定域名失败')
        } finally {
            setDomainLoading(false)
        }
    }

    const handleDomainModalCancel = () => {
        setDomainModalVisible(false)
        domainForm.resetFields()
    }

    const handleDeleteDomain = async (domain: string) => {
        Modal.confirm({
            title: '确认解绑',
            icon: <ExclamationCircleOutlined/>,
            content: `确定要解绑域名 "${domain}" 吗？此操作不可恢复。`,
            okText: '确认解绑',
            okType: 'danger',
            cancelText: '取消',
            async onOk() {
                try {
                    await portalApi.unbindDomain(portal.portalId, domain)
                    message.success('域名解绑成功')
                    onRefresh?.()
                } catch (error) {
                    message.error('解绑域名失败')
                }
            },
        })
    }

    const domainColumns = [
        {
            title: '域名',
            dataIndex: 'domain',
            key: 'domain',
        },
        {
            title: '协议',
            dataIndex: 'protocol',
            key: 'protocol',
            render: (protocol: string) => protocol?.toUpperCase() || 'HTTP'
        },
        {
            title: '类型',
            dataIndex: 'type',
            key: 'type',
            render: (type: string) => type === 'CUSTOM' ? '自定义域名' : '系统域名'
        },
        {
            title: '操作',
            key: 'action',
            render: (_: any, record: any) => (
                <Space>
                    {record.type === 'CUSTOM' ? (
                        <Button 
                            type="link" 
                            danger 
                            size="small"
                            onClick={() => handleDeleteDomain(record.domain)}
                        >
                            解绑
                        </Button>
                    ) : (
                        <span className="text-gray-400 text-sm">-</span>
                    )}
                </Space>
            ),
        },
    ]

    return (
        <div className="p-6 space-y-6">
            <div className="flex justify-between items-center">
                <div>
                    <h1 className="text-2xl font-bold mb-2">域名列表</h1>
                    <p className="text-gray-600">管理Portal的域名配置</p>
                </div>
                <Space>
                    <Button type="primary" icon={<PlusOutlined/>} onClick={handleAddDomain}>
                        绑定域名
                    </Button>
                </Space>
            </div>

            <Card>
                <div className="space-y-6">
                    {/* 域名列表内容 */}
                    <div>
                        <Table
                            columns={domainColumns}
                            dataSource={portal.portalDomainConfig || []}
                            rowKey="domain"
                            pagination={false}
                            size="small"
                            locale={{
                                emptyText: '暂无绑定域名'
                            }}
                        />
                    </div>
                </div>
            </Card>

            {/* 域名绑定模态框 */}
            <Modal
                title="绑定域名"
                open={domainModalVisible}
                onOk={handleDomainModalOk}
                onCancel={handleDomainModalCancel}
                confirmLoading={domainLoading}
                destroyOnClose
            >
                <Form form={domainForm} layout="vertical" initialValues={{ protocol: 'HTTP' }}>
                    <Form.Item
                        name="domain"
                        label="域名"
                        rules={[{ required: true, message: '请输入要绑定的域名' }]}
                    >
                        <Input placeholder="例如：example.com" />
                    </Form.Item>
                    
                    <Form.Item
                        name="protocol"
                        label="协议"
                        rules={[{ required: true, message: '请选择协议' }]}
                    >
                        <Select placeholder="请选择协议">
                            <Select.Option value="HTTPS">HTTPS</Select.Option>
                            <Select.Option value="HTTP">HTTP</Select.Option>
                        </Select>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    )
}

import {Card, Row, Col, Statistic, Button, message} from 'antd'
import {
    UserOutlined,
    ApiOutlined,
    LinkOutlined,
    CheckCircleFilled,
    MinusCircleFilled,
    EditOutlined,
    CopyOutlined
} from '@ant-design/icons'
import {Portal} from '@/types'
import {useState, useEffect} from 'react'
import {portalApi, apiProductApi} from '@/lib/api'
import {copyToClipboard} from '@/lib/utils'
import {useNavigate} from 'react-router-dom'

interface PortalOverviewProps {
    portal: Portal
    onEdit?: () => void
}

export function PortalOverview({portal, onEdit}: PortalOverviewProps) {
    const navigate = useNavigate()
    const [apiCount, setApiCount] = useState(0)
    const [developerCount, setDeveloperCount] = useState(0)

    useEffect(() => {
        if (!portal.portalId) return;
        
        portalApi.getDeveloperList(portal.portalId, {
            page: 1,
            size: 10
        }).then((res: any) => {
            setDeveloperCount(res.data.totalElements || 0)
        })
        apiProductApi.getApiProducts({
            portalId: portal.portalId,
            page: 1,
            size: 10
        }).then((res: any) => {
            setApiCount(res.data.totalElements || 0)
        })

    }, [portal.portalId]) // 只依赖portalId，而不是整个portal对象

    return (
        <div className="p-6 space-y-6">
            <div>
                <h1 className="text-2xl font-bold mb-2">概览</h1>
                <p className="text-gray-600">Portal概览</p>
            </div>

            {/* 基本信息 */}
            <Card 
                title="基本信息"
                extra={
                    onEdit && (
                        <Button 
                            type="primary" 
                            icon={<EditOutlined />} 
                            onClick={onEdit}
                        >
                            编辑
                        </Button>
                    )
                }
            >
                <div>
                    <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
                        <span className="text-xs text-gray-600">Portal名称:</span>
                        <span className="col-span-2 text-xs text-gray-900">{portal.name}</span>
                        <span className="text-xs text-gray-600">Portal ID:</span>
                        <div className="col-span-2 flex items-center gap-2">
                            <span className="text-xs text-gray-700">{portal.portalId}</span>
                            <CopyOutlined 
                                className="text-gray-400 hover:text-blue-600 cursor-pointer transition-colors ml-1" 
                                style={{ fontSize: '12px' }}
                                onClick={async () => {
                                    try {
                                        await copyToClipboard(portal.portalId);
                                        message.success('Portal ID已复制');
                                    } catch {
                                        message.error('复制失败，请手动复制');
                                    }
                                }}
                            />
                        </div>
                    </div>
                    
                    <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
                        <span className="text-xs text-gray-600">域名:</span>
                        <div className="col-span-2 flex items-center gap-2">
                            <LinkOutlined className="text-blue-500" />
                            <a 
                                href={`http://${portal.portalDomainConfig?.[portal.portalDomainConfig.length - 1]?.domain}`} 
                                target="_blank"
                                rel="noopener noreferrer" 
                                className="text-xs text-blue-600 hover:underline"
                            >
                                {portal.portalDomainConfig?.[portal.portalDomainConfig.length - 1]?.domain}
                            </a>
                        </div>
                        <span className="text-xs text-gray-600">账号密码登录:</span>
                        <div className="col-span-2 flex items-center">
                            {portal.portalSettingConfig?.builtinAuthEnabled ? (
                                <CheckCircleFilled className="text-green-500 mr-2" style={{fontSize: '10px'}} />
                            ) : (
                                <MinusCircleFilled className="text-gray-400 mr-2" style={{fontSize: '10px'}} />
                            )}
                            <span className="text-xs text-gray-900">
                                {portal.portalSettingConfig?.builtinAuthEnabled ? '已启用' : '已停用'}
                            </span>
                        </div>
                    </div>
                    
                    <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
                        <span className="text-xs text-gray-600">开发者自动审批:</span>
                        <div className="col-span-2 flex items-center">
                            {portal.portalSettingConfig?.autoApproveDevelopers ? (
                                <CheckCircleFilled className="text-green-500 mr-2" style={{fontSize: '10px'}} />
                            ) : (
                                <MinusCircleFilled className="text-gray-400 mr-2" style={{fontSize: '10px'}} />
                            )}
                            <span className="text-xs text-gray-900">
                                {portal.portalSettingConfig?.autoApproveDevelopers ? '已启用' : '已停用'}
                            </span>
                        </div>
                        <span className="text-xs text-gray-600">订阅自动审批:</span>
                        <div className="col-span-2 flex items-center">
                            {portal.portalSettingConfig?.autoApproveSubscriptions ? (
                                <CheckCircleFilled className="text-green-500 mr-2" style={{fontSize: '10px'}} />
                            ) : (
                                <MinusCircleFilled className="text-gray-400 mr-2" style={{fontSize: '10px'}} />
                            )}
                            <span className="text-xs text-gray-900">
                                {portal.portalSettingConfig?.autoApproveSubscriptions ? '已启用' : '已停用'}
                            </span>
                        </div>
                    </div>

                    <div className="grid grid-cols-6 gap-8 items-start pt-2 pb-2">
                        <span className="text-xs text-gray-600">描述:</span>
                        <span className="col-span-5 text-xs text-gray-900 leading-relaxed">
                            {portal.description || '-'}
                        </span>
                    </div>
                </div>
            </Card>

            {/* 统计数据 */}
            <Row gutter={[16, 16]}>
                <Col xs={24} sm={12} lg={12}>
                    <Card 
                        className="cursor-pointer hover:shadow-md transition-shadow"
                        onClick={() => {
                            navigate(`/portals/detail?id=${portal.portalId}&tab=developers`)
                        }}
                    >
                        <Statistic
                            title="注册开发者"
                            value={developerCount}
                            prefix={<UserOutlined className="text-blue-500" />}
                            valueStyle={{ color: '#1677ff', fontSize: '24px' }}
                        />
                    </Card>
                </Col>
                <Col xs={24} sm={12} lg={12}>
                    <Card 
                        className="cursor-pointer hover:shadow-md transition-shadow"
                        onClick={() => {
                            navigate(`/portals/detail?id=${portal.portalId}&tab=published-apis`)
                        }}
                    >
                        <Statistic
                            title="已发布的API"
                            value={apiCount}
                            prefix={<ApiOutlined className="text-blue-500" />}
                            valueStyle={{ color: '#1677ff', fontSize: '24px' }}
                        />
                    </Card>
                </Col>
            </Row>
        </div>
    )
} 
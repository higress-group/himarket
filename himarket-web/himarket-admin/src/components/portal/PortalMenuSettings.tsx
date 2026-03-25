import {Card, Form, Switch, message} from 'antd'
import {Portal} from '@/types'
import {portalApi} from '@/lib/api'

interface PortalMenuSettingsProps {
    portal: Portal
    onRefresh?: () => void
}

const MENU_ITEMS = [
    {key: "chat", label: "HiChat"},
    {key: "coding", label: "HiCoding"},
    {key: "agents", label: "智能体"},
    {key: "mcp", label: "MCP"},
    {key: "models", label: "模型"},
    {key: "apis", label: "API"},
    {key: "skills", label: "Skills"},
]

export function PortalMenuSettings({portal, onRefresh}: PortalMenuSettingsProps) {
    const [form] = Form.useForm()

    const getMenuVisibility = (key: string): boolean => {
        return portal.portalUiConfig?.menuVisibility?.[key] ?? true
    }

    const handleToggle = async (key: string, checked: boolean) => {
        const currentVisibility = {...(portal.portalUiConfig?.menuVisibility || {})}
        const newVisibility = {...currentVisibility, [key]: checked}

        // 至少保留一个菜单项可见
        const visibleCount = MENU_ITEMS.filter(
            item => newVisibility[item.key] ?? true
        ).length
        if (visibleCount === 0) {
            message.warning('至少保留一个菜单项为可见状态')
            // 恢复 form 中的值
            form.setFieldValue(key, true)
            return
        }

        try {
            await portalApi.updatePortal(portal.portalId, {
                name: portal.name,
                description: portal.description,
                portalSettingConfig: portal.portalSettingConfig,
                portalDomainConfig: portal.portalDomainConfig,
                portalUiConfig: {
                    ...portal.portalUiConfig,
                    menuVisibility: newVisibility,
                },
            })
            message.success('菜单配置保存成功')
            onRefresh?.()
        } catch {
            message.error('保存菜单配置失败')
            // 恢复 form 中的值
            form.setFieldValue(key, !checked)
        }
    }

    const initialValues = MENU_ITEMS.reduce((acc, item) => {
        acc[item.key] = getMenuVisibility(item.key)
        return acc
    }, {} as Record<string, boolean>)

    return (
        <div className="p-6 space-y-6">
            <div>
                <h1 className="text-2xl font-bold mb-2">菜单管理</h1>
                <p className="text-gray-600">控制开发者门户导航栏的菜单项显隐</p>
            </div>

            <Form
                form={form}
                layout="vertical"
                initialValues={initialValues}
            >
                <Card>
                    <div className="space-y-6">
                        <h3 className="text-lg font-medium">导航菜单项</h3>
                        <div className="grid grid-cols-2 gap-6">
                            {MENU_ITEMS.map(item => (
                                <Form.Item
                                    key={item.key}
                                    name={item.key}
                                    label={item.label}
                                    valuePropName="checked"
                                >
                                    <Switch
                                        onChange={(checked) => handleToggle(item.key, checked)}
                                    />
                                </Form.Item>
                            ))}
                        </div>
                    </div>
                </Card>
            </Form>
        </div>
    )
}

import { Modal, Form, Input, message } from 'antd'
import { useEffect } from 'react'
import { portalApi } from '@/lib/api'
import { Portal } from '@/types'

interface PortalFormModalProps {
  visible: boolean
  onCancel: () => void
  onSuccess: () => void
  portal: Portal | null
}

export default function PortalFormModal({
  visible,
  onCancel,
  onSuccess,
  portal,
}: PortalFormModalProps) {
  const [form] = Form.useForm()

  useEffect(() => {
    if (visible && portal) {
      form.setFieldsValue({
        name: portal.name,
        description: portal.description || '',
      })
    }
  }, [visible, portal, form])

  const handleOk = async () => {
    try {
      const values = await form.validateFields()
      if (!portal) return

      await portalApi.updatePortal(portal.portalId, {
        name: values.name,
        description: values.description,
        portalSettingConfig: portal.portalSettingConfig,
        portalDomainConfig: portal.portalDomainConfig,
        portalUiConfig: portal.portalUiConfig,
      })

      message.success('Portal信息更新成功')
      form.resetFields()
      onSuccess()
    } catch (error) {
      message.error('更新失败，请稍后重试')
    }
  }

  const handleCancel = () => {
    form.resetFields()
    onCancel()
  }

  return (
    <Modal
      title="编辑Portal"
      open={visible}
      onOk={handleOk}
      onCancel={handleCancel}
      okText="保存"
      cancelText="取消"
      width={600}
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="name"
          label="Portal名称"
          rules={[{ required: true, message: "请输入Portal名称" }]}
        >
          <Input placeholder="请输入Portal名称" />
        </Form.Item>

        <Form.Item
          name="description"
          label="描述"
        >
          <Input.TextArea rows={3} placeholder="请输入Portal描述" />
        </Form.Item>
      </Form>
    </Modal>
  )
}

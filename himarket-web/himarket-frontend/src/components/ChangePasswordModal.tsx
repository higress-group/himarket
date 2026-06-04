import {
  CheckCircleFilled,
  KeyOutlined,
  LockOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import { Form, Input, Modal } from 'antd';
import { useTranslation } from 'react-i18next';

interface ChangePasswordFormValues {
  confirmPassword: string;
  newPassword: string;
  oldPassword: string;
}

interface ChangePasswordModalProps {
  loading?: boolean;
  onCancel: () => void;
  onSubmit: (values: { newPassword: string; oldPassword: string }) => Promise<void> | void;
  open: boolean;
}

interface PasswordRequirement {
  label: string;
  passed: boolean;
}

function PasswordRequirementItem({ label, passed }: PasswordRequirement) {
  return (
    <div
      className={`flex items-center gap-1.5 text-xs ${passed ? 'text-emerald-600' : 'text-gray-400'}`}
    >
      {passed ? (
        <CheckCircleFilled className="text-[13px]" />
      ) : (
        <span className="h-[13px] w-[13px] rounded-full border border-gray-300" />
      )}
      <span>{label}</span>
    </div>
  );
}

export function ChangePasswordModal({
  loading = false,
  onCancel,
  onSubmit,
  open,
}: ChangePasswordModalProps) {
  const { t } = useTranslation(['profile', 'common']);
  const [form] = Form.useForm<ChangePasswordFormValues>();
  const newPassword = Form.useWatch('newPassword', form);
  const confirmPassword = Form.useWatch('confirmPassword', form);
  const passwordRequirements: PasswordRequirement[] = [
    {
      label: t('profile:newPasswordPlaceholder'),
      passed: !!newPassword && newPassword.length >= 6 && newPassword.length <= 32,
    },
    {
      label: t('profile:passwordRequirementMatch'),
      passed: !!confirmPassword && newPassword === confirmPassword,
    },
  ];

  const handleCancel = () => {
    form.resetFields();
    onCancel();
  };

  const handleFinish = async (values: ChangePasswordFormValues) => {
    await onSubmit({
      newPassword: values.newPassword,
      oldPassword: values.oldPassword,
    });
    form.resetFields();
  };

  return (
    <Modal
      cancelText={t('common:cancel')}
      centered
      className="[&_.ant-modal-content]:!rounded-[10px] [&_.ant-modal-content]:!p-6 [&_.ant-modal-header]:!mb-2"
      confirmLoading={loading}
      destroyOnHidden
      okText={t('profile:savePassword')}
      onCancel={handleCancel}
      onOk={() => form.submit()}
      open={open}
      title={t('profile:changePassword')}
      width={460}
    >
      <Form
        className="[&_.ant-form-item:last-child]:!mb-0 [&_.ant-form-item]:!mb-3"
        form={form}
        layout="vertical"
        onFinish={handleFinish}
        requiredMark={false}
        size="large"
      >
        <Form.Item
          name="oldPassword"
          rules={[{ message: t('profile:currentPasswordRequired'), required: true }]}
        >
          <Input.Password
            autoComplete="current-password"
            className="rounded-lg"
            placeholder={t('profile:currentPassword')}
            prefix={<LockOutlined className="text-gray-400" />}
          />
        </Form.Item>

        <Form.Item
          name="newPassword"
          rules={[
            { message: t('profile:newPasswordRequired'), required: true },
            { message: t('profile:passwordMinLength'), min: 6 },
            { max: 32, message: t('profile:passwordMaxLength') },
          ]}
        >
          <Input.Password
            autoComplete="new-password"
            className="rounded-lg"
            placeholder={t('profile:newPassword')}
            prefix={<KeyOutlined className="text-gray-400" />}
          />
        </Form.Item>

        <Form.Item
          dependencies={['newPassword']}
          name="confirmPassword"
          rules={[
            { message: t('profile:confirmPasswordRequired'), required: true },
            ({ getFieldValue }) => ({
              validator(_, value: string | undefined) {
                if (!value || getFieldValue('newPassword') === value) {
                  return Promise.resolve();
                }
                return Promise.reject(new Error(t('profile:passwordMismatch')));
              },
            }),
          ]}
        >
          <Input.Password
            autoComplete="new-password"
            className="rounded-lg"
            placeholder={t('profile:confirmPassword')}
            prefix={<SafetyCertificateOutlined className="text-gray-400" />}
          />
        </Form.Item>

        <div className="grid gap-1.5 pb-1">
          {passwordRequirements.map((item) => (
            <PasswordRequirementItem key={item.label} label={item.label} passed={item.passed} />
          ))}
        </div>
      </Form>
    </Modal>
  );
}

import { KeyOutlined, LockOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { Button, Form, Input } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

interface ChangePasswordFormValues {
  confirmPassword: string;
  newPassword: string;
  oldPassword: string;
}

interface ChangePasswordFormProps {
  loading?: boolean;
  onSubmit: (values: { newPassword: string; oldPassword: string }) => Promise<void> | void;
}

export function ChangePasswordForm({ loading = false, onSubmit }: ChangePasswordFormProps) {
  const { t } = useTranslation('profile');
  const [form] = Form.useForm<ChangePasswordFormValues>();
  const [editing, setEditing] = useState(false);
  const oldPassword = Form.useWatch('oldPassword', form);
  const newPassword = Form.useWatch('newPassword', form);
  const confirmPassword = Form.useWatch('confirmPassword', form);

  const canSubmit =
    !!oldPassword &&
    !!newPassword &&
    !!confirmPassword &&
    newPassword.length >= 6 &&
    newPassword.length <= 32 &&
    newPassword === confirmPassword;

  const handleFinish = async (values: ChangePasswordFormValues) => {
    await onSubmit({
      newPassword: values.newPassword,
      oldPassword: values.oldPassword,
    });
    form.resetFields();
    setEditing(false);
  };

  const handleCancel = () => {
    form.resetFields();
    setEditing(false);
  };

  return (
    <section className="mt-4">
      <div className="flex min-h-16 flex-col gap-3 border-b border-gray-100 py-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="text-sm font-medium text-gray-800">{t('passwordPanelTitle')}</div>
        </div>
        {!editing && (
          <Button
            className="w-fit transition-transform active:scale-[0.98]"
            onClick={() => setEditing(true)}
            type="primary"
          >
            {t('changePassword')}
          </Button>
        )}
      </div>

      {editing && (
        <div className="py-5">
          <Form
            className="mx-auto max-w-[420px] [&_.ant-form-item]:!mb-3"
            form={form}
            onFinish={handleFinish}
            requiredMark={false}
          >
            <Form.Item
              name="oldPassword"
              rules={[{ message: t('currentPasswordRequired'), required: true }]}
            >
              <Input.Password
                autoComplete="current-password"
                className="h-9 rounded-lg"
                placeholder={t('currentPassword')}
                prefix={<LockOutlined className="text-gray-400" />}
              />
            </Form.Item>

            <Form.Item
              name="newPassword"
              rules={[
                { message: t('newPasswordRequired'), required: true },
                { message: t('passwordMinLength'), min: 6 },
                { max: 32, message: t('passwordMaxLength') },
              ]}
            >
              <Input.Password
                autoComplete="new-password"
                className="h-9 rounded-lg"
                placeholder={t('newPassword')}
                prefix={<KeyOutlined className="text-gray-400" />}
              />
            </Form.Item>

            <Form.Item
              className="!mb-1"
              dependencies={['newPassword']}
              name="confirmPassword"
              rules={[
                { message: t('confirmPasswordRequired'), required: true },
                ({ getFieldValue }) => ({
                  validator(_, value: string | undefined) {
                    if (!value || getFieldValue('newPassword') === value) {
                      return Promise.resolve();
                    }
                    return Promise.reject(new Error(t('passwordMismatch')));
                  },
                }),
              ]}
            >
              <Input.Password
                autoComplete="new-password"
                className="h-9 rounded-lg"
                placeholder={t('confirmPassword')}
                prefix={<SafetyCertificateOutlined className="text-gray-400" />}
              />
            </Form.Item>
            <div className="mb-3 text-right text-xs text-gray-400">{t('passwordReloginHint')}</div>

            <div className="flex justify-center gap-3 pt-2">
              <Button
                className="w-16 px-0 transition-transform active:scale-[0.98]"
                disabled={!canSubmit || loading}
                htmlType="submit"
                loading={loading}
                type="primary"
              >
                {t('savePassword')}
              </Button>
              <Button className="w-16 px-0" disabled={loading} onClick={handleCancel}>
                {t('cancelChangePassword')}
              </Button>
            </div>
          </Form>
        </div>
      )}
    </section>
  );
}

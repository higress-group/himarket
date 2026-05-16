import { LockOutlined, LoginOutlined, UserAddOutlined } from '@ant-design/icons';
import { Button, Modal } from 'antd';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';

interface LoginPromptProps {
  open: boolean;
  onClose: () => void;
  contextMessage: string;
  returnUrl?: string;
}

export function LoginPrompt({ contextMessage, onClose, open, returnUrl }: LoginPromptProps) {
  const navigate = useNavigate();
  const { t } = useTranslation('loginPrompt');

  const handleLogin = () => {
    const url = returnUrl || window.location.pathname + window.location.search;
    navigate(`/login?returnUrl=${encodeURIComponent(url)}`);
    onClose();
  };

  const handleRegister = () => {
    navigate('/register');
    onClose();
  };

  return (
    <Modal
      centered
      className="[&_.ant-modal-close]:!right-4 [&_.ant-modal-close]:!top-4 [&_.ant-modal-content]:!rounded-2xl [&_.ant-modal-content]:!p-6"
      destroyOnClose
      footer={null}
      onCancel={onClose}
      open={open}
      width={390}
    >
      <div className="pb-1 pt-2 text-center">
        <div className="mx-auto mb-4 flex h-11 w-11 items-center justify-center rounded-xl border border-indigo-100 bg-indigo-50 text-indigo-600">
          <LockOutlined className="text-lg" />
        </div>
        <p className="mx-auto mb-6 max-w-[300px] text-base font-semibold leading-6 text-gray-500">
          {contextMessage}
        </p>
        <div className="flex flex-col gap-2.5">
          <Button
            block
            className="!h-10 !rounded-lg !border-indigo-600 !bg-indigo-600 !font-semibold shadow-sm shadow-indigo-200 hover:!border-indigo-500 hover:!bg-indigo-500"
            icon={<LoginOutlined />}
            onClick={handleLogin}
            size="large"
            type="primary"
          >
            {t('login')}
          </Button>
          <Button
            block
            className="!h-10 !rounded-lg !border-gray-200 !bg-white !font-medium !text-gray-700 hover:!border-indigo-200 hover:!text-indigo-600"
            icon={<UserAddOutlined />}
            onClick={handleRegister}
            size="large"
          >
            {t('registerNewAccount')}
          </Button>
        </div>
      </div>
    </Modal>
  );
}

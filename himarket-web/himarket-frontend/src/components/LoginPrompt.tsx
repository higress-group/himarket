import { Modal, Button } from 'antd';
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
    <Modal centered destroyOnClose footer={null} onCancel={onClose} open={open} width={420}>
      <div className="text-center py-4">
        <div className="text-2xl font-semibold mb-3">{t('title')}</div>
        <p className="text-gray-500 mb-6 text-sm leading-relaxed">{contextMessage}</p>
        <div className="flex flex-col gap-3">
          <Button block onClick={handleLogin} size="large" type="primary">
            {t('login')}
          </Button>
          <Button block onClick={handleRegister} size="large">
            {t('registerNewAccount')}
          </Button>
        </div>
      </div>
    </Modal>
  );
}

import { Modal, Button } from "antd";
import { useNavigate } from "react-router-dom";

interface LoginPromptProps {
  open: boolean;
  onClose: () => void;
  contextMessage: string;
  returnUrl?: string;
}

export function LoginPrompt({
  open,
  onClose,
  contextMessage,
  returnUrl,
}: LoginPromptProps) {
  const navigate = useNavigate();

  const handleLogin = () => {
    const url = returnUrl || window.location.pathname + window.location.search;
    navigate(`/login?returnUrl=${encodeURIComponent(url)}`);
    onClose();
  };

  const handleRegister = () => {
    navigate("/register");
    onClose();
  };

  return (
    <Modal open={open} onCancel={onClose} footer={null} centered width={420} destroyOnClose>
      <div className="text-center py-4">
        <div className="text-2xl font-semibold mb-3">登录以继续</div>
        <p className="text-gray-500 mb-6 text-sm leading-relaxed">
          {contextMessage}
        </p>
        <div className="flex flex-col gap-3">
          <Button type="primary" size="large" block onClick={handleLogin}>
            登录
          </Button>
          <Button size="large" block onClick={handleRegister}>
            注册新账号
          </Button>
        </div>
      </div>
    </Modal>
  );
}

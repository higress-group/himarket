import { Card, Typography, Steps, Space, Alert } from "antd";
import { UserOutlined, ApiOutlined, RocketOutlined } from "@ant-design/icons";
// import { Link } from "react-router-dom";
import { useTranslation } from 'react-i18next';
import { Layout } from "../components/Layout";

const { Title, Paragraph } = Typography;

function GettingStartedPage() {
  const { t } = useTranslation('gettingStarted');
  const steps = [
    {
      title: t('steps.registerAccount.title'),
      description: t('steps.registerAccount.description'),
      icon: <UserOutlined />,
      content: t('steps.registerAccount.content')
    },
    {
      title: t('steps.browseApi.title'),
      description: t('steps.browseApi.description'),
      icon: <ApiOutlined />,
      content: t('steps.browseApi.content')
    },
    {
      title: t('steps.startIntegration.title'),
      description: t('steps.startIntegration.description'),
      icon: <RocketOutlined />,
      content: t('steps.startIntegration.content')
    }
  ];

  return (
    <Layout>
      <div className="text-center mb-12">
        <Title level={1} className="mb-4">
          {t('quickStart')}
        </Title>
        <Paragraph className="text-xl text-gray-600 max-w-2xl mx-auto">
          {t('quickStartDesc')}
        </Paragraph>
      </div>

      <Card className="mb-8">
        <Steps
          current={0}
          items={steps.map((step) => ({
            title: step.title,
            description: step.description,
            icon: step.icon,
            content: (
              <div className="mt-4 p-4 bg-gray-50 rounded">
                <Paragraph>{step.content}</Paragraph>
              </div>
            )
          }))}
        />
      </Card>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-8 mb-8">
        <Card title={t('developerDocs')} 
          // extra={<Link to="/apis"><Button type="link">查看</Button></Link>}
          >
          <Paragraph>
            {t('developerDocsDesc')}
          </Paragraph>
          <Space>
            {/* <Button type="primary" icon={<ApiOutlined />}>
              浏览API
            </Button> */}
          </Space>
        </Card>

        <Card title={t('sdkAndTools')} 
          // extra={<Button type="link">下载</Button>}
        >
          <Paragraph>
            {t('sdkAndToolsDesc')}
          </Paragraph>
          <Space>
            {/* <Button type="default" icon={<RocketOutlined />}>
              下载SDK
            </Button> */}
          </Space>
        </Card>
      </div>

      <Alert
        message={t('needHelp')}
        description={t('needHelpDesc')}
        type="info"
        showIcon
        // action={
        //   <Button size="small" type="link">
        //     联系支持
        //   </Button>
        // }
      />
    </Layout>
  );
}

export default GettingStartedPage; 
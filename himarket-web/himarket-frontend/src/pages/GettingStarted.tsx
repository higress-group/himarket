import { UserOutlined, ApiOutlined, RocketOutlined } from '@ant-design/icons';
import { Card, Typography, Steps, Space, Alert } from 'antd';
// import { Link } from "react-router-dom";
import { useTranslation } from 'react-i18next';

import { Layout } from '../components/Layout';

const { Paragraph, Title } = Typography;

function GettingStartedPage() {
  const { t } = useTranslation('gettingStarted');
  const steps = [
    {
      content: t('steps.registerAccount.content'),
      description: t('steps.registerAccount.description'),
      icon: <UserOutlined />,
      title: t('steps.registerAccount.title'),
    },
    {
      content: t('steps.browseApi.content'),
      description: t('steps.browseApi.description'),
      icon: <ApiOutlined />,
      title: t('steps.browseApi.title'),
    },
    {
      content: t('steps.startIntegration.content'),
      description: t('steps.startIntegration.description'),
      icon: <RocketOutlined />,
      title: t('steps.startIntegration.title'),
    },
  ];

  return (
    <Layout>
      <div className="text-center mb-12">
        <Title className="mb-4" level={1}>
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
            content: (
              <div className="mt-4 p-4 bg-gray-50 rounded">
                <Paragraph>{step.content}</Paragraph>
              </div>
            ),
            description: step.description,
            icon: step.icon,
            title: step.title,
          }))}
        />
      </Card>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-8 mb-8">
        <Card
          title={t('developerDocs')}
          // extra={<Link to="/apis"><Button type="link">查看</Button></Link>}
        >
          <Paragraph>{t('developerDocsDesc')}</Paragraph>
          <Space>
            {/* <Button type="primary" icon={<ApiOutlined />}>
              浏览API
            </Button> */}
          </Space>
        </Card>

        <Card
          title={t('sdkAndTools')}
          // extra={<Button type="link">下载</Button>}
        >
          <Paragraph>{t('sdkAndToolsDesc')}</Paragraph>
          <Space>
            {/* <Button type="default" icon={<RocketOutlined />}>
              下载SDK
            </Button> */}
          </Space>
        </Card>
      </div>

      <Alert
        description={t('needHelpDesc')}
        message={t('needHelp')}
        showIcon
        type="info"
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

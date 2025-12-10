import { Card, Typography, Steps, Space, Alert } from "antd";
import { UserOutlined, ApiOutlined, RocketOutlined } from "@ant-design/icons";
// import { Link } from "react-router-dom";
import { Layout } from "../components/Layout";

const { Title, Paragraph } = Typography;

function GettingStartedPage() {
  const steps = [
    {
      title: '注册账户',
      description: '创建您的开发者账户',
      icon: <UserOutlined />,
      content: '填写基本信息，验证邮箱，完成账户注册。'
    },
    {
      title: '浏览API',
      description: '探索可用的API产品',
      icon: <ApiOutlined />,
      content: '查看API文档，了解功能特性，选择合适的API。'
    },
    {
      title: '开始集成',
      description: '获取消费者密钥并开始使用',
      icon: <RocketOutlined />,
      content: '申请消费者密钥，按照文档进行集成开发。'
    }
  ];

  return (
    <Layout>
      <div className="text-center mb-12">
        <Title level={1} className="mb-4">
          快速开始
        </Title>
        <Paragraph className="text-xl text-gray-600 max-w-2xl mx-auto">
          在几分钟内开始使用我们的API产品，构建强大的应用程序
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
        <Card title="开发者文档" 
          // extra={<Link to="/apis"><Button type="link">查看</Button></Link>}
          >
          <Paragraph>
            详细的API文档，包含所有端点的说明、参数和响应示例。
          </Paragraph>
          <Space>
            {/* <Button type="primary" icon={<ApiOutlined />}>
              浏览API
            </Button> */}
          </Space>
        </Card>

        <Card title="SDK和工具" 
          // extra={<Button type="link">下载</Button>}
        >
          <Paragraph>
            提供多种编程语言的SDK，简化API集成过程。
          </Paragraph>
          <Space>
            {/* <Button type="default" icon={<RocketOutlined />}>
              下载SDK
            </Button> */}
          </Space>
        </Card>
      </div>

      <Alert
        message="需要帮助？"
        description="我们的技术支持团队随时为您提供帮助，确保您能够顺利集成和使用我们的API产品。"
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
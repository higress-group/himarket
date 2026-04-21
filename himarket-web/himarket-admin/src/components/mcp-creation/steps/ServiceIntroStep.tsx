import { Form, Input, Tag } from 'antd';

/**
 * ServiceIntroStep — Markdown 格式的服务文档编辑步骤。
 *
 * 渲染在父级 Form 内部，使用 Form.useFormInstance() 获取表单实例。
 * 无必填字段。
 */
export default function ServiceIntroStep() {
  return (
    <>
      <div className="flex items-center justify-between mb-3">
        <div>
          <div className="text-sm font-medium text-gray-700">服务详情文档</div>
          <div className="text-xs text-gray-400 mt-0.5">
            使用 Markdown 语法编写，发布后将渲染为富文本
          </div>
        </div>
        <Tag className="m-0 border-0" color="blue">
          Markdown
        </Tag>
      </div>

      <Form.Item className="mb-0" name="serviceIntro">
        <Input.TextArea
          autoSize={{ maxRows: 22, minRows: 16 }}
          className="font-mono text-xs"
          placeholder={`# 服务介绍\n\n简要描述你的 MCP Server...\n\n## 功能特性\n\n- 特性一\n- 特性二\n\n## 使用方式\n\n\`\`\`bash\nnpx -y @your/mcp-server\n\`\`\`\n\n## 注意事项\n\n> 请确保已配置必要的环境变量`}
        />
      </Form.Item>
    </>
  );
}

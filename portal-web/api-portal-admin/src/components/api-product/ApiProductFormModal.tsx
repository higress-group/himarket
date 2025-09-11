import { useState, useEffect } from "react";
import {
  Modal,
  Form,
  Input,
  Select,
  Upload,
  Image,
  message,
  UploadFile,
  UploadProps,
} from "antd";
import { PlusOutlined } from "@ant-design/icons";
import { apiProductApi } from "@/lib/api";
import type { ApiProduct } from "@/types/api-product";

interface ApiProductFormModalProps {
  visible: boolean;
  onCancel: () => void;
  onSuccess: () => void;
  productId?: string;
  initialData?: Partial<ApiProduct>;
}

export default function ApiProductFormModal({
  visible,
  onCancel,
  onSuccess,
  productId,
  initialData,
}: ApiProductFormModalProps) {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewImage, setPreviewImage] = useState("");
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const isEditMode = !!productId;

  // 初始化时加载已有数据
  useEffect(() => {
    if (visible && isEditMode && initialData && initialData.name) {
      setTimeout(() => {
        // 1. 先设置所有字段（name、description、type）
        form.setFieldsValue({
          name: initialData.name,
          description: initialData.description,
          type: initialData.type,
        });
      }, 100);

      // 2. 再单独处理 icon 字段
      if (initialData.icon) {
        const startIndex = initialData.icon.indexOf("value=") + 6;
        const endIndex = initialData.icon.length - 1;
        const base64Data = initialData.icon
          .substring(startIndex, endIndex)
          .trim();

        setFileList([
          {
            uid: "-1",
            name: "头像.png",
            status: "done",
            url: base64Data,
          },
        ]);
        form.setFieldsValue({ icon: base64Data }); // ✅ 仅更新 icon 字段
      }
    } else if (visible && !isEditMode) {
      // 新建模式下清空表单
      form.resetFields();
      setFileList([]);
    }
  }, [visible, isEditMode, initialData, form]);

  // 将文件转为 Base64
  const getBase64 = (file: File): Promise<string> =>
    new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.readAsDataURL(file);
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = (error) => reject(error);
    });

  // 预览图片
  const handlePreview = async (file: UploadFile) => {
    if (!file.url && !file.preview) {
      file.preview = await getBase64(file.originFileObj as File);
    }
    setPreviewImage(file.url || (file.preview as string));
    setPreviewOpen(true);
  };

  // 上传变化时更新 fileList 和表单字段
  const handleChange: UploadProps["onChange"] = ({ fileList }) => {
    if (fileList.length > 0) {
      const newFileList = [fileList[fileList.length - 1]]; // 仅保留最新文件
      setFileList(newFileList);
      if (newFileList[0].originFileObj) {
        getBase64(newFileList[0].originFileObj as File).then((base64) => {
          form.setFieldsValue({ icon: base64 }); // ✅ 设置到表单字段
        });
      }
    } else {
      setFileList([]);
      form.setFieldsValue({ icon: null }); // ✅ 清空表单字段
    }
  };

  const uploadButton = (
    <div>
      <PlusOutlined />
      <div style={{ marginTop: 8 }}>上传</div>
    </div>
  );

  const resetForm = () => {
    form.resetFields();
    setFileList([]);
    setPreviewImage("");
    setPreviewOpen(false);
  };

  const handleCancel = () => {
    resetForm();
    onCancel();
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);

      const { icon, ...otherValues } = values;

      if (isEditMode) {
        let params = { ...otherValues };
        if (!icon) {
          // 如果没有上传新头像，保持原有头像不变
          delete params.icon;
        } else {
          params.icon = {
            type: "BASE64",
            value: icon,
          };
        }
        await apiProductApi.updateApiProduct(productId!, params);
        message.success("API Product 更新成功");
      } else {
        let params = { ...otherValues };
        if (icon) {
          params.icon = {
            type: "BASE64",
            value: icon,
          };
        }
        await apiProductApi.createApiProduct(params);
        message.success("API Product 创建成功");
      }

      resetForm();
      onSuccess();
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error("操作失败");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      title={isEditMode ? "编辑 API Product" : "创建 API Product"}
      open={visible}
      onOk={handleSubmit}
      onCancel={handleCancel}
      confirmLoading={loading}
      width={600}
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item
          label="名称"
          name="name"
          rules={[{ required: true, message: "请输入API Product名称" }]}
        >
          <Input placeholder="请输入API Product名称" />
        </Form.Item>

        <Form.Item
          label="描述"
          name="description"
          rules={[{ required: true, message: "请输入描述" }]}
        >
          <Input.TextArea placeholder="请输入描述" rows={3} />
        </Form.Item>

        <Form.Item
          label="类型"
          name="type"
          rules={[{ required: true, message: "请选择类型" }]}
        >
          <Select placeholder="请选择类型">
            <Select.Option value="REST_API">REST API</Select.Option>
            <Select.Option value="MCP_SERVER">MCP Server</Select.Option>
            <Select.Option value="MODEL_API">Model API</Select.Option>
          </Select>
        </Form.Item>

        <Form.Item
          label="自动审批订阅"
          name="autoApprove"
          tooltip="启用后，该产品的订阅申请将自动审批通过，无需管理员手动审批。留空则使用平台级别的自动审批设置。"
        >
          <Select placeholder="请选择自动审批设置" allowClear>
            <Select.Option value={true}>启用</Select.Option>
            <Select.Option value={false}>禁用</Select.Option>
          </Select>
        </Form.Item>

        <Form.Item label="上传头像" name="icon">
          <Upload
            listType="picture-card"
            beforeUpload={() => false} // ✅ 关键：阻止自动上传
            fileList={fileList}
            onPreview={handlePreview}
            onChange={handleChange}
            maxCount={1}
          >
            {fileList.length >= 1 ? null : uploadButton}
          </Upload>
        </Form.Item>

        {/* 图片预览弹窗 */}
        {previewImage && (
          <Image
            wrapperStyle={{ display: "none" }}
            preview={{
              visible: previewOpen,
              onVisibleChange: (visible) => setPreviewOpen(visible),
              afterOpenChange: (visible) => {
                if (!visible) setPreviewImage("");
              },
            }}
            src={previewImage}
          />
        )}
      </Form>
    </Modal>
  );
}

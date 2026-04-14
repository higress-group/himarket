import { useRef, useState } from "react";
import { Modal, Form, Input, Select, Button, Upload, message } from "antd";
import { InboxOutlined } from "@ant-design/icons";
import { useTranslation } from "react-i18next";
import { createDeveloperSkill, uploadDeveloperSkillPackage } from "../../lib/apis/developerSkill";

/**
 * 从 ZIP 文件中解析 SKILL.md 的 YAML front matter，提取 name 字段。
 */
async function extractNameFromZip(file: File): Promise<string | null> {
  try {
    const JSZip = (await import("jszip")).default;
    const zip = await JSZip.loadAsync(file);
    // 查找 SKILL.md（可能在根目录或子目录）
    const skillMdEntry = Object.keys(zip.files).find((path) => {
      const parts = path.split("/");
      return parts[parts.length - 1] === "SKILL.md";
    });
    if (!skillMdEntry) return null;
    const content = await zip.files[skillMdEntry].async("string");
    return parseNameFromFrontMatter(content);
  } catch {
    return null;
  }
}

/**
 * 解析 YAML front matter 中的 name 字段。
 * front matter 格式：---\nkey: value\n---\n
 */
function parseNameFromFrontMatter(content: string): string | null {
  if (!content) return null;
  const trimmed = content.trimStart();
  if (!trimmed.startsWith("---")) return null;
  const end = trimmed.indexOf("---", 3);
  if (end < 0) return null;
  const frontMatter = trimmed.substring(3, end);
  for (const line of frontMatter.split("\n")) {
    const trimmedLine = line.trim();
    if (trimmedLine.startsWith("name:")) {
      let value = trimmedLine.substring("name:".length).trim();
      if (
        (value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'"))
      ) {
        value = value.substring(1, value.length - 1);
      }
      return value;
    }
  }
  return null;
}

interface Props {
  open: boolean;
  onClose: () => void;
  onCreated: () => void;
}

export function CreateSkillModal({ open, onClose, onCreated }: Props) {
  const { t } = useTranslation("square");
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [uploadStatus, setUploadStatus] = useState<string>("");
  const fileRef = useRef<File | null>(null);

  const handleFileChange = async (file: File) => {
    fileRef.current = file;
    // 读取 SKILL.md 中的 name，自动填充到表单
    const skillMdName = await extractNameFromZip(file);
    if (skillMdName) {
      const currentName = form.getFieldValue("name");
      if (currentName && currentName !== skillMdName) {
        message.info(t("nameOverridden", "已使用压缩包中 SKILL.md 的名称: {{name}}", { name: skillMdName }));
      }
      form.setFieldsValue({ name: skillMdName });
    }
    return false; // 阻止自动上传
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);

      // 如果有 ZIP 文件，再次校验 SKILL.md name 与表单 name 是否一致
      if (fileRef.current) {
        const skillMdName = await extractNameFromZip(fileRef.current);
        if (skillMdName && skillMdName !== values.name) {
          message.error(t("nameMismatch", "压缩包中 SKILL.md 的名称({{skillMdName}})与表单名称({{formName}})不一致", { skillMdName, formName: values.name }));
          setLoading(false);
          return;
        }
      }

      // 1. 创建 Skill
      const resp = await createDeveloperSkill({
        name: values.name,
        description: values.description,
        tags: values.tags ?? [],
      });

      if (resp.code !== "SUCCESS") return;

      const productId = resp.data.productId;

      // 2. 如果选了文件，上传压缩包
      if (fileRef.current) {
        setUploadStatus(t("uploadingPackage", "正在上传压缩包..."));
        try {
          await uploadDeveloperSkillPackage(productId, fileRef.current);
        } catch {
          message.warning(t("uploadPackageFailed", "Skill 已创建，但压缩包上传失败，可稍后重新上传"));
          form.resetFields();
          fileRef.current = null;
          onCreated();
          onClose();
          return;
        }
      }

      message.success(t("createSkillSuccess", "Skill 创建成功"));
      form.resetFields();
      fileRef.current = null;
      setUploadStatus("");
      onCreated();
      onClose();
    } catch {
      // validation errors shown inline
    } finally {
      setLoading(false);
      setUploadStatus("");
    }
  };

  const handleCancel = () => {
    form.resetFields();
    fileRef.current = null;
    setUploadStatus("");
    onClose();
  };

  return (
    <Modal
      title={t("createSkillTitle", "创建个人 Skill")}
      open={open}
      onCancel={handleCancel}
      footer={[
        <Button key="cancel" onClick={handleCancel} disabled={loading}>
          {t("cancel", "取消")}
        </Button>,
        <Button key="submit" type="primary" loading={loading} onClick={handleSubmit}>
          {uploadStatus || t("create", "创建")}
        </Button>,
      ]}
      width={520}
      destroyOnClose
    >
      <Form form={form} layout="vertical" className="mt-4">
        <Form.Item
          name="name"
          label={t("skillName", "Skill 名称")}
          rules={[{ required: true, message: t("skillNameRequired", "请输入 Skill 名称") }]}
        >
          <Input maxLength={64} placeholder={t("skillNamePlaceholder", "请输入 Skill 名称")} />
        </Form.Item>

        <Form.Item name="description" label={t("skillDescription", "描述")}>
          <Input.TextArea
            maxLength={1000}
            rows={3}
            placeholder={t("skillDescriptionPlaceholder", "请输入描述（可选）")}
          />
        </Form.Item>

        <Form.Item name="tags" label={t("skillTags", "标签")}>
          <Select
            mode="tags"
            placeholder={t("skillTagsPlaceholder", "输入标签后按回车添加")}
            tokenSeparators={[","]}
          />
        </Form.Item>

        <Form.Item label={t("skillPackage", "Skill 压缩包（可选）")}>
          <Upload.Dragger
            accept=".zip"
            maxCount={1}
            beforeUpload={handleFileChange}
            onRemove={() => {
              fileRef.current = null;
            }}
          >
            <p className="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p className="ant-upload-text">{t("uploadDragHint", "点击或拖拽 .zip 文件到此处")}</p>
            <p className="ant-upload-hint">{t("uploadHint", "支持 .zip 格式，不上传则创建空 Skill。选择 ZIP 后将自动使用 SKILL.md 中的名称")}</p>
          </Upload.Dragger>
        </Form.Item>
      </Form>
    </Modal>
  );
}

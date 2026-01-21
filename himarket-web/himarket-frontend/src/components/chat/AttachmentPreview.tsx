import { useState, useEffect } from "react";
import { CloseCircleFilled, LoadingOutlined } from "@ant-design/icons";
import { File as FileIcon } from "../icon";
import { type IAttachment, getAttachment } from "../../lib/apis";

export type PreviewAttachment = Partial<IAttachment> & { attachmentId: string; url?: string };

interface AttachmentPreviewProps {
  attachments: PreviewAttachment[];
  onRemove?: (id: string) => void;
  isUploading?: boolean;
  className?: string;
  itemClassName?: string;
}

const AttachmentItem = ({ file, onRemove, itemClassName }: { file: PreviewAttachment, onRemove?: (id: string) => void, itemClassName: string }) => {
  const [details, setDetails] = useState<PreviewAttachment>(file);
  const [imgSrc, setImgSrc] = useState<string | undefined>(file.url);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    // If metadata is missing (no type) or it's an image without source, fetch it
    const needsFetch = !details.type || (details.type === "IMAGE" && !imgSrc && !details.url);

    if (needsFetch && details.attachmentId) {
      setLoading(true);
      getAttachment(details.attachmentId)
        .then(res => {
          if (res.code === "SUCCESS" && res.data) {
            const data = res.data;
            setDetails(prev => ({ ...prev, ...data }));
            if (data.type === "IMAGE" && data.data) {
              setImgSrc(`data:${data.mimeType};base64,${data.data}`);
            }
          }
        })
        .catch(console.error)
        .finally(() => setLoading(false));
    }
  }, [details.attachmentId, details.type, imgSrc, details.url]);

  useEffect(() => {
    if (file.url) setImgSrc(file.url);
  }, [file.url]);

  if (details.type === "IMAGE" && (imgSrc || loading)) {
    return (
      <div
        className={`relative group rounded-2xl w-16 h-16 overflow-hidden flex-shrink-0 ${itemClassName}`}
      >
        {onRemove && (
          <div
            onClick={() => onRemove(details.attachmentId)}
            className="absolute cursor-pointer hidden group-hover:block top-2 right-2 leading-none z-10"
          >
            <CloseCircleFilled className="text-white/80 hover:text-white drop-shadow-md" />
          </div>
        )}
        {loading ? (
          <div className="w-full h-full bg-gray-50 flex items-center justify-center">
            <LoadingOutlined className="text-colorPrimary" />
          </div>
        ) : (
          <img
            src={imgSrc}
            alt={details.name}
            className="w-full h-full object-cover"
          />
        )}
      </div>
    );
  }

  // If loading metadata (type unknown), show loading placeholder or file card with ID?
  // Showing loading state for the whole card if type is unknown
  if (loading && !details.type) {
      return (
        <div className={`flex items-center justify-center p-2 bg-gray-50 rounded-lg border border-dashed border-gray-200 min-w-[60px] h-16 ${itemClassName}`}>
          <LoadingOutlined className="text-colorPrimary" />
        </div>
      )
  }

  return (
    <div
      className={`relative group rounded-2xl p-3 bg-white/80 hover:bg-white/50 transition-colors flex items-center w-[160px] gap-2 h-16 ${itemClassName}`}
      style={{
        boxShadow: "0px 4px 12px 0px rgba(118, 94, 252, 0.15)",
      }}
    >
      {onRemove && (
        <div
          onClick={() => onRemove(details.attachmentId)}
          className="absolute cursor-pointer hidden group-hover:block top-2 right-2 leading-none"
        >
          <CloseCircleFilled className="text-ring-light" />
        </div>
      )}
      <div
        style={{ background: "var(--gradient-iondigo-500)" }}
        className="flex min-w-10 w-10 h-10 items-center justify-center rounded-lg flex-shrink-0"
      >
        <FileIcon className="fill-indigo-500" />
      </div>
      <div className="flex flex-col justify-between min-w-0 flex-1">
        <div className="text-sm text-accent-dark font-medium text-ellipsis overflow-hidden whitespace-nowrap" title={details.name || details.attachmentId}>
          {details.name || "Loading..."}
        </div>
        <span className="text-xs text-accent-dark">
          {details.name ? details.name.split(".").pop() : ""}
        </span>
      </div>
    </div>
  );
}

export function AttachmentPreview({
  attachments,
  onRemove,
  isUploading = false,
  className = "",
  itemClassName = "",
}: AttachmentPreviewProps) {
  if ((!attachments || attachments.length === 0) && !isUploading) return null;

  return (
    <div className={`flex items-center gap-1 ${className}`}>
      {attachments?.map((file) => (
        <AttachmentItem key={file.attachmentId} file={file} onRemove={onRemove} itemClassName={itemClassName} />
      ))}
      {isUploading && (
        <div className="flex items-center justify-center p-2 bg-gray-50 rounded-lg border border-dashed border-gray-200 min-w-[60px] h-16">
          <LoadingOutlined className="text-colorPrimary" />
        </div>
      )}
    </div>
  );
}

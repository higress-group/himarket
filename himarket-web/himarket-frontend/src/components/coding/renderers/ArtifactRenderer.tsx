import type { ArtifactType } from "../../../types/artifact";
import { HtmlRenderer } from "./HtmlRenderer";
import { MarkdownRenderer } from "./MarkdownRenderer";
import { SvgRenderer } from "./SvgRenderer";
import { ImageRenderer } from "./ImageRenderer";
import { PdfRenderer } from "./PdfRenderer";
import { FileRenderer } from "./FileRenderer";

interface ArtifactRendererProps {
  type: ArtifactType;
  content: string | null;
  path: string;
  fileName: string;
}

export function ArtifactRenderer({
  type,
  content,
  path,
  fileName,
}: ArtifactRendererProps) {
  if (type === "file" || !content) {
    return <FileRenderer fileName={fileName} path={path} />;
  }

  switch (type) {
    case "html":
      return <HtmlRenderer content={content} />;
    case "markdown":
      return <MarkdownRenderer content={content} />;
    case "svg":
      return <SvgRenderer content={content} />;
    case "image":
      return <ImageRenderer content={content} path={path} />;
    case "pdf":
      return <PdfRenderer content={content} />;
    default:
      return <FileRenderer fileName={fileName} path={path} />;
  }
}

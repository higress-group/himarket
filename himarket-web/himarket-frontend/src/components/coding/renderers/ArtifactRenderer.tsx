import { FileRenderer } from './FileRenderer';
import { HtmlRenderer } from './HtmlRenderer';
import { ImageRenderer } from './ImageRenderer';
import { MarkdownRenderer } from './MarkdownRenderer';
import { PdfRenderer } from './PdfRenderer';
import { SvgRenderer } from './SvgRenderer';

import type { ArtifactType } from '../../../types/artifact';

interface ArtifactRendererProps {
  type: ArtifactType;
  content: string | null;
  path: string;
  fileName: string;
}

export function ArtifactRenderer({ content, fileName, path, type }: ArtifactRendererProps) {
  if (type === 'file' || !content) {
    return <FileRenderer fileName={fileName} path={path} />;
  }

  switch (type) {
    case 'html':
      return <HtmlRenderer content={content} />;
    case 'markdown':
      return <MarkdownRenderer content={content} />;
    case 'svg':
      return <SvgRenderer content={content} />;
    case 'image':
      return <ImageRenderer content={content} path={path} />;
    case 'pdf':
      return <PdfRenderer content={content} />;
    default:
      return <FileRenderer fileName={fileName} path={path} />;
  }
}

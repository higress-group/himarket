interface SvgRendererProps {
  content: string;
}

export function SvgRenderer({ content }: SvgRendererProps) {
  return (
    <iframe
      srcDoc={`<!DOCTYPE html><html><head><style>body{margin:0;display:flex;align-items:center;justify-content:center;min-height:100vh;background:#fff;}</style></head><body>${content}</body></html>`}
      sandbox=""
      className="w-full h-full border-none bg-white"
      title="SVG Preview"
    />
  );
}

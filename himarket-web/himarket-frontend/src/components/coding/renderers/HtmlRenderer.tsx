interface HtmlRendererProps {
  content: string;
}

export function HtmlRenderer({ content }: HtmlRendererProps) {
  return (
    <iframe
      srcDoc={content}
      sandbox="allow-scripts"
      className="w-full h-full border-none bg-white"
      title="HTML Preview"
    />
  );
}

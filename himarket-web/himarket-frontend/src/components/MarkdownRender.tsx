import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import rehypeHighlight from 'rehype-highlight';
import { Image } from 'antd';
import 'highlight.js/styles/atom-one-dark.css';
import "github-markdown-css/github-markdown-light.css"

const MarkdownRender = ({ content }: { content: string }) => {
  return (
    <ReactMarkdown
      remarkPlugins={[remarkGfm]}
      rehypePlugins={[rehypeHighlight]}
      components={{
        img: ({ src, alt }) => (
          <Image
            src={src}
            alt={alt || ''}
            style={{ maxWidth: '300px', maxHeight: '300px', objectFit: 'contain', cursor: 'pointer' }}
            preview={{
              mask: '点击查看大图',
            }}
          />
        ),
      }}
    >
      {content}
    </ReactMarkdown>
  )
}

export default MarkdownRender;
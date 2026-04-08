import { Image } from 'antd';
import ReactMarkdown from 'react-markdown';
import rehypeHighlight from 'rehype-highlight';
import remarkGfm from 'remark-gfm';
import 'highlight.js/styles/github.css';
import 'github-markdown-css/github-markdown-light.css';

const MarkdownRender = ({ content }: { content: string }) => {
  return (
    <ReactMarkdown
      components={{
        img: ({ alt, src }) => (
          <Image
            alt={alt || ''}
            preview={{
              mask: '点击查看大图',
            }}
            src={src}
            style={{
              cursor: 'pointer',
              maxHeight: '300px',
              maxWidth: '300px',
              objectFit: 'contain',
            }}
          />
        ),
      }}
      rehypePlugins={[rehypeHighlight]}
      remarkPlugins={[remarkGfm]}
    >
      {content}
    </ReactMarkdown>
  );
};

export default MarkdownRender;

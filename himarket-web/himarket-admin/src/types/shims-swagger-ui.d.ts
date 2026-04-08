declare module 'swagger-ui-react' {
  import type * as React from 'react';

  interface SwaggerUIProps {
    spec?: Record<string, unknown>;
    url?: string;
    layout?: string;
    docExpansion?: string;
    filter?: boolean | string;
    onRequest?: (request: Record<string, unknown>) => void;
    onComplete?: () => void;
    deepLinking?: boolean;
    defaultModelExpandDepth?: number;
    defaultModelsExpandDepth?: number;
    displayOperationId?: boolean;
    displayRequestDuration?: boolean;
    enableCORS?: boolean;
    showExtensions?: boolean;
    showMutatedRequest?: boolean;
    showSummary?: boolean;
    tryItOutEnabled?: boolean;
    validatorUrl?: string | null;
    persistAuthorization?: boolean;
    withCredentials?: boolean;
    oauth2RedirectUrl?: string;
    supportedSubmitMethods?: string[];
    domNode?: HTMLElement;
    dom_id?: string | null;
    presets?: Array<unknown>;
    plugins?: Array<unknown>;
    requestInterceptor?: (req: Record<string, unknown>) => Record<string, unknown>;
    responseInterceptor?: (res: Record<string, unknown>) => Record<string, unknown>;
    requestSnippets?: Record<string, unknown>;
    requestSnippetsEnabled?: boolean;
    showCommonExtensions?: boolean;
    showRequestHeaders?: boolean;
    syntaxHighlight?: Record<string, unknown>;
  }

  const SwaggerUI: React.FC<SwaggerUIProps>;
  export default SwaggerUI;
}

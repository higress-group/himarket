import type { Product } from "./index";

export interface Consumer {
  consumerId: string;
  name: string;
  description?: string;
  status?: string;
  createAt?: string;
  enabled?: boolean;
}

export type ConsumerCredential = HMACCredential | APIKeyCredential;

export interface HMACCredential {
  ak?: string;
  sk?: string;
  mode?: 'SYSTEM' | 'CUSTOM';
}

export interface APIKeyCredential {
  apiKey?: string;
  mode?: 'SYSTEM' | 'CUSTOM';
}

export interface ConsumerCredentialResult {
  apiKeyConfig?: {
    credentials?: Array<{
      apiKey?: string;
      mode?: 'SYSTEM' | 'CUSTOM';
    }>;
    source?: string;
    key?: string;
  };
  hmacConfig?: {
    credentials?: Array<{
      ak?: string;
      sk?: string;
      mode?: 'SYSTEM' | 'CUSTOM';
    }>;
  };
  jwtConfig?: Record<string, unknown>;
}

export interface Subscription {
  productId: string;
  consumerId: string;
  status: 'PENDING' | 'APPROVED';
  createAt: string;
  updatedAt: string;
  productName: string;
  productType: 'REST_API' | 'MCP_SERVER' | 'AGENT_API' | 'MODEL_API';
  consumerName?: string;
  product?: Product;
}

export interface CreateCredentialParam {
  apiKeyConfig?: {
    credentials?: Array<{
      apiKey?: string;
      mode?: 'SYSTEM' | 'CUSTOM';
    }>;
    source?: string;
    key?: string;
  };
  hmacConfig?: {
    credentials?: Array<{
      ak?: string;
      sk?: string;
      mode?: 'SYSTEM' | 'CUSTOM';
    }>;
  };
  jwtConfig?: Record<string, unknown>;
} 
// Consumer 相关类型定义

export interface Consumer {
  consumerId: string;
  name: string;
  description?: string;
  status?: string;
  createAt?: string;
  createdAt?: string;  // 支持两种字段名
  enabled?: boolean;
}

// Portal 开发者统计信息（之前错误命名为Consumer）
export interface DeveloperStats {
  id: string;
  name: string;
  email: string;
  status: string;
  plan: string;
  joinedAt: string;
  lastActive: string;
  apiCalls: number;
  subscriptions: number;
}

// 凭据相关类型
export interface HMACCredential {
  ak?: string;
  sk?: string;
}

export interface APIKeyCredential {
  apiKey?: string;
}

export type ConsumerCredential = HMACCredential | APIKeyCredential;

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
    source?: string;
    accessKeyId?: string;
    secretAccessKey?: string;
  };
}

export interface Subscription {
  subscriptionId: string;
  productId: string;
  productName?: string;
  status: string;
  createdAt: string;
}

export interface CreateCredentialParam {
  credentialType: 'HMAC' | 'API_KEY';
  apiKey?: string;
}

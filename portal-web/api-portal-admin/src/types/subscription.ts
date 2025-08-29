export interface Subscription {
  subscriptionId: string;
  consumerId: string;
  productId: string;
  status: 'PENDING' | 'APPROVED';
  createAt: string;
  updatedAt: string;
  productName: string;
  productType: string;
}

export interface Product {
  productId: string;
  name: string;
  description?: string;
  type: string;
}

export interface SubscriptionModalProps {
  visible: boolean;
  consumerId: string;
  consumerName: string;
  onCancel: () => void;
}




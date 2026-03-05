// ===== Runtime Types =====

export type RuntimeType = 'local' | 'k8s';

export interface RuntimeInfo {
  type: RuntimeType;
  label: string;
  description: string;
  available: boolean;
  unavailableReason?: string;
}

export interface CliProviderWithRuntime {
  key: string;
  displayName: string;
  command: string;
  runtimeCategory: 'native' | 'nodejs' | 'python';
  compatibleRuntimes: RuntimeType[];
  containerImage?: string;
}


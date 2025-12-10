export interface AuthCodeConfig {
  clientId: string;
  clientSecret: string;
  scopes: string;
  authorizationEndpoint: string;
  tokenEndpoint: string;
  userInfoEndpoint: string;
  jwkSetUri: string;
  // OIDC issuer地址（用于自动发现模式）
  issuer?: string;
  // 可选的身份映射配置
  identityMapping?: IdentityMapping;
}

export interface OidcConfig {
  provider: string;
  name: string;
  logoUrl?: string | null;
  enabled: boolean;
  grantType: 'AUTHORIZATION_CODE';
  authCodeConfig: AuthCodeConfig;
  identityMapping?: IdentityMapping;
}

// 第三方认证相关类型定义
export enum AuthenticationType {
  OIDC = 'OIDC',
  OAUTH2 = 'OAUTH2'
}

export enum GrantType {
  AUTHORIZATION_CODE = 'AUTHORIZATION_CODE',
  JWT_BEARER = 'JWT_BEARER'
}

export enum PublicKeyFormat {
  PEM = 'PEM',
  JWK = 'JWK'
}

export interface PublicKeyConfig {
  kid: string;
  format: PublicKeyFormat;
  algorithm: string;
  value: string;
}

export interface JwtBearerConfig {
  publicKeys: PublicKeyConfig[];
}

export interface IdentityMapping {
  userIdField?: string | null;
  userNameField?: string | null;
  emailField?: string | null;
  customFields?: { [key: string]: string } | null;
}

// OAuth2配置（使用现有格式）
export interface OAuth2Config {
  provider: string;
  name: string;
  enabled: boolean;
  grantType: GrantType;
  jwtBearerConfig?: JwtBearerConfig;
  identityMapping?: IdentityMapping;
}

// 为了UI显示方便，给配置添加类型标识的联合类型
export type ThirdPartyAuthConfig = 
  | (OidcConfig & { type: AuthenticationType.OIDC })
  | (OAuth2Config & { type: AuthenticationType.OAUTH2 })

export interface PortalSettingConfig {
  builtinAuthEnabled: boolean;
  oidcAuthEnabled: boolean;
  autoApproveDevelopers: boolean;
  autoApproveSubscriptions: boolean;
  frontendRedirectUrl: string;
  
  // 第三方认证配置（分离存储）
  oidcConfigs?: OidcConfig[];
  oauth2Configs?: OAuth2Config[];
}

export interface PortalUiConfig {
  logo: string | null;
  icon: string | null;
}

export interface PortalDomainConfig {
  domain: string;
  type: string;
  protocol: string;
}

export interface Portal {
  portalId: string;
  name: string;
  title: string;
  description: string;
  adminId: string;
  portalSettingConfig: PortalSettingConfig;
  portalUiConfig: PortalUiConfig;
  portalDomainConfig: PortalDomainConfig[];
} 

export interface Developer {
  portalId: string;
  developerId: string;
  username: string;
  status: string;
  avatarUrl?: string;
  createAt: string;
}
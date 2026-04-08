// @rc-component/upload/lib/interface 的类型声明
// 这个模块没有官方的类型声明，我们需要手动声明

declare module '@rc-component/upload/lib/interface' {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  import type * as _React from 'react';

  export type BeforeUploadFileType = File | Blob | boolean | string;
  export type UploadRequestFile = Exclude<BeforeUploadFileType, File | boolean> | RcFile;

  export interface RcFile extends File {
    uid: string;
  }

  export interface UploadProgressEvent extends Partial<ProgressEvent> {
    percent?: number;
  }

  export interface UploadRequestError extends Error {
    status?: number;
    method?: string;
    url?: string;
  }

  export type UploadRequestMethod = 'POST' | 'PUT' | 'PATCH' | 'post' | 'put' | 'patch';
  export type UploadRequestHeader = Record<string, string>;

  export interface UploadRequestOption<T = unknown> {
    onProgress?: (event: UploadProgressEvent, file?: UploadRequestFile) => void;
    onError?: (event: UploadRequestError | ProgressEvent, body?: T) => void;
    onSuccess?: (body: T, fileOrXhr?: UploadRequestFile | XMLHttpRequest) => void;
    data?: Record<string, unknown>;
    filename?: string;
    file: UploadRequestFile;
    withCredentials?: boolean;
    action: string;
    headers?: UploadRequestHeader;
    method: UploadRequestMethod;
  }

  export type CustomUploadRequestOption = (
    option: UploadRequestOption,
    info: {
      defaultRequest: (option: UploadRequestOption) => { abort: () => void } | void;
    },
  ) => void | { abort: () => void };
}

// 兼容旧的导入路径
declare module 'rc-upload/lib/interface' {
  export type {
    UploadRequestOption,
    UploadRequestFile,
    RcFile,
    UploadProgressEvent,
    UploadRequestError,
    CustomUploadRequestOption,
  } from '@rc-component/upload/lib/interface';
}

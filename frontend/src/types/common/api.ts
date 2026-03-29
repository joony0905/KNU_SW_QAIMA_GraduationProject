export interface ApiResponse<T> {
  meta: {
    requestId?: string;
    status: string;
    timestamp?: string;
    warning?: string | null;
    warnings?: string[];
  } | null;
  data: T;
  errors: Array<{
    code: string;
    message: string;
  }>;
}

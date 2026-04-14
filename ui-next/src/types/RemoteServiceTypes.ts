/**
 * Shared types for Remote Service definitions.
 * These are here (instead of pages/remoteServices) so that components in
 * src/components/ can reference them without importing from an enterprise page.
 */

export enum ServiceType {
  HTTP = "HTTP",
  GRPC = "gRPC",
  MCP_REMOTE = "MCP_REMOTE",
}

export interface Method {
  id?: number;
  operationName: string;
  methodName: string;
  methodType?:
    | "GET"
    | "POST"
    | "PUT"
    | "DELETE"
    | "PATCH"
    | "UNARY"
    | "SERVER_STREAMING"
    | "CLIENT_STREAMING"
    | "BIDIRECTIONAL_STREAMING";
  inputType: string;
  outputType: string;
  exampleInput?: Record<string, unknown>;
  requestContentType?: string;
  responseContentType?: string;
  description?: string;
  deprecated?: boolean;
  requestParams?: {
    name: string;
    type: string;
    required: boolean;
    schema?: { type?: string };
  }[];
}

type CircuitBreakerConfig = {
  failureRateThreshold?: number;
  slidingWindowSize?: number;
  minimumNumberOfCalls?: number;
  waitDurationInOpenState?: number;
  permittedNumberOfCallsInHalfOpenState?: number;
  slowCallRateThreshold?: number;
  slowCallDurationThreshold?: number;
  automaticTransitionFromOpenToHalfOpenEnabled?: boolean;
  maxWaitDurationInHalfOpenState?: number;
};

type Config = {
  circuitBreakerConfig: CircuitBreakerConfig;
};

export type Server = {
  url: string;
  type: "OPENAPI_SPEC" | "USER_DEFINED";
};

type commonServiceDef = {
  name: string;
  type: string;
  methods: Method[];
  serviceURI?: string;
  config: Config;
  circuitBreakerEnabled?: boolean;
  servers?: Server[];
  authMetadata?: {
    key: string;
    value: string;
  };
};

export interface HttpServiceDefDto extends commonServiceDef {
  swaggerUrl: string;
  bearerToken?: string;
  selectedServerUrl?: string;
}

export interface GrpcServiceDefDto extends commonServiceDef {
  host: string;
  port: number;
  useSSL?: boolean;
  trustCert?: boolean;
}

export type ServiceDefDto = HttpServiceDefDto & GrpcServiceDefDto;

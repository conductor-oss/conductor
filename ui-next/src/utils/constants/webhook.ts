import { TagDto } from "types/Tag";

export enum SOURCE_PLATFORM {
  GITHUB = "Github",
  MICROSOFT_TEAMS = "Microsoft Teams",
  SEND_GRID = "SendGrid",
  SLACK = "Slack",
  STRIPE = "Stripe",
  CUSTOM = "Custom",
}

export enum VERIFIER {
  SLACK_BASED = "SLACK_BASED",
  SIGNATURE_BASED = "SIGNATURE_BASED",
  HEADER_BASED = "HEADER_BASED",
  HMAC_BASED = "HMAC_BASED",
  STRIPE = "STRIPE",
  SEND_GRID = "SENDGRID",
}

export const WEBHOOK_HEADER_NAME = {
  STRIPE_SIGNATURE: "Stripe-Signature",
  X_HUB_SIGNATURE_256: "X-Hub-Signature-256",
  AUTHORIZATION: "Authorization",
};

export type WebhookAuthParam = {
  vendor: SOURCE_PLATFORM;
  signing: string;
  headerKey?: string;
  secretKey?: string;
  secretKeyLabel?: string;
  secretValue?: string;
  secretLabel?: string;
  iconName: string;
};

export const WEBHOOK_ICON = {
  [SOURCE_PLATFORM.SLACK]: "slack-icon",
  [SOURCE_PLATFORM.GITHUB]: "github-icon",
  [SOURCE_PLATFORM.STRIPE]: "stripe-icon",
  [SOURCE_PLATFORM.SEND_GRID]: "send-grid-icon",
  [SOURCE_PLATFORM.MICROSOFT_TEAMS]: "microsoft-teams-icon",
  [SOURCE_PLATFORM.CUSTOM]: "default-icon",
};

export const WEBHOOK_AUTH_PARAMS: WebhookAuthParam[] = [
  {
    vendor: SOURCE_PLATFORM.SLACK,
    signing: VERIFIER.SLACK_BASED,
    iconName: WEBHOOK_ICON[SOURCE_PLATFORM.SLACK],
  },
  {
    vendor: SOURCE_PLATFORM.GITHUB,
    signing: VERIFIER.SIGNATURE_BASED,
    headerKey: WEBHOOK_HEADER_NAME.X_HUB_SIGNATURE_256,
    secretLabel: "Secret",
    iconName: WEBHOOK_ICON[SOURCE_PLATFORM.GITHUB],
  },
  {
    vendor: SOURCE_PLATFORM.STRIPE,
    signing: VERIFIER.STRIPE,
    headerKey: WEBHOOK_HEADER_NAME.STRIPE_SIGNATURE,
    secretValue: "endpointSecret",
    secretLabel: "Endpoint secret",
    iconName: WEBHOOK_ICON[SOURCE_PLATFORM.STRIPE],
  },
  {
    vendor: SOURCE_PLATFORM.SEND_GRID,
    signing: VERIFIER.SEND_GRID,
    secretKeyLabel: "Verification key",
    iconName: WEBHOOK_ICON[SOURCE_PLATFORM.SEND_GRID],
  },
  {
    vendor: SOURCE_PLATFORM.MICROSOFT_TEAMS,
    signing: VERIFIER.HMAC_BASED,
    headerKey: WEBHOOK_HEADER_NAME.AUTHORIZATION,
    secretLabel: "Security token",
    iconName: WEBHOOK_ICON[SOURCE_PLATFORM.MICROSOFT_TEAMS],
  },
  {
    vendor: SOURCE_PLATFORM.CUSTOM,
    signing: VERIFIER.HEADER_BASED,
    iconName: WEBHOOK_ICON[SOURCE_PLATFORM.CUSTOM],
  },
];

export interface IWebhookDTO {
  id?: string;
  name: string;
  receiverWorkflowNamesToVersions: { [key: string]: number };
  authenticationType: string;
  urlVerified?: boolean;
  sourcePlatform: string;
  headers?: { [key: string]: string };
  bodyKey?: string;
  bodyValue?: string;
  headerKey?: string;
  secretKey?: string;
  secretValue?: string;
  verifier?: VERIFIER;
  workflowsToStart?: { [key: string]: number | string };
  url?: string;
  tags?: TagDto[];
}

export interface WebhookHistoryDTO {
  eventId?: string;
  matched?: boolean;
  workflowIds?: string[];
  timeStamp?: number;
}

export enum REPEATER_KEY {
  HEADERS = "headers",
}

export const GUIDE_STEPS = [
  {
    id: "webhookName",
    title: "Webhook Name",
    description: "name for the webhook.",
  },
  {
    id: "webhookEvent",
    title: "Workflows to receive webhook event",
    description: "Workflows that are supposed to receive this webhook event.",
  },
  {
    id: "sourcePlatform",
    title: "Source Platform",
    description: "Platform from which this webhook event will be invoked.",
  },
  {
    id: "url",
    title: "URL",
    description: "URL on which the webhook event must be invoked.",
  },
  {
    id: "urlStatus",
    title: "URL Status",
    description:
      "Unverified - No single event has been received on the URL. \nVerified - Either url verification is done or successful event has been received.",
  },
  {
    id: "startWf",
    title: "Start workflow when webhook comes",
    description: "Start a new workflow when the webhook event comes.",
  },
  {
    id: "secret",
    title: "Secret",
    description: "Secret will be visible only once while storing.",
  },
];

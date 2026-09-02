import { TagDto } from "types/Tag";
export declare enum SOURCE_PLATFORM {
    GITHUB = "Github",
    MICROSOFT_TEAMS = "Microsoft Teams",
    SEND_GRID = "SendGrid",
    SLACK = "Slack",
    STRIPE = "Stripe",
    CUSTOM = "Custom"
}
export declare enum VERIFIER {
    SLACK_BASED = "SLACK_BASED",
    SIGNATURE_BASED = "SIGNATURE_BASED",
    HEADER_BASED = "HEADER_BASED",
    HMAC_BASED = "HMAC_BASED",
    STRIPE = "STRIPE",
    SEND_GRID = "SENDGRID"
}
export declare const WEBHOOK_HEADER_NAME: {
    STRIPE_SIGNATURE: string;
    X_HUB_SIGNATURE_256: string;
    AUTHORIZATION: string;
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
export declare const WEBHOOK_ICON: {
    Slack: string;
    Github: string;
    Stripe: string;
    SendGrid: string;
    "Microsoft Teams": string;
    Custom: string;
};
export declare const WEBHOOK_AUTH_PARAMS: WebhookAuthParam[];
export interface IWebhookDTO {
    id?: string;
    name: string;
    receiverWorkflowNamesToVersions: {
        [key: string]: number;
    };
    authenticationType: string;
    urlVerified?: boolean;
    sourcePlatform: string;
    headers?: {
        [key: string]: string;
    };
    bodyKey?: string;
    bodyValue?: string;
    headerKey?: string;
    secretKey?: string;
    secretValue?: string;
    verifier?: VERIFIER;
    workflowsToStart?: {
        [key: string]: number | string;
    };
    url?: string;
    tags?: TagDto[];
}
export interface WebhookHistoryDTO {
    eventId?: string;
    matched?: boolean;
    workflowIds?: string[];
    timeStamp?: number;
}
export declare enum REPEATER_KEY {
    HEADERS = "headers"
}
export declare const GUIDE_STEPS: {
    id: string;
    title: string;
    description: string;
}[];

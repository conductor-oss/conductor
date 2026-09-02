declare global {
    interface Window {
        gtag: any;
        dataLayer: any;
    }
}
interface EventParams {
    user_uuid?: string;
    workflow_name?: string;
    user_performed_action?: string;
    error_type?: string;
    event?: object;
    start_time?: number;
    end_time?: number;
    item_id?: string;
}
type SimpleUserInfo = {
    uuid?: string;
    user?: any;
    id?: string;
};
export declare const GTAG_LABEL = "G-6DLM7JND12";
declare const gtagAbstract: (event_name: string, event_params: EventParams) => void;
export declare const useConfigureGtagUserIdIfPlayground: (conductorUser?: SimpleUserInfo) => void;
type FlattenedObject = Record<string, any>;
declare const flattenGtagObject: (obj: Record<string, any>, prefix?: string) => FlattenedObject;
export { gtagAbstract, flattenGtagObject };

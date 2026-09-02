export declare const useErrorMonitoring: () => {
    notifyError: (error: Error | string, metadata?: {
        [key: string]: any;
    }) => void;
};

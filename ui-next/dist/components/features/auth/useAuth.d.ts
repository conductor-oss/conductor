export declare const useAuth: () => import("./types").AuthState | {
    readonly authService: import("xstate").ActorRef<import("../../..").AuthProviderMachineEvents, any> | undefined;
    readonly user: unknown;
    readonly isAuthenticated: boolean;
    readonly isTrialExpired: boolean;
    readonly trialExpiryDate: number | Date | undefined;
    readonly isAnnouncementBannerDismissed: boolean;
    readonly provider: import("../../..").SupportedProviders;
    readonly conductorUser: import("../../..").User | undefined;
    readonly oidcConfig: unknown;
    readonly fetchingUserInformation: boolean;
    readonly logOut: () => void;
    readonly solveExpireToken: () => void;
    readonly setToken: (token: string) => void;
    readonly redirectToAuthorizationEndpoint: (currentPath: string) => void;
    readonly fetchOidcTokenWithCode: (code: string, stateParam: string) => void;
    readonly dismissAnnouncementBanner: () => void;
};

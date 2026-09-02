export declare const useEventHandlerFormActor: (actor: any) => readonly [{
    readonly action: any;
    readonly name: any;
    readonly condition: any;
    readonly actions: any;
    readonly event: any;
    readonly active: any;
    readonly description: any;
}, {
    readonly handleChangeAction: (index: number, payload: any) => void;
    readonly handleChange: (name: string, value: string | boolean) => void;
    readonly handleAction: (action: string) => void;
    readonly removeAction: (index: number) => void;
    readonly handleEventChange: (event: string) => void;
}];

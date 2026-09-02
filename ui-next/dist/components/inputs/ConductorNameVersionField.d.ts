export interface ConductorNameVersionFieldProps {
    label: string;
    optionsUrl: string;
    value?: {
        name: string;
        version?: number;
    };
    onChange?: (value?: {
        name?: string;
        version?: number;
    }) => void;
    mapOptions?: (data: any) => {
        name: string;
        versions: number[];
    }[];
    nameField?: {
        id?: string;
        clearIndicator?: boolean;
    };
    versionField?: {
        id?: string;
        emptyText?: string;
        autocomplete?: boolean;
        required?: boolean;
    };
    showErrorIfItemNotInList?: boolean;
    disabled?: boolean;
}
export declare const ConductorNameVersionField: import("react").ForwardRefExoticComponent<ConductorNameVersionFieldProps & import("react").RefAttributes<{
    refetch: () => void;
}>>;

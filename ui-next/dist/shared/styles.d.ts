export declare const disabledInputStyle: {
    "& .MuiOutlinedInput-root.Mui-disabled .MuiOutlinedInput-notchedOutline": {
        borderColor: string;
        backgroundColor: string;
    };
};
export declare const dateRangePickerStyle: {
    wrapper: {
        display: string;
    };
    input: {
        "& .MuiOutlinedInput-root.Mui-disabled .MuiOutlinedInput-notchedOutline": {
            borderColor: string;
            backgroundColor: string;
        };
        ">div": {
            width: string;
        };
    };
};
export declare const autocompleteStyle: ({ value }: {
    value: any;
}) => {
    ".MuiTextField-root": {
        ".MuiOutlinedInput-root": {
            pt: string;
            pl: string;
            pb: string;
            ".MuiAutocomplete-input": {
                p: number;
            };
        };
        ".MuiInputLabel-root": any;
    };
};
export declare const customButtonStyle: {
    color: string;
    "&:hover": {
        background: string;
    };
};

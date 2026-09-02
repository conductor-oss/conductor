declare namespace _default {
    namespace wrapper {
        let overflowY: string;
        let overflowX: string;
        let height: string;
        let width: string;
        let display: string;
        let justifyContent: string;
        let backgroundColor: string;
    }
    namespace fullWidth {
        let width_1: string;
        export { width_1 as width };
        let height_1: string;
        export { height_1 as height };
        let overflowY_1: string;
        export { overflowY_1 as overflowY };
        let backgroundColor_1: string;
        export { backgroundColor_1 as backgroundColor };
        export let paddingBottom: string;
    }
    namespace padded {
        let padding: string;
    }
    let header: {
        backgroundColor: string;
        paddingLeft: string;
        paddingTop: string;
        "@media (min-width: 1920px)": {
            paddingLeft: string;
        };
    };
    let tabContent: {
        marginTop: string;
        paddingTop: string;
        paddingRight: string;
        paddingBottom: string;
        paddingLeft: string;
        "@media (min-width: 1920px)": {
            paddingLeft: string;
        };
    };
    namespace gridFlex {
        let display_1: string;
        export { display_1 as display };
        export let margin: number;
        let padding_1: number;
        export { padding_1 as padding };
        export let overflow: string;
        let width_2: string;
        export { width_2 as width };
        export let flexWrap: string;
        export let alignItems: string;
        let justifyContent_1: string;
        export { justifyContent_1 as justifyContent };
        export let minWidth: string;
    }
    let fixedDisplayHeader: {
        backgroundColor: string;
        paddingLeft: string;
        paddingTop: string;
        "@media (min-width: 1920px)": {
            paddingLeft: string;
        };
        overflowY: string;
        overflowX: string;
        display: string;
        justifyContent: string;
        position: string;
        left: number;
        top: number;
        zIndex: number;
    };
    let tabContentScroll: {
        paddingTop: number;
        paddingRight: string;
        paddingBottom: string;
        paddingLeft: string;
        "@media (min-width: 1920px)": {
            paddingLeft: string;
        };
        overflowY: string;
        overflowX: string;
    };
    namespace paperMargin {
        let marginBottom: string;
    }
    let iconButton: {
        color: string;
        opacity: number;
        paddingRight: string;
        fontSize: number;
        "&:hover": {
            opacity: number;
            backgroundColor: string;
        };
    };
    let editorLabel: {
        color: string;
        opacity: number;
        paddingLeft: string;
        fontSize: string;
        lineHeight: number;
        fontWeight: string;
        "& span": {
            fontSize: string;
            fontWeight: string;
        };
        "& svg": {
            fontSize: string;
        };
    };
    let chipContainer: {
        display: string;
        flexWrap: string;
        "& > *": {
            margin: string;
        };
    };
    let resizer: {
        width: string;
        margin: string;
        cursor: string;
        backgroundColor: string;
        zIndex: number;
        flexShrink: number;
        resize: string;
        "&:hover": {
            backgroundColor: string;
        };
    };
    namespace workflowDefFirstRowMenu {
        let width_3: string;
        export { width_3 as width };
        let display_2: string;
        export { display_2 as display };
        export let flexFlow: string;
        let justifyContent_2: string;
        export { justifyContent_2 as justifyContent };
        export let paddingTop: number;
        let paddingBottom_1: number;
        export { paddingBottom_1 as paddingBottom };
        let alignItems_1: string;
        export { alignItems_1 as alignItems };
    }
    namespace definitionEditorSecondRowMenu {
        let width_4: string;
        export { width_4 as width };
        let display_3: string;
        export { display_3 as display };
        let flexFlow_1: string;
        export { flexFlow_1 as flexFlow };
        let justifyContent_3: string;
        export { justifyContent_3 as justifyContent };
        let paddingTop_1: string;
        export { paddingTop_1 as paddingTop };
        let paddingBottom_2: string;
        export { paddingBottom_2 as paddingBottom };
        export let borderBottom: string;
        let alignItems_2: string;
        export { alignItems_2 as alignItems };
        export let position: string;
        export let top: number;
        export let left: number;
    }
    let popover: {
        "& .MuiPopover-paper": {
            padding: string;
            backgroundColor: string;
            color: string;
        };
        "& .MuiTypography-root": {
            fontSize: string;
        };
    };
    let deleteIcon: {
        "& svg": {
            color: string;
            fontSize: string;
        };
    };
}
export default _default;

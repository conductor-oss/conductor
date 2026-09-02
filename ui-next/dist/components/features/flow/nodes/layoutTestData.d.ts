export namespace oneLoopOneLevelDeep {
    let nodes: ({
        id: string;
        type: string;
        data: {
            label: string;
        };
        position: {
            x: number;
            y: number;
        };
        style?: undefined;
        parentNode?: undefined;
        extent?: undefined;
    } | {
        id: string;
        type: string;
        data: {
            label: string;
        };
        style: {
            width: number;
            height: number;
        };
        position?: undefined;
        parentNode?: undefined;
        extent?: undefined;
    } | {
        id: string;
        type: string;
        data: {
            label: string;
        };
        position: {
            x: number;
            y: number;
        };
        parentNode: string;
        extent: string;
        style?: undefined;
    })[];
    let edges: ({
        id: string;
        source: string;
        target: string;
        type: string;
        zIndex?: undefined;
    } | {
        id: string;
        source: string;
        target: string;
        type?: undefined;
        zIndex?: undefined;
    } | {
        id: string;
        source: string;
        target: string;
        type: string;
        zIndex: number;
    })[];
}

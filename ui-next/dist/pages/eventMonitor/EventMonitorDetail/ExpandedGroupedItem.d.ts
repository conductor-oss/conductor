import { ExpanderComponentProps } from "react-data-table-component";
import { EventItem } from "../types";
interface GroupedData {
    groupedItems: EventItem[];
}
interface ExpandableGroupedItemsProps extends ExpanderComponentProps<GroupedData> {
    actionFilter: string[];
    statusFilter: string[];
    onOpenModal: (payload: any) => void;
}
export declare const StatusBadge: ({ status }: {
    status: string;
}) => import("react").JSX.Element;
export declare const ExpandableGroupedItems: ({ data, actionFilter, statusFilter, onOpenModal, }: ExpandableGroupedItemsProps) => import("react").JSX.Element | null;
export {};

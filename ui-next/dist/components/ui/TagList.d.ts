import { TagDto } from "../../types/Tag";
interface TagListProps {
    tags?: TagDto[];
    name: string;
    sx?: Record<string, unknown>;
    style?: Record<string, unknown>;
}
export declare const TagList: ({ tags, name, sx, style, }: TagListProps) => import("react").JSX.Element | null;
export default TagList;
export declare const TagsRenderer: <T extends {
    name: string;
}>(tags: TagDto[], row: T) => import("react").JSX.Element;

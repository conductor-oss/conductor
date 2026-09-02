import { ParsedErrorMessage } from "./types";
export interface ForbiddenProps {
    parsedMessage: ParsedErrorMessage;
}
export default function Forbidden({ parsedMessage }: ForbiddenProps): import("react").JSX.Element;

import { AgentTurn } from "./types";
interface TurnBarProps {
    turns: AgentTurn[];
    selectedTurn: string;
    onSelectTurn: (turnId: string) => void;
}
export declare function TurnBar({ turns, selectedTurn, onSelectTurn }: TurnBarProps): import("react").JSX.Element | null;
export default TurnBar;

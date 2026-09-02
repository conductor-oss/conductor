type Props = {
    runtime: string;
    credentials: Record<string, unknown> | undefined;
    /** Replaces the whole credentials map, so switching method cannot leave stale keys behind. */
    onCredentialsChange: (credentials: Record<string, string>) => void;
    useCallerIdentity: boolean;
    onUseCallerIdentityChange: (value: boolean) => void;
};
/**
 * Guided credential entry for a hosted agent.
 *
 * The user picks how to authenticate and, if they keep the credential in Conductor, which secret it
 * is — and the `${workflow.secrets.…}` references are written for them. Conductor substitutes those
 * before the task runs, so this is what the agent actually authenticates with; the generated values
 * stay visible and editable rather than being hidden behind the picker.
 */
export default function AgentCredentialsSection({ runtime, credentials, onCredentialsChange, useCallerIdentity, onUseCallerIdentityChange, }: Props): import("react").JSX.Element | null;
export {};

import React from "react";
interface AuthGuardProps {
    fallback?: React.ReactNode;
    runWorkflow?: boolean;
}
declare const AuthGuard: ({ fallback: _fallback, runWorkflow, }: AuthGuardProps) => React.JSX.Element;
export default AuthGuard;

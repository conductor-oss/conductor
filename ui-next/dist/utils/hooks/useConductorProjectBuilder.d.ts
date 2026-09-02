import { CodeLanguage, JavaLanguageSet } from "components/features/getStartedSample/types";
interface UseConductorProjectBuilderOptionsBase {
    apiKey?: string;
    apiSecret?: string;
    serverUrl: string;
    language: CodeLanguage;
    taskName: string;
    useEnvVars: boolean;
}
interface UseConductorProjectBuilderOptionsJava extends UseConductorProjectBuilderOptionsBase {
    language: CodeLanguage.JAVA;
    languageSet: JavaLanguageSet;
    projectName?: string;
    packageName?: string;
}
interface UseConductorProjectBuilderOptionsGo extends UseConductorProjectBuilderOptionsBase {
    language: CodeLanguage.GO;
}
interface UseConductorProjectBuilderOptionsPython extends UseConductorProjectBuilderOptionsBase {
    language: CodeLanguage.PYTHON;
}
interface UseConductorProjectBuilderOptionsJavaScript extends UseConductorProjectBuilderOptionsBase {
    language: CodeLanguage.JS;
}
interface UseConductorProjectBuilderOptionsCSharp extends UseConductorProjectBuilderOptionsBase {
    language: CodeLanguage.CSHARP;
    namespace?: string;
}
interface UseConductorProjectBuilderOptionsClojure extends UseConductorProjectBuilderOptionsBase {
    language: CodeLanguage.CLOJURE;
}
interface UseConductorProjectBuilderOptionsGroovy extends UseConductorProjectBuilderOptionsBase {
    language: CodeLanguage.GROOVY;
    packageName?: string;
}
type UseConductorProjectBuilderOptions = UseConductorProjectBuilderOptionsJava | UseConductorProjectBuilderOptionsGo | UseConductorProjectBuilderOptionsPython | UseConductorProjectBuilderOptionsJavaScript | UseConductorProjectBuilderOptionsCSharp | UseConductorProjectBuilderOptionsClojure | UseConductorProjectBuilderOptionsGroovy;
interface UseConductorProjectBuilderReturn {
    displayCode: string;
    onDownload: () => Promise<void>;
}
export declare const useConductorProjectBuilder: (options: UseConductorProjectBuilderOptions) => UseConductorProjectBuilderReturn;
export {};

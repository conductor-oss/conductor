import {
  CodeLanguage,
  JavaLanguageSet,
} from "components/GetStartedSample/types";
import { useState, useEffect, useCallback } from "react";

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

type UseConductorProjectBuilderOptions =
  | UseConductorProjectBuilderOptionsJava
  | UseConductorProjectBuilderOptionsGo
  | UseConductorProjectBuilderOptionsPython
  | UseConductorProjectBuilderOptionsJavaScript
  | UseConductorProjectBuilderOptionsCSharp
  | UseConductorProjectBuilderOptionsClojure
  | UseConductorProjectBuilderOptionsGroovy;

interface UseConductorProjectBuilderReturn {
  displayCode: string;
  onDownload: () => Promise<void>;
}

const BASE_URL = "https://m9mk8uem2r.us-east-1.awsapprunner.com/";

export const useConductorProjectBuilder = (
  options: UseConductorProjectBuilderOptions,
): UseConductorProjectBuilderReturn => {
  const { apiKey, apiSecret, serverUrl, language, taskName, useEnvVars } =
    options;
  const [displayCode, setDisplayCode] = useState<string>("");

  const getUrl = useCallback(
    (isCode: boolean) => {
      const url = new URL(BASE_URL);

      if (language === CodeLanguage.JAVA) {
        const { languageSet, projectName, packageName } = options;

        if (languageSet === JavaLanguageSet.GRADLE) {
          url.pathname = isCode
            ? "project/worker/java/file/HelloWorldWorker.java"
            : "project/worker/java/project.zip";
        } else if (languageSet === JavaLanguageSet.SPRING_GRADLE) {
          url.pathname = isCode
            ? "project/worker/spring/file/Worker.java"
            : "project/worker/spring/project.zip";
        }

        if (isCode) {
          if (projectName) {
            url.searchParams.append("projectName", projectName);
          }
          if (packageName) {
            url.searchParams.append("packageName", packageName);
          }
        }
      }

      if (language === CodeLanguage.GO) {
        url.pathname = isCode
          ? "project/worker/go/file/worker.go"
          : "project/worker/go/project.zip";
      }

      if (language === CodeLanguage.PYTHON) {
        url.pathname = isCode
          ? "project/worker/python/file/worker.py"
          : "project/worker/python/project.zip";
      }

      if (language === CodeLanguage.JS) {
        url.pathname = isCode
          ? "project/worker/javascript/file/worker.js"
          : "project/worker/javascript/project.zip";
      }

      if (language === CodeLanguage.CSHARP) {
        const { namespace } = options;

        url.pathname = isCode
          ? "project/worker/csharp/file/Worker.cs"
          : "project/worker/csharp/project.zip";

        if (isCode) {
          if (namespace) {
            url.searchParams.append("namespace", namespace);
          }
        }
      }

      if (language === CodeLanguage.CLOJURE) {
        url.pathname = isCode
          ? "project/worker/clojure/file/core.clj"
          : "project/worker/clojure/project.zip";
      }

      if (language === CodeLanguage.GROOVY) {
        const { packageName } = options;

        url.pathname = isCode
          ? "project/worker/groovydsl/file/worker.groovy"
          : "project/worker/groovydsl/project.zip";

        if (isCode) {
          if (packageName) {
            url.searchParams.append("packageName", packageName);
          }
        }
      }

      if (isCode) {
        if (useEnvVars) {
          url.searchParams.append("useEnvVars", useEnvVars.toString());
        } else {
          if (apiKey) {
            url.searchParams.append("keyId", apiKey);
          }
          if (apiSecret) {
            url.searchParams.append("secret", apiSecret);
          }
        }
        url.searchParams.append("serverUrl", serverUrl);
        url.searchParams.append("taskName", taskName);
      }

      return url;
    },
    [language, useEnvVars, apiKey, apiSecret, serverUrl, taskName, options],
  );

  // Function to fetch the project code based on user inputs
  const fetchProjectCode = useCallback(async () => {
    try {
      const response = await fetch(getUrl(true), {
        method: "GET",
        headers: {
          "Content-Type": "application/json",
        },
      });

      if (!response.ok) {
        throw new Error(`Error fetching project code: ${response.statusText}`);
      }

      const code = await response.text();
      setDisplayCode(code || "No code available");
    } catch (error) {
      console.error("Error fetching project code:", error);
      setDisplayCode("Error fetching project code.");
    }
  }, [getUrl]);

  // Function to download the project as a file
  const onDownload = useCallback(async () => {
    try {
      const requestBody = {
        ...(apiKey && { keyId: apiKey }),
        ...(apiSecret && { secret: apiSecret }),
        ...(serverUrl && { serverUrl }),
        ...(taskName && { taskName }),
        ...(useEnvVars && { useEnvVars }),
        ...(options.language === CodeLanguage.JAVA && {
          projectName:
            (options as UseConductorProjectBuilderOptionsJava).projectName ||
            undefined,
          packageName:
            (options as UseConductorProjectBuilderOptionsJava).packageName ||
            undefined,
        }),
        ...(options.language === CodeLanguage.CSHARP && {
          namespace:
            (options as UseConductorProjectBuilderOptionsCSharp).namespace ||
            undefined,
        }),
        ...(options.language === CodeLanguage.GROOVY && {
          packageNacme:
            (options as UseConductorProjectBuilderOptionsGroovy).packageName ||
            undefined,
        }),
      };

      const response = await fetch(getUrl(false), {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(requestBody),
      });

      if (!response.ok) {
        throw new Error(`Error downloading project: ${response.statusText}`);
      }

      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute("download", `project.zip`);
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (error) {
      console.error("Error downloading project:", error);
    }
  }, [apiKey, apiSecret, serverUrl, taskName, useEnvVars, options, getUrl]);

  useEffect(() => {
    const delay = setTimeout(() => {
      fetchProjectCode();
    }, 500); // 500ms debounce delay

    return () => clearTimeout(delay);
  }, [fetchProjectCode]);

  return { displayCode, onDownload };
};

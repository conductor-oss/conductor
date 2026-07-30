import {
  AGENT_GUIDE_LANGUAGES,
  getAgentGuide,
  getAgentGuideLanguage,
} from "./manifest";

describe("agent guide manifest", () => {
  it("defaults to the Python Conductor guide", () => {
    const language = getAgentGuideLanguage("unsupported");
    const guide = getAgentGuide(language, "unsupported");

    expect(language.id).toBe("python");
    expect(guide.id).toBe("native");
  });

  it("only exposes supported frameworks for each language", () => {
    const typescript = getAgentGuideLanguage("typescript");
    const dotnet = getAgentGuideLanguage("dotnet");

    expect(typescript.guides.map(({ id }) => id)).toContain("vercel-ai");
    expect(dotnet.guides.map(({ id }) => id)).not.toContain("vercel-ai");
    expect(dotnet.label).toBe(".NET");
    expect(AGENT_GUIDE_LANGUAGES).toHaveLength(4);
  });

  it("uses Conductor connection variables in every guide", () => {
    const guides = AGENT_GUIDE_LANGUAGES.flatMap(({ guides }) => guides);

    guides.forEach(({ markdown }) => {
      expect(markdown).toContain("CONDUCTOR_SERVER_URL");
      expect(markdown).toContain("CONDUCTOR_AUTH_KEY");
      expect(markdown).toContain("CONDUCTOR_AUTH_SECRET");
      expect(markdown).not.toContain("AGENTSPAN_SERVER_URL");
      expect(markdown).not.toContain("AGENTSPAN_LLM_MODEL");
      expect(markdown).not.toMatch(
        /export (?:OPENAI|ANTHROPIC|GOOGLE_GEMINI)_API_KEY=/,
      );
      expect(markdown.trimStart()).not.toMatch(/^# /);
    });
  });

  it("selects the latest published SDK release in every guide", () => {
    const expectedInstallByLanguage = {
      python: "python -m pip install --upgrade --pre",
      typescript: "@io-orkes/conductor-javascript@latest",
      java: "org.conductoross:conductor-client-ai:latest.release",
      dotnet: "--prerelease",
    } as const;

    AGENT_GUIDE_LANGUAGES.forEach(({ id, guides }) => {
      guides.forEach(({ markdown }) => {
        expect(markdown).toContain(expectedInstallByLanguage[id]);
      });
    });

    const javaGuides = getAgentGuideLanguage("java").guides;
    javaGuides.forEach(({ markdown }) => {
      expect(markdown).not.toContain("org.conductoross:conductor-ai:");
    });
  });
});

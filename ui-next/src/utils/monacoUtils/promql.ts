// noinspection JSUnusedGlobalSymbols
const languageConfiguration = {
  // the default separators except `@$`
  // eslint-disable-next-line
  wordPattern: /(-?\d*\.\d\w*)|([^`~!#%^&*()\-=+\[{\]}\\|;:'",.<>\/?\s]+)/g,
  // Not possible to make comments in PromQL syntax
  comments: {
    lineComment: "#",
  },
  brackets: [
    ["{", "}"],
    ["[", "]"],
    ["(", ")"],
  ],
  autoClosingPairs: [
    { open: "{", close: "}" },
    { open: "[", close: "]" },
    { open: "(", close: ")" },
    { open: '"', close: '"' },
    { open: "'", close: "'" },
  ],
  surroundingPairs: [
    { open: "{", close: "}" },
    { open: "[", close: "]" },
    { open: "(", close: ")" },
    { open: '"', close: '"' },
    { open: "'", close: "'" },
    { open: "<", close: ">" },
  ],
  folding: {},
};

// PromQL Aggregation Operators
// (https://prometheus.io/docs/prometheus/latest/querying/operators/#aggregation-operators)
const aggregations = [
  "sum",
  "min",
  "max",
  "avg",
  "group",
  "stddev",
  "stdvar",
  "count",
  "count_values",
  "bottomk",
  "topk",
  "quantile",
];

// PromQL functions
// (https://prometheus.io/docs/prometheus/latest/querying/functions/)
const functions = [
  "abs",
  "absent",
  "ceil",
  "changes",
  "clamp_max",
  "clamp_min",
  "day_of_month",
  "day_of_week",
  "days_in_month",
  "delta",
  "deriv",
  "exp",
  "floor",
  "histogram_quantile",
  "holt_winters",
  "hour",
  "idelta",
  "increase",
  "irate",
  "label_join",
  "label_replace",
  "ln",
  "log2",
  "log10",
  "minute",
  "month",
  "predict_linear",
  "rate",
  "resets",
  "round",
  "scalar",
  "sort",
  "sort_desc",
  "sqrt",
  "time",
  "timestamp",
  "vector",
  "year",
];

// PromQL specific functions: Aggregations over time
// (https://prometheus.io/docs/prometheus/latest/querying/functions/#aggregation_over_time)
const aggregationsOverTime = [];
for (const agg of aggregations) {
  aggregationsOverTime.push(agg + "_over_time");
}

// PromQL vector matching + the by and without clauses
// (https://prometheus.io/docs/prometheus/latest/querying/operators/#vector-matching)
const vectorMatching = [
  "on",
  "ignoring",
  "group_right",
  "group_left",
  "by",
  "without",
];
// Produce a regex matching elements : (elt1|elt2|...)
const vectorMatchingRegex = `(${vectorMatching.reduce(
  (prev, curr) => `${prev}|${curr}`,
)})`;

// PromQL Operators
// (https://prometheus.io/docs/prometheus/latest/querying/operators/)
const operators = [
  "+",
  "-",
  "*",
  "/",
  "%",
  "^",
  "==",
  "!=",
  ">",
  "<",
  ">=",
  "<=",
  "and",
  "or",
  "unless",
];

// PromQL offset modifier
// (https://prometheus.io/docs/prometheus/latest/querying/basics/#offset-modifier)
const offsetModifier = ["offset"];

// Merging all the keywords in one list
const keywords = aggregations
  .concat(functions)
  .concat(aggregationsOverTime)
  .concat(vectorMatching)
  .concat(offsetModifier);

// noinspection JSUnusedGlobalSymbols
const language = {
  ignoreCase: false,
  defaultToken: "",
  tokenPostfix: ".promql",

  keywords: keywords,

  operators: operators,
  vectorMatching: vectorMatchingRegex,

  // we include these common regular expressions

  // eslint-disable-next-line
  symbols: /[=><!~?:&|+\-*\/^%]+/,
  escapes:
    /\\(?:[abfnrtv\\"']|x[0-9A-Fa-f]{1,4}|u[0-9A-Fa-f]{4}|U[0-9A-Fa-f]{8})/,
  digits: /\d+(_+\d+)*/,
  octaldigits: /[0-7]+(_+[0-7]+)*/,
  binarydigits: /[0-1]+(_+[0-1]+)*/,
  hexdigits: /[[0-9a-fA-F]+(_+[0-9a-fA-F]+)*/,
  integersuffix: /(ll|LL|u|U|l|L)?(ll|LL|u|U|l|L)?/,
  floatsuffix: /[fFlL]?/,

  // The main tokenizer for our languages
  tokenizer: {
    root: [
      // 'by', 'without' and vector matching
      [/@vectorMatching\s*(?=\()/, "type", "@clauses"],

      // labels
      [/[a-z_]\w*(?=\s*(=|!=|=~|!~))/, "tag"],

      // comments
      [/(^#.*$)/, "comment"],

      // all keywords have the same color
      [
        /[a-zA-Z_]\w*/,
        {
          cases: {
            "@keywords": "type",
            "@default": "identifier",
          },
        },
      ],

      // strings
      [/"([^"\\]|\\.)*$/, "string.invalid"], // non-teminated string
      [/'([^'\\]|\\.)*$/, "string.invalid"], // non-teminated string
      [/"/, "string", "@string_double"],
      [/'/, "string", "@string_single"],
      [/`/, "string", "@string_backtick"],

      // whitespace
      { include: "@whitespace" },

      // delimiters and operators

      // eslint-disable-next-line
      [/[{}()\[\]]/, "@brackets"],
      [/[<>](?!@symbols)/, "@brackets"],
      [
        /@symbols/,
        {
          cases: {
            "@operators": "delimiter",
            "@default": "",
          },
        },
      ],

      // numbers
      [/\d+[smhdwy]/, "number"], // 24h, 5m are often encountered in prometheus

      // eslint-disable-next-line
      [/\d*\d+[eE]([\-+]?\d+)?(@floatsuffix)/, "number.float"],

      // eslint-disable-next-line
      [/\d*\.\d+([eE][\-+]?\d+)?(@floatsuffix)/, "number.float"],
      [/0[xX][0-9a-fA-F']*[0-9a-fA-F](@integersuffix)/, "number.hex"],
      [/0[0-7']*[0-7](@integersuffix)/, "number.octal"],
      [/0[bB][0-1']*[0-1](@integersuffix)/, "number.binary"],
      [/\d[\d']*\d(@integersuffix)/, "number"],
      [/\d(@integersuffix)/, "number"],
    ],

    string_double: [
      [/[^\\"]+/, "string"],
      [/@escapes/, "string.escape"],
      [/\\./, "string.escape.invalid"],
      [/"/, "string", "@pop"],
    ],

    string_single: [
      [/[^\\']+/, "string"],
      [/@escapes/, "string.escape"],
      [/\\./, "string.escape.invalid"],
      [/'/, "string", "@pop"],
    ],

    string_backtick: [
      [/[^\\`$]+/, "string"],
      [/@escapes/, "string.escape"],
      [/\\./, "string.escape.invalid"],
      [/`/, "string", "@pop"],
    ],

    clauses: [
      [/[^(,)]/, "tag"],
      [/\)/, "identifier", "@pop"],
    ],

    whitespace: [[/[ \t\r\n]+/, "white"]],
  },
};

// noinspection JSUnusedGlobalSymbols
const loadLanguage = (monaco: any) => ({
  id: "promql",
  extensions: [".promql"],
  aliases: [
    "Prometheus",
    "prometheus",
    "prom",
    "Prom",
    "promql",
    "Promql",
    "promQL",
    "PromQL",
  ],
  mimetypes: [],
  loader: () =>
    Promise.resolve({
      language,
      languageConfiguration,
      completionItemProvider: {
        provideCompletionItems: () => {
          // To simplify, we made the choice to never create automatically the parenthesis behind keywords
          // It is because in PromQL, some keywords need parenthesis behind, some don't, some can have but it's optional.
          const suggestions = keywords.map((value) => {
            return {
              label: value,
              kind: monaco.languages.CompletionItemKind.Keyword,
              insertText: value,
              insertTextRules:
                monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            };
          });

          return { suggestions };
        },
      },
    }),
});

export const registerPromQlLangauge = (monaco: any) => {
  const promLanguageDefinition = loadLanguage(monaco);
  const languageId = promLanguageDefinition.id;
  monaco.languages.register(promLanguageDefinition);
  monaco.languages.onLanguage(languageId, () => {
    promLanguageDefinition.loader().then((mod) => {
      monaco.languages.setMonarchTokensProvider(languageId, mod.language);
      monaco.languages.setLanguageConfiguration(
        languageId,
        mod.languageConfiguration,
      );
      monaco.languages.registerCompletionItemProvider(
        languageId,
        mod.completionItemProvider,
      );
    });
  });
};

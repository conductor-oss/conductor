import { flipObject } from "./object";

// This util means to replace console.log
type LogFunction = typeof console.log;

interface Logger {
  debug: LogFunction;
  info: LogFunction;
  log: LogFunction;
  warn: LogFunction;
  error: LogFunction;
}

enum LogLevels {
  debug = "debug",
  info = "info",
  log = "log",
  warn = "warn",
  error = "error",
}

// Defines the oder
const LEVEL_ARRAY = [
  LogLevels.debug,
  LogLevels.info,
  LogLevels.log,
  LogLevels.warn,
  LogLevels.error,
];

const arrayAsObject: Record<number, string> = Object.assign({}, LEVEL_ARRAY);
const LevelOrderObject = flipObject(arrayAsObject);

const MIN_LOG_LEVEL_IDX =
  LevelOrderObject[
    process.env.NODE_ENV === "development" ? LogLevels.debug : LogLevels.warn
  ];

const log = (level: LogLevels) => {
  const levelIdx = LevelOrderObject[level];
  return (...params: unknown[]) => {
    if (levelIdx >= MIN_LOG_LEVEL_IDX) {
      console[level](...params);
    }
  };
};

const logger: Logger = LEVEL_ARRAY.reduce(
  (acc, curLevel) => ({ ...acc, [curLevel]: log(curLevel) }),
  {},
) as Logger;

export { logger };

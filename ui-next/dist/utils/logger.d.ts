type LogFunction = typeof console.log;
interface Logger {
    debug: LogFunction;
    info: LogFunction;
    log: LogFunction;
    warn: LogFunction;
    error: LogFunction;
}
declare const logger: Logger;
export { logger };

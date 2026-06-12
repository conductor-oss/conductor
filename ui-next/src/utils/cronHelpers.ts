import cron from "cron-validate";

export const cronExpressionIsValid = (
  cronExpression: string,
): { isValid: boolean; errors: any } => {
  try {
    const cronResult = cron(cronExpression, {
      preset: "default",
      override: {
        // seconds field
        useSeconds: true,
        // the ? alias
        useBlankDay: true,
        // aliases like 'mon'
        useAliases: true,
        // allow 'L' for last day of month
        useLastDayOfMonth: true,
        // allow 'W' for last day of week
        useLastDayOfWeek: true,
        useNearestWeekday: true,
        useNthWeekdayOfMonth: true,
      },
    });
    if (cronResult.isValid()) {
      return {
        isValid: true,
        errors: null,
      };
    } else {
      return {
        isValid: false,
        errors: cronResult.getError(),
      };
    }
  } catch (e: any) {
    return {
      isValid: false,
      errors: [e.message],
    };
  }
};

const isDev = import.meta.env.DEV;

export const clientLog = {
  warn(message: string, context?: unknown) {
    if (isDev) {
      console.warn(message, context);
    }
  },
  error(message: string, context?: unknown) {
    if (isDev) {
      console.error(message, context);
    }
  },
};

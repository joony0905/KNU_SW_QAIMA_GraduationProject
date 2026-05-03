/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  darkMode: "class",
  theme: {
    extend: {
      colors: {
        bg: "rgb(var(--color-bg) / <alpha-value>)",
        "bg-soft": "rgb(var(--color-bg-soft) / <alpha-value>)",
        "bg-sunk": "rgb(var(--color-bg-sunk) / <alpha-value>)",
        surface: "rgb(var(--color-surface) / <alpha-value>)",
        "surface-2": "rgb(var(--color-surface-2) / <alpha-value>)",

        ink: "rgb(var(--color-ink) / <alpha-value>)",
        "ink-2": "rgb(var(--color-ink-2) / <alpha-value>)",
        "ink-3": "rgb(var(--color-ink-3) / <alpha-value>)",
        "ink-4": "rgb(var(--color-ink-4) / <alpha-value>)",

        line: "rgb(var(--color-line) / <alpha-value>)",
        "line-strong": "rgb(var(--color-line-strong) / <alpha-value>)",

        accent: {
          DEFAULT: "rgb(var(--color-accent) / <alpha-value>)",
          ink: "rgb(var(--color-accent-ink) / <alpha-value>)",
          soft: "rgb(var(--color-accent-soft) / <alpha-value>)",
        },

        rise: {
          DEFAULT: "rgb(var(--color-rise) / <alpha-value>)",
          soft: "rgb(var(--color-rise-soft) / <alpha-value>)",
        },
        fall: {
          DEFAULT: "rgb(var(--color-fall) / <alpha-value>)",
          soft: "rgb(var(--color-fall-soft) / <alpha-value>)",
        },
        flat: "rgb(var(--color-flat) / <alpha-value>)",

        success: "rgb(var(--color-success) / <alpha-value>)",
        warn: "rgb(var(--color-warn) / <alpha-value>)",
        danger: "rgb(var(--color-danger) / <alpha-value>)",
      },

      borderRadius: {
        sm: "6px",
        DEFAULT: "10px",
        md: "12px",
        lg: "14px",
        xl: "16px",
        "2xl": "20px",
        "3xl": "28px",
      },

      boxShadow: {
        card: "0 1px 2px rgb(0 0 0 / 0.04), 0 8px 24px rgb(0 0 0 / 0.04)",
        "card-hover":
          "0 2px 4px rgb(0 0 0 / 0.06), 0 12px 32px rgb(0 0 0 / 0.08)",
        pop: "0 8px 28px rgb(0 0 0 / 0.12)",
        focus: "0 0 0 3px rgb(var(--color-accent) / 0.18)",
      },

      fontFamily: {
        sans: [
          "Pretendard",
          "Pretendard Variable",
          "-apple-system",
          "BlinkMacSystemFont",
          "Segoe UI",
          "Apple SD Gothic Neo",
          "Noto Sans KR",
          "sans-serif",
        ],
        mono: [
          "JetBrains Mono",
          "SF Mono",
          "Menlo",
          "Consolas",
          "monospace",
        ],
      },

      letterSpacing: {
        tight: "-0.015em",
        tighter: "-0.025em",
      },
    },
  },
  plugins: [],
};

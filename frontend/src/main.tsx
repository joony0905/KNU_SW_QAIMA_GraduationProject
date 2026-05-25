// src/main.tsx
import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App.tsx";
import { DictProvider } from "./components/DictContext.tsx";
import { DictSeenScope } from "./components/DictSeenScope.tsx";
import "./i18n";
import "./index.css";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <BrowserRouter>
      <DictProvider>
        <DictSeenScope>
          <App />
        </DictSeenScope>
      </DictProvider>
    </BrowserRouter>
  </React.StrictMode>
);

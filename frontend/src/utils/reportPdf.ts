import html2canvas from "html2canvas-pro";
import jsPDF from "jspdf";

const loadImageElement = (src: string): Promise<HTMLImageElement> =>
  new Promise((resolve, reject) => {
    const img = new Image();
    img.crossOrigin = "anonymous";
    img.onload = () => resolve(img);
    img.onerror = reject;
    img.src = src;
  });

export const waitForPdfCaptureReady = async (settleMs = 700) => {
  await new Promise<void>((resolve) =>
    requestAnimationFrame(() => requestAnimationFrame(() => resolve())),
  );
  await new Promise((resolve) => setTimeout(resolve, settleMs));
};

export const downloadElementAsPdf = async (
  element: HTMLElement,
  fileName: string,
  logoSrc?: string,
) => {
  element.classList.add("qaima-pdf-capture");
  element.querySelectorAll(".qaima-scroll-stagger > *").forEach((node) => {
    if (node instanceof HTMLElement) node.classList.add("is-visible");
  });

  try {
    const [fullCanvas, logoImg] = await Promise.all([
      html2canvas(element, {
        scale: 2,
        useCORS: true,
        backgroundColor: "#ffffff",
        windowWidth: element.scrollWidth,
        windowHeight: element.scrollHeight,
        ignoreElements: (candidate) =>
          candidate instanceof HTMLElement &&
          candidate.hasAttribute("data-pdf-exclude"),
        onclone: (clonedDocument) => {
          const clonedRoot = clonedDocument.querySelector(".qaima-pdf-capture");
          if (!(clonedRoot instanceof HTMLElement)) return;

          clonedRoot.querySelectorAll(".qaima-scroll-stagger > *").forEach((node) => {
            if (node instanceof HTMLElement) node.classList.add("is-visible");
          });
        },
      }),
      logoSrc ? loadImageElement(logoSrc).catch(() => null) : Promise.resolve(null),
    ]);

    const doc = new jsPDF("p", "mm", "a4");
    const pdfWidth = doc.internal.pageSize.getWidth();
    const pdfHeight = doc.internal.pageSize.getHeight();
    const margin = 10;
    const usableWidth = pdfWidth - margin * 2;
    const usableHeight = pdfHeight - margin * 2;
    const pxPerMm = fullCanvas.width / usableWidth;
    const pageSlicePx = Math.floor(usableHeight * pxPerMm);

    let yPx = 0;
    let pageIndex = 0;

    while (yPx < fullCanvas.height) {
      const sliceHeightPx = Math.min(pageSlicePx, fullCanvas.height - yPx);
      const slice = document.createElement("canvas");
      slice.width = fullCanvas.width;
      slice.height = sliceHeightPx;
      const ctx = slice.getContext("2d");
      if (!ctx) throw new Error("Canvas context unavailable");

      ctx.fillStyle = "#ffffff";
      ctx.fillRect(0, 0, slice.width, slice.height);
      ctx.drawImage(fullCanvas, 0, -yPx);

      const sliceImg = slice.toDataURL("image/png");
      const sliceImgHeightMm = sliceHeightPx / pxPerMm;

      if (pageIndex > 0) doc.addPage();
      doc.addImage(sliceImg, "PNG", margin, margin, usableWidth, sliceImgHeightMm);

      yPx += sliceHeightPx;
      pageIndex++;
    }

    if (logoImg && logoImg.naturalWidth > 0) {
      const docGState = doc as unknown as {
        GState: new (opts: { opacity: number }) => unknown;
        setGState: (g: unknown) => void;
      };
      const wmWidth = pdfWidth * 0.5;
      const wmHeight = wmWidth * (logoImg.naturalHeight / logoImg.naturalWidth);
      const wmX = (pdfWidth - wmWidth) / 2;
      const wmY = (pdfHeight - wmHeight) / 2;
      const pageCount = doc.getNumberOfPages();

      for (let p = 1; p <= pageCount; p++) {
        doc.setPage(p);
        doc.saveGraphicsState();
        docGState.setGState(new docGState.GState({ opacity: 0.08 }));
        doc.addImage(logoImg, "PNG", wmX, wmY, wmWidth, wmHeight);
        doc.restoreGraphicsState();
      }
    }

    doc.save(fileName);
  } finally {
    element.classList.remove("qaima-pdf-capture");
  }
};

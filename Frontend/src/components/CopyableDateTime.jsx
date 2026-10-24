import { formatDateTime } from "../utils/chart.js";
import "./CopyableDateTime.css";

function fallbackCopy(text) {
  const textarea = document.createElement("textarea");
  textarea.value = text;
  textarea.setAttribute("readonly", "");
  textarea.style.position = "fixed";
  textarea.style.opacity = "0";
  document.body.appendChild(textarea);
  textarea.select();
  const copied = document.execCommand("copy");
  textarea.remove();
  if (!copied) throw new Error("Trình duyệt không hỗ trợ sao chép.");
}

async function copyText(text) {
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text);
      return;
    } catch {
      // Một số trình duyệt chặn Clipboard API nhưng vẫn hỗ trợ execCommand.
    }
  }
  fallbackCopy(text);
}

export default function CopyableDateTime({ value }) {
  const formatted = formatDateTime(value);

  async function handleCopy() {
    try {
      await copyText(formatted);
    } catch (error) {
      console.error("Không thể sao chép thời gian:", error);
    }
  }

  return (
    <span className="copyable-time">
      <time dateTime={value || undefined}>{formatted}</time>
      <button
        className="copy-time-button"
        type="button"
        aria-label={`Sao chép ${formatted}`}
        title="Sao chép thời gian"
        onClick={handleCopy}
      >
        <i className="fa-solid fa-copy" aria-hidden="true" />
      </button>
    </span>
  );
}

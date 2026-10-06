export const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export type Mode = { id: string; label: string; description: string; requiresTone: boolean };
export type Tone = { id: string; label: string };
export type Catalog = { maxTextLength: number; modes: Mode[]; tones: Tone[] };

export type RewriteParams = { text: string; mode: string; tone?: string };

export async function fetchCatalog(signal?: AbortSignal): Promise<Catalog> {
  const res = await fetch(`${API_URL}/api/modes`, { signal });
  if (!res.ok) throw new Error("Could not load the server configuration.");
  return res.json();
}

/**
 * Calls the streaming endpoint and invokes `onToken` for every text fragment.
 * Uses fetch (not EventSource) because the endpoint is a POST.
 * Throws an Error with a user-friendly message on any failure.
 */
export async function streamRewrite(
  params: RewriteParams,
  onToken: (text: string) => void,
  signal: AbortSignal,
): Promise<void> {
  let res: Response;
  try {
    res = await fetch(`${API_URL}/api/rewrite`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(params),
      signal,
    });
  } catch (e) {
    if (signal.aborted) throw e;
    throw new Error("Could not connect to the server.");
  }

  if (!res.ok || !res.body) {
    const body = await res.json().catch(() => null);
    throw new Error(body?.message ?? "An error occurred while processing the request.");
  }

  const reader = res.body.pipeThrough(new TextDecoderStream()).getReader();
  let buffer = "";

  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += value;

    // SSE events are separated by a blank line.
    const events = buffer.split(/\r?\n\r?\n/);
    buffer = events.pop() ?? "";
    for (const raw of events) handleEvent(raw, onToken);
  }
}

function handleEvent(raw: string, onToken: (text: string) => void) {
  let name = "message";
  const data: string[] = [];
  for (const line of raw.split(/\r?\n/)) {
    if (line.startsWith("event:")) name = line.slice(6).trim();
    else if (line.startsWith("data:")) data.push(line.slice(5).trimStart());
  }
  if (data.length === 0) return;

  const payload = JSON.parse(data.join("\n"));
  if (name === "token") onToken(payload.text);
  else if (name === "error") throw new Error(payload.message ?? "An error occurred.");
}

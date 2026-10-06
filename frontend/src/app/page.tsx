"use client";

import { useEffect, useState } from "react";
import { type Catalog, fetchCatalog, streamRewrite } from "@/lib/api";

export default function Home() {
  const [catalog, setCatalog] = useState<Catalog | null>(null);
  const [mode, setMode] = useState("CORRECT");
  const [tone, setTone] = useState("FRIENDLY");
  const [text, setText] = useState("");
  const [result, setResult] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [copied, setCopied] = useState(false);

  // Load the available modes and tones from the backend once.
  useEffect(() => {
    fetchCatalog().then(setCatalog).catch((e) => setError(e.message));
  }, []);

  const currentMode = catalog?.modes.find((m) => m.id === mode);

  async function generate() {
    setLoading(true);
    setError("");
    setResult("");
    try {
      await streamRewrite(
        { text, mode, tone: currentMode?.requiresTone ? tone : undefined },
        (token) => setResult((prev) => prev + token),
        new AbortController().signal,
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unexpected error.");
    } finally {
      setLoading(false);
    }
  }

  async function copy() {
    await navigator.clipboard.writeText(result);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  }

  return (
    <main className="container py-5" style={{ maxWidth: 720 }}>
      <h1 className="mb-1">Writing Assistant</h1>
      <p className="text-muted mb-4">Fix, summarize and rewrite your text with AI.</p>

      <div className="row g-3 mb-3">
        <div className="col">
          <label className="form-label" htmlFor="mode">Mode</label>
          <select id="mode" className="form-select" value={mode} onChange={(e) => setMode(e.target.value)}>
            {catalog?.modes.map((m) => (
              <option key={m.id} value={m.id}>{m.label}</option>
            ))}
          </select>
          <div className="form-text">{currentMode?.description}</div>
        </div>

        {currentMode?.requiresTone && (
          <div className="col">
            <label className="form-label" htmlFor="tone">Tone</label>
            <select id="tone" className="form-select" value={tone} onChange={(e) => setTone(e.target.value)}>
              {catalog?.tones.map((t) => (
                <option key={t.id} value={t.id}>{t.label}</option>
              ))}
            </select>
          </div>
        )}
      </div>

      <label className="form-label" htmlFor="text">Your text</label>
      <textarea
        id="text"
        className="form-control mb-1"
        rows={6}
        maxLength={catalog?.maxTextLength}
        value={text}
        onChange={(e) => setText(e.target.value)}
        placeholder="Paste or type your text here…"
      />
      <div className="form-text mb-3">{text.length} / {catalog?.maxTextLength ?? "…"} characters</div>

      <button className="btn btn-primary mb-4" onClick={generate} disabled={loading || !catalog || !text.trim()}>
        {loading ? "Generating…" : "Generate"}
      </button>

      {error && <div className="alert alert-danger">{error}</div>}

      {(result || loading) && (
        <div className="card">
          <div className="card-header d-flex justify-content-between align-items-center">
            Result
            <button className="btn btn-sm btn-outline-secondary" onClick={copy} disabled={loading || !result}>
              {copied ? "Copied!" : "Copy"}
            </button>
          </div>
          <div className="card-body" style={{ whiteSpace: "pre-wrap" }}>{result}</div>
        </div>
      )}
    </main>
  );
}

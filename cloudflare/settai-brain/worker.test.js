import { env } from "cloudflare:workers";
import { afterEach, describe, expect, it, vi } from "vitest";
import worker, { MODEL } from "./worker.js";

const req = (input, headers = {}) => new Request("https://example.test/v1/chat", { method: "POST", headers, body: typeof input === "string" ? input : JSON.stringify(input) });
const input = (id = crypto.randomUUID()) => ({ userId: id, characterId: "devil", system: "You are Kutti Satan.", bodySense: "Standing.", message: "Hello", history: [] });
afterEach(() => vi.unstubAllGlobals());
describe("Set-Tai gateway", () => {
  it("health is keyless and never calls Gemini", async () => {
    const f = vi.fn(); vi.stubGlobal("fetch", f);
    const r = await worker.fetch(new Request("https://example.test/health"), {});
    expect(await r.json()).toEqual({ ok: true, model: MODEL }); expect(f).not.toHaveBeenCalled();
  });
  it("rejects missing user, malformed JSON, wrong routes/methods and streamed oversized input before Gemini", async () => {
    const f = vi.fn(); vi.stubGlobal("fetch", f);
    expect((await worker.fetch(req({ message: "hi" }), env)).status).toBe(400);
    expect((await worker.fetch(req("{"), env)).status).toBe(400);
    expect((await worker.fetch(req("x".repeat(8193)), env)).status).toBe(413);
    expect((await worker.fetch(new Request("https://example.test/other"), env)).status).toBe(404);
    expect((await worker.fetch(new Request("https://example.test/v1/chat"), env)).status).toBe(405);
    expect(f).not.toHaveBeenCalled();
  });
  it("uses exact model/minimal thinking, keeps key out of URL and truncates Unicode bubble", async () => {
    const f = vi.fn(async (url, init) => {
      expect(url).toBe(`https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent`);
      expect(url).not.toContain(env.GEMINI_API_KEY);
      const body = JSON.parse(init.body);
      expect(body.generationConfig.maxOutputTokens).toBe(160);
      expect(body.generationConfig.thinkingConfig).toEqual({ thinkingLevel: "minimal" });
      expect(body.generationConfig).not.toHaveProperty("temperature");
      expect(body.generationConfig).not.toHaveProperty("topP");
      expect(body.generationConfig).not.toHaveProperty("topK");
      return Response.json({ candidates: [{ content: { parts: [{ text: "hidden", thought: true }, { text: "😈".repeat(250) }] } }] });
    }); vi.stubGlobal("fetch", f);
    const data = input(); const r = await worker.fetch(req(data), env); const out = await r.json();
    expect(r.status).toBe(200); expect(Array.from(out.text)).toHaveLength(200); expect(out.leftToday).toBe(39);
    const kv = await env.QUOTA.list();
    expect(kv.keys.some(k => k.name.includes(data.userId))).toBe(false);
    expect(kv.keys.some(k => /^[a-f0-9]{64}:\d{4}-\d{2}-\d{2}$/.test(k.name))).toBe(true);
  });

  it("allows a longer Unicode reply only when the user asked for more detail", async () => {
    const f = vi.fn(async (_url, init) => {
      const body = JSON.parse(init.body);
      expect(body.generationConfig.maxOutputTokens).toBe(320);
      return Response.json({ candidates: [{ content: { parts: [{ text: "😈".repeat(700) }] } }] });
    }); vi.stubGlobal("fetch", f);
    const data = input(); data.message = "Please explain that more fully";
    const r = await worker.fetch(req(data), env); const out = await r.json();
    expect(r.status).toBe(200); expect(Array.from(out.text)).toHaveLength(560); expect(out.text.endsWith("…")).toBe(true);
  });
  it("a show request (task: show) gets the story-line guard and a longer reply", async () => {
    const f = vi.fn(async (url, init) => {
      const body = JSON.parse(init.body);
      expect(body.generationConfig.maxOutputTokens).toBe(600);
      expect(body.systemInstruction.parts[0].text).toContain("ONLY with the story lines");
      expect(body.systemInstruction.parts[0].text).not.toContain("own voice");
      return Response.json({ candidates: [{ content: { parts: [{ text: "a".repeat(1500) }] } }] });
    }); vi.stubGlobal("fetch", f);
    const out = await (await worker.fetch(req({ ...input(), task: "show", message: "Write a show: a chase" }), env)).json();
    expect(Array.from(out.text)).toHaveLength(1400);
  });
  it("40 atomic reservations hold even concurrently; next day resets; users are independent", async () => {
    const q = env.QUOTA_GATE.getByName(crypto.randomUUID());
    const results = await Promise.all(Array.from({ length: 60 }, () => q.reserve("2026-11-06")));
    expect(results.filter(r => r.allowed)).toHaveLength(40);
    expect(results.filter(r => !r.allowed)).toHaveLength(20);
    expect(await q.reserve("2026-11-06")).toMatchObject({ allowed: false, leftToday: 0 });
    expect(await q.reserve("2026-11-07")).toMatchObject({ allowed: true, leftToday: 39 });
    expect(await env.QUOTA_GATE.getByName(crypto.randomUUID()).reserve("2026-11-06")).toMatchObject({ allowed: true, leftToday: 39 });
  });
  it("HTTP 429 does not call Gemini", async () => {
    const data = input();
    const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(data.userId));
    const hash = Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2, "0")).join("");
    const q = env.QUOTA_GATE.getByName(hash); const day = new Date().toISOString().slice(0, 10);
    for (let n = 0; n < 40; n++) await q.reserve(day);
    const f = vi.fn(); vi.stubGlobal("fetch", f);
    const r = await worker.fetch(req(data), env);
    expect(r.status).toBe(429); expect(await r.json()).toEqual({ error: "daily_limit", leftToday: 0 });
    expect(f).not.toHaveBeenCalled();
  });
  it("upstream failures are safe, count attempts and never expose raw errors/key", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(`secret=${env.GEMINI_API_KEY}`, { status: 400 })));
    const r = await worker.fetch(req(input()), env);
    expect(r.status).toBe(502); const text = await r.text(); expect(text).not.toContain(env.GEMINI_API_KEY);
    expect(JSON.parse(text)).toEqual({ error: "gemini_unavailable", upstreamStatus: 400, leftToday: 39 });
  });
});

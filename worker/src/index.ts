import { XMLParser } from "fast-xml-parser";
import { Article, DailyBrief, GeminiResponse } from "./types";
import { FEEDS } from "./feeds";
import { buildPrompt } from "./prompt";

const EXCLUDE_PATTERNS = /\b(murder|killed|shooting|stabbing|crash|killed in|bombed|deadly)\b/i;
const INCLUDE_PATTERNS = /\b(sanctions|trade|treaty|summit|protest|policy|economic|market|election|tariff|regulation|reform|climate|pandemic|migration|budget|inflation|crisis|agreement|alliance)\b/i;

interface Env {
  BRIEF_KV: KVNamespace;
  GEMINI_API_KEY: string;
  ENVIRONMENT?: string;
}

const xmlParser = new XMLParser({
  ignoreAttributes: false,
  attributeNamePrefix: "@_",
});

async function parseFeed(feedUrl: string): Promise<{ title: string; description: string; link: string }[]> {
  const resp = await fetch(feedUrl, {
    headers: { "User-Agent": "NotizieBrief/1.0" },
  });
  if (!resp.ok) return [];

  const xml = await resp.text();
  const parsed = xmlParser.parse(xml);

  const channel = parsed.rss?.channel ?? parsed.feed ?? {};
  const items = channel.item ?? channel.entry ?? [];
  const raw = Array.isArray(items) ? items : [items];

  return raw.map((item: any) => ({
    title: typeof item.title === "object" ? item.title?.["#text"] ?? item.title?._ ?? "" : String(item.title ?? ""),
    description: typeof item.description === "object" ? item.description?.["#text"] ?? item.description?._ ?? "" : String(item.description ?? ""),
    link: typeof item.link === "object" ? item.link?.["@_href"] ?? item.link?.["#text"] ?? "" : String(item.link ?? ""),
  }));
}

function isRelevant(article: { title: string; description: string }): boolean {
  const text = `${article.title} ${article.description}`;
  const excluded = EXCLUDE_PATTERNS.test(text);
  const included = INCLUDE_PATTERNS.test(text);
  return !excluded || included;
}

async function fetchAllFeeds(): Promise<Article[]> {
  const results = await Promise.allSettled(
    FEEDS.map(async (feed) => {
      try {
        const items = await parseFeed(feed.url);
        return items.map((item) => ({
          title: item.title.slice(0, 300),
          description: item.description.replace(/<[^>]*>/g, "").slice(0, 300),
          link: item.link,
          source: feed.source,
          category: feed.category,
        }));
      } catch {
        console.error(`Failed to fetch: ${feed.source}`);
        return [];
      }
    })
  );

  return results
    .flatMap((r) => (r.status === "fulfilled" ? r.value : []))
    .filter((a) => a.title && isRelevant(a))
    .slice(0, 80);
}

async function callGemini(
  articles: Article[],
  apiKey: string
): Promise<GeminiResponse> {
  const { system, user } = buildPrompt(articles);

  const resp = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${apiKey}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        system_instruction: { parts: [{ text: system }] },
        contents: [{ parts: [{ text: user }] }],
        generationConfig: {
          temperature: 0.3,
          maxOutputTokens: 8192,
        },
      }),
    }
  );

  if (!resp.ok) {
    const err = await resp.text();
    throw new Error(`Gemini API error (${resp.status}): ${err}`);
  }

  const data: any = await resp.json();
  const candidate = data?.candidates?.[0];
  const finishReason = candidate?.finishReason;
  const text = candidate?.content?.parts?.[0]?.text;

  if (finishReason && finishReason !== "STOP") {
    console.error(`Gemini finish reason: ${finishReason}`);
  }

  if (!text) {
    const safety = JSON.stringify(candidate?.safetyRatings ?? []);
    throw new Error(`No response from Gemini. finishReason=${finishReason} safety=${safety}`);
  }
  if (!text) throw new Error("No response from Gemini");

  const jsonBlock = text.match(/```(?:json)?\s*([\s\S]*?)```/);
  const jsonText = jsonBlock ? jsonBlock[1].trim() : text.trim();
  const cleaned = jsonText
    .replace(/\/\/.*$/gm, "")
    .replace(/\n\s*/g, " ")
    .replace(/,\s*]/g, "]");

  const parsed = JSON.parse(cleaned);
  if (!Array.isArray(parsed)) {
    throw new Error("Gemini response is not an array");
  }
  return parsed as GeminiResponse;
}

async function buildBrief(env: Env): Promise<DailyBrief> {
  const articles = await fetchAllFeeds();
  const result = await callGemini(articles, env.GEMINI_API_KEY);
  return {
    date: new Date().toISOString().split("T")[0],
    items: result,
  };
}

export default {
  async scheduled(event: ScheduledEvent, env: Env, ctx: ExecutionContext): Promise<void> {
    console.log("Starting daily brief pipeline...");
    const brief = await buildBrief(env);
    await env.BRIEF_KV.put("daily-brief", JSON.stringify(brief));
    console.log("Brief stored in KV");
  },

  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (url.pathname === "/trigger") {
      try {
        const brief = await buildBrief(env);
        await env.BRIEF_KV.put("daily-brief", JSON.stringify(brief));
        return new Response(JSON.stringify({ ok: true, brief: { date: brief.date, items: brief.items } }), {
          headers: { "Content-Type": "application/json" },
        });
      } catch (err: any) {
        return new Response(JSON.stringify({ ok: false, error: err.message }), {
          status: 500,
          headers: { "Content-Type": "application/json" },
        });
      }
    }

    const cached = await env.BRIEF_KV.get("daily-brief");
    if (!cached) {
      return new Response(JSON.stringify({ error: "No brief available yet" }), {
        status: 404,
        headers: { "Content-Type": "application/json" },
      });
    }
    return new Response(cached, {
      headers: {
        "Content-Type": "application/json",
        "Cache-Control": "public, max-age=3600",
      },
    });
  },
};

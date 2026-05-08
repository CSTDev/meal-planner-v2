import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

interface AuthRecord {
  id: string;
  email: string;
  raw_user_meta_data?: { name?: string; [key: string]: unknown };
}

interface WebhookPayload {
  type: "INSERT" | "UPDATE" | "DELETE";
  table: string;
  schema: string;
  record: AuthRecord | null;
  old_record: AuthRecord | null;
}

const RECIPE_SERVICE_URL = Deno.env.get("RECIPE_SERVICE_URL") ??
  "http://host.docker.internal:8080";
const INTERNAL_API_SECRET = Deno.env.get("INTERNAL_API_SECRET");

serve(async (req: Request) => {
  // Validate shared secret
  const webhookSecret = req.headers.get("X-Webhook-Secret");
  if (!INTERNAL_API_SECRET || webhookSecret !== INTERNAL_API_SECRET) {
    return new Response("Unauthorized", { status: 401 });
  }

  let payload: WebhookPayload;
  try {
    payload = await req.json();
  } catch {
    return new Response("Invalid JSON", { status: 400 });
  }

  const { type, record, old_record } = payload;

  try {
    if (type === "INSERT" || type === "UPDATE") {
      if (!record) {
        return new Response("Missing record", { status: 400 });
      }

      const name = record.raw_user_meta_data?.name ?? record.email;

      const res = await fetch(`${RECIPE_SERVICE_URL}/internal/users`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-Internal-Secret": INTERNAL_API_SECRET,
        },
        body: JSON.stringify({
          id: record.id,
          email: record.email,
          name,
        }),
      });

      if (!res.ok) {
        const body = await res.text();
        console.error(`Upsert failed: ${res.status} ${body}`);
        return new Response(`Upstream error: ${res.status}`, {
          status: 502,
        });
      }
    } else if (type === "DELETE") {
      const target = old_record ?? record;
      if (!target) {
        return new Response("Missing record for DELETE", { status: 400 });
      }

      const res = await fetch(
        `${RECIPE_SERVICE_URL}/internal/users/${target.id}`,
        {
          method: "DELETE",
          headers: {
            "X-Internal-Secret": INTERNAL_API_SECRET,
          },
        },
      );

      if (!res.ok) {
        const body = await res.text();
        console.error(`Delete failed: ${res.status} ${body}`);
        return new Response(`Upstream error: ${res.status}`, {
          status: 502,
        });
      }
    } else {
      return new Response(`Unknown event type: ${type}`, { status: 400 });
    }
  } catch (err) {
    console.error("Error calling recipe-service:", err);
    return new Response("Internal error", { status: 500 });
  }

  return new Response("OK", { status: 200 });
});

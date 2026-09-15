import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import { OAuth2Client } from 'google-auth-library';
import { SignJWT, jwtVerify } from 'jose';
import crypto from 'node:crypto';
import { Pool } from 'pg';
import { z } from 'zod';

const app = express();
const port = Number(process.env.PORT ?? 10000);
const googleClientId = process.env.GOOGLE_WEB_CLIENT_ID;
const adminEmail = (process.env.ADMIN_EMAIL ?? 'aslbekqoziboyev536@gmail.com').toLowerCase();
const serviceEmail = process.env.SERVICE_EMAIL ?? adminEmail;
const sessionSecret = process.env.SESSION_SECRET;
const paymentsBasicUsername = process.env.PAYMENTS_BASIC_USERNAME;
const paymentsBasicPassword = process.env.PAYMENTS_BASIC_PASSWORD;

if (!googleClientId || !sessionSecret) throw new Error('Missing GOOGLE_WEB_CLIENT_ID or SESSION_SECRET');
if (sessionSecret.length < 32) throw new Error('SESSION_SECRET must be at least 32 characters');
if (!paymentsBasicUsername || !paymentsBasicPassword) throw new Error('Missing PAYMENTS_BASIC_USERNAME or PAYMENTS_BASIC_PASSWORD');
if (paymentsBasicPassword.length < 16) throw new Error('PAYMENTS_BASIC_PASSWORD must be at least 16 characters');

const jwtSecret = new TextEncoder().encode(sessionSecret);
const google = new OAuth2Client(googleClientId);
const pool = process.env.DATABASE_URL
  ? new Pool({ connectionString: process.env.DATABASE_URL, ssl: { rejectUnauthorized: false } })
  : null;

app.disable('x-powered-by');
app.set('trust proxy', 1);
app.use(helmet({ crossOriginResourcePolicy: { policy: 'cross-origin' } }));
app.use(cors({
  origin: (process.env.ALLOWED_ORIGINS ?? '').split(',').map(x => x.trim()).filter(Boolean),
  credentials: true,
  methods: ['GET', 'POST'],
  allowedHeaders: ['Content-Type', 'Authorization']
}));
app.use(express.json({ limit: '256kb' }));
app.use(rateLimit({ windowMs: 60_000, limit: 60, standardHeaders: true, legacyHeaders: false }));

app.get('/health', (_req, res) => res.json({ ok: true, service: 'phentist-api' }));

const googleTokenSchema = z.object({ idToken: z.string().min(100).max(10000) });

async function issueSession(userId: string, email: string, isAdmin: boolean) {
  const jti = crypto.randomUUID();
  return new SignJWT({ sub: userId, email, role: isAdmin ? 'admin' : 'user', jti })
    .setProtectedHeader({ alg: 'HS256', typ: 'JWT' })
    .setIssuedAt()
    .setExpirationTime('15m')
    .sign(jwtSecret);
}

app.post('/v1/auth/google', async (req, res) => {
  const parsed = googleTokenSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'invalid_request' });
  try {
    const ticket = await google.verifyIdToken({ idToken: parsed.data.idToken, audience: googleClientId });
    const payload = ticket.getPayload();
    if (!payload?.sub || !payload.email || payload.email_verified !== true) return res.status(401).json({ error: 'google_identity_not_verified' });
    const email = payload.email.toLowerCase();
    const isAdmin = email === adminEmail;
    let userId = payload.sub;
    if (pool) {
      const result = await pool.query(
        `INSERT INTO public.users (google_sub,email,display_name,avatar_url,role,last_login_at,updated_at)
         VALUES ($1,$2,$3,$4,$5,NOW(),NOW())
         ON CONFLICT (google_sub) DO UPDATE SET email=EXCLUDED.email,display_name=EXCLUDED.display_name,avatar_url=EXCLUDED.avatar_url,role=EXCLUDED.role,last_login_at=NOW(),updated_at=NOW()
         RETURNING id`,
        [payload.sub, email, payload.name ?? null, payload.picture ?? null, isAdmin ? 'admin' : 'user']
      );
      userId = String(result.rows[0].id);
    }
    const accessToken = await issueSession(userId, email, isAdmin);
    return res.json({ accessToken, expiresIn: 900, user: { id: userId, email, displayName: payload.name ?? null, isAdmin } });
  } catch (error) {
    console.error('Google authentication failed', error instanceof Error ? error.message : 'unknown');
    return res.status(401).json({ error: 'invalid_google_token' });
  }
});

async function authenticate(req: express.Request, res: express.Response, next: express.NextFunction) {
  const value = req.header('authorization');
  if (!value?.startsWith('Bearer ')) return res.status(401).json({ error: 'missing_token' });
  try {
    const { payload } = await jwtVerify(value.slice(7), jwtSecret, { algorithms: ['HS256'] });
    (req as express.Request & { auth?: typeof payload }).auth = payload;
    next();
  } catch {
    return res.status(401).json({ error: 'invalid_or_expired_session' });
  }
}

function isAdminRequest(req: express.Request): boolean {
  const auth = (req as express.Request & { auth?: Record<string, unknown> }).auth;
  return auth?.role === 'admin' && String(auth.email).toLowerCase() === adminEmail;
}

function requireAdmin(req: express.Request, res: express.Response): boolean {
  if (!isAdminRequest(req)) {
    res.status(403).json({ error: 'admin_only' });
    return false;
  }
  return true;
}

function basicAuth(req: express.Request): boolean {
  const header = req.header('authorization');
  if (!header?.startsWith('Basic ')) return false;
  try {
    const decoded = Buffer.from(header.slice(6), 'base64').toString('utf8');
    const separator = decoded.indexOf(':');
    if (separator < 0) return false;
    const username = decoded.slice(0, separator);
    const password = decoded.slice(separator + 1);
    const usernameOk = Buffer.byteLength(username) === Buffer.byteLength(paymentsBasicUsername) && crypto.timingSafeEqual(Buffer.from(username), Buffer.from(paymentsBasicUsername));
    const passwordOk = Buffer.byteLength(password) === Buffer.byteLength(paymentsBasicPassword) && crypto.timingSafeEqual(Buffer.from(password), Buffer.from(paymentsBasicPassword));
    return usernameOk && passwordOk;
  } catch {
    return false;
  }
}

function requirePaymentsBrowserAuth(req: express.Request, res: express.Response, next: express.NextFunction) {
  if (!basicAuth(req)) {
    res.setHeader('WWW-Authenticate', 'Basic realm="Phentist Payments", charset="UTF-8"');
    return res.status(401).send('Authentication required');
  }
  next();
}

app.get('/v1/me', authenticate, (req, res) => {
  const auth = (req as express.Request & { auth?: Record<string, unknown> }).auth!;
  res.json({ id: auth.sub, email: auth.email, role: auth.role });
});

app.get('/v1/admin/status', authenticate, (req, res) => {
  if (!requireAdmin(req, res)) return;
  res.json({ admin: true });
});

const paymentSchema = z.object({
  name: z.string().trim().min(1).max(100),
  surname: z.string().trim().min(1).max(100),
  location: z.string().trim().min(1).max(200),
  phone: z.string().trim().min(7).max(30),
  serviceEmail: z.string().email().optional(),
  amountMinor: z.number().int().nonnegative().optional(),
  currency: z.string().trim().length(3).default('UZS'),
  contentId: z.string().uuid().optional(),
  note: z.string().trim().max(1000).optional()
});

async function ensurePaymentsTable() {
  if (!pool) return false;
  await pool.query(`
    CREATE TABLE IF NOT EXISTS public.payments (
      id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
      name text NOT NULL,
      surname text NOT NULL,
      location text NOT NULL,
      phone text NOT NULL,
      service_email text NOT NULL,
      amount_minor bigint,
      currency text NOT NULL DEFAULT 'UZS',
      content_id uuid REFERENCES public.content_items(id) ON DELETE SET NULL,
      status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','paid','failed','refunded','canceled')),
      note text,
      created_by text NOT NULL,
      created_at timestamptz NOT NULL DEFAULT now(),
      updated_at timestamptz NOT NULL DEFAULT now()
    );
    CREATE INDEX IF NOT EXISTS idx_payments_created_at ON public.payments(created_at DESC);
  `);
  return true;
}

async function createPayment(req: express.Request, res: express.Response, createdBy: string) {
  const parsed = paymentSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'invalid_payment', details: parsed.error.flatten() });
  if (!pool) return res.status(503).json({ error: 'payments_database_unavailable' });
  try {
    await ensurePaymentsTable();
    const p = parsed.data;
    const result = await pool.query(
      `INSERT INTO public.payments (name,surname,location,phone,service_email,amount_minor,currency,content_id,note,created_by)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)
       RETURNING id,name,surname,location,phone,service_email,amount_minor,currency,content_id,status,created_at`,
      [p.name, p.surname, p.location, p.phone, serviceEmail, p.amountMinor ?? null, p.currency, p.contentId ?? null, p.note ?? null, createdBy]
    );
    return res.status(201).json({ payment: result.rows[0] });
  } catch (error) {
    console.error('Payment creation failed', error instanceof Error ? error.message : 'unknown');
    return res.status(500).json({ error: 'payment_creation_failed' });
  }
}

// Android admin clients use the Google session. Direct browser access to this
// same /payments path uses HTTP Basic Auth with credentials stored in Render env.
app.post('/payments', authenticate, async (req, res) => {
  if (!requireAdmin(req, res)) return;
  const auth = (req as express.Request & { auth?: Record<string, unknown> }).auth!;
  return createPayment(req, res, String(auth.sub ?? auth.email));
});

app.get('/payments', requirePaymentsBrowserAuth, async (_req, res) => {
  if (!pool) return res.status(503).send('Payments database is not configured.');
  try {
    await ensurePaymentsTable();
    const result = await pool.query(`
      SELECT id,name,surname,location,phone,service_email,amount_minor,currency,content_id,status,created_by,created_at
      FROM public.payments ORDER BY created_at DESC LIMIT 100
    `);
    const rows = result.rows.map(row => `<tr><td>${escapeHtml(String(row.id))}</td><td>${escapeHtml(String(row.name))} ${escapeHtml(String(row.surname))}</td><td>${escapeHtml(String(row.location))}</td><td>${escapeHtml(String(row.phone))}</td><td>${escapeHtml(String(row.amount_minor ?? ''))} ${escapeHtml(String(row.currency))}</td><td>${escapeHtml(String(row.status))}</td><td>${escapeHtml(new Date(row.created_at).toISOString())}</td></tr>`).join('');
    res.type('html').send(`<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Phentist Payments</title><style>body{font-family:system-ui,sans-serif;margin:24px;color:#17212b}table{border-collapse:collapse;width:100%;font-size:14px}th,td{border:1px solid #ddd;padding:8px;text-align:left}th{background:#f4f6f8}</style></head><body><h1>Phentist Payments</h1><p>Last 100 payment records.</p><table><thead><tr><th>ID</th><th>Name</th><th>Location</th><th>Phone</th><th>Amount</th><th>Status</th><th>Created</th></tr></thead><tbody>${rows || '<tr><td colspan="7">No payments yet.</td></tr>'}</tbody></table></body></html>`);
  } catch (error) {
    console.error('Payments page failed', error instanceof Error ? error.message : 'unknown');
    res.status(500).send('Unable to load payments');
  }
});

function escapeHtml(value: string): string {
  return value.replace(/[&<>\'"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[char] ?? char));
}

app.use((_req, res) => res.status(404).json({ error: 'not_found' }));
app.use((err: unknown, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
  console.error('Unhandled server error', err instanceof Error ? err.message : 'unknown');
  res.status(500).json({ error: 'internal_server_error' });
});

app.listen(port, '0.0.0.0', () => console.log(`Phentist API listening on ${port}`));

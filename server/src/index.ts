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
const sessionSecret = process.env.SESSION_SECRET;

if (!googleClientId || !sessionSecret) {
  throw new Error('Missing GOOGLE_WEB_CLIENT_ID or SESSION_SECRET');
}
if (sessionSecret.length < 32) {
  throw new Error('SESSION_SECRET must be at least 32 characters');
}

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
    const ticket = await google.verifyIdToken({
      idToken: parsed.data.idToken,
      audience: googleClientId
    });
    const payload = ticket.getPayload();
    if (!payload?.sub || !payload.email || payload.email_verified !== true) {
      return res.status(401).json({ error: 'google_identity_not_verified' });
    }

    const email = payload.email.toLowerCase();
    const isAdmin = email === adminEmail;
    let userId = payload.sub;

    if (pool) {
      await pool.query(`
        CREATE TABLE IF NOT EXISTS users (
          id TEXT PRIMARY KEY,
          email TEXT UNIQUE NOT NULL,
          display_name TEXT,
          role TEXT NOT NULL CHECK (role IN ('user','admin')),
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          last_login_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )
      `);
      const result = await pool.query(
        `INSERT INTO users (id,email,display_name,role) VALUES ($1,$2,$3,$4)
         ON CONFLICT (email) DO UPDATE SET display_name=EXCLUDED.display_name,last_login_at=NOW(),role=EXCLUDED.role
         RETURNING id`,
        [payload.sub, email, payload.name ?? null, isAdmin ? 'admin' : 'user']
      );
      userId = result.rows[0].id;
    }

    const accessToken = await issueSession(userId, email, isAdmin);
    return res.json({
      accessToken,
      expiresIn: 900,
      user: { id: userId, email, displayName: payload.name ?? null, isAdmin }
    });
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

app.get('/v1/me', authenticate, (req, res) => {
  const auth = (req as express.Request & { auth?: Record<string, unknown> }).auth!;
  res.json({ id: auth.sub, email: auth.email, role: auth.role });
});

app.get('/v1/admin/status', authenticate, (req, res) => {
  const auth = (req as express.Request & { auth?: Record<string, unknown> }).auth!;
  if (auth.role !== 'admin' || String(auth.email).toLowerCase() !== adminEmail) {
    return res.status(403).json({ error: 'admin_only' });
  }
  res.json({ admin: true });
});

app.use((_req, res) => res.status(404).json({ error: 'not_found' }));
app.use((err: unknown, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
  console.error('Unhandled server error', err instanceof Error ? err.message : 'unknown');
  res.status(500).json({ error: 'internal_server_error' });
});

app.listen(port, '0.0.0.0', () => console.log(`Phentist API listening on ${port}`));

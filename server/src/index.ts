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
const pool = process.env.DATABASE_URL ? new Pool({ connectionString: process.env.DATABASE_URL, ssl: { rejectUnauthorized: false } }) : null;
const appCorsOrigins = (process.env.ALLOWED_ORIGINS ?? '').split(',').map(x => x.trim()).filter(Boolean);

app.disable('x-powered-by');
app.set('trust proxy', 1);
app.use(helmet({ crossOriginResourcePolicy: { policy: 'cross-origin' } }));
app.use(cors({ origin: appCorsOrigins.length ? appCorsOrigins : true, credentials: true, methods: ['GET', 'POST', 'DELETE'], allowedHeaders: ['Content-Type', 'Authorization'] }));
app.use(express.json({ limit: '256kb' }));
app.use(rateLimit({ windowMs: 60_000, limit: 60, standardHeaders: true, legacyHeaders: false }));
app.get('/health', (_req, res) => res.json({ ok: true, service: 'phentist-api' }));

const googleTokenSchema = z.object({ idToken: z.string().min(100).max(10000) });
async function issueSession(userId: string, email: string, isAdmin: boolean) {
  return new SignJWT({ sub: userId, email, role: isAdmin ? 'admin' : 'user', jti: crypto.randomUUID() })
    .setProtectedHeader({ alg: 'HS256', typ: 'JWT' }).setIssuedAt().setExpirationTime('15m').sign(jwtSecret);
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
      const result = await pool.query(`INSERT INTO public.users (google_sub,email,display_name,avatar_url,role,last_login_at,updated_at)
        VALUES ($1,$2,$3,$4,$5,NOW(),NOW())
        ON CONFLICT (google_sub) DO UPDATE SET email=EXCLUDED.email,display_name=EXCLUDED.display_name,avatar_url=EXCLUDED.avatar_url,role=EXCLUDED.role,last_login_at=NOW(),updated_at=NOW()
        RETURNING id`, [payload.sub,email,payload.name ?? null,payload.picture ?? null,isAdmin ? 'admin' : 'user']);
      userId = String(result.rows[0].id);
    }
    return res.json({ accessToken: await issueSession(userId,email,isAdmin), expiresIn: 900, user: { id:userId,email,displayName:payload.name ?? null,isAdmin } });
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
  } catch { return res.status(401).json({ error: 'invalid_or_expired_session' }); }
}
function isAdmin(req: express.Request) {
  const auth = (req as express.Request & { auth?: Record<string, unknown> }).auth;
  return auth?.role === 'admin' && String(auth.email ?? '').toLowerCase() === adminEmail;
}
function requireAdmin(req: express.Request, res: express.Response) { if (!isAdmin(req)) { res.status(403).json({ error:'admin_only' }); return false; } return true; }

app.get('/v1/me', authenticate, (req,res) => { const a=(req as any).auth; res.json({ id:a.sub,email:a.email,role:a.role }); });
app.get('/v1/admin/status', authenticate, (req,res) => { if (!requireAdmin(req,res)) return; res.json({ admin:true }); });

const universitySchema = z.object({ name:z.string().trim().min(1).max(200), location:z.string().trim().min(1).max(200), imageUrl:z.string().url().max(2000).optional().or(z.literal('')) });
app.get('/v1/universities', async (req,res) => {
  if (!pool) return res.status(503).json({ error:'database_unavailable' });
  const search = String(req.query.search ?? '').trim().slice(0,100);
  try {
    const result = await pool.query(`SELECT id,name,location,image_url,is_active,created_at FROM public.universities
      WHERE is_active=true AND ($1='' OR name ILIKE '%'||$1||'%' OR location ILIKE '%'||$1||'%') ORDER BY name ASC LIMIT 200`, [search]);
    res.json({ universities:result.rows });
  } catch (error) { console.error('University list failed', error instanceof Error ? error.message : 'unknown'); res.status(500).json({ error:'university_list_failed' }); }
});
app.post('/v1/universities', authenticate, async (req,res) => {
  if (!requireAdmin(req,res)) return;
  if (!pool) return res.status(503).json({ error:'database_unavailable' });
  const parsed=universitySchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error:'invalid_university',details:parsed.error.flatten() });
  try {
    const a=(req as any).auth;
    const result=await pool.query(`INSERT INTO public.universities(name,location,image_url,created_by) VALUES($1,$2,$3,$4) RETURNING id,name,location,image_url,is_active,created_at`,[parsed.data.name,parsed.data.location,parsed.data.imageUrl || null,a.sub]);
    res.status(201).json({ university:result.rows[0] });
  } catch (error) { console.error('University creation failed', error instanceof Error ? error.message : 'unknown'); res.status(500).json({ error:'university_creation_failed' }); }
});
app.delete('/v1/universities/:id', authenticate, async (req,res) => {
  if (!requireAdmin(req,res)) return;
  if (!pool) return res.status(503).json({ error:'database_unavailable' });
  const id=z.string().uuid().safeParse(req.params.id);
  if (!id.success) return res.status(400).json({ error:'invalid_university_id' });
  try { const result=await pool.query(`UPDATE public.universities SET is_active=false,updated_at=NOW() WHERE id=$1 RETURNING id`,[id.data]); if (!result.rowCount) return res.status(404).json({error:'university_not_found'}); res.json({ok:true}); }
  catch (error) { console.error('University deletion failed', error instanceof Error ? error.message : 'unknown'); res.status(500).json({error:'university_deletion_failed'}); }
});

const paymentSchema=z.object({ name:z.string().trim().min(1).max(100),surname:z.string().trim().min(1).max(100),location:z.string().trim().min(1).max(200),phone:z.string().trim().min(7).max(30),serviceEmail:z.string().email().optional(),amountMinor:z.number().int().nonnegative().optional(),currency:z.string().trim().length(3).default('UZS'),contentId:z.string().uuid().optional(),note:z.string().trim().max(1000).optional() });
app.post('/payments',authenticate,async(req,res)=>{
  const parsed=paymentSchema.safeParse(req.body); if(!parsed.success)return res.status(400).json({error:'invalid_payment',details:parsed.error.flatten()}); if(!pool)return res.status(503).json({error:'payments_database_unavailable'});
  try { const p=parsed.data; const a=(req as any).auth; const result=await pool.query(`INSERT INTO public.payments(name,surname,location,phone,service_email,amount_minor,currency,content_id,note,created_by) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10) RETURNING id,name,surname,location,phone,service_email,amount_minor,currency,content_id,status,created_at`,[p.name,p.surname,p.location,p.phone,serviceEmail,p.amountMinor ?? null,p.currency,p.contentId ?? null,p.note ?? null,String(a.sub ?? a.email)]); res.status(201).json({payment:result.rows[0]}); }
  catch(error){console.error('Payment creation failed',error instanceof Error?error.message:'unknown');res.status(500).json({error:'payment_creation_failed'});}
});

function basicAuth(req:express.Request){const header=req.header('authorization');if(!header?.startsWith('Basic '))return false;try{const decoded=Buffer.from(header.slice(6),'base64').toString('utf8');const i=decoded.indexOf(':');if(i<0)return false;const u=Buffer.from(decoded.slice(0,i)),eu=Buffer.from(paymentsBasicUsername!);const p=Buffer.from(decoded.slice(i+1)),ep=Buffer.from(paymentsBasicPassword!);return u.length===eu.length&&p.length===ep.length&&crypto.timingSafeEqual(u,eu)&&crypto.timingSafeEqual(p,ep);}catch{return false;}}
app.get('/payments',(req,res,next)=>{if(!basicAuth(req)){res.setHeader('WWW-Authenticate','Basic realm="Phentist Payments", charset="UTF-8"');return res.status(401).send('Authentication required');}next();},async(_req,res)=>{
  if(!pool)return res.status(503).send('Payments database is not configured.');
  try{const result=await pool.query(`SELECT id,name,surname,location,phone,amount_minor,currency,status,created_at FROM public.payments ORDER BY created_at DESC LIMIT 100`);const rows=result.rows.map(r=>`<tr><td>${escapeHtml(String(r.id))}</td><td>${escapeHtml(String(r.name))} ${escapeHtml(String(r.surname))}</td><td>${escapeHtml(String(r.location))}</td><td>${escapeHtml(String(r.phone))}</td><td>${escapeHtml(String(r.amount_minor??''))} ${escapeHtml(String(r.currency))}</td><td>${escapeHtml(String(r.status))}</td><td>${escapeHtml(new Date(r.created_at).toISOString())}</td></tr>`).join('');res.type('html').send(`<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Phentist Payments</title><style>body{font-family:system-ui,sans-serif;margin:24px;color:#17212b}table{border-collapse:collapse;width:100%;font-size:14px}th,td{border:1px solid #ddd;padding:8px;text-align:left}th{background:#f4f6f8}</style></head><body><h1>Phentist Payments</h1><p>Last 100 payment records.</p><table><thead><tr><th>ID</th><th>Name</th><th>Location</th><th>Phone</th><th>Amount</th><th>Status</th><th>Created</th></tr></thead><tbody>${rows||'<tr><td colspan="7">No payments yet.</td></tr>'}</tbody></table></body></html>`);}
  catch(error){console.error('Payments page failed',error instanceof Error?error.message:'unknown');res.status(500).send('Unable to load payments');}
});
function escapeHtml(value:string){return value.replace(/[&<>\'\"]/g,char=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[char]??char));}
app.use((_req,res)=>res.status(404).json({error:'not_found'}));
app.use((err:unknown,_req:express.Request,res:express.Response,_next:express.NextFunction)=>{console.error('Unhandled server error',err instanceof Error?err.message:'unknown');res.status(500).json({error:'internal_server_error'});});
app.listen(port,'0.0.0.0',()=>console.log(`Phentist API listening on ${port}`));

import type { Express, NextFunction, Request, Response } from 'express';
import type { Pool } from 'pg';
import { z } from 'zod';

const essaySchema = z.object({
  universityId: z.string().uuid(),
  question: z.string().trim().min(1).max(2000),
  wordLimit: z.number().int().positive().max(5000).default(650),
  previewText: z.string().trim().max(5000).default(''),
  authorName: z.string().trim().min(1).max(120).default('Phentist'),
  authorAvatarUrl: z.string().url().max(2000).optional().or(z.literal('')),
  fullText: z.string().max(50000).default(''),
  priceMinor: z.number().int().nonnegative().max(1000000000).default(25000),
  currency: z.string().trim().length(3).default('UZS')
});
const essayPatchSchema = essaySchema.partial().omit({ universityId: true });
const idSchema = z.string().uuid();
type AuthRequest = Request & { auth?: Record<string, unknown> };
type Authenticate = (req: Request, res: Response, next: NextFunction) => Promise<unknown>;
type RequireAdmin = (req: Request, res: Response) => boolean;

export function registerEssayRoutes(app: Express, pool: Pool | null, authenticate: Authenticate, requireAdmin: RequireAdmin) {
  app.get('/v1/universities/:universityId/essays', async (req, res) => {
    if (!pool) return res.status(503).json({ error: 'database_unavailable' });
    const parsed = idSchema.safeParse(req.params.universityId);
    if (!parsed.success) return res.status(400).json({ error: 'invalid_university_id' });
    try {
      const result = await pool.query(`
        SELECT e.id,e.university_id,e.question,e.word_limit,e.preview_text,e.author_name,e.author_avatar_url,e.price_minor,e.currency,e.created_at
        FROM public.university_essays e
        WHERE e.university_id=$1 AND e.is_active=true
        ORDER BY e.created_at ASC`, [parsed.data]);
      return res.json({ essays: result.rows });
    } catch (error) {
      console.error('Essay list failed', error instanceof Error ? error.message : 'unknown');
      return res.status(500).json({ error: 'essay_list_failed' });
    }
  });

  app.get('/v1/university-essays/:id', authenticate, async (req: AuthRequest, res) => {
    if (!pool) return res.status(503).json({ error: 'database_unavailable' });
    const id = idSchema.safeParse(req.params.id);
    if (!id.success) return res.status(400).json({ error: 'invalid_essay_id' });
    try {
      const a=req.auth ?? {};
      const result=await pool.query(`SELECT id,university_id,question,word_limit,preview_text,full_text,author_name,author_avatar_url,price_minor,currency,created_at FROM public.university_essays WHERE id=$1 AND is_active=true LIMIT 1`,[id.data]);
      if(!result.rowCount)return res.status(404).json({error:'essay_not_found'});
      const row=result.rows[0];
      if(String(a.role ?? '')==='admin') return res.json({essay:row,unlocked:true});
      if(!pool)return res.status(503).json({error:'database_unavailable'});
      const purchase=await pool.query(`SELECT 1 FROM public.purchases p WHERE p.user_id=$1 AND p.content_id=$2 AND p.purchase_state IN ('purchased','active') LIMIT 1`,[String(a.sub ?? ''),row.id]);
      if(purchase.rowCount)return res.json({essay:row,unlocked:true});
      return res.json({essay:{...row,full_text:''},unlocked:false});
    } catch(error){console.error('Essay detail failed',error instanceof Error?error.message:'unknown');return res.status(500).json({error:'essay_detail_failed'});}
  });

  app.post('/v1/university-essays', authenticate, async (req: AuthRequest, res) => {
    if (!requireAdmin(req, res)) return;
    if (!pool) return res.status(503).json({ error: 'database_unavailable' });
    const parsed = essaySchema.safeParse(req.body);
    if (!parsed.success) return res.status(400).json({ error: 'invalid_essay', details: parsed.error.flatten() });
    try {
      const a = req.auth ?? {};
      const university = await pool.query('SELECT id FROM public.universities WHERE id=$1 AND is_active=true', [parsed.data.universityId]);
      if (!university.rowCount) return res.status(404).json({ error: 'university_not_found' });
      const result = await pool.query(`
        INSERT INTO public.university_essays(university_id,question,word_limit,preview_text,full_text,author_name,author_avatar_url,price_minor,currency,created_by)
        VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)
        RETURNING id,university_id,question,word_limit,preview_text,author_name,author_avatar_url,price_minor,currency,created_at`,
        [parsed.data.universityId, parsed.data.question, parsed.data.wordLimit, parsed.data.previewText, parsed.data.fullText, parsed.data.authorName, parsed.data.authorAvatarUrl || null, parsed.data.priceMinor, parsed.data.currency, String(a.sub ?? '') || null]);
      return res.status(201).json({ essay: result.rows[0] });
    } catch (error) {
      console.error('Essay creation failed', error instanceof Error ? error.message : 'unknown');
      return res.status(500).json({ error: 'essay_creation_failed' });
    }
  });

  app.patch('/v1/university-essays/:id', authenticate, async (req: AuthRequest, res) => {
    if (!requireAdmin(req, res)) return;
    if (!pool) return res.status(503).json({ error: 'database_unavailable' });
    const id=idSchema.safeParse(req.params.id); const parsed=essayPatchSchema.safeParse(req.body);
    if(!id.success||!parsed.success)return res.status(400).json({error:'invalid_essay',details:parsed.success?undefined:parsed.error.flatten()});
    try{
      const p=parsed.data;
      const result=await pool.query(`UPDATE public.university_essays SET question=COALESCE($2,question),word_limit=COALESCE($3,word_limit),preview_text=COALESCE($4,preview_text),full_text=COALESCE($5,full_text),author_name=COALESCE($6,author_name),author_avatar_url=CASE WHEN $7::text IS NULL THEN author_avatar_url ELSE NULLIF($7,'') END,price_minor=COALESCE($8,price_minor),currency=COALESCE($9,currency),updated_at=NOW() WHERE id=$1 AND is_active=true RETURNING id,university_id,question,word_limit,preview_text,author_name,author_avatar_url,price_minor,currency,created_at`,[id.data,p.question??null,p.wordLimit??null,p.previewText??null,p.fullText??null,p.authorName??null,p.authorAvatarUrl??null,p.priceMinor??null,p.currency??null]);
      if(!result.rowCount)return res.status(404).json({error:'essay_not_found'}); return res.json({essay:result.rows[0]});
    }catch(error){console.error('Essay update failed',error instanceof Error?error.message:'unknown');return res.status(500).json({error:'essay_update_failed'});}
  });

  app.delete('/v1/university-essays/:id', authenticate, async (req: AuthRequest, res) => {
    if (!requireAdmin(req, res)) return;
    if (!pool) return res.status(503).json({ error: 'database_unavailable' });
    const parsed = idSchema.safeParse(req.params.id);
    if (!parsed.success) return res.status(400).json({ error: 'invalid_essay_id' });
    try {
      const result = await pool.query('UPDATE public.university_essays SET is_active=false,updated_at=NOW() WHERE id=$1 RETURNING id', [parsed.data]);
      if (!result.rowCount) return res.status(404).json({ error: 'essay_not_found' });
      return res.json({ ok: true });
    } catch (error) {
      console.error('Essay deletion failed', error instanceof Error ? error.message : 'unknown');
      return res.status(500).json({ error: 'essay_deletion_failed' });
    }
  });
}

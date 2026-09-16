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
        SELECT e.id,e.university_id,e.question,e.word_limit,e.preview_text,e.full_text,e.author_name,e.author_avatar_url,e.price_minor,e.currency,e.created_at
        FROM public.university_essays e
        WHERE e.university_id=$1 AND e.is_active=true
        ORDER BY e.created_at ASC`, [parsed.data]);
      return res.json({ essays: result.rows });
    } catch (error) {
      console.error('Essay list failed', error instanceof Error ? error.message : 'unknown');
      return res.status(500).json({ error: 'essay_list_failed' });
    }
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
        RETURNING id,university_id,question,word_limit,preview_text,full_text,author_name,author_avatar_url,price_minor,currency,created_at`,
        [parsed.data.universityId, parsed.data.question, parsed.data.wordLimit, parsed.data.previewText, parsed.data.fullText, parsed.data.authorName, parsed.data.authorAvatarUrl || null, parsed.data.priceMinor, parsed.data.currency, String(a.sub ?? '') || null]);
      return res.status(201).json({ essay: result.rows[0] });
    } catch (error) {
      console.error('Essay creation failed', error instanceof Error ? error.message : 'unknown');
      return res.status(500).json({ error: 'essay_creation_failed' });
    }
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

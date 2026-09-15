import express from 'express';
import { jwtVerify } from 'jose';
import { Pool } from 'pg';
import { z } from 'zod';

const universitySchema = z.object({
  name: z.string().trim().min(1).max(200),
  location: z.string().trim().min(1).max(200),
  imageUrl: z.string().url().max(2000).optional().or(z.literal(''))
});

export function registerUniversityRoutes(
  app: express.Express,
  pool: Pool | null,
  jwtSecret: Uint8Array,
  adminEmail: string
) {
  app.get('/v1/universities', async (req, res) => {
    if (!pool) return res.status(503).json({ error: 'database_unavailable' });
    const search = String(req.query.search ?? '').trim().slice(0, 100);
    try {
      const result = await pool.query(
        `SELECT id,name,location,image_url,is_active,created_at
           FROM public.universities
          WHERE is_active = true
            AND ($1 = '' OR name ILIKE '%' || $1 || '%' OR location ILIKE '%' || $1 || '%')
          ORDER BY name ASC
          LIMIT 200`,
        [search]
      );
      return res.json({ universities: result.rows });
    } catch (error) {
      console.error('University list failed', error instanceof Error ? error.message : 'unknown');
      return res.status(500).json({ error: 'university_list_failed' });
    }
  });

  app.post('/v1/universities', async (req, res) => {
    const auth = await verifyAdmin(req, jwtSecret, adminEmail);
    if (!auth) return res.status(403).json({ error: 'admin_only' });
    if (!pool) return res.status(503).json({ error: 'database_unavailable' });
    const parsed = universitySchema.safeParse(req.body);
    if (!parsed.success) return res.status(400).json({ error: 'invalid_university', details: parsed.error.flatten() });
    try {
      const result = await pool.query(
        `INSERT INTO public.universities (name,location,image_url,created_by)
         VALUES ($1,$2,$3,$4)
         RETURNING id,name,location,image_url,is_active,created_at`,
        [parsed.data.name, parsed.data.location, parsed.data.imageUrl || null, auth.sub]
      );
      return res.status(201).json({ university: result.rows[0] });
    } catch (error) {
      console.error('University creation failed', error instanceof Error ? error.message : 'unknown');
      return res.status(500).json({ error: 'university_creation_failed' });
    }
  });

  app.delete('/v1/universities/:id', async (req, res) => {
    const auth = await verifyAdmin(req, jwtSecret, adminEmail);
    if (!auth) return res.status(403).json({ error: 'admin_only' });
    if (!pool) return res.status(503).json({ error: 'database_unavailable' });
    const id = z.string().uuid().safeParse(req.params.id);
    if (!id.success) return res.status(400).json({ error: 'invalid_university_id' });
    try {
      const result = await pool.query(
        `UPDATE public.universities SET is_active = false, updated_at = NOW() WHERE id = $1 RETURNING id`,
        [id.data]
      );
      if (!result.rowCount) return res.status(404).json({ error: 'university_not_found' });
      return res.json({ ok: true });
    } catch (error) {
      console.error('University deletion failed', error instanceof Error ? error.message : 'unknown');
      return res.status(500).json({ error: 'university_deletion_failed' });
    }
  });
}

async function verifyAdmin(req: express.Request, jwtSecret: Uint8Array, adminEmail: string) {
  const value = req.header('authorization');
  if (!value?.startsWith('Bearer ')) return null;
  try {
    const { payload } = await jwtVerify(value.slice(7), jwtSecret, { algorithms: ['HS256'] });
    if (payload.role !== 'admin' || String(payload.email ?? '').toLowerCase() !== adminEmail) return null;
    return payload;
  } catch {
    return null;
  }
}

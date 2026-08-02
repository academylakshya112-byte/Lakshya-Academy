-- =========================================================================
-- LAKSHYA ACADEMY - COMMUNITY POPUP TABLE & POLICIES MIGRATION
-- =========================================================================

CREATE TABLE IF NOT EXISTS public.community_popup (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title TEXT DEFAULT 'SHADOWXRAHUL',
    description TEXT DEFAULT 'Join our Official Community to receive the latest updates, study materials, notices, announcements, and important information.',
    image_url TEXT DEFAULT '',
    whatsapp_url TEXT DEFAULT '',
    telegram_url TEXT DEFAULT '',
    enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Enable Row Level Security (RLS)
ALTER TABLE public.community_popup ENABLE ROW LEVEL SECURITY;

-- Policies for community_popup
DROP POLICY IF EXISTS "Enable read access for all users" ON public.community_popup;
DROP POLICY IF EXISTS "Enable insert access for all users" ON public.community_popup;
DROP POLICY IF EXISTS "Enable update access for all users" ON public.community_popup;
DROP POLICY IF EXISTS "Enable delete access for all users" ON public.community_popup;

CREATE POLICY "Enable read access for all users" ON public.community_popup FOR SELECT USING (true);
CREATE POLICY "Enable insert access for all users" ON public.community_popup FOR INSERT WITH CHECK (true);
CREATE POLICY "Enable update access for all users" ON public.community_popup FOR UPDATE USING (true) WITH CHECK (true);
CREATE POLICY "Enable delete access for all users" ON public.community_popup FOR DELETE USING (true);

-- Seed default initial configuration if table is empty
INSERT INTO public.community_popup (id, title, description, image_url, whatsapp_url, telegram_url, enabled)
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'SHADOWXRAHUL', 'Join our Official Community to receive the latest updates, study materials, notices, announcements, and important information.', '', '', '', true
WHERE NOT EXISTS (SELECT 1 FROM public.community_popup);

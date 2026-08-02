-- =========================================================================
-- LAKSHYA ACADEMY - SUPABASE ROW LEVEL SECURITY (RLS) MIGRATION
-- =========================================================================
-- This script safely enables Row Level Security (RLS) and configures
-- open access policies (SELECT, INSERT, UPDATE, DELETE) for the six
-- folder and file management tables.
--
-- This script does not drop, recreate, or modify any existing tables,
-- storage buckets, videos, or other modules.
-- =========================================================================

-- 1. Enable Row Level Security (RLS) on all six tables
ALTER TABLE IF EXISTS syllabus_folders ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS syllabus_files ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS previous_paper_folders ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS previous_paper_files ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS free_book_folders ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS free_book_files ENABLE ROW LEVEL SECURITY;

-- 2. Policies for syllabus_folders
DROP POLICY IF EXISTS "Enable read access for all users" ON syllabus_folders;
DROP POLICY IF EXISTS "Enable insert access for all users" ON syllabus_folders;
DROP POLICY IF EXISTS "Enable update access for all users" ON syllabus_folders;
DROP POLICY IF EXISTS "Enable delete access for all users" ON syllabus_folders;

CREATE POLICY "Enable read access for all users" ON syllabus_folders FOR SELECT USING (true);
CREATE POLICY "Enable insert access for all users" ON syllabus_folders FOR INSERT WITH CHECK (true);
CREATE POLICY "Enable update access for all users" ON syllabus_folders FOR UPDATE USING (true) WITH CHECK (true);
CREATE POLICY "Enable delete access for all users" ON syllabus_folders FOR DELETE USING (true);

-- 3. Policies for syllabus_files
DROP POLICY IF EXISTS "Enable read access for all users" ON syllabus_files;
DROP POLICY IF EXISTS "Enable insert access for all users" ON syllabus_files;
DROP POLICY IF EXISTS "Enable update access for all users" ON syllabus_files;
DROP POLICY IF EXISTS "Enable delete access for all users" ON syllabus_files;

CREATE POLICY "Enable read access for all users" ON syllabus_files FOR SELECT USING (true);
CREATE POLICY "Enable insert access for all users" ON syllabus_files FOR INSERT WITH CHECK (true);
CREATE POLICY "Enable update access for all users" ON syllabus_files FOR UPDATE USING (true) WITH CHECK (true);
CREATE POLICY "Enable delete access for all users" ON syllabus_files FOR DELETE USING (true);

-- 4. Policies for previous_paper_folders
DROP POLICY IF EXISTS "Enable read access for all users" ON previous_paper_folders;
DROP POLICY IF EXISTS "Enable insert access for all users" ON previous_paper_folders;
DROP POLICY IF EXISTS "Enable update access for all users" ON previous_paper_folders;
DROP POLICY IF EXISTS "Enable delete access for all users" ON previous_paper_folders;

CREATE POLICY "Enable read access for all users" ON previous_paper_folders FOR SELECT USING (true);
CREATE POLICY "Enable insert access for all users" ON previous_paper_folders FOR INSERT WITH CHECK (true);
CREATE POLICY "Enable update access for all users" ON previous_paper_folders FOR UPDATE USING (true) WITH CHECK (true);
CREATE POLICY "Enable delete access for all users" ON previous_paper_folders FOR DELETE USING (true);

-- 5. Policies for previous_paper_files
DROP POLICY IF EXISTS "Enable read access for all users" ON previous_paper_files;
DROP POLICY IF EXISTS "Enable insert access for all users" ON previous_paper_files;
DROP POLICY IF EXISTS "Enable update access for all users" ON previous_paper_files;
DROP POLICY IF EXISTS "Enable delete access for all users" ON previous_paper_files;

CREATE POLICY "Enable read access for all users" ON previous_paper_files FOR SELECT USING (true);
CREATE POLICY "Enable insert access for all users" ON previous_paper_files FOR INSERT WITH CHECK (true);
CREATE POLICY "Enable update access for all users" ON previous_paper_files FOR UPDATE USING (true) WITH CHECK (true);
CREATE POLICY "Enable delete access for all users" ON previous_paper_files FOR DELETE USING (true);

-- 6. Policies for free_book_folders
DROP POLICY IF EXISTS "Enable read access for all users" ON free_book_folders;
DROP POLICY IF EXISTS "Enable insert access for all users" ON free_book_folders;
DROP POLICY IF EXISTS "Enable update access for all users" ON free_book_folders;
DROP POLICY IF EXISTS "Enable delete access for all users" ON free_book_folders;

CREATE POLICY "Enable read access for all users" ON free_book_folders FOR SELECT USING (true);
CREATE POLICY "Enable insert access for all users" ON free_book_folders FOR INSERT WITH CHECK (true);
CREATE POLICY "Enable update access for all users" ON free_book_folders FOR UPDATE USING (true) WITH CHECK (true);
CREATE POLICY "Enable delete access for all users" ON free_book_folders FOR DELETE USING (true);

-- 7. Policies for free_book_files
DROP POLICY IF EXISTS "Enable read access for all users" ON free_book_files;
DROP POLICY IF EXISTS "Enable insert access for all users" ON free_book_files;
DROP POLICY IF EXISTS "Enable update access for all users" ON free_book_files;
DROP POLICY IF EXISTS "Enable delete access for all users" ON free_book_files;

CREATE POLICY "Enable read access for all users" ON free_book_files FOR SELECT USING (true);
CREATE POLICY "Enable insert access for all users" ON free_book_files FOR INSERT WITH CHECK (true);
CREATE POLICY "Enable update access for all users" ON free_book_files FOR UPDATE USING (true) WITH CHECK (true);
CREATE POLICY "Enable delete access for all users" ON free_book_files FOR DELETE USING (true);

-- =========================================================================
-- LAKSHYA ACADEMY - PROFILES TABLE & POLICIES
-- =========================================================================
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    email TEXT,
    phone TEXT,
    photo_url TEXT,
    role TEXT DEFAULT 'student',
    created_at TIMESTAMPTZ DEFAULT now(),
    last_login TIMESTAMPTZ DEFAULT now(),
    status TEXT DEFAULT 'active'
);

-- Enable RLS
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- Policies for profiles
DROP POLICY IF EXISTS "Enable read access for all users" ON public.profiles;
DROP POLICY IF EXISTS "Enable insert access for all users" ON public.profiles;
DROP POLICY IF EXISTS "Enable update access for all users" ON public.profiles;
DROP POLICY IF EXISTS "Enable delete access for all users" ON public.profiles;

CREATE POLICY "Enable read access for all users" ON public.profiles FOR SELECT USING (true);
CREATE POLICY "Enable insert access for all users" ON public.profiles FOR INSERT WITH CHECK (true);
CREATE POLICY "Enable update access for all users" ON public.profiles FOR UPDATE USING (true) WITH CHECK (true);
CREATE POLICY "Enable delete access for all users" ON public.profiles FOR DELETE USING (true);

-- =========================================================================
-- LAKSHYA ACADEMY - COMMUNITY POPUP TABLE & POLICIES
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

ALTER TABLE public.community_popup ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Enable read access for all users" ON public.community_popup;
DROP POLICY IF EXISTS "Enable insert access for all users" ON public.community_popup;
DROP POLICY IF EXISTS "Enable update access for all users" ON public.community_popup;
DROP POLICY IF EXISTS "Enable delete access for all users" ON public.community_popup;

CREATE POLICY "Enable read access for all users" ON public.community_popup FOR SELECT USING (true);
CREATE POLICY "Enable insert access for all users" ON public.community_popup FOR INSERT WITH CHECK (true);
CREATE POLICY "Enable update access for all users" ON public.community_popup FOR UPDATE USING (true) WITH CHECK (true);
CREATE POLICY "Enable delete access for all users" ON public.community_popup FOR DELETE USING (true);

INSERT INTO public.community_popup (id, title, description, image_url, whatsapp_url, telegram_url, enabled)
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'SHADOWXRAHUL', 'Join our Official Community to receive the latest updates, study materials, notices, announcements, and important information.', '', '', '', true
WHERE NOT EXISTS (SELECT 1 FROM public.community_popup);



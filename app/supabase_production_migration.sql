-- =========================================================================
-- LAKSHYA ACADEMY - PRODUCTION SUPABASE DB SCHEMA MIGRATION SCRIPT
-- =========================================================================
-- Idempotent, safe to execute multiple times.
-- Contains all tables, indexes, constraints, storage configurations, and RLS policies.
-- =========================================================================

-- 1. SCHEMAS & STORAGE CONFIGURATION
CREATE SCHEMA IF NOT EXISTS storage;

-- Ensure buckets exist
INSERT INTO storage.buckets (id, name, public)
VALUES 
    ('videos', 'videos', true),
    ('materials', 'materials', true)
ON CONFLICT (id) DO NOTHING;

-- 2. TABLE GENERATION
CREATE TABLE IF NOT EXISTS courses (
    id SERIAL PRIMARY KEY,
    title TEXT NOT NULL,
    category TEXT NOT NULL,
    subject TEXT NOT NULL,
    description TEXT NOT NULL,
    "isFree" BOOLEAN NOT NULL DEFAULT FALSE,
    price DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "totalLessons" INTEGER NOT NULL DEFAULT 0,
    "imageUrl" TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS lessons (
    id SERIAL PRIMARY KEY,
    "courseId" INTEGER NOT NULL,
    "chapterName" TEXT NOT NULL,
    title TEXT NOT NULL,
    "videoUrl" TEXT NOT NULL,
    folder TEXT NOT NULL DEFAULT 'General',
    "pdfUrl" TEXT NOT NULL,
    "pdfName" TEXT NOT NULL,
    "pdfContent" TEXT NOT NULL DEFAULT '',
    "fileSize" TEXT NOT NULL DEFAULT '2.5 MB',
    "thumbnailUrl" TEXT NOT NULL DEFAULT '',
    "videoSourceType" TEXT NOT NULL DEFAULT 'YOUTUBE',
    "youtubeVideoId" TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS enrollments (
    id SERIAL PRIMARY KEY,
    "userEmail" TEXT NOT NULL,
    "courseId" INTEGER NOT NULL,
    "completedLessonsCount" INTEGER NOT NULL DEFAULT 0,
    "isCompleted" BOOLEAN NOT NULL DEFAULT FALSE,
    "purchaseDate" BIGINT NOT NULL DEFAULT extract(epoch from now())::bigint * 1000
);

CREATE TABLE IF NOT EXISTS tests (
    id SERIAL PRIMARY KEY,
    title TEXT NOT NULL,
    type TEXT NOT NULL,
    "durationMinutes" INTEGER NOT NULL,
    "hasNegativeMarking" BOOLEAN NOT NULL DEFAULT TRUE,
    "marksPerCorrect" INTEGER NOT NULL DEFAULT 2,
    "marksPerWrong" REAL NOT NULL DEFAULT -0.5
);

CREATE TABLE IF NOT EXISTS questions (
    id SERIAL PRIMARY KEY,
    "testId" INTEGER NOT NULL,
    "questionText" TEXT NOT NULL,
    "optionA" TEXT NOT NULL,
    "optionB" TEXT NOT NULL,
    "optionC" TEXT NOT NULL,
    "optionD" TEXT NOT NULL,
    "correctIndex" INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS test_scores (
    id SERIAL PRIMARY KEY,
    "testId" INTEGER NOT NULL,
    "testTitle" TEXT NOT NULL,
    "userEmail" TEXT NOT NULL,
    score REAL NOT NULL,
    "totalQuestions" INTEGER NOT NULL,
    "correctAnswers" INTEGER NOT NULL,
    "wrongAnswers" INTEGER NOT NULL,
    "selectedAnswersJson" TEXT NOT NULL DEFAULT '{}',
    "timestamp" BIGINT NOT NULL DEFAULT extract(epoch from now())::bigint * 1000
);

CREATE TABLE IF NOT EXISTS doubts (
    id SERIAL PRIMARY KEY,
    "userEmail" TEXT NOT NULL,
    "userName" TEXT NOT NULL,
    subject TEXT NOT NULL,
    "questionText" TEXT NOT NULL,
    "replyText" TEXT NOT NULL DEFAULT '',
    "answeredBy" TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id SERIAL PRIMARY KEY,
    "senderName" TEXT NOT NULL,
    "senderEmail" TEXT NOT NULL,
    text TEXT NOT NULL,
    "timestamp" BIGINT NOT NULL DEFAULT extract(epoch from now())::bigint * 1000,
    "isAdminReply" BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS notifications (
    id SERIAL PRIMARY KEY,
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    "timestamp" BIGINT NOT NULL DEFAULT extract(epoch from now())::bigint * 1000
);

CREATE TABLE IF NOT EXISTS materials (
    id SERIAL PRIMARY KEY,
    type TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    "fileSize" TEXT NOT NULL DEFAULT '1.5 MB',
    "uploadDate" BIGINT NOT NULL DEFAULT extract(epoch from now())::bigint * 1000,
    "fileContent" TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS banners (
    id SERIAL PRIMARY KEY,
    title TEXT NOT NULL,
    "imageUrl" TEXT NOT NULL DEFAULT '',
    "linkUrl" TEXT NOT NULL DEFAULT '',
    "buttonText" TEXT NOT NULL DEFAULT 'VIEW',
    description TEXT NOT NULL DEFAULT '',
    "isActive" BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS live_classes (
    id SERIAL PRIMARY KEY,
    title TEXT NOT NULL,
    subject TEXT NOT NULL,
    "teacherName" TEXT NOT NULL,
    "thumbnailUri" TEXT NOT NULL DEFAULT '',
    "isLive" BOOLEAN NOT NULL DEFAULT FALSE,
    "scheduledTime" BIGINT NOT NULL DEFAULT 0,
    "recordingUri" TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS ai_animation_limits (
    "userEmail" TEXT PRIMARY KEY,
    count INTEGER NOT NULL DEFAULT 5,
    "weekOfYear" INTEGER NOT NULL DEFAULT -1,
    year INTEGER NOT NULL DEFAULT -1
);

CREATE TABLE IF NOT EXISTS ai_video_limits (
    "userEmail" TEXT PRIMARY KEY,
    count INTEGER NOT NULL DEFAULT 0,
    "weekOfYear" INTEGER NOT NULL DEFAULT -1,
    year INTEGER NOT NULL DEFAULT -1
);

CREATE TABLE IF NOT EXISTS videos (
    id SERIAL PRIMARY KEY,
    title TEXT NOT NULL,
    "class" TEXT NOT NULL,
    subject TEXT NOT NULL,
    chapter TEXT NOT NULL,
    description TEXT NOT NULL,
    video_url TEXT NOT NULL,
    thumbnail_url TEXT NOT NULL DEFAULT '',
    upload_date TEXT NOT NULL,
    resource_type TEXT NOT NULL DEFAULT 'VIDEO',
    duration TEXT NOT NULL DEFAULT '',
    order_number INTEGER NOT NULL DEFAULT 0,
    visibility BOOLEAN NOT NULL DEFAULT TRUE
);

-- =========================================================================
-- FOLDER & FILE MANAGEMENT SYSTEM (Course Syllabus, Previous Papers, Free Books)
-- =========================================================================

CREATE TABLE IF NOT EXISTS syllabus_folders (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT,
    course_id TEXT,
    name TEXT NOT NULL,
    image_url TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS syllabus_files (
    id BIGSERIAL PRIMARY KEY,
    folder_id BIGINT NOT NULL,
    course_id TEXT,
    file_name TEXT NOT NULL,
    file_type TEXT NOT NULL,
    storage_url TEXT NOT NULL,
    file_size TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS previous_paper_folders (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT,
    name TEXT NOT NULL,
    image_url TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS previous_paper_files (
    id BIGSERIAL PRIMARY KEY,
    folder_id BIGINT NOT NULL,
    file_name TEXT NOT NULL,
    file_type TEXT NOT NULL,
    storage_url TEXT NOT NULL,
    file_size TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS free_book_folders (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT,
    name TEXT NOT NULL,
    image_url TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS free_book_files (
    id BIGSERIAL PRIMARY KEY,
    folder_id BIGINT NOT NULL,
    file_name TEXT NOT NULL,
    file_type TEXT NOT NULL,
    storage_url TEXT NOT NULL,
    file_size TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL
);

-- 3. INDEXES
CREATE INDEX IF NOT EXISTS idx_courses_category ON courses (category);
CREATE INDEX IF NOT EXISTS idx_courses_subject ON courses (subject);
CREATE INDEX IF NOT EXISTS idx_lessons_course_id ON lessons ("courseId");
CREATE INDEX IF NOT EXISTS idx_enrollments_user_email ON enrollments ("userEmail");
CREATE INDEX IF NOT EXISTS idx_enrollments_course_id ON enrollments ("courseId");
CREATE INDEX IF NOT EXISTS idx_tests_type ON tests (type);
CREATE INDEX IF NOT EXISTS idx_questions_test_id ON questions ("testId");
CREATE INDEX IF NOT EXISTS idx_test_scores_test_id ON test_scores ("testId");
CREATE INDEX IF NOT EXISTS idx_test_scores_user_email ON test_scores ("userEmail");
CREATE INDEX IF NOT EXISTS idx_doubts_user_email ON doubts ("userEmail");
CREATE INDEX IF NOT EXISTS idx_doubts_subject ON doubts (subject);
CREATE INDEX IF NOT EXISTS idx_chat_messages_timestamp ON chat_messages ("timestamp");
CREATE INDEX IF NOT EXISTS idx_notifications_timestamp ON notifications ("timestamp");
CREATE INDEX IF NOT EXISTS idx_materials_type ON materials (type);
CREATE INDEX IF NOT EXISTS idx_live_classes_scheduled_time ON live_classes ("scheduledTime");
CREATE INDEX IF NOT EXISTS idx_videos_class ON videos ("class");
CREATE INDEX IF NOT EXISTS idx_videos_subject ON videos (subject);

CREATE INDEX IF NOT EXISTS idx_syllabus_folders_parent ON syllabus_folders (parent_id);
CREATE INDEX IF NOT EXISTS idx_syllabus_files_folder ON syllabus_files (folder_id);
CREATE INDEX IF NOT EXISTS idx_previous_paper_folders_parent ON previous_paper_folders (parent_id);
CREATE INDEX IF NOT EXISTS idx_previous_paper_files_folder ON previous_paper_files (folder_id);
CREATE INDEX IF NOT EXISTS idx_free_book_folders_parent ON free_book_folders (parent_id);
CREATE INDEX IF NOT EXISTS idx_free_book_files_folder ON free_book_files (folder_id);

-- 4. CONSTRAINTS
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'fk_lessons_courses') THEN
        ALTER TABLE lessons ADD CONSTRAINT fk_lessons_courses FOREIGN KEY ("courseId") REFERENCES courses(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'fk_enrollments_courses') THEN
        ALTER TABLE enrollments ADD CONSTRAINT fk_enrollments_courses FOREIGN KEY ("courseId") REFERENCES courses(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'fk_questions_tests') THEN
        ALTER TABLE questions ADD CONSTRAINT fk_questions_tests FOREIGN KEY ("testId") REFERENCES tests(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'fk_test_scores_tests') THEN
        ALTER TABLE test_scores ADD CONSTRAINT fk_test_scores_tests FOREIGN KEY ("testId") REFERENCES tests(id) ON DELETE CASCADE;
    END IF;
END $$;

-- 5. ROW LEVEL SECURITY (RLS) & ACCESS POLICIES
DO $$
DECLARE
    t_name TEXT;
    tables_to_enable TEXT[] := ARRAY[
        'courses', 'lessons', 'enrollments', 'tests', 'questions', 
        'test_scores', 'doubts', 'chat_messages', 'notifications', 
        'materials', 'banners', 'live_classes', 'ai_animation_limits', 
        'ai_video_limits', 'videos',
        'syllabus_folders', 'syllabus_files', 
        'previous_paper_folders', 'previous_paper_files', 
        'free_book_folders', 'free_book_files'
    ];
BEGIN
    FOREACH t_name IN ARRAY tables_to_enable LOOP
        -- Enable RLS
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t_name);
        
        -- Drop existing policies
        EXECUTE format('DROP POLICY IF EXISTS %I ON %I', t_name || '_select_all', t_name);
        EXECUTE format('DROP POLICY IF EXISTS %I ON %I', t_name || '_insert_all', t_name);
        EXECUTE format('DROP POLICY IF EXISTS %I ON %I', t_name || '_update_all', t_name);
        EXECUTE format('DROP POLICY IF EXISTS %I ON %I', t_name || '_delete_all', t_name);

        -- Create idempotent policies
        EXECUTE format('CREATE POLICY %I ON %I FOR SELECT USING (true)', t_name || '_select_all', t_name);
        EXECUTE format('CREATE POLICY %I ON %I FOR INSERT WITH CHECK (true)', t_name || '_insert_all', t_name);
        EXECUTE format('CREATE POLICY %I ON %I FOR UPDATE USING (true) WITH CHECK (true)', t_name || '_update_all', t_name);
        EXECUTE format('CREATE POLICY %I ON %I FOR DELETE USING (true)', t_name || '_delete_all', t_name);
    END LOOP;
END $$;

-- 6. STORAGE BUCKET POLICIES (Idempotent)
DROP POLICY IF EXISTS "Public Select Access" ON storage.objects;
DROP POLICY IF EXISTS "Admin Upload Access" ON storage.objects;
DROP POLICY IF EXISTS "Admin Update Access" ON storage.objects;
DROP POLICY IF EXISTS "Admin Delete Access" ON storage.objects;

CREATE POLICY "Public Select Access" ON storage.objects
    FOR SELECT USING (bucket_id IN ('videos', 'materials'));

CREATE POLICY "Admin Upload Access" ON storage.objects
    FOR INSERT WITH CHECK (bucket_id IN ('videos', 'materials'));

CREATE POLICY "Admin Update Access" ON storage.objects
    FOR UPDATE USING (bucket_id IN ('videos', 'materials')) WITH CHECK (bucket_id IN ('videos', 'materials'));

CREATE POLICY "Admin Delete Access" ON storage.objects
    FOR DELETE USING (bucket_id IN ('videos', 'materials'));

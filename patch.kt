        // 1. Sync Courses (Batches)
        try {
            Log.d("AcademyRepository", "[BATCH SYNC] Fetching all courses/batches from Supabase...")
            val remoteCourses = api.getAllCourses()
            Log.i("AcademyRepository", "[BATCH SYNC] Successfully downloaded ${remoteCourses.size} courses/batches from Supabase.")
            
            // Preserve local imageUrl if remote one is blank
            val localCourses = academyDao.getAllCoursesDirect()
            val coursesToInsert = remoteCourses.map { remoteCourse ->
                val localCourse = localCourses.find { it.id == remoteCourse.id }
                if (localCourse != null && remoteCourse.imageUrl.isBlank() && localCourse.imageUrl.isNotBlank()) {
                    remoteCourse.copy(imageUrl = localCourse.imageUrl)
                } else {
                    remoteCourse
                }
            }
            
            academyDao.deleteAllCourses()
            coursesToInsert.forEach { 
                academyDao.insertCourse(it)
            }
        } catch (e: Exception) {

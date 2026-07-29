sed -i 's/@GET("rest\/v1\/questions?select=\*&order=id.asc")/    @GET("rest\/v1\/questions?select=*")\n    suspend fun getAllQuestions(): List<QuestionEntity>\n    @GET("rest\/v1\/questions?select=*\&order=id.asc")/' app/src/main/java/com/example/api/SupabaseApi.kt

sed -i 's/@GET("rest\/v1\/materials?select=\*&order=uploadDate.desc")/    @GET("rest\/v1\/materials?select=*")\n    suspend fun getAllMaterials(): List<MaterialEntity>\n    @GET("rest\/v1\/materials?select=*\&order=uploadDate.desc")/' app/src/main/java/com/example/api/SupabaseApi.kt

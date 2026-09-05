/*
    DESTRUCTIVE local-development reset.

    This drops and recreates the CourseQA schema, then applies every additive
    migration. Never run it against a database whose data must be preserved.
*/
:r VietnameseCourseQA20DB.sql
GO
:r APPLY_ALL.sql
GO

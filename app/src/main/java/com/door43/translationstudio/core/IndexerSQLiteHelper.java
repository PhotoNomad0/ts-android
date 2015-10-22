package com.door43.translationstudio.core;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.os.Build;
import android.util.Log;

import com.door43.util.Security;
import com.door43.util.StringUtilities;

import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by joel on 10/1/2015.
 * TODO: these methods need to throw exeptions so we can log the error
 */
public class IndexerSQLiteHelper extends SQLiteOpenHelper{

    // TRICKY: when you bump the db version you should run the library tests to generate a new index.
    // Note that the extract test will fail.
    private static final int DATABASE_VERSION = 8;
    private final String mDatabaseName;
    private final String mSchema;

    /**
     * Creates a new sql helper for the indexer.
     * This currently expects an asset named schema.sql
     * @param context
     * @param name
     * @throws IOException
     */
    public IndexerSQLiteHelper(Context context, String name) throws IOException {
        super(context, name, null, DATABASE_VERSION);
        mSchema = Util.readStream(context.getAssets().open("schema.sql"));
        mDatabaseName = name;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            db.execSQL("PRAGMA foreign_keys=OFF;");
        }
        String[] queries = mSchema.split(";");
        for (String query : queries) {
            query = query.trim();
            if(!query.isEmpty()) {
                try {
                    db.execSQL(query);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * TRICKY: this is only supported in API 16+
     * @param db
     */
    @Override
    public void onConfigure(SQLiteDatabase db) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            db.setForeignKeyConstraintsEnabled(false);
        } else {
            db.execSQL("PRAGMA foreign_keys=OFF;");
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion < 2) {
            // new tables
            db.execSQL("DROP TABLE IF EXISTS `file`");
            db.execSQL("DROP TABLE IF EXISTS `link`");
            onCreate(db);
        } else {
            onCreate(db);
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onCreate(db);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            db.setForeignKeyConstraintsEnabled(true);
        } else {
            db.execSQL("PRAGMA foreign_keys=ON;");
        }
    }

    /**
     * Destroys the database
     */
    public void deleteDatabase(Context context) {
        context.deleteDatabase(mDatabaseName);
    }

    /**
     * Inserts or updates a project
     * @param db
     * @param slug
     * @param sort
     * @param dateModified
     * @param sourceLanguageCatalogUrl
     */
    public long addProject(SQLiteDatabase db, String slug, int sort, int dateModified, String sourceLanguageCatalogUrl, String[] categorySlugs) {
        ContentValues values = new ContentValues();
        values.put("slug", slug);
        values.put("sort", sort);
        values.put("modified_at", dateModified);
        values.put("source_language_catalog_url", sourceLanguageCatalogUrl);

        // add project
        Cursor cursor = db.rawQuery("SELECT `id` FROM `project` WHERE `slug`=?", new String[]{slug});
        long projectId;
        if(cursor.moveToFirst()) {
            // update
            projectId = cursor.getLong(0);
            db.update("project", values, "`id`=" + projectId, null);
        } else {
            // insert
            projectId = db.insert("project", null, values);
        }
        cursor.close();

        // add categories
        db.delete("project__category", "project_id=" + projectId, null);
        addProjectCategories(db, projectId, categorySlugs);
        return projectId;
    }

    /**
     * Adds the project categories and links the project to the last category
     * @param db
     * @param projectId
     * @param categorySlugs
     * @return
     */
    private void addProjectCategories(SQLiteDatabase db, long projectId, String[] categorySlugs) {
        if(categorySlugs != null && categorySlugs.length > 0) {
            long categoryId = 0L;
            for (String catSlug : categorySlugs) {
                Cursor cursor = db.rawQuery("SELECT `id` FROM `category` WHERE `slug`=? AND `parent_id`=" + categoryId, new String[]{catSlug});
                if (cursor.moveToFirst()) {
                    // follow
                    categoryId = cursor.getLong(0);
                } else {
                    // insert
                    ContentValues values = new ContentValues();
                    values.put("slug", catSlug);
                    values.put("parent_id", categoryId);
                    categoryId = db.insert("category", null, values);
                }
                cursor.close();
            }
            ContentValues values = new ContentValues();
            values.put("project_id", projectId);
            values.put("category_id", categoryId);
            db.insert("project__category", null, values);
        }
    }

    /**
     * Removes a project.
     * This will cascade
     * @param db
     * @param slug
     */
    public void deleteProject(SQLiteDatabase db, String slug) {
        db.delete("project", "`slug`=?", new String[]{slug});
    }

    /**
     * Inserts or updates a source language
     * @param db
     * @param slug
     * @param projectId
     * @param name
     * @param projectName
     * @param projectDescription
     * @param direction
     * @param dateModified
     * @param resourceCatalogUrl
     */
    public long addSourceLanguage(SQLiteDatabase db, String slug, long projectId, String name, String projectName, String projectDescription, String direction, int dateModified, String resourceCatalogUrl, String[] categoryNames) {
        ContentValues values = new ContentValues();
        values.put("slug", slug);
        values.put("project_id", projectId);
        values.put("name", name);
        values.put("project_name", projectName);
        values.put("project_description", projectDescription);
        values.put("direction", direction);
        values.put("modified_at", dateModified);
        values.put("resource_catalog_url", resourceCatalogUrl);

        Cursor cursor = db.rawQuery("SELECT `id` FROM `source_language` WHERE `slug`=? AND `project_id`=" + projectId, new String[]{slug});
        long sourceLanguageId;
        if(cursor.moveToFirst()) {
            // update
            sourceLanguageId = cursor.getLong(0);
            db.update("source_language", values, "`id`=" + sourceLanguageId, null);
        } else {
            // insert
            sourceLanguageId = db.insert("source_language", null, values);
        }
        cursor.close();

        db.delete("source_language__category", "source_language_id=" + sourceLanguageId, null);
        addSourceLanguageCategories(db, projectId, sourceLanguageId, categoryNames);
        return sourceLanguageId;
    }

    /**
     * Adds the names for categories
     * @param db
     * @param sourceLanguageId
     * @param categoryNames
     */
    public void addSourceLanguageCategories(SQLiteDatabase db, long projectId, long sourceLanguageId, String[] categoryNames) {
        if(categoryNames != null && categoryNames.length > 0) {
            Cursor cursor = db.rawQuery("SELECT `c`.`id` from `category` AS `c`"
                    + " LEFT JOIN `project__category` AS `pc` ON `pc`.`category_id`=`c`.`id`"
                    + " WHERE `pc`.`project_id`=" + projectId, null);
            if (cursor.moveToFirst()) {
                // bottom category
                long categoryId = cursor.getLong(0);
                cursor.close();

                // name categories from bottom to top
                for (String name : categoryNames) {
                    ContentValues values = new ContentValues();
                    values.put("source_language_id", sourceLanguageId);
                    values.put("category_id", categoryId);
                    values.put("category_name", name);
                    db.insert("source_language__category", null, values);

                    // move up in categories
                    cursor = db.rawQuery("SELECT `parent_id` FROM `category` WHERE `id`=" + categoryId, null);
                    if(cursor.moveToFirst()) {
                        categoryId = cursor.getLong(0);
                        if(categoryId == 0L) {
                            // stop when we reach the top
                            break;
                        }
                    }
                }
            } else {
                cursor.close();
            }
        }
    }

    /**
     * Removes a source language.
     * This will cascade
     * @param db
     * @param sourceLanguageSlug
     * @param projectSlug
     */
    public void deleteSourceLanguage(SQLiteDatabase db, String sourceLanguageSlug, String projectSlug) {
        db.execSQL("DELETE FROM `source_language` AS `sl`"
                + " LEFT JOIN `project` AS `p` ON `p`.`id`=`sl`.`project_id`"
                + " WHERE `sl`.`slug`=? AND `p`.`slug`=?", new String[]{sourceLanguageSlug, projectSlug});
    }

    /**
     * Inserts or updates a resource
     * @param db
     * @param slug
     * @param sourceLanguageId
     * @param name
     * @param checkingLevel
     * @param version
     * @param dateModified
     * @param sourceCatalog
     * @param sourceDateModified
     * @param notesCatalog
     * @param notesDateModified
     * @param wordsCatalog
     * @param wordsDateModified
     * @param wordAssignmentsCatalog
     * @param wordAssignmentsDateModified
     * @param questionsCatalog
     * @param questionsDateModified
     */
    public long addResource(SQLiteDatabase db, String slug, long sourceLanguageId, String name,
                            int checkingLevel, String version, int dateModified, String sourceCatalog,
                            int sourceDateModified, String notesCatalog, int notesDateModified,
                            String wordsCatalog, int wordsDateModified, String wordAssignmentsCatalog,
                            int wordAssignmentsDateModified, String questionsCatalog, int questionsDateModified) {
        ContentValues values = new ContentValues();
        values.put("slug", slug);
        values.put("source_language_id", sourceLanguageId);
        values.put("name", name);
        values.put("checking_level", checkingLevel);
        values.put("version", version);
        values.put("modified_at", dateModified);
        values.put("source_catalog_url", sourceCatalog);
        values.put("source_catalog_server_modified_at", sourceDateModified);
        values.put("translation_notes_catalog_url", notesCatalog);
        values.put("translation_notes_catalog_server_modified_at", notesDateModified);
        values.put("translation_words_catalog_url", wordsCatalog);
        values.put("translation_words_catalog_server_modified_at", wordsDateModified);
        values.put("translation_word_assignments_catalog_url", wordAssignmentsCatalog);
        values.put("translation_word_assignments_catalog_server_modified_at", wordAssignmentsDateModified);
        values.put("checking_questions_catalog_url", questionsCatalog);
        values.put("checking_questions_catalog_server_modified_at", questionsDateModified);

        Cursor cursor = db.rawQuery("SELECT `id` FROM `resource` WHERE `slug`=? AND `source_language_id`=" + sourceLanguageId, new String[]{slug});
        long resourceId;
        if(cursor.moveToFirst()) {
            // update
            resourceId = cursor.getLong(0);
            db.update("resource", values, "`id`=" + resourceId, null);
        } else {
            // insert
            resourceId = db.insert("resource", null, values);
        }
        cursor.close();
        return resourceId;
    }

    /**
     * Removes a resource.
     * This will cascade
     * @param db
     * @param resourceSlug
     * @param sourceLanguageSlug
     */
    public void deleteResource(SQLiteDatabase db, String resourceSlug, String sourceLanguageSlug, String projectSlug) {
        db.execSQL("DELETE FROM `resource` AS `r`"
                + " LEFT JOIN `source_language` AS `sl` ON `sl`.`id`=`r`.`source_language_id`"
                + " LEFT JOIN `project` AS `p` ON `p`.`id`=`sl`.`project_id`"
                + " WHERE `r`.`slug`=? AND `sl`.`slug`=? AND `p`.`slug`=?", new String[]{resourceSlug, sourceLanguageSlug, projectSlug});
    }

    /**
     * Inserts or updates a chapter
     * @param db
     * @param slug
     * @param resourceId
     * @param reference
     * @param title
     * @return
     */
    public long addChapter(SQLiteDatabase db, String slug, long resourceId, String reference, String title) {
        ContentValues values = new ContentValues();
        values.put("slug", slug);
        values.put("resource_id", resourceId);
        values.put("sort", Integer.parseInt(slug));
        values.put("reference", reference);
        values.put("title", title);

        Cursor cursor = db.rawQuery("SELECT `id` FROM `chapter` WHERE `slug`=? AND `resource_id`=" + resourceId, new String[]{slug});
        long chapterId;
        if(cursor.moveToFirst()) {
            // update
            chapterId = cursor.getLong(0);
            db.update("chapter", values, "`id`=" + chapterId, null);
        } else {
            // insert
            chapterId = db.insert("chapter", null, values);
        }
        cursor.close();
        return chapterId;
    }

    /**
     * Removes a chapter.
     * This will cascade
     * @param db
     * @param slug
     * @param resourceId
     */
    public void deleteChapter(SQLiteDatabase db, String slug, long resourceId) {
        db.delete("chapter", "`slug`=? AND `resource_id`=" + resourceId, new String[]{slug});
    }

    /**
     * Inserts or updates a frame
     * @param db
     * @param slug
     * @param chapterId
     * @param body
     * @param format
     * @param imageUrl
     * @return
     */
    public long addFrame(SQLiteDatabase db, String slug, long chapterId, String body, String format, String imageUrl) {
        ContentValues values = new ContentValues();
        values.put("slug", slug);
        values.put("chapter_id", chapterId);
        values.put("sort", Integer.parseInt(slug));
        values.put("body", body);
        values.put("format", format);
        values.put("image_url", imageUrl);

        Cursor cursor = db.rawQuery("SELECT `id` FROM `frame` WHERE `slug`=? AND `chapter_id`=" + chapterId, new String[]{slug});
        long frameId;
        if(cursor.moveToFirst()) {
            // update
            frameId = cursor.getLong(0);
            db.update("frame", values, "`id`=" + frameId, null);
        } else {
            // insert
            frameId = db.insert("frame", null, values);
        }
        cursor.close();
        return frameId;
    }

    /**
     * Removes a frame.
     * This will cascade
     * @param db
     * @param slug
     * @param chapterId
     */
    public void deleteFrame(SQLiteDatabase db, String slug, long chapterId) {
        db.delete("frame", "`slug`=? AND `chapter_id`=" + chapterId, new String[]{slug});
    }

    /**
     * Returns the database id of a project
     * @param db
     * @param slug
     * @return returns 0 if no record was found
     */
    public long getProjectDBId(SQLiteDatabase db, String slug) {
        Cursor cursor = db.rawQuery("SELECT `id` FROM `project` WHERE `slug`=?", new String[]{slug});
        long projectId = 0;
        if(cursor.moveToFirst()) {
            projectId = cursor.getLong(0);
        }
        cursor.close();
        return projectId;
    }

    /**
     * Returns the database id of a source language
     * @param db
     * @param slug
     * @param projectId
     * @return returns 0 if no record was found
     */
    public long getSourceLanguageDBId(SQLiteDatabase db, String slug, long projectId) {
        Cursor cursor = db.rawQuery("SELECT `id` FROM `source_language` WHERE `slug`=? AND `project_id`=" + projectId, new String[]{slug});
        long sourceLanguageId = 0;
        if(cursor.moveToFirst()) {
            sourceLanguageId = cursor.getLong(0);
        }
        cursor.close();
        return sourceLanguageId;
    }

    /**
     * Returns an array of sorted project slugs
     * @param db
     * @return
     */
    public String[] getProjectSlugs(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery("SELECT `slug` FROM `project` ORDER BY `sort` ASC", null);
        cursor.moveToFirst();
        List<String> slugs = new ArrayList<>();
        while(!cursor.isAfterLast()) {
            slugs.add(cursor.getString(0));
            cursor.moveToNext();
        }
        cursor.close();
        return slugs.toArray(new String[slugs.size()]);
    }

    /**
     * Returns an array of sorted source language slugs
     * @param db
     * @param projectId
     * @return
     */
    public String[] getSourceLanguageSlugs(SQLiteDatabase db, long projectId) {
        Cursor cursor = db.rawQuery("SELECT `slug` FROM `source_language` WHERE `project_id`=" + projectId + " ORDER BY `slug` ASC", null);
        cursor.moveToFirst();
        List<String> slugs = new ArrayList<>();
        while(!cursor.isAfterLast()) {
            slugs.add(cursor.getString(0));
            cursor.moveToNext();
        }
        cursor.close();
        return slugs.toArray(new String[slugs.size()]);
    }

    /**
     * Returns an array of resource slugs
     * @param db
     * @param sourceLanguageId
     * @return
     */
    public String[] getResourceSlugs(SQLiteDatabase db, long sourceLanguageId) {
        Cursor cursor = db.rawQuery("SELECT `slug` FROM `resource` WHERE `source_language_id`=" + sourceLanguageId + " ORDER BY `slug` ASC", null);
        cursor.moveToFirst();
        List<String> slugs = new ArrayList<>();
        while(!cursor.isAfterLast()) {
            slugs.add(cursor.getString(0));
            cursor.moveToNext();
        }
        cursor.close();
        return slugs.toArray(new String[slugs.size()]);
    }

    /**
     * Returns the database id for the resource
     * @param db
     * @param slug
     * @param sourceLanguageId
     * @return
     */
    public long getResourceDBId(SQLiteDatabase db, String slug, long sourceLanguageId) {
        Cursor cursor = db.rawQuery("SELECT `id` FROM `resource` WHERE `slug`=? AND `source_language_id`=" + sourceLanguageId, new String[]{slug});
        long resourceId = 0;
        if(cursor.moveToFirst()) {
            resourceId = cursor.getLong(0);
        }
        cursor.close();
        return resourceId;
    }

    /**
     * Returns an array of chapter slugs
     * @param db
     * @param resourceId
     * @return
     */
    public String[] getChapterSlugs(SQLiteDatabase db, long resourceId) {
        Cursor cursor = db.rawQuery("SELECT `slug` FROM `chapter` WHERE `resource_id`=" + resourceId + " ORDER BY `sort` ASC", null);
        cursor.moveToFirst();
        List<String> slugs = new ArrayList<>();
        while(!cursor.isAfterLast()) {
            slugs.add(cursor.getString(0));
            cursor.moveToNext();
        }
        cursor.close();
        return slugs.toArray(new String[slugs.size()]);
    }

    /**
     * Returns the database id for the chapter
     * @param db
     * @param slug
     * @param resourceId
     * @return
     */
    public long getChapterDBId(SQLiteDatabase db, String slug, long resourceId) {
        Cursor cursor = db.rawQuery("SELECT `id` FROM `chapter` WHERE `slug`=? AND `resource_id`=" + resourceId, new String[]{slug});
        long chapterId = 0;
        if(cursor.moveToFirst()) {
            chapterId = cursor.getLong(0);
        }
        cursor.close();
        return chapterId;
    }

    /**
     * Returns an array of frame slugs
     * @param db
     * @param chapterId
     * @return
     */
    public String[] getFrameSlugs(SQLiteDatabase db, long chapterId) {
        Cursor cursor = db.rawQuery("SELECT `slug` FROM `frame` WHERE `chapter_id`=" + chapterId + " ORDER BY `sort` ASC", null);
        cursor.moveToFirst();
        List<String> slugs = new ArrayList<>();
        while(!cursor.isAfterLast()) {
            slugs.add(cursor.getString(0));
            cursor.moveToNext();
        }
        cursor.close();
        return slugs.toArray(new String[slugs.size()]);
    }

    /**
     * Returns the database id for the frame
     * @param db
     * @param slug
     * @param chapterId
     * @return
     */
    public long getFrameDBId(SQLiteDatabase db, String slug, long chapterId) {
        Cursor cursor = db.rawQuery("SELECT `id` FROM `frame` WHERE `slug`=? AND `chapter_id`=" + chapterId, new String[]{slug});
        long frameId = 0;
        if(cursor.moveToFirst()) {
            frameId = cursor.getLong(0);
        }
        cursor.close();
        return frameId;
    }

    /**
     * Inserts or updates a translation note
     * @param db
     * @param projectSlug
     * @param sourceLanguageSlug
     * @param resourceSlug
     * @param chapterSlug
     * @param frameSlug
     * @param noteSlug
     * @param frameId
     * @param title
     * @param body
     * @return
     */
    public long addTranslationNote(SQLiteDatabase db, String projectSlug, String sourceLanguageSlug, String resourceSlug, String chapterSlug, String frameSlug, String noteSlug, long frameId, String title, String body) {
        ContentValues values = new ContentValues();
        values.put("slug", noteSlug);
        values.put("frame_id", frameId);
        values.put("project_slug", projectSlug);
        values.put("source_language_slug", sourceLanguageSlug);
        values.put("resource_slug", resourceSlug);
        values.put("chapter_slug", chapterSlug);
        values.put("frame_slug", frameSlug);
        values.put("title", title);
        values.put("body", body);

        Cursor cursor = db.rawQuery("SELECT `id` FROM `translation_note` WHERE `slug`=? AND `frame_id`=" + frameId, new String[]{noteSlug});
        long noteId;
        if(cursor.moveToFirst()) {
            // update
            noteId = cursor.getLong(0);
            db.update("translation_note", values, "`id`=" + noteId, null);
        } else {
            // insert
            noteId = db.insert("translation_note", null, values);
        }
        cursor.close();
        return noteId;
    }

    /**
     * Returns an array of translation note slugs
     * @param db
     * @param frameId
     * @return
     */
    public String[] getTranslationNoteSlugs(SQLiteDatabase db, long frameId) {
        Cursor cursor = db.rawQuery("SELECT `slug` FROM `translation_note` WHERE `frame_id`=" + frameId + " ORDER BY `title` ASC", null);
        cursor.moveToFirst();
        List<String> slugs = new ArrayList<>();
        while(!cursor.isAfterLast()) {
            slugs.add(cursor.getString(0));
            cursor.moveToNext();
        }
        cursor.close();
        return slugs.toArray(new String[slugs.size()]);
    }

    /**
     * Returns a translation note
     * @param db
     * @param slug
     * @param frameId
     * @return
     */
    public TranslationNote getTranslationNote(SQLiteDatabase db, String slug, long frameId) {
        Cursor cursor = db.rawQuery("SELECT `c`.`slug`, `f`.`slug`, `tn`.`id`, `tn`.`title`, `tn`.`body` FROM `translation_note` AS `tn`"
                + " LEFT JOIN `frame` AS `f` ON `f`.`id`=`tn`.`frame_id`"
                + " LEFT JOIN `chapter` AS `c` ON `c`.`id`=`f`.`chapter_id`"
                + " WHERE `tn`.`slug`=? AND `tn`.`frame_id`=" + frameId, new String[]{slug});
        TranslationNote note = null;
        if(cursor.moveToFirst()) {
            note = new TranslationNote(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4));
        }
        cursor.close();
        return note;
    }

    /**
     * Returns a chapter
     * @param db
     * @param slug
     * @param resourceId
     * @return
     */
    public Chapter getChapter(SQLiteDatabase db, String slug, long resourceId) {
        Cursor cursor = db.rawQuery("SELECT `title`, `reference`, `slug` FROM `chapter` WHERE `slug`=? AND `resource_id`=" + resourceId, new String[]{slug});
        Chapter chapter = null;
        if(cursor.moveToFirst()) {
            chapter = new Chapter(cursor.getString(0), cursor.getString(1), cursor.getString(2));
        }
        cursor.close();
        return chapter;
    }

    /**
     * Returns a frame
     * @param db
     * @param slug
     * @param chapterId
     * @return
     */
    public Frame getFrame(SQLiteDatabase db, String slug, long chapterId) {
        Cursor cursor = db.rawQuery("SELECT `f`.`id`, `f`.`slug`, `c`.`slug`, `f`.`body`, `f`.`format`, `f`.`image_url` FROM `frame` AS `f`"
                + " LEFT JOIN `chapter` AS `c` ON `c`.`id`=`f`.`chapter_id`"
                + " WHERE `f`.`slug`=? AND `f`.`chapter_id`=" + chapterId, new String[]{slug});
        Frame frame = null;
        if(cursor.moveToFirst()) {
            frame = new Frame(cursor.getString(1), cursor.getString(2), cursor.getString(3), TranslationFormat.get(cursor.getString(4)), cursor.getString(5));
            frame.setDBId(cursor.getLong(0));
        }
        cursor.close();
        return frame;
    }

    /**
     * inserts or replace a translation word
     * @param db
     * @param wordSlug
     * @param resourceId
     * @param catalogHash
     * @param term
     * @param definitionTitle
     * @param definition
     * @return
     */
    public long addTranslationWord(SQLiteDatabase db, String wordSlug, long resourceId, String catalogHash, String term, String definitionTitle, String definition) {
        ContentValues values = new ContentValues();
        values.put("slug", wordSlug);
        values.put("catalog_hash", catalogHash);
        values.put("term", term);
        values.put("definition_title", definitionTitle);
        values.put("definition", definition);

        Cursor cursor = db.rawQuery("SELECT `id` FROM `translation_word` WHERE `slug`=? AND `catalog_hash`=?", new String[]{wordSlug, catalogHash});
        long wordId;
        if(cursor.moveToFirst()) {
            // update
            wordId = cursor.getLong(0);
            db.update("translation_word", values, "`id`=" + wordId, null);
        } else {
            // insert
            wordId = db.insert("translation_word", null, values);
        }
        cursor.close();

        // link word to resource
        ContentValues linkValues = new ContentValues();
        linkValues.put("resource_id", resourceId);
        linkValues.put("translation_word_id", wordId);
        db.insertWithOnConflict("resource__translation_word", null, linkValues, SQLiteDatabase.CONFLICT_IGNORE);

        return wordId;
    }

    /**
     * Returns an array of translation word slugs
     * @param db
     * @param resourceId
     * @return
     */
    public String[] getTranslationWordSlugs(SQLiteDatabase db, long resourceId) {
        Cursor cursor = db.rawQuery("SELECT `slug` FROM `translation_word`"
                + " WHERE `id` IN ("
                + "   SELECT `translation_word_id` FROM `resource__translation_word`"
                + "   WHERE `resource_id`=" + resourceId
                + ") ORDER BY `slug` ASC", null);
        cursor.moveToFirst();
        List<String> slugs = new ArrayList<>();
        while(!cursor.isAfterLast()) {
            slugs.add(cursor.getString(0));
            cursor.moveToNext();
        }
        cursor.close();
        return slugs.toArray(new String[slugs.size()]);
    }

    /**
     * Returns a translation word
     * @param db
     * @param slug
     * @param resourceId
     * @return
     */
    public TranslationWord getTranslationWord(SQLiteDatabase db, String slug, long resourceId) {
        Cursor cursor = db.rawQuery("SELECT `tw`.`id`, `tw`.`term`, `tw`.`definition`, `tw`.`definition_title` FROM `translation_word` AS `tw`"
                + " LEFT JOIN `resource__translation_word` AS `rtw` ON `rtw`.`translation_word_id`=`tw`.`id`"
                + " WHERE `tw`.`slug`=? AND `rtw`.`resource_id`=" + resourceId, new String[]{slug});
        TranslationWord word = null;
        if(cursor.moveToFirst()) {
            long wordId = cursor.getLong(0);
            String term = cursor.getString(1);
            String definition = cursor.getString(2);
            String definitionTitle = cursor.getString(3);

            // TODO: 10/16/2015 retrieve the related terms and exmaple passages
            // TODO: 10/16/2015 we could create a comma delimited list for related (and aliases)
            word = new TranslationWord(slug, term, definition, definitionTitle, new String[0],  new String[0],  new TranslationWord.Example[0]);
        }
        cursor.close();
        return word;
    }

    /**
     * Returns an array of translation words that are linked to the frame
     * @param db
     * @param projectSlug
     * @param sourceLanguageSlug
     * @param resourceSlug
     * @param chapterSlug
     * @param frameSlug
     * @return
     */
    public TranslationWord[] getTranslationWordsForFrame(SQLiteDatabase db, String projectSlug, String sourceLanguageSlug, String resourceSlug, String chapterSlug, String frameSlug) {
        List<TranslationWord> words = new ArrayList<>();
        Cursor cursor = db.rawQuery("SELECT `id`, `slug`, `term`, `definition`, `definition_title` FROM `translation_word`"
                + " WHERE `id` IN ("
                + "   SELECT `translation_word_id` FROM `frame__translation_word`"
                + "   WHERE `project_slug`=? AND `source_language_slug`=? AND `resource_slug`=? AND `chapter_slug`=? AND `frame_slug`=?"
                + " ) ORDER BY `slug` DESC", new String[]{projectSlug, sourceLanguageSlug, resourceSlug, chapterSlug, frameSlug});
        cursor.moveToFirst();
        while(!cursor.isAfterLast()) {
            long wordId = cursor.getLong(0);
            String wordSlug = cursor.getString(1);
            String term = cursor.getString(2);
            String definition = cursor.getString(3);
            String definitionTitle = cursor.getString(4);

            // TODO: 10/16/2015 retrieve the related terms and exmaple passages
            // TODO: 10/16/2015 we could create a comma delimited list for related (and aliases)
            words.add(new TranslationWord(wordSlug, term, definition, definitionTitle, new String[0],  new String[0],  new TranslationWord.Example[0]));
            cursor.moveToNext();
        }
        cursor.close();
        return words.toArray(new TranslationWord[words.size()]);
    }

    /**
     * links a translation word to a frame
     * @param db
     * @param wordSlug
     * @param frameId
     */
    public void addTranslationWordToFrame(SQLiteDatabase db, String wordSlug, long resourceId, long frameId, String projectSlug, String sourceLanguageSlug, String resourceSlug, String chapterSlug, String frameSlug) {
        long wordId = getTranslationWordDBId(db, wordSlug, resourceId);
        if(wordId > 0) {
            db.execSQL("REPLACE INTO `frame__translation_word` (`frame_id`, `translation_word_id`, `project_slug`, `source_language_slug`, `resource_slug`, `chapter_slug`, `frame_slug`) VALUES (" + frameId + "," + wordId + ",?,?,?,?,?)", new String[]{projectSlug, sourceLanguageSlug, resourceSlug, chapterSlug, frameSlug});
        }
    }

    /**
     * Returns the database id for a translation word
     * @param db
     * @param wordSlug
     * @param resourceId
     * @return
     */
    private long getTranslationWordDBId(SQLiteDatabase db, String wordSlug, long resourceId) {
        Cursor cursor = db.rawQuery("SELECT `tw`.`id` FROM `translation_word` AS `tw`"
                + " LEFT JOIN `resource__translation_word` AS `rtw` ON `rtw`.`translation_word_id`=`tw`.`id`"
                + " WHERE `tw`.`slug`=? AND `rtw`.`resource_id`=" + resourceId, new String[]{wordSlug});
        long wordId = 0;
        if(cursor.moveToFirst()) {
            wordId = cursor.getLong(0);
        }
        cursor.close();
        return wordId;
    }

    /**
     * Adds a checking question and links it to the frame
     * @param db
     * @param frameId
     * @param chapterId
     * @param question
     * @param answer
     */
    public long addCheckingQuestion(SQLiteDatabase db, String projectSlug, String sourceLanguageSlug, String resourceSlug, String chapterSlug, String frameSlug, String questionSlug, long frameId, long chapterId, String question, String answer) {
        ContentValues values = new ContentValues();
        values.put("slug", questionSlug);
        values.put("chapter_id", chapterId);
        values.put("question", question);
        values.put("answer", answer);

        Cursor cursor = db.rawQuery("SELECT `id` FROM `checking_question` WHERE `slug`=? AND `chapter_id`=" + chapterId, new String[]{questionSlug});
        long questionId;
        if(cursor.moveToFirst()) {
            // update
            questionId = cursor.getLong(0);
            db.update("checking_question", values, "`id`=" + questionId, null);
        } else {
            // insert
            questionId = db.insert("checking_question", null, values);
        }
        cursor.close();

        // link question to frame
        ContentValues linkValues = new ContentValues();
        linkValues.put("frame_id", frameId);
        linkValues.put("checking_question_id", questionId);
        linkValues.put("project_slug", projectSlug);
        linkValues.put("source_language_slug", sourceLanguageSlug);
        linkValues.put("resource_slug", resourceSlug);
        linkValues.put("chapter_slug", chapterSlug);
        linkValues.put("frame_slug", frameSlug);
        db.replace("frame__checking_question", null, linkValues);

        return questionId;
    }

    /**
     * Returns an array of checking questions
     * @param db
     * @param projectSlug
     * @param sourceLanguageSlug
     * @param resourceSlug
     * @param chapterSlug
     * @param frameSlug
     * @return
     */
    public CheckingQuestion[] getCheckingQuestions(SQLiteDatabase db, String projectSlug, String sourceLanguageSlug, String resourceSlug, String chapterSlug, String frameSlug) {
        List<CheckingQuestion> questions = new ArrayList<>();
        Cursor cursor = db.rawQuery("SELECT `slug`, `question`, `answer` FROM `checking_question`"
                + " WHERE `id` IN ("
                + "   SELECT `checking_question_id` FROM `frame__checking_question`"
                + "   WHERE `project_slug`=? AND `source_language_slug`=? AND `resource_slug`=? AND `chapter_slug`=? AND `frame_slug`=?"
                + ")", new String[]{projectSlug, sourceLanguageSlug, resourceSlug, chapterSlug, frameSlug});
        cursor.moveToFirst();
        while(!cursor.isAfterLast()) {
            String questionSlug = cursor.getString(0);
            String question = cursor.getString(1);
            String answer = cursor.getString(2);

            // TODO: 10/16/2015 retrieve the references
            questions.add(new CheckingQuestion(questionSlug, chapterSlug, frameSlug, question, answer, new CheckingQuestion.Reference[0]));
            cursor.moveToNext();
        }
        cursor.close();
        return questions.toArray(new CheckingQuestion[questions.size()]);
    }

    public CheckingQuestion getCheckingQuestion(SQLiteDatabase db, long chapterId, String frameSlug, String questionSlug) {
        CheckingQuestion question = null;
        Cursor cursor = db.rawQuery("SELECT `c`.`slug`, `cq`.`question`, `cq`.`answer`, `ref`.`references` FROM `checking_question` AS `cq`"
                + " LEFT JOIN ("
                + "   SELECT `checking_question_id`, GROUP_CONCAT(`chapter_slug` || '-' || `frame_slug`, ',') AS `references` FROM `frame__checking_question`"
                + "   GROUP BY `checking_question_id`"
                + " ) AS `ref` ON `ref`.`checking_question_id`=`cq`.`id`"
                + " LEFT JOIN `frame__checking_question` AS `fcq` ON `fcq`.`checking_question_id`=`cq`.`id`"
                + " LEFT JOIN `frame` AS `f` ON `f`.`id`=`fcq`.`frame_id`"
                + " LEFT JOIN `chapter` AS `c` ON `c`.`id`=`f`.`chapter_id`"
                + " WHERE `f`.`slug`=? AND `cq`.`slug`=? AND `c`.`id`=" + chapterId, new String[]{frameSlug, questionSlug});
        if(cursor.moveToFirst()) {
            String chapterSlug = cursor.getString(0);
            String questionText = cursor.getString(1);
            String answer = cursor.getString(2);

            String[] referenceStrings = cursor.getString(3).split(",");
            List<CheckingQuestion.Reference> references = new ArrayList<>();
            for(String reference:referenceStrings) {
                try {
                    references.add(CheckingQuestion.Reference.generate(reference));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            question = new CheckingQuestion(questionSlug, chapterSlug, frameSlug, questionText, answer, references.toArray(new CheckingQuestion.Reference[references.size()]));
        }
        cursor.close();
        return question;
    }

    /**
     * Returns a project
     * If the source language does not exist the first available source language will be used
     * @param db
     * @param projectSlug
     * @param sourceLanguageSlug
     * @return
     */
    public Project getProject(SQLiteDatabase db, String projectSlug, String sourceLanguageSlug) {
        Project project = null;
        Cursor cursor = db.rawQuery("SELECT `p`.`sort`, `p`.`modified_at`, `p`.`source_language_catalog_url`,"
                + " CASE WHEN `sl`.`project_name` IS NOT NULL THEN `sl`.`project_name` ELSE `sl`.`project_name` END,"
                + " CASE WHEN `sl`.`project_description` IS NOT NULL THEN `sl`.`project_description` ELSE `sl2`.`project_description` END"
                + " FROM `project` AS `p`"
                + " LEFT JOIN `source_language` AS `sl` ON `sl`.`project_id`=`p`.`id`"
                + " LEFT JOIN ("
                + " SELECT * FROM `source_language` LIMIT 1"
                + ") AS `sl2` ON `sl2`.`project_id`=`p`.`id`"
                + " WHERE `p`.`slug`=? AND `sl`.`slug`=?", new String[]{projectSlug, sourceLanguageSlug});
        if(!cursor.moveToFirst()) {
            // try to select project without language
            cursor.close();
            cursor = db.rawQuery("SELECT `sort`, `modified_at`, `source_language_catalog_url`, `slug` AS `project_name`, '' AS `project_description` FROM `project`"
                    + " WHERE `slug`=?", new String[]{projectSlug});
        }
        if(cursor.moveToFirst()) {
            int sort = cursor.getInt(0);
            int dateModified = cursor.getInt(1);
            String sourceLanguageCatalog = cursor.getString(2);
            String projectName = cursor.getString(3);
            String projectDescription = cursor.getString(4);
            project = new Project(projectSlug, sourceLanguageSlug, projectName, projectDescription, dateModified, sort, sourceLanguageCatalog);
        }
        cursor.close();
        return project;
    }

    /**
     * Returns a single source language
     * @param db
     * @param projectSlug
     * @param sourceLanguageSlug
     * @return
     */
    public SourceLanguage getSourceLanguage(SQLiteDatabase db, String projectSlug, String sourceLanguageSlug) {
        SourceLanguage sourceLanguage = null;
        Cursor cursor = db.rawQuery("SELECT `sl`.`name`, `sl`.`project_name`, `sl`.`project_description`, `sl`.`direction`, `sl`.`modified_at`, `sl`.`resource_catalog_url` FROM `source_language` AS `sl`"
                + " LEFT JOIN `project` AS `p` ON `p`.`id` = `sl`.`project_id`"
                + " WHERE `p`.`slug`=? AND `sl`.`slug`=?", new String[]{projectSlug, sourceLanguageSlug});

        if(cursor.moveToFirst()) {
            String sourceLanguageName = cursor.getString(0);
            String projectName = cursor.getString(1);
            String projectDescription = cursor.getString(2);
            String rawDirection = cursor.getString(3);
            int dateModified = cursor.getInt(4);
            String resourceCatalog = cursor.getString(5);
            LanguageDirection direction = LanguageDirection.get(rawDirection);
            if(direction == null) {
                direction = LanguageDirection.LeftToRight;
            }
            sourceLanguage = new SourceLanguage(sourceLanguageSlug, sourceLanguageName, dateModified, direction, projectName, projectDescription, resourceCatalog);
        }
        cursor.close();
        return sourceLanguage;
    }

    /**
     * Returns a resource
     * @param db
     * @param projectSlug
     * @param sourceLanguageSlug
     * @param resourceSlug
     * @return
     */
    public Resource getResource(SQLiteDatabase db, String projectSlug, String sourceLanguageSlug, String resourceSlug) {
        Resource resource = null;
        Cursor cursor = db.rawQuery("SELECT `r`.`name`, `r`.`checking_level`, `r`.`version`, `r`.`modified_at`,"
                + " `r`.`source_catalog_url`, `r`.`source_catalog_local_modified_at`, `r`.`source_catalog_server_modified_at`,"
                + " `r`.`translation_notes_catalog_url`, `r`.`translation_notes_catalog_local_modified_at`, `r`.`translation_notes_catalog_server_modified_at`,"
                + " `r`.`translation_words_catalog_url`, `r`.`translation_words_catalog_local_modified_at`, `r`.`translation_words_catalog_server_modified_at`,"
                + " `r`.`translation_word_assignments_catalog_url`, `r`.`translation_word_assignments_catalog_local_modified_at`, `r`.`translation_word_assignments_catalog_server_modified_at`,"
                + " `r`.`checking_questions_catalog_url`, `r`.`checking_questions_catalog_local_modified_at`, `r`.`checking_questions_catalog_server_modified_at`,"
                + " `r`.`id` FROM `resource` AS `r`"
                + " LEFT JOIN `source_language` AS `sl` ON `sl`.`id`=`r`.`source_language_id`"
                + " LEFT JOIN `project` AS `p` ON `p`.`id` = `sl`.`project_id`"
                + " WHERE `p`.`slug`=? AND `sl`.`slug`=? AND `r`.`slug`=?", new String[]{projectSlug, sourceLanguageSlug, resourceSlug});

        if(cursor.moveToFirst()) {
            String resourceName = cursor.getString(0);
            int checkingLevel = cursor.getInt(1);
            String version = cursor.getString(2);
            int dateModified = cursor.getInt(3);

            String sourceCatalog = cursor.getString(4);
            int sourceCatalogModified = cursor.getInt(5);
            int sourceCatalogServerModified = cursor.getInt(6);

            String notesCatalog = cursor.getString(7);
            int notesCatalogModified = cursor.getInt(8);
            int notesCatalogServerModified = cursor.getInt(9);

            String termsCatalog = cursor.getString(10);
            int termsCatalogModified = cursor.getInt(11);
            int termsCatalogServerModified = cursor.getInt(12);

            String termAssignmentsCatalog = cursor.getString(13);
            int termAssignmentsCatalogModified = cursor.getInt(14);
            int termAssignmentsCatalogServerModified = cursor.getInt(15);

            String questionsCatalog = cursor.getString(16);
            int questionsCatalogModified = cursor.getInt(17);
            int questionsCatalogServerModified = cursor.getInt(18);

            long resourceId = cursor.getLong(19);
            resource = new Resource(resourceName, resourceSlug, checkingLevel, version, dateModified,
                    sourceCatalog, sourceCatalogModified, sourceCatalogServerModified,
                    notesCatalog, notesCatalogModified, notesCatalogServerModified,
                    termsCatalog, termsCatalogModified, termsCatalogServerModified,
                    termAssignmentsCatalog, termAssignmentsCatalogModified, termAssignmentsCatalogServerModified,
                    questionsCatalog, questionsCatalogModified, questionsCatalogServerModified);
            resource.setDBId(resourceId);
        }
        cursor.close();
        return resource;
    }

    /**
     * Returns an array of target languages
     * @param db
     * @return
     */
    public TargetLanguage[] getTargetLanguages(SQLiteDatabase db) {
        List<TargetLanguage> targetLanguages = new ArrayList<>();
        Cursor cursor = db.rawQuery("SELECT `slug`, `name`, `direction`, `region` FROM `target_language`", null);
        cursor.moveToFirst();
        while(!cursor.isAfterLast()) {
            String slug = cursor.getString(0);
            String name = cursor.getString(1);
            LanguageDirection direction = LanguageDirection.get(cursor.getString(2));
            if(direction == null) {
                direction = LanguageDirection.LeftToRight;
            }
            String region = cursor.getString(3);
            targetLanguages.add(new TargetLanguage(slug, name, region, direction));
            cursor.moveToNext();
        }
        cursor.close();
        return targetLanguages.toArray(new TargetLanguage[targetLanguages.size()]);
    }

    /**
     * Adds a target language
     * @param db
     * @param slug
     * @param direction
     * @param name
     * @param region
     */
    public long addTargetLanguage(SQLiteDatabase db, String slug, String direction, String name, String region) {
        ContentValues values = new ContentValues();
        values.put("slug", slug);
        values.put("name", name);
        values.put("direction", direction);
        values.put("region", region);
        return db.replace("target_language", null, values);
    }

    /**
     * Returns the number of target languages there are in the database
     * @param db
     * @return
     */
    public int getTargetLanguagesLength(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM `target_language`", null);
        int count = 0;
        if(cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        return count;
    }

    /**
     * Returns a target language
     * @param db
     * @param targetLanguageSlug
     * @return
     */
    public TargetLanguage getTargetLanguage(SQLiteDatabase db, String targetLanguageSlug) {
        Cursor cursor = db.rawQuery("SELECT `name`, `direction`, `region` FROM `target_language` WHERE `slug`=?", new String[]{targetLanguageSlug});
        TargetLanguage targetLanguage = null;
        if(cursor.moveToFirst()) {
            String name = cursor.getString(0);
            LanguageDirection direction = LanguageDirection.get(cursor.getString(1));
            if(direction == null) {
                direction = LanguageDirection.LeftToRight;
            }
            String region = cursor.getString(2);
            targetLanguage = new TargetLanguage(targetLanguageSlug, name, region, direction);
        }
        cursor.close();
        return targetLanguage;
    }

    /**
     * Updates the local date modified to match the server date modified for catalogs
     * @param db
     * @param resourceId
     */
    public void markResourceUpToDate(SQLiteDatabase db, long resourceId) {
        db.execSQL("UPDATE `resource` SET"
                + " `source_catalog_local_modified_at`=`source_catalog_server_modified_at`,"
                + " `translation_notes_catalog_local_modified_at`=`translation_notes_catalog_server_modified_at`,"
                + " `translation_words_catalog_local_modified_at`=`translation_words_catalog_server_modified_at`,"
                + " `translation_word_assignments_catalog_local_modified_at`=`translation_word_assignments_catalog_server_modified_at`,"
                + " `checking_questions_catalog_local_modified_at`=`checking_questions_catalog_server_modified_at`"
                + " WHERE `id`=" + resourceId);
    }

    /**
     * Returns a source translation
     * @param db
     * @param projectSlug
     * @param sourceLanguageSlug
     * @param resourceSlug
     * @return
     */
    public SourceTranslation getSourceTranslation(SQLiteDatabase db, String projectSlug, String sourceLanguageSlug, String resourceSlug) {
        SourceTranslation sourceTranslation = null;
        Cursor cursor = db.rawQuery("SELECT `sl`.`project_name`, `sl`.`name`, `r`.`name`, `r`.`checking_level`, `r`.`modified_at`, `r`.`version`"
                + " FROM `resource` AS `r`"
                + " LEFT JOIN `source_language` AS `sl` ON `sl`.`id`=`r`.`source_language_id`"
                + " LEFT JOIN `project` AS `p` ON `p`.`id` = `sl`.`project_id`"
                + " WHERE `p`.`slug`=? AND `sl`.`slug`=? AND `r`.`slug`=?", new String[]{projectSlug, sourceLanguageSlug, resourceSlug});
        if(cursor.moveToFirst()) {
            String projectName = cursor.getString(0);
            String sourceLanguageName = cursor.getString(1);
            String resourceName = cursor.getString(2);
            int checkingLevel = cursor.getInt(3);
            int dateModified = cursor.getInt(4);
            String version = cursor.getString(5);
            sourceTranslation = new SourceTranslation(projectSlug, sourceLanguageSlug, resourceSlug, projectName, sourceLanguageName, resourceName, checkingLevel, dateModified, version);
        }
        cursor.close();
        return sourceTranslation;
    }

    /**
     * Returns the branch of the category list
     * @param db
     * @param sourcelanguageSlug
     * @param parentCategoryId
     * @return
     */
    public ProjectCategory[] getCategoryBranch(SQLiteDatabase db, String sourcelanguageSlug, long parentCategoryId) {
        List<ProjectCategory> categories = new ArrayList<>();
        Cursor cursor = db.rawQuery("SELECT * FROM ("
                + " SELECT `c`.`slug` AS `category_slug`, `slc`.`category_name` AS `title`, NULL AS `project_slug`, 0 AS `sort`, `c`.`id` AS `category_id` FROM `category` AS `c`"
                + " LEFT JOIN `source_language__category` AS `slc` ON `slc`.`category_id`=`c`.`id`"
                + " LEFT JOIN `source_language` AS `sl` ON `sl`.`id`=`slc`.`source_language_id`"
                + " WHERE `sl`.`slug`=? AND `c`.`parent_id`=" + parentCategoryId
                + " UNION"
                + " SELECT `c`.`slug` AS `category_slug`, `sl`.`project_name` AS `title`, `p`.`slug` AS `project_id`, `p`.`sort` AS `sort`, " + parentCategoryId + " AS `category_id` FROM `project` AS `p`"
                + " LEFT JOIN `project__category` AS `pc` ON `pc`.`project_id`=`p`.`id`"
                + " LEFT JOIN `category` AS `c` ON `c`.`id`=`pc`.`category_id`"
                + " LEFT JOIN `source_language` AS `sl` ON `sl`.`project_id`=`p`.`id`"
                + " WHERE CASE WHEN " + parentCategoryId + "=0 THEN `pc`.`category_id` IS NULL ELSE `pc`.`category_id`=" + parentCategoryId + " END AND `sl`.`slug`=?"
                + ") ORDER BY `sort` ASC", new String[]{sourcelanguageSlug, sourcelanguageSlug});
        cursor.moveToFirst();
        while(!cursor.isAfterLast()) {
            String categorySlug = cursor.getString(0);
            String title = cursor.getString(1);
            String projectSlug = cursor.getString(2);
            int sort = cursor.getInt(3);
            long categoryId = cursor.getLong(4);
            categories.add(new ProjectCategory(title, categorySlug, projectSlug, sourcelanguageSlug, categoryId));
            cursor.moveToNext();
        }
        cursor.close();
        return categories.toArray(new ProjectCategory[categories.size()]);
    }

    /**
     * Returns an array of chapters
     * @param db
     * @param resourceId
     * @return
     */
    public Chapter[] getChapters(SQLiteDatabase db, long resourceId) {
        List<Chapter> chapters = new ArrayList<>();
        Cursor cursor = db.rawQuery("SELECT `slug`, `reference`, `title` FROM `chapter` WHERE `resource_id`=" + resourceId + " ORDER BY `sort` ASC", null);
        cursor.moveToFirst();
        while(!cursor.isAfterLast()) {
            String slug = cursor.getString(0);
            String reference = cursor.getString(1);
            String title = cursor.getString(2);
            chapters.add(new Chapter(title, reference, slug));
            cursor.moveToNext();
        }
        cursor.close();
        return chapters.toArray(new Chapter[chapters.size()]);
    }

    /**
     * Returns an array of frames
     * @param db
     * @param chapterSlug
     * @return
     */
    public Frame[] getFrames(SQLiteDatabase db, String projectSlug, String sourceLanguageSlug, String resourceSlug, String chapterSlug) {
        List<Frame> frames = new ArrayList<>();
        Cursor cursor = db.rawQuery("SELECT `f`.`id`, `f`.`slug`, `f`.`body`, `f`.`format`, `f`.`image_url` FROM `frame` AS `f`"
                + " LEFT JOIN `chapter` AS `c` ON `c`.`id`=`f`.`chapter_id`"
                + " LEFT JOIN `resource` AS `r` ON `r`.`id`=`c`.`resource_id`"
                + " LEFT JOIN `source_language` AS `sl` ON `sl`.`id`=`r`.`source_language_id`"
                + " LEFT JOIN `project` AS `p` ON `p`.`id`=`sl`.`project_id`"
                + " WHERE `p`.`slug`=? AND `sl`.`slug`=? AND `r`.`slug`=? AND `c`.`slug`=? ORDER BY `f`.`sort` ASC", new String[]{projectSlug, sourceLanguageSlug, resourceSlug, chapterSlug});
        cursor.moveToFirst();
        while(!cursor.isAfterLast()) {
            long id = cursor.getLong(0);
            String slug = cursor.getString(1);
            String body = cursor.getString(2);
            String rawFormat = cursor.getString(3);
            TranslationFormat format = TranslationFormat.get(rawFormat);
            if(format == null) {
                format = TranslationFormat.DEFAULT;
            }
            String imageUrl = cursor.getString(4);
            Frame frame = new Frame(slug, chapterSlug, body, format, imageUrl);
            frame.setDBId(id);
            frames.add(frame);
            cursor.moveToNext();
        }
        cursor.close();
        return frames.toArray(new Frame[frames.size()]);
    }

    /**
     * Returns the chapter body
     * @param db
     * @param projectSlug
     * @param sourceLanguageSlug
     * @param resourceSlug
     * @param chapterSlug
     * @return
     */
    public String getChapterBody(SQLiteDatabase db, String projectSlug, String sourceLanguageSlug, String resourceSlug, String chapterSlug) {
        Cursor cursor = db.rawQuery("SELECT GROUP_CONCAT(`f`.`body`, ' ') AS `body` FROM `frame` AS `f`"
                + " LEFT JOIN `chapter` AS `c` ON `c`.`id`=`f`.`chapter_id`"
                + " LEFT JOIN `resource` AS `r` ON `r`.`id`=`c`.`resource_id`"
                + " LEFT JOIN `source_language` AS `sl` ON `sl`.`id`=`r`.`source_language_id`"
                + " LEFT JOIN `project` AS `p` ON `p`.`id`=`sl`.`project_id`"
                + " WHERE `p`.`slug`=? AND `sl`.`slug`=? AND `r`.`slug`=? AND `c`.`slug`=? ORDER BY `c`.`sort`, `f`.`sort` ASC", new String[]{projectSlug, sourceLanguageSlug, resourceSlug, chapterSlug});
        String body = "";
        if(cursor.moveToFirst()) {
            body = cursor.getString(0);
            if(body == null) {
                body = "";
            }
        }
        cursor.close();
        return body;
    }

    /**
     * Returns the format of the chapter body
     * @param db
     * @param projectSlug
     * @param sourceLanguageSlug
     * @param resourceSlug
     * @param chapterSlug
     * @return
     */
    public TranslationFormat getChapterBodyFromat(SQLiteDatabase db, String projectSlug, String sourceLanguageSlug, String resourceSlug, String chapterSlug) {
        Cursor cursor = db.rawQuery("SELECT `f`.`format` FROM `frame` AS `f`"
                + " LEFT JOIN `chapter` AS `c` ON `c`.`id`=`f`.`chapter_id`"
                + " LEFT JOIN `resource` AS `r` ON `r`.`id`=`c`.`resource_id`"
                + " LEFT JOIN `source_language` AS `sl` ON `sl`.`id`=`r`.`source_language_id`"
                + " LEFT JOIN `project` AS `p` ON `p`.`id`=`sl`.`project_id`"
                + " WHERE `p`.`slug`=? AND `sl`.`slug`=? AND `r`.`slug`=? AND `c`.`slug`=? AND `f`.`format` IS NOT NULL LIMIT 1", new String[]{projectSlug, sourceLanguageSlug, resourceSlug, chapterSlug});
        TranslationFormat format = TranslationFormat.DEFAULT;
        if(cursor.moveToFirst()) {
            format = TranslationFormat.get(cursor.getString(0));
            if(format == null) {
                format = TranslationFormat.DEFAULT;
            }
        }
        cursor.close();
        return format;
    }

    /**
     * Returns an array of translation notes
     * @param db
     * @param projectSlug
     * @param sourceLanguageSlug
     * @param resourceSlug
     * @param chapterSlug
     * @param frameSlug
     * @return
     */
    public TranslationNote[] getTranslationNotes(SQLiteDatabase db, String projectSlug, String sourceLanguageSlug, String resourceSlug, String chapterSlug, String frameSlug) {
        List<TranslationNote> notes = new ArrayList<>();
        Cursor cursor = db.rawQuery("SELECT `slug`, `title`, `body` FROM `translation_note`"
                + " WHERE `project_slug`=? AND `source_language_slug`=? AND `resource_slug`=? AND `chapter_slug`=? AND `frame_slug`=?"
                , new String[]{projectSlug, sourceLanguageSlug, resourceSlug, chapterSlug, frameSlug});
        cursor.moveToFirst();
        while(!cursor.isAfterLast()) {
            String noteSlug = cursor.getString(0);
            String title = cursor.getString(1);
            String body = cursor.getString(2);

            notes.add(new TranslationNote(chapterSlug, frameSlug, noteSlug, title, body));
            cursor.moveToNext();
        }
        cursor.close();
        return notes.toArray(new TranslationNote[notes.size()]);
    }
}

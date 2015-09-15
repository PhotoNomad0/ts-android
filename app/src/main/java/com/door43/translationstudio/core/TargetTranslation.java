package com.door43.translationstudio.core;

import com.door43.util.Manifest;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

/**
 * Created by joel on 8/29/2015.
 */
public class TargetTranslation {
    private final String mTargetLanguageId;
    private final String mProjectId;
    private static final String GLOBAL_PROJECT_ID = "uw";
    private final File mTargetTranslationDirectory;
    private final Manifest mManifest;
    private final String mTargetTranslationName;

    public TargetTranslation(String targetLanguageId, String projectId, File rootDir) {
        mTargetLanguageId = targetLanguageId;
        mProjectId = projectId;
        mTargetTranslationDirectory = generateTargetTranslationDir(targetLanguageId, projectId, rootDir);;
        mManifest = Manifest.generate(mTargetTranslationDirectory);
        String name = targetLanguageId;
        try {
            name = mManifest.getJSONObject("target_language").getString("name");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mTargetTranslationName = name;
    }

    /**
     * Returns the id of the target translation
     * @return
     */
    public String getId() {
        return generateTargetTranslationId(mTargetLanguageId, mProjectId);
    }

    /**
     * Returns the name of the target language
     * @return
     */
    public String getTargetLanguageName() {
        return mTargetTranslationName;
    }

    /**
     * Returns the id of the project being translated
     * @return
     */
    public String getProjectId() {
        return mProjectId;
    }

    /**
     * Returns the id of the target language the project is being translated into
     * @return
     */
    public String getTargetLanguageId() {
        return mTargetLanguageId;
    }

    /**
     * Creates a new target translation
     *
     * If the target translation already exists the existing one will be returned
     *
     * @param targetLanguage the target language the project will be translated into
     * @param projectId the id of the project that will be translated
     * @param mRootDir the parent directory in which the target translation directory will be created
     * @return
     */
    public static TargetTranslation generate(TargetLanguage targetLanguage, String projectId, File mRootDir) throws Exception {
        // create folder
        File translationDir = generateTargetTranslationDir(targetLanguage.getId(), projectId, mRootDir);

        if(!translationDir.exists()) {
            // build new manifest
            Manifest manifest = Manifest.generate(translationDir);
            manifest.put("slug", projectId);
            JSONObject targetLangaugeJson = new JSONObject();
            targetLangaugeJson.put("direction", targetLanguage.direction.toString());
            targetLangaugeJson.put("slug", targetLanguage.code);
            targetLangaugeJson.put("name", targetLanguage.name);
            // TODO: we should restructure this output to match what we see in the api.
            // also the target language should have a toJson method that will do all of this.
            manifest.put("target_language", targetLangaugeJson);
        }

        return new TargetTranslation(targetLanguage.getId(), projectId, translationDir);
    }

    /**
     * Returns a properly formatted target language id
     * @param targetLanguageId
     * @param projectId
     * @return
     */
    private static String generateTargetTranslationId(String targetLanguageId, String projectId) {
        return GLOBAL_PROJECT_ID + "-" + projectId + "-" + targetLanguageId;
    }

    /**
     * Returns the id of the project of the target translation
     * @param targetTranslationId the target translation id
     * @return
     */
    public static String getProjectIdFromId(String targetTranslationId) throws StringIndexOutOfBoundsException{
        String[] complexId = targetTranslationId.split("-", 3);
        if(complexId.length == 3) {
            return complexId[1];
        } else {
            throw new StringIndexOutOfBoundsException("malformed target translation id" + targetTranslationId);
        }
    }

    /**
     * Returns the id of the target lanugage of the target translation
     * @param targetTranslationId the target translation id
     * @return
     */
    public static String getTargetLanguageIdFromId(String targetTranslationId) throws StringIndexOutOfBoundsException {
        String[] complexId = targetTranslationId.split("-", 3);
        if(complexId.length == 3) {
            return complexId[2];
        } else {
            throw new StringIndexOutOfBoundsException("malformed target translation id" + targetTranslationId);
        }
    }

    /**
     * Generates the file to the directory where the target translation is located
     *
     * @param targetLanguageId the language to which the project is being translated
     * @param projectId the id of the project that is being translated
     * @param rootDir the directory where the target translations are stored
     * @return
     */
    public static File generateTargetTranslationDir(String targetLanguageId, String projectId, File rootDir) {
        String targetTranslationId = generateTargetTranslationId(targetLanguageId, projectId);
        return new File(rootDir, targetTranslationId);
    }

    /**
     * Adds a source translation to the list of used sources
     * This is used for tracking what source translations are used to create a target translation
     *
     * @param sourceTranslation
     * @throws JSONException
     */
    public void addSourceTranslation(SourceTranslation sourceTranslation) throws JSONException {
        JSONObject sourceTranslationsJson = mManifest.getJSONObject("source_translations");
        JSONObject translationJson = new JSONObject();
        translationJson.put("checking_level", sourceTranslation.getCheckingLevel());
        translationJson.put("date_modified", sourceTranslation.getDateModified());
        translationJson.put("version", sourceTranslation.getVersion());
        sourceTranslationsJson.put(sourceTranslation.getId(), translationJson);
    }
}
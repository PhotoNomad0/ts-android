package com.door43.translationstudio.newui.newtranslation;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.support.design.widget.Snackbar;
import android.support.v4.view.MenuItemCompat;
import android.os.Bundle;
import android.support.v7.widget.SearchView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;

import com.door43.tools.reporting.Logger;
import com.door43.translationstudio.R;
import com.door43.translationstudio.SettingsActivity;
import com.door43.translationstudio.core.LanguageDirection;
import com.door43.translationstudio.core.NewLanguagePackage;
import com.door43.translationstudio.core.NewLanguageRequest;
import com.door43.translationstudio.core.Resource;
import com.door43.translationstudio.core.SourceLanguage;
import com.door43.translationstudio.core.SourceTranslation;
import com.door43.translationstudio.core.TargetLanguage;
import com.door43.translationstudio.core.TargetTranslation;
import com.door43.translationstudio.core.TranslationType;
import com.door43.translationstudio.core.Translator;
import com.door43.translationstudio.dialogs.CustomAlertDialog;
import com.door43.translationstudio.newui.library.ServerLibraryActivity;
import com.door43.translationstudio.newui.library.Searchable;
import com.door43.translationstudio.newui.BaseActivity;
import com.door43.translationstudio.AppContext;
import com.door43.translationstudio.newui.newlanguage.NewLanguageActivity;
import com.door43.util.StringUtilities;
import com.door43.widget.ViewUtil;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class NewTargetTranslationActivity extends BaseActivity implements TargetLanguageListFragment.OnItemClickListener, ProjectListFragment.OnItemClickListener {

    public static final String EXTRA_TARGET_TRANSLATION_ID = "extra_target_translation_id";
    public static final int RESULT_DUPLICATE = 2;
    private static final String STATE_TARGET_TRANSLATION_ID = "state_target_translation_id";
    private static final String STATE_TARGET_LANGUAGE = "state_target_language_id";
    public static final int RESULT_ERROR = 3;
    public static final String TAG = NewTargetTranslationActivity.class.getSimpleName();
    public static final int NEW_LANGUAGE_REQUEST = 1001;
    public static final String NEW_LANGUAGE_CONFIRMATION = "new-language-confirmation";
    private static final String STATE_NEW_LANGUAGE = "new_language";
    private TargetLanguage mSelectedTargetLanguage = null;
    private Searchable mFragment;
    private String mNewTargetTranslationId = null;
    private ImageButton mNewLanguageButton;
    private boolean createdNewLanguage = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_target_translation);

        mNewLanguageButton = (ImageButton) findViewById(R.id.newLanguageRequest);
        if (null != mNewLanguageButton) {
            mNewLanguageButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent requestNewLangaugeIntent = new Intent(NewTargetTranslationActivity.this, NewLanguageActivity.class);
                    startActivityForResult(requestNewLangaugeIntent, NEW_LANGUAGE_REQUEST);
                }
            });
        }

        if(savedInstanceState != null) {
            createdNewLanguage = savedInstanceState.getBoolean(STATE_NEW_LANGUAGE, false);
            if (savedInstanceState.containsKey(STATE_TARGET_TRANSLATION_ID)) {
                mNewTargetTranslationId = (String) savedInstanceState.getSerializable(STATE_TARGET_TRANSLATION_ID);
            }

            if (savedInstanceState.containsKey(STATE_TARGET_LANGUAGE)) {
                String targetLanguageJsonStr = savedInstanceState.getString(STATE_TARGET_LANGUAGE);
                try {
                    mSelectedTargetLanguage = TargetLanguage.generate(new JSONObject(targetLanguageJsonStr));
                } catch (Exception e) { }
            }
        }

        if(findViewById(R.id.fragment_container) != null) {
            if(savedInstanceState != null) {
                mFragment = (Searchable)getFragmentManager().findFragmentById(R.id.fragment_container);
            } else {
                mFragment = new TargetLanguageListFragment();
                ((TargetLanguageListFragment) mFragment).setArguments(getIntent().getExtras());
                getFragmentManager().beginTransaction().add(R.id.fragment_container, (TargetLanguageListFragment) mFragment).commit();
                // TODO: animate
            }
        }

        if(createdNewLanguage) {
            confirmNewLanguage(mSelectedTargetLanguage);
        }
    }

    /**
     * use new language information passed in JSON format string to create a new target language
     * @param request
     */
    private void registerCustomLanguageCode(NewLanguageRequest request) {
        if(request != null) {
            File requestFile = new File(AppContext.getPublicDirectory(), "new_languages/" + request.tempLanguageCode + ".json");
            requestFile.getParentFile().mkdirs();
            try {
                com.door43.tools.reporting.FileUtils.writeStringToFile(requestFile, request.toJson());

                // TODO: 6/2/16 retrieve the language region from the request
                mSelectedTargetLanguage = new TargetLanguage(request.tempLanguageCode, request.getLanguageName(), "uncertain", LanguageDirection.LeftToRight);

                // TODO: 6/2/16 add the language code to the actual target language list.
                // The temp codes will be stored in a seperate table and joined when retrieving target languages.
                // We will likely have yet another table to indicate the migration of temp language codes to correct language codes.

                this.createdNewLanguage = true;

                confirmNewLanguage(mSelectedTargetLanguage);
                return;
            } catch (IOException e) {
                Logger.e(this.getClass().getName(), "Failed to save the new langauge request", e);
            }
        }
        CustomAlertDialog.Create(this)
                .setTitle(R.string.error)
                .setMessage(R.string.try_again)
                .show("error-questionnaire");
    }

    /**
     * Displays a confirmation for the new language
     * @param language
     */
    private void confirmNewLanguage(final TargetLanguage language) {
        if(language != null) {
            String msg = String.format(getResources().getString(R.string.new_language_confirmation), language.getId(), language.name);
            final CustomAlertDialog dialog = CustomAlertDialog.Create(this)
                    .setCancelableChainable(false)
                    .setAutoDismiss(false)
                    .setTitle(R.string.language)
                    .setMessage(msg);
            dialog.setPositiveButton(R.string.label_continue, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    onItemClick(language);
                }
            })
            .setNeutralButton(R.string.copy, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    StringUtilities.copyToClipboard(NewTargetTranslationActivity.this, language.code);
                    Snackbar snack = Snackbar.make(v, R.string.copied_to_clipboard, Snackbar.LENGTH_SHORT);
                    ViewUtil.setSnackBarTextColor(snack, getResources().getColor(R.color.light_primary_text));
                    snack.show();
                }
            });
            dialog.show(NEW_LANGUAGE_CONFIRMATION);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_new_target_translation, menu);
        return true;
    }

    @Override
    public void onItemClick(TargetLanguage targetLanguage) {
        mSelectedTargetLanguage = targetLanguage;

        // display project list
        mFragment = new ProjectListFragment();
        ((ProjectListFragment) mFragment).setArguments(getIntent().getExtras());
        getFragmentManager().beginTransaction().replace(R.id.fragment_container, (ProjectListFragment) mFragment).commit();
        // TODO: animate
        invalidateOptionsMenu();
    }

    @Override
    public void onItemClick(String projectId) {
        Translator translator = AppContext.getTranslator();
        // TRICKY: android only supports translating regular text projects
        String resourceSlug = projectId.equals("obs") ? "obs" : Resource.REGULAR_SLUG;
        TargetTranslation existingTranslation = translator.getTargetTranslation(TargetTranslation.generateTargetTranslationId(mSelectedTargetLanguage.getId(), projectId, TranslationType.TEXT, resourceSlug));
        if(existingTranslation == null) {
            // create new target translation
            SourceLanguage sourceLanguage = AppContext.getLibrary().getPreferredSourceLanguage(projectId, Locale.getDefault().getLanguage()); // get project name
            // TODO: 3/2/2016 eventually the format will be specified in the project
            SourceTranslation sourceTranslation = AppContext.getLibrary().getDefaultSourceTranslation(projectId, sourceLanguage.getId());
            final TargetTranslation targetTranslation = AppContext.getTranslator().createTargetTranslation(AppContext.getProfile().getNativeSpeaker(), mSelectedTargetLanguage, projectId, TranslationType.TEXT, resourceSlug, sourceTranslation.getFormat());
            if(targetTranslation != null) {

//                if(mNewLanguageData != null) {
//                    saveNewLanguageData(targetTranslation, mNewLanguageData);
//
//                    String msg = String.format(AppContext.context().getResources().getString(R.string.new_language_confirmation), targetTranslation.getTargetLanguageId(), targetTranslation.getTargetLanguageName());
//                    CustomAlertDialog.Create(this)
//                            .setTitle(R.string.language)
//                            .setMessage(msg)
//                            .setPositiveButton(R.string.confirm, new View.OnClickListener() {
//                                @Override
//                                public void onClick(View v) {
//                                    newProjectCreated(targetTranslation);
//                                }
//                            })
//                            .setNegativeButton(R.string.title_cancel, null)
//                            .show("NewLang");
//                } else {
                    newProjectCreated(targetTranslation);
//                }
            } else {
                AppContext.getTranslator().deleteTargetTranslation(TargetTranslation.generateTargetTranslationId(mSelectedTargetLanguage.getId(), projectId, TranslationType.TEXT, resourceSlug));
                Intent data = new Intent();
                setResult(RESULT_ERROR, data);
                finish();
            }
        } else {
            // that translation already exists
            Intent data = new Intent();
            data.putExtra(EXTRA_TARGET_TRANSLATION_ID, existingTranslation.getId());
            setResult(RESULT_DUPLICATE, data);
            finish();
        }
    }

    private void newProjectCreated(TargetTranslation targetTranslation) {
        mNewTargetTranslationId = targetTranslation.getId();

        Intent data = new Intent();
        data.putExtra(EXTRA_TARGET_TRANSLATION_ID, mNewTargetTranslationId);
        setResult(RESULT_OK, data);
        finish();
    }

    /**
     * save new language data into target translation as well as "new_languages" folder
     * @param targetTranslation
     * @param newLanguageData
     * @return
     */
    private boolean saveNewLanguageData(TargetTranslation targetTranslation, String newLanguageData) {
        String path = "";
        try {
            NewLanguagePackage newLang = NewLanguagePackage.parse(newLanguageData);
            if(null == newLang) {
                return false;
            }

            File folder = targetTranslation.getPath();
            path = folder.toString();
            newLang.commit(folder);

            File dataPath = NewLanguagePackage.getNewLanguageFolder();
            path = dataPath.toString();
            FileUtils.forceMkdir(dataPath);
            File newLanguagePath = new File(dataPath,targetTranslation.getId() + NewLanguagePackage.NEW_LANGUAGE_FILE_EXTENSION);
            path = newLanguagePath.toString();
            newLang.commitToFile(newLanguagePath);

        } catch (Exception e) {
            Logger.e(TAG, "Could not write new language data to: " + path, e);
            return false;
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if(mFragment instanceof ProjectListFragment) {
            menu.findItem(R.id.action_update).setVisible(true);
        } else {
            menu.findItem(R.id.action_update).setVisible(false);
        }
        SearchManager searchManager = (SearchManager)getSystemService(Context.SEARCH_SERVICE);
        final MenuItem searchMenuItem = menu.findItem(R.id.action_search);
        final SearchView searchViewAction = (SearchView) MenuItemCompat.getActionView(searchMenuItem);
        searchViewAction.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                mFragment.onSearchQuery(s);
                return true;
            }
        });
        searchViewAction.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch(id) {
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_search:
                return true;
            case R.id.action_update:
                CustomAlertDialog.Create(this)
                        .setTitle(R.string.update_projects)
                        .setIcon(R.drawable.ic_local_library_black_24dp)
                        .setMessage(R.string.use_internet_confirmation)
                        .setPositiveButton(R.string.yes, new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Intent intent = new Intent(NewTargetTranslationActivity.this, ServerLibraryActivity.class);
//                                intent.putExtra(ServerLibraryActivity.ARG_SHOW_UPDATES, true);
                                startActivity(intent);
                            }
                        })
                        .setNegativeButton(R.string.no, null)
                        .show("Update");
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(STATE_TARGET_TRANSLATION_ID, mNewTargetTranslationId);
        outState.putBoolean(STATE_NEW_LANGUAGE, createdNewLanguage);
        if(mSelectedTargetLanguage != null) {
            JSONObject targetLanguageJson = mSelectedTargetLanguage.toApiFormatJson();
            if(targetLanguageJson != null) {
                outState.putString(STATE_TARGET_LANGUAGE, targetLanguageJson.toString());
            }
        } else {
            outState.remove(STATE_TARGET_LANGUAGE);
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (NEW_LANGUAGE_REQUEST == requestCode) {
            if(RESULT_OK == resultCode) {
                String rawResponse = data.getStringExtra(NewLanguageActivity.EXTRA_QUESTIONNAIRE_RESPONSE);
                registerCustomLanguageCode(NewLanguageRequest.generate(rawResponse));
            } else if(RESULT_FIRST_USER == resultCode) {
                String message = data.getStringExtra(NewLanguageActivity.EXTRA_MESSAGE);
                Snackbar snack = Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG);
                ViewUtil.setSnackBarTextColor(snack, getResources().getColor(R.color.light_primary_text));
                snack.show();
            }
        }
    }
}
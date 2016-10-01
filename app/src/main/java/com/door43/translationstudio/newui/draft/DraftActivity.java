package com.door43.translationstudio.newui.draft;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.MenuItem;
import android.view.View;

import com.door43.translationstudio.App;
import com.door43.translationstudio.R;
import com.door43.translationstudio.core.TargetTranslation;
import com.door43.translationstudio.core.Translator;
import com.door43.translationstudio.newui.BaseActivity;
import com.door43.translationstudio.tasks.ImportDraftTask;

import org.unfoldingword.door43client.Door43Client;
import org.unfoldingword.resourcecontainer.Resource;
import org.unfoldingword.resourcecontainer.ResourceContainer;
import org.unfoldingword.tools.taskmanager.SimpleTaskWatcher;
import org.unfoldingword.tools.taskmanager.ManagedTask;
import org.unfoldingword.tools.taskmanager.TaskManager;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;


public class DraftActivity extends BaseActivity implements SimpleTaskWatcher.OnFinishedListener {
    public static final String TAG = "DraftActivity";
    public static final String EXTRA_TARGET_TRANSLATION_ID = "target_translation_id";
    private TargetTranslation mTargetTranslation;
    private Translator mTranslator;
    private Door43Client mLibrary;
    private RecyclerView mRecylerView;
    private LinearLayoutManager mLayoutManager;
    private DraftAdapter mAdapter;
    private SimpleTaskWatcher taskWatcher;
    private ResourceContainer mDraftTranslation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_draft_preview);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mTranslator = App.getTranslator();
        mLibrary = App.getLibrary();

        // validate parameters
        List<ResourceContainer> draftTranslations = new ArrayList<>();
        Bundle extras = getIntent().getExtras();
        if(extras != null) {
            String targetTranslationId = extras.getString(EXTRA_TARGET_TRANSLATION_ID, null);
            mTargetTranslation = mTranslator.getTargetTranslation(targetTranslationId);
            if(mTargetTranslation != null) {
                List<Resource> resources = mLibrary.index().getResources(mTargetTranslation.getTargetLanguageId(), mTargetTranslation.getProjectId());
                for(Resource r:resources) {
                    if(Integer.parseInt(r.checkingLevel) < App.MIN_CHECKING_LEVEL) {
                        try {
                            draftTranslations.add(mLibrary.open(mTargetTranslation.getTargetLanguageId(), mTargetTranslation.getProjectId(), r.slug));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            } else {
                throw new InvalidParameterException("a valid target translation id is required");
            }
        } else {
            throw new InvalidParameterException("This activity expects some arguments");
        }

        if(draftTranslations.size() == 0) {
            finish();
            return;
        }

        // TODO: 1/20/2016 eventually users will be forced to define which resource they are translating into as well. When that happens we'll know which draft to choose
        // Right now users will not be able to choose between several different resources if they exist.
        mDraftTranslation = draftTranslations.get(0);

        mRecylerView = (RecyclerView)findViewById(R.id.recycler_view);
        mLayoutManager = new LinearLayoutManager(this);
        mRecylerView.setLayoutManager(mLayoutManager);
        mRecylerView.setItemAnimator(new DefaultItemAnimator());
        mAdapter = new DraftAdapter(this, mDraftTranslation);
        mRecylerView.setAdapter(mAdapter);

        taskWatcher = new SimpleTaskWatcher(this, R.string.loading);
        taskWatcher.setOnFinishedListener(this);

        FloatingActionButton fab = (FloatingActionButton)findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(DraftActivity.this,R.style.AppTheme_Dialog)
                        .setTitle(R.string.import_draft)
                        .setMessage(R.string.import_draft_confirmation)
                        .setNegativeButton(R.string.menu_cancel, null)
                        .setPositiveButton(R.string.label_import, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // // TODO: 1/20/2016 use the draft from the selected tab
                                ImportDraftTask task = new ImportDraftTask(mDraftTranslation);
                                taskWatcher.watch(task);
                                TaskManager.addTask(task, ImportDraftTask.TASK_ID);
                            }
                        }).show();
            }
        });

        // re-attach to tasks
        ManagedTask task = TaskManager.getTask(ImportDraftTask.TASK_ID);
        if(task != null) {
            taskWatcher.watch(task);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onFinished(ManagedTask task) {
        taskWatcher.stop();
        TaskManager.clearTask(task);
        TargetTranslation targetTranslation = ((ImportDraftTask) task).getTargetTranslation();
        if(targetTranslation != null) {
            finish();
        } else {
            new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                    .setTitle(R.string.error)
                    .setMessage(R.string.translation_import_failed)
                    .setNeutralButton(R.string.dismiss, null)
                    .show();
        }
    }
    @Override
    public void onDestroy() {
        taskWatcher.stop();

        super.onDestroy();
    }
}
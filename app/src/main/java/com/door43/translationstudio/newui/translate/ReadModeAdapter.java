package com.door43.translationstudio.newui.translate;

import android.app.Activity;
import android.content.ContentValues;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.design.widget.TabLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.door43.translationstudio.App;
import com.door43.translationstudio.R;
import com.door43.translationstudio.core.Chapter;
import com.door43.translationstudio.core.ChapterTranslation;
import com.door43.translationstudio.core.FrameTranslation;
import com.door43.translationstudio.core.LanguageDirection;
import com.door43.translationstudio.core.ProjectTranslation;
import com.door43.translationstudio.core.TranslationFormat;
import com.door43.translationstudio.core.Frame;
import com.door43.translationstudio.core.SourceTranslation;
import com.door43.translationstudio.core.TargetTranslation;
import com.door43.translationstudio.core.TranslationViewMode;
import com.door43.translationstudio.core.Translator;
import com.door43.translationstudio.core.Typography;
import com.door43.translationstudio.rendering.ClickableRenderingEngine;
import com.door43.translationstudio.rendering.Clickables;
import com.door43.translationstudio.rendering.DefaultRenderer;
import com.door43.translationstudio.rendering.RenderingGroup;
import com.door43.translationstudio.spannables.NoteSpan;
import com.door43.translationstudio.spannables.Span;
import com.door43.widget.ViewUtil;

import java.util.ArrayList;
import java.util.List;

import org.unfoldingword.door43client.Door43Client;
import org.unfoldingword.door43client.models.SourceLanguage;
import org.unfoldingword.door43client.models.TargetLanguage;
import org.unfoldingword.resourcecontainer.ResourceContainer;


/**
 * Created by joel on 9/9/2015.
 */
public class ReadModeAdapter extends ViewModeAdapter<ReadModeAdapter.ViewHolder> {

    private final CharSequence[] mRenderedTargetBody;
    private SourceLanguage mSourceLanguage;
    private final TargetLanguage mTargetLanguage;
    private boolean[] mTargetStateOpen;
    private CharSequence[] mRenderedSourceBody;
    private final Activity mContext;
    private static final int BOTTOM_ELEVATION = 2;
    private static final int TOP_ELEVATION = 3;
    private final TargetTranslation mTargetTranslation;
    private ResourceContainer mResourceContainer;
    private final Door43Client mLibrary;
    private final Translator mTranslator;
    private String[] mChapters;
    private int mLayoutBuildNumber = 0;
    private ContentValues[] mTabs;

    public ReadModeAdapter(Activity context, String targetTranslationId, String sourceTranslationId, String chapterId, String frameId) {
        mLibrary = App.getLibrary();
        mTranslator = App.getTranslator();
        mContext = context;
        mTargetTranslation = mTranslator.getTargetTranslation(targetTranslationId);

        try {
            mResourceContainer = mLibrary.open(SourceTranslation.getSourceLanguageIdFromId(sourceTranslationId),
                    SourceTranslation.getProjectIdFromId(sourceTranslationId),
                    SourceTranslation.getResourceIdFromId(sourceTranslationId));
        } catch (Exception e) {
            e.printStackTrace();
        }
        mSourceLanguage = mLibrary.index().getSourceLanguage(mResourceContainer.language.slug);
        mTargetLanguage = App.languageFromTargetTranslation(mTargetTranslation);

        mChapters = mResourceContainer.chapters();
        if(chapterId != null) {
            // identify starting selection
            for (int i = 0; i < mChapters.length; i ++) {
                String cSlug = mChapters[i];
                if (cSlug.equals(chapterId)) {
                    setListStartPosition(i);
                    break;
                }
            }
        }
        mTargetStateOpen = new boolean[mChapters.length];
        mRenderedSourceBody = new CharSequence[mChapters.length];
        mRenderedTargetBody = new CharSequence[mChapters.length];

        loadTabInfo();
    }

    /**
     * Updates the source translation displayed
     * @param sourceTranslationId
     */
    public void setSourceTranslation(String sourceTranslationId) {
        try {
            mResourceContainer = mLibrary.open(SourceTranslation.getSourceLanguageIdFromId(sourceTranslationId),
                    SourceTranslation.getProjectIdFromId(sourceTranslationId),
                    SourceTranslation.getResourceIdFromId(sourceTranslationId));
        } catch (Exception e) {
            e.printStackTrace();
        }

        mSourceLanguage = mLibrary.index().getSourceLanguage(mResourceContainer.language.slug);

        mChapters = mResourceContainer.chapters();
        mTargetStateOpen = new boolean[mChapters.length];
        mRenderedSourceBody = new CharSequence[mChapters.length];

        loadTabInfo();

        notifyDataSetChanged();
    }

    @Override
    void onCoordinate(ViewHolder holder) {

    }

    @Override
    public String getFocusedFrameId(int position) {
        return null;
    }

    @Override
    public String getFocusedChapterId(int position) {
        if(position >= 0 && position < mChapters.length) {
            return mChapters[position];
        } else {
            return null;
        }
    }

    @Override
    public int getItemPosition(String chapterId, String frameId) {
        for(int i = 0; i < mChapters.length; i ++) {
            String slug = mChapters[i];
            if(slug.equals(chapterId)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void reload() {
        setSourceTranslation(mResourceContainer.slug);
    }


    @Override
    public ViewHolder onCreateManagedViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.fragment_read_list_item, parent, false);
        ViewHolder vh = new ViewHolder(v);
        return vh;
    }

    /**
     * Rebuilds the card tabs
     */
    private void loadTabInfo() {
        List<ContentValues> tabContents = new ArrayList<>();
        String[] sourceTranslationIds = App.getOpenSourceTranslationIds(mTargetTranslation.getId());
        for(String id:sourceTranslationIds) {
            SourceTranslation sourceTranslation = mLibrary.getSourceTranslation(id);
            if(sourceTranslation != null) {
                ContentValues values = new ContentValues();
                // include the resource id if there are more than one
                if(mLibrary.getResources(sourceTranslation.projectSlug, sourceTranslation.sourceLanguageSlug).length > 1) {
                    values.put("title", sourceTranslation.getSourceLanguageTitle() + " " + sourceTranslation.resourceSlug.toUpperCase());
                } else {
                    values.put("title", sourceTranslation.getSourceLanguageTitle());
                }
                values.put("tag", sourceTranslation.getId());
                tabContents.add(values);
            }
        }
        mTabs = tabContents.toArray(new ContentValues[tabContents.size()]);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        int cardMargin = mContext.getResources().getDimensionPixelSize(R.dimen.card_margin);
        int stackedCardMargin = mContext.getResources().getDimensionPixelSize(R.dimen.stacked_card_margin);
        if(mTargetStateOpen[position]) {
            // target on top
            // elevation takes precedence for API 21+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                holder.mSourceCard.setElevation(BOTTOM_ELEVATION);
                holder.mTargetCard.setElevation(TOP_ELEVATION);
            }
            holder.mTargetCard.bringToFront();
            CardView.LayoutParams targetParams = (CardView.LayoutParams)holder.mTargetCard.getLayoutParams();
            targetParams.setMargins(cardMargin, cardMargin, stackedCardMargin, stackedCardMargin);
            holder.mTargetCard.setLayoutParams(targetParams);
            CardView.LayoutParams sourceParams = (CardView.LayoutParams)holder.mSourceCard.getLayoutParams();
            sourceParams.setMargins(stackedCardMargin, stackedCardMargin, cardMargin, cardMargin);
            holder.mSourceCard.setLayoutParams(sourceParams);
            ((View) holder.mTargetCard.getParent()).requestLayout();
            ((View) holder.mTargetCard.getParent()).invalidate();

            // disable new tab button so we don't accidently open it
            holder.mNewTabButton.setEnabled(false);
        } else {
            // source on top
            // elevation takes precedence for API 21+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                holder.mTargetCard.setElevation(BOTTOM_ELEVATION);
                holder.mSourceCard.setElevation(TOP_ELEVATION);
            }
            holder.mSourceCard.bringToFront();
            CardView.LayoutParams sourceParams = (CardView.LayoutParams)holder.mSourceCard.getLayoutParams();
            sourceParams.setMargins(cardMargin, cardMargin, stackedCardMargin, stackedCardMargin);
            holder.mSourceCard.setLayoutParams(sourceParams);
            CardView.LayoutParams targetParams = (CardView.LayoutParams)holder.mTargetCard.getLayoutParams();
            targetParams.setMargins(stackedCardMargin, stackedCardMargin, cardMargin, cardMargin);
            holder.mTargetCard.setLayoutParams(targetParams);
            ((View) holder.mSourceCard.getParent()).requestLayout();
            ((View) holder.mSourceCard.getParent()).invalidate();

            // re-enable new tab button
            holder.mNewTabButton.setEnabled(true);
        }

        holder.mTargetCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTargetTranslationCard(holder, position);
            }
        });
        holder.mSourceCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeTargetTranslationCard(holder, position);
            }
        });

        holder.mNewTabButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getListener() != null) {
                    getListener().onNewSourceTranslationTabClick();
                }
            }
        });

        final Chapter chapter = mChapters[position];

        // render the source chapter body
        if(mRenderedSourceBody[position] == null) {
            String chapterBody = mLibrary.getChapterBody(mResourceContainer, chapter.getId());
            TranslationFormat bodyFormat = mLibrary.getChapterBodyFormat(mResourceContainer, chapter.getId());
            RenderingGroup sourceRendering = new RenderingGroup();
            if (Clickables.isClickableFormat(bodyFormat)) {
                // TODO: add click listeners
                Span.OnClickListener noteClickListener = new Span.OnClickListener() {
                    @Override
                    public void onClick(View view, Span span, int start, int end) {
                        if(span instanceof NoteSpan) {
                            new AlertDialog.Builder(mContext,R.style.AppTheme_Dialog)
                                    .setTitle(R.string.title_note)
                                    .setMessage(((NoteSpan)span).getNotes())
                                    .setPositiveButton(R.string.dismiss, null)
                                    .show();
                        }
                    }

                    @Override
                    public void onLongClick(View view, Span span, int start, int end) {

                    }
                };
                ClickableRenderingEngine renderer = Clickables.setupRenderingGroup(bodyFormat, sourceRendering, null, noteClickListener, true);

                // In read mode (and only in read mode), pull leading major section headings out for
                // display above chapter headings.
                renderer.setSuppressLeadingMajorSectionHeadings(true);
                CharSequence heading = renderer.getLeadingMajorSectionHeading(chapterBody);
                holder.mSourceHeading.setText(heading);
                holder.mSourceHeading.setVisibility(
                        heading.length() > 0 ? View.VISIBLE : View.GONE);
            } else {
                sourceRendering.addEngine(new DefaultRenderer());
            }
            sourceRendering.init(chapterBody);
            mRenderedSourceBody[position] = sourceRendering.start();
        }

        holder.mSourceBody.setText(mRenderedSourceBody[position]);
        ViewUtil.makeLinksClickable(holder.mSourceBody);
        String chapterTitle = chapter.title;
        if(chapter.title.isEmpty()) {
            chapterTitle = mResourceContainer.getProjectTitle() + " " + Integer.parseInt(chapter.getId());
        }
        holder.mSourceTitle.setText(chapterTitle);

        // render the target chapter body
        if(mRenderedTargetBody[position] == null) {
            TranslationFormat bodyFormat = mTargetTranslation.getFormat();
            String chapterBody = "";
            String[] frameSlugs = mLibrary.getFrameSlugs(mResourceContainer, chapter.getId());
            for (String frameSlug : frameSlugs) {
                Frame simpleFrame = new Frame(frameSlug, chapter.getId(), null, bodyFormat, null);
                FrameTranslation frameTranslation = mTargetTranslation.getFrameTranslation(simpleFrame);
                chapterBody += " " + frameTranslation.body;
            }
            RenderingGroup targetRendering = new RenderingGroup();
            if(Clickables.isClickableFormat(bodyFormat)) {
                // TODO: add click listeners
                ClickableRenderingEngine renderer = Clickables.setupRenderingGroup(bodyFormat, targetRendering, null, null, true);
                renderer.setVersesEnabled(true);
            } else {
                targetRendering.addEngine(new DefaultRenderer());
            }
            targetRendering.init(chapterBody);
            mRenderedTargetBody[position] = targetRendering.start();
        }

        // display begin translation button
        if(mRenderedTargetBody[position].toString().trim().isEmpty()) {
            holder.mBeginButton.setVisibility(View.VISIBLE);
            final GestureDetector detector = new GestureDetector(new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    Bundle args = new Bundle();
                    args.putBoolean(ChunkModeFragment.EXTRA_TARGET_OPEN, true);
                    args.putString(App.EXTRA_CHAPTER_ID, chapter.getId());
                    getListener().openTranslationMode(TranslationViewMode.CHUNK, args);
                    return true;
                }
            });
            holder.mBeginButton.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return detector.onTouchEvent(event);
                }
            });
        } else {
            holder.mBeginButton.setVisibility(View.GONE);
        }

        // TODO: indicate completed chapter translations
//        if(frameTranslation.isTitleFinished()) {
//            holder.mTargetInnerCard.setBackgroundResource(R.color.white);
//        } else {
//            holder.mTargetInnerCard.setBackgroundResource(R.drawable.paper_repeating);
//        }

        holder.mTargetBody.setText(mRenderedTargetBody[position]);

//        ChapterTranslation getChapterTranslation(String chapterSlug);

        String targetCardTitle = "";

        // look for translated chapter title first
        final ChapterTranslation chapterTranslation = mTargetTranslation.getChapterTranslation(chapter);
        if(null != chapterTranslation) {
            targetCardTitle = chapterTranslation.title;
        }

        if (targetCardTitle.isEmpty() && !chapterTitle.isEmpty()) { // if no target chapter title translation, fall back to source chapter title
            if(!chapter.title.isEmpty()) {
                targetCardTitle = chapterTitle;
            }
        }

        if (targetCardTitle.isEmpty()) { // if no chapter titles, fall back to project title, try translated title first
            ProjectTranslation projTrans = mTargetTranslation.getProjectTranslation();
            if(!projTrans.getTitle().isEmpty()) {
                targetCardTitle = projTrans.getTitle() + " " + Integer.parseInt(chapter.getId());
            }
        }

        if (targetCardTitle.isEmpty()) { // fall back to project source title
            targetCardTitle = mResourceContainer.getProjectTitle() + " " + Integer.parseInt(chapter.getId());
        }

        holder.mTargetTitle.setText(targetCardTitle + " - " + mTargetLanguage.name);

        // load tabs
        holder.mTabLayout.setOnTabSelectedListener(null);
        holder.mTabLayout.removeAllTabs();
        for(ContentValues values:mTabs) {
            TabLayout.Tab tab = holder.mTabLayout.newTab();
            tab.setText(values.getAsString("title"));
            tab.setTag(values.getAsString("tag"));
            holder.mTabLayout.addTab(tab);
        }

        // select correct tab
        for(int i = 0; i < holder.mTabLayout.getTabCount(); i ++) {
            TabLayout.Tab tab = holder.mTabLayout.getTabAt(i);
            if(tab.getTag().equals(mResourceContainer.getId())) {
                tab.select();
                break;
            }
        }

        // hook up listener
        holder.mTabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                final String sourceTranslationId = (String) tab.getTag();
                if (getListener() != null) {
                    Handler hand = new Handler(Looper.getMainLooper());
                    hand.post(new Runnable() {
                        @Override
                        public void run() {
                            getListener().onSourceTranslationTabClick(sourceTranslationId);
                        }
                    });
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });

        // set up fonts
        if(holder.mLayoutBuildNumber != mLayoutBuildNumber) {
            holder.mLayoutBuildNumber = mLayoutBuildNumber;

            Typography.formatTitle(mContext, holder.mSourceHeading, mSourceLanguage.slug, LanguageDirection.get(mSourceLanguage.direction));
            Typography.formatTitle(mContext, holder.mSourceTitle, mSourceLanguage.slug, LanguageDirection.get(mSourceLanguage.direction));
            Typography.format(mContext, holder.mSourceBody, mSourceLanguage.slug, LanguageDirection.get(mSourceLanguage.direction));
            Typography.formatTitle(mContext, holder.mTargetTitle, mTargetLanguage.slug, LanguageDirection.get(mTargetLanguage.direction));
            Typography.format(mContext, holder.mTargetBody, mTargetLanguage.slug, LanguageDirection.get(mTargetLanguage.direction));
        }
    }

    @Override
    public int getItemCount() {
        return mChapters.length;
    }

    public void rebuild() {
        mLayoutBuildNumber ++;
        notifyDataSetChanged();
    }

    /**
     * Toggle the target translation card between front and back
     * @param holder
     * @param position
     * @param swipeLeft
     * @return true if action was taken, else false
     */
    public void toggleTargetTranslationCard(final ViewHolder holder, final int position, final boolean swipeLeft) {
        if (mTargetStateOpen[position]) {
            closeTargetTranslationCard( holder, position, !swipeLeft);
            return;
        }

        openTargetTranslationCard( holder, position, !swipeLeft);
        return;
    }

    /**
     * Moves the target translation card to the back
     * @param holder
     * @param position
     * @param leftToRight
     */
    public void closeTargetTranslationCard(final ViewHolder holder, final int position, final boolean leftToRight) {
        if (mTargetStateOpen[position]) {
            ViewUtil.animateSwapCards(holder.mTargetCard, holder.mSourceCard, TOP_ELEVATION, BOTTOM_ELEVATION, leftToRight, new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    mTargetStateOpen[position] = false;
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });

            // re-enable new tab button
            holder.mNewTabButton.setEnabled(true);
        }
    }


    /**
     * Moves the target translation card to the back - left to right
     * @param holder
     * @param position
     * @return true if action was taken, else false
     */
    public void closeTargetTranslationCard(final ViewHolder holder, final int position) {
        closeTargetTranslationCard ( holder, position, true);
    }

    /**
     * Moves the target translation to the top
     * @param holder
     * @param position
     * @param leftToRight
     */
    public void openTargetTranslationCard(final ViewHolder holder, final int position, final boolean leftToRight) {
        if (!mTargetStateOpen[position]) {
            ViewUtil.animateSwapCards(holder.mSourceCard, holder.mTargetCard, TOP_ELEVATION, BOTTOM_ELEVATION, leftToRight, new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    mTargetStateOpen[position] = true;
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });

            // disable new tab button so we don't accidently open it
            holder.mNewTabButton.setEnabled(false);
        }
    }

    /**
     * Moves the target translation to the top
     * @param holder
     * @param position
     * @return true if action was taken, else false
     */
    public void openTargetTranslationCard(final ViewHolder holder, final int position) {
        openTargetTranslationCard( holder, position, false);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final Button mBeginButton;
        private final TextView mTargetTitle;
        private final TextView mTargetBody;
        private final CardView mTargetCard;
        private final CardView mSourceCard;
        private final ImageButton mNewTabButton;
        public TextView mSourceHeading;
        public TextView mSourceTitle;
        public TextView mSourceBody;
        public TabLayout mTabLayout;
        public int mLayoutBuildNumber = -1;

        public ViewHolder(View v) {
            super(v);
            mSourceCard = (CardView)v.findViewById(R.id.source_translation_card);
            mSourceHeading = (TextView)v.findViewById(R.id.source_translation_heading);
            mSourceTitle = (TextView)v.findViewById(R.id.source_translation_title);
            mSourceBody = (TextView)v.findViewById(R.id.source_translation_body);
            mTargetCard = (CardView)v.findViewById(R.id.target_translation_card);
            mTargetTitle = (TextView)v.findViewById(R.id.target_translation_title);
            mTargetBody = (TextView)v.findViewById(R.id.target_translation_body);
            mTabLayout = (TabLayout)v.findViewById(R.id.source_translation_tabs);
            mTabLayout.setTabTextColors(R.color.dark_disabled_text, R.color.dark_secondary_text);
            mNewTabButton = (ImageButton) v.findViewById(R.id.new_tab_button);
            mBeginButton = (Button) v.findViewById(R.id.begin_translating_button);
        }
    }
}

package com.door43.translationstudio.newui;

import org.apache.commons.io.FileUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.ViewInteraction;
import android.support.test.espresso.matcher.BoundedMatcher;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.v7.widget.Toolbar;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.View;

import com.door43.translationstudio.AppContext;
import com.door43.translationstudio.R;
import com.door43.translationstudio.core.Profile;


import java.io.File;
import java.io.InputStream;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static android.support.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static junit.framework.Assert.assertTrue;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;


@RunWith(AndroidJUnit4.class)
@LargeTest
public class ImportUsfmActivityUiTest {

    private File mTestFile;
    private File mTempDir;
    private Context mTestContext;

    @Rule
    public ActivityTestRule<ImportUsfmActivity> mActivityRule = new ActivityTestRule<>(
            ImportUsfmActivity.class,
            true,    // initialTouchMode
            false);  // don't launchActivity yet

    @Before
    public void setUp() {
        mTestContext = InstrumentationRegistry.getContext();
        if(AppContext.getProfile() == null) { // make sure this is initialized
            AppContext.setProfile(new Profile("testing"));
        }
    }

    @After
    public void tearDown() {
        if(mTempDir != null) {
            FileUtils.deleteQuietly(mTempDir);
            mTempDir = null;
            mTestFile = null;
        }
        mActivityRule.getActivity().finish();
    }

    @Test
    public void markOnlyOneChapter() throws Exception {

        //given
        String testFile = "usfm/mrk.usfm_one_chapter.txt";
        Intent intent = getIntentForTestFile(testFile);
        mActivityRule.launchActivity(intent);
        checkDisplayState(R.string.title_activity_import_usfm_language, true);
        onView(withText("aa")).perform(click());
        boolean seen = waitWhileDisplayed(R.string.reading_usfm);

        //when
        matchSummaryDialog(R.string.title_import_usfm_error, "mrk", false);
        rotateScreen();

        //then
        matchSummaryDialog(R.string.title_import_usfm_error, "mrk", false);
        rotateScreen();
    }


    @Test
    public void markNoId() throws Exception {

        //given
        String testFile = "usfm/mrk.usfm_no_id.txt";
        Intent intent = getIntentForTestFile(testFile);
        mActivityRule.launchActivity(intent);
        checkDisplayState(R.string.title_activity_import_usfm_language, true);
        onView(withText("aa")).perform(click());
        boolean seen = waitWhileDisplayed(R.string.reading_usfm);
        thenShouldShowMissingBookNameDialog();
        rotateScreen();

        //when
        thenShouldShowMissingBookNameDialog();
        onView(withId(R.id.positiveButton)).perform(click());
        onView(withText("Bible: NT")).perform(click());
        onView(withText("Mark")).perform(click());

        //then
        rotateScreen();
        matchSummaryDialog(R.string.title_import_usfm_summary, "mrk", true);
    }


    @Test
    public void markValid() throws Exception {

        //given
        String testFile = "usfm/mrk.usfm.txt";
        Intent intent = getIntentForTestFile(testFile);
        mActivityRule.launchActivity(intent);
        checkDisplayState(R.string.title_activity_import_usfm_language, true);

        //when
        onView(withText("aa")).perform(click());
        boolean seen = waitWhileDisplayed(R.string.reading_usfm);
        matchSummaryDialog(R.string.title_import_usfm_summary, "mrk", true);
        rotateScreen();

        //then
        matchSummaryDialog(R.string.title_import_usfm_summary, "mrk", true);
        rotateScreen();
    }

    /**
     * match expected values on summary dialog
     * @param title
     * @param book
     * @param noErrors
     */
    protected void matchSummaryDialog(int title, String book, boolean noErrors) {
        thenShouldHaveSummaryDialog(title);
        shouldHaveFoundBook(book);
        checkForImportErrors(noErrors);
    }

    /**
     * check if dialog content shows no errors
     * @param noErrors
     */
    private void checkForImportErrors(boolean noErrors) {
        String matchText = AppContext.context().getResources().getString(R.string.no_error);
        Matcher<View> viewMatcher = withText(containsString(matchText));
        if(!noErrors) {
            viewMatcher = not(viewMatcher);
        }
        onView(withId(R.id.dialog_content)).check(matches(viewMatcher));
    }

    /**
     * make sure we found the book
     * @param book
     */
    private void shouldHaveFoundBook(String book) {
        String format = AppContext.context().getResources().getString(R.string.found_book);
        String matchText = String.format(format, book);
        onView(withId(R.id.dialog_content)).check(matches(withText(containsString(matchText))));
    }

    protected void thenShouldShowMissingBookNameDialog() {
        onView(withId(R.id.dialog_title)).check(matches(withText(R.string.title_activity_import_usfm_language)));
    }

    protected void thenShouldHaveSummaryDialog(int title) {
        onView(withId(R.id.dialog_title)).check(matches(withText(title)));
    }

    /**
     * since progress dialog is async, then we idle while it is shown
     * @param resource
     * @return
     */
    private boolean waitWhileDisplayed(int resource) {
        String text = AppContext.context().getResources().getString(resource);
        return waitWhileDisplayed(text);
    }

    /**
     * since progress dialog is async, then we idle while it is shown
     * @param text
     * @return
     */
    private boolean waitWhileDisplayed(String text) {
        boolean done = false;
        boolean viewSeen = false;
        ViewInteraction interaction = onView(withText(text));
        while(!done) {
            try {
                interaction.check(matches(isCompletelyDisplayed()));
                viewSeen = true;
            } catch (Exception e) {
                done = true;
            }
        }
        return viewSeen;
    }

    /**
     * make sure text is displayed or not displayed in view
     * @param resource
     * @param displayed
     */
    private void checkDisplayState(int resource, boolean displayed) {
        String text = AppContext.context().getResources().getString(resource);
        checkDisplayState(text, displayed);
    }

    /**
     * make sure text is displayed or not displayed in view
     * @param text
     * @param displayed
     */
    private void checkDisplayState(String text, boolean displayed) {
        ViewInteraction interaction = onView(withText(text));
        Matcher<View> displayState = isCompletelyDisplayed();
        if(!displayed) {
            displayState = not(displayState);
        }
        interaction.check(matches(displayState));
    }

    /**
     * generate intent for ImportUsfmActivity 
     * @param fileName
     * @return
     * @throws Exception
     */
    protected Intent getIntentForTestFile(String fileName) throws Exception {
        Intent intent = new Intent();
        Resources testRes = mTestContext.getResources();

        mTempDir = new File(AppContext.context().getCacheDir(), System.currentTimeMillis() + "");
        mTempDir.mkdirs();

        InputStream usfmStream = mTestContext.getAssets().open(fileName);
        mTestFile = new File(mTempDir, "testFile.usfm");
        FileUtils.copyInputStreamToFile(usfmStream, mTestFile);

        intent.putExtra(ImportUsfmActivity.EXTRA_USFM_IMPORT_FILE, mTestFile);
        return intent;
    }


    /**
     * force page orientation change
     */
    protected void rotateScreen() {
        Context context = InstrumentationRegistry.getTargetContext();
        int orientation
                = context.getResources().getConfiguration().orientation;

        Activity activity = mActivityRule.getActivity();
        activity.setRequestedOrientation(
                (orientation == Configuration.ORIENTATION_PORTRAIT) ?
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE :
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    /**
     * get interaction for toolbar with title
     * @param resource
     * @return
     */
    private static ViewInteraction matchToolbarTitle(
            int resource) {
        String title = AppContext.context().getResources().getString(resource);
        return matchToolbarTitle(title);
    }

    /**
     * get interaction for toolbar with title
     * @param title
     * @return
     */
    private static ViewInteraction matchToolbarTitle(
            CharSequence title) {
        return onView(allOf(isAssignableFrom(Toolbar.class)))
                .check(matches(withToolbarTitle(is(title))));
    }

    /**
     * get interaction for toolbar with title
     * @param title
     * @return
     */
    private static ViewInteraction notMatchToolbarTitle(
            CharSequence title) {
        return onView(isAssignableFrom(Toolbar.class))
                .check(matches(not(withToolbarTitle(is(title)))));
    }

    /**
     * match toolbar
     * @param textMatcher
     * @return
     */
    private static Matcher<Object> withToolbarTitle(
            final Matcher<CharSequence> textMatcher) {
        return new BoundedMatcher<Object, Toolbar>(Toolbar.class) {
            @Override public boolean matchesSafely(Toolbar toolbar) {
                return textMatcher.matches(toolbar.getTitle());
            }
            @Override public void describeTo(Description description) {
                description.appendText("with toolbar title: ");
                textMatcher.describeTo(description);
            }
        };
    }

}

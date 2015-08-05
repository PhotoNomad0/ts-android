package com.door43.translationstudio.panes.left;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.astuetz.PagerSlidingTabStrip;
import com.door43.translationstudio.R;
import com.door43.translationstudio.panes.left.tabs.ChaptersTabFragment;
import com.door43.translationstudio.panes.left.tabs.FramesTabFragment;
import com.door43.translationstudio.panes.left.tabs.ProjectsTabFragment;
import com.door43.translationstudio.util.StringFragmentKeySet;
import com.door43.translationstudio.util.TabbedViewPagerAdapter;
import com.door43.translationstudio.util.TabsFragmentAdapterNotification;
import com.door43.translationstudio.util.TranslatorBaseFragment;
import com.door43.util.Screen;

import java.util.ArrayList;

/**
 * Created by joel on 8/26/2014.
 */
public class LeftPaneFragment extends TranslatorBaseFragment {
    private ViewPager mViewPager;
    private PagerSlidingTabStrip mSlidingTabLayout;
    private TabbedViewPagerAdapter tabbedViewPagerAdapter;
    private ArrayList<StringFragmentKeySet> tabs = new ArrayList<StringFragmentKeySet>();
    private int mDefaultPage = 0;
    private int mSelectedTabColor = 0;
    private ProjectsTabFragment mProjectsTab = new ProjectsTabFragment();
    private ChaptersTabFragment mChaptersTab = new ChaptersTabFragment();
    private FramesTabFragment mFramesTab = new FramesTabFragment();
    private int mLayoutWidth = 0;
    private View mRootView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        mRootView = inflater.inflate(R.layout.fragment_pane_left, container, false);

        if(tabs.size() == 0) {
            // Tabs
            tabs.add(new StringFragmentKeySet(getResources().getString(R.string.title_projects), mProjectsTab));
            tabs.add(new StringFragmentKeySet(getResources().getString(R.string.title_chapters), mChaptersTab));
            tabs.add(new StringFragmentKeySet(getResources().getString(R.string.title_frames), mFramesTab));
        }

//        ViewPager
        mViewPager = (ViewPager) mRootView.findViewById(R.id.leftViewPager);
        tabbedViewPagerAdapter = new TabbedViewPagerAdapter(getFragmentManager(), tabs);
        mViewPager.setAdapter(tabbedViewPagerAdapter);

        // Sliding tab layout
        mSlidingTabLayout = (PagerSlidingTabStrip) mRootView.findViewById(R.id.left_sliding_tabs);
        mSlidingTabLayout.setViewPager(mViewPager);
        mSlidingTabLayout.setTextColorResource(R.color.light_primary_text);
        mSlidingTabLayout.setTextSize(Screen.dpToPx(getActivity(), 20));

        selectTab(mDefaultPage);

        if(mLayoutWidth != 0) {
            mRootView.setLayoutParams(new ViewGroup.LayoutParams(mLayoutWidth, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        return mRootView;
    }

    /**
     * Changes the selected tab
     * @param i
     */
    public void selectTab(int i) {
        if(mViewPager != null) {
            if(tabs.size() > i && i >= 0) {
                // select the tab
                mViewPager.setCurrentItem(i);
                // notify the tab list adapter that it should reload
                ((TabsFragmentAdapterNotification)tabs.get(i).getFragment()).NotifyAdapterDataSetChanged();
            }
        } else {
            mDefaultPage = i;
        }
    }

    /**
     * Returns the currently selected tab index
     * @return
     */
    public int getSelectedTabIndex() {
        if(mViewPager != null) {
            return mViewPager.getCurrentItem();
        } else {
            return 0;
        }
    }

    /**
     * Notifies the projects adapter that the dataset has changed
     */
    public void reloadProjectsTab() {
        mProjectsTab.NotifyAdapterDataSetChanged();
    }

    /**
     * Notifies the chapters adapter that the dataset has changed
     */
    public void reloadChaptersTab() {
        mChaptersTab.NotifyAdapterDataSetChanged();
    }

    /**
     * Notifies the frames adapter that the dataset has changed
     */
    public void reloadFramesTab() {
        mFramesTab.NotifyAdapterDataSetChanged();
    }

    /**
     * Specifies the width of the layout
     * @param width
     */
    public void setLayoutWidth(int width) {
        if(mRootView != null) {
            mRootView.setLayoutParams(new ViewGroup.LayoutParams(mLayoutWidth, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            mLayoutWidth = width;
        }
    }
}

package com.door43.translationstudio.newui;

import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.door43.translationstudio.R;

/**
 * A placeholder fragment containing a simple view.
 */
public class DraftPreviewActivityFragment extends Fragment {

    public DraftPreviewActivityFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_draft_preview, container, false);
    }
}

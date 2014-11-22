/******************************************************************************
 * Project:  NextGIS mobile
 * Purpose:  Mobile GIS for Android.
 * Author:   Dmitry Baryshnikov (aka Bishop), polimax@mail.ru
 ******************************************************************************
 *   Copyright (C) 2014 NextGIS
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ****************************************************************************/
package com.nextgis.mobile.dialogs;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.nextgis.mobile.MainActivity;
import com.nextgis.mobile.R;
import com.nextgis.mobile.datasource.NgwConnection;
import com.nextgis.mobile.datasource.NgwConnectionList;
import com.nextgis.mobile.datasource.NgwConnectionWorker;
import com.nextgis.mobile.datasource.NgwResource;
import com.nextgis.mobile.map.MapBase;
import com.nextgis.mobile.map.NgwRasterLayer;
import com.nextgis.mobile.map.NgwVectorLayer;
import com.nextgis.mobile.util.Constants;
import com.nextgis.mobile.util.GeoConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

public class NgwResourcesDialog extends DialogFragment {

    protected MainActivity mMainActivity;
    protected MapBase mMap;

    protected NgwConnectionList mNgwConnections;
    protected NgwConnection mCurrConn;
    protected NgwConnectionWorker mNgwConnWorker;
    protected boolean mIsHttpRunning;

    protected NgwResourceRoots mNgwResRoots;
    protected NgwResource mCurrNgwRes;
    protected Set<NgwResource> mSelectedResources;
    protected Iterator<NgwResource> mSelResIterator;

    protected TextView mDialogTitleText;
    protected RelativeLayout mButtonBar;
    protected ImageButton mAddConnectionButton;
    protected ImageButton mOkButton;
    protected ImageButton mCancelButton;
    protected ProgressBar mHttpProgressBar;
    protected boolean mIsConnectionView;

    protected ListView mResourceList;
    protected NgwConnectionsListAdapter mConnectionsAdapter;
    protected AdapterView.OnItemClickListener mConnectionOnClickListener;
    protected AdapterView.OnItemLongClickListener mConnectionOnLongClickListener;

    // TODO: edit connections


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setStyle(STYLE_NO_TITLE, getTheme()); // remove title from DialogFragment

        mMainActivity = (MainActivity) getActivity();
        mMap = mMainActivity.getMap();
        mNgwConnections = mMap.getNgwConnections();

        mNgwResRoots = new NgwResourceRoots();
        for (int i = 0, connectionsSize = mNgwConnections.size(); i < connectionsSize; i++) {
            NgwConnection connection = mNgwConnections.get(i);
            mNgwResRoots.add(new NgwResource(connection.getId()));
        }

        mSelectedResources = new TreeSet<NgwResource>();
        mCurrNgwRes = null;

        mIsConnectionView = true;
        mIsHttpRunning = false;

        mConnectionsAdapter = new NgwConnectionsListAdapter(mMainActivity, mNgwConnections);
        mConnectionOnClickListener = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mCurrConn = mNgwConnections.get(position);
                mCurrNgwRes = mNgwResRoots.get(mCurrConn.getId());

                if (mCurrNgwRes.size() == 0) {
                    setHttpRunningView(true);
                    mCurrConn.setLoadResourceArray(mCurrNgwRes);
                    mNgwConnWorker.loadNgwJson(mCurrConn);

                } else {
                    setJsonView();
                }

            }
        };
        mConnectionOnLongClickListener = new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                NgwDeleteConnectionDialog dialog = NgwDeleteConnectionDialog.newInstance(position);

                dialog.setOnDeleteConnectionListener(
                        new NgwDeleteConnectionDialog.OnDeleteConnectionListener() {
                            @Override
                            public void onDeleteConnection(int index) {
                                mNgwResRoots.remove(mNgwConnections.get(index).getId());
                                mNgwConnections.remove(index);
                                NgwConnection.saveNgwConnections(mNgwConnections, mMap.getMapPath());
                                ((BaseAdapter) mResourceList.getAdapter()).notifyDataSetChanged();
                            }
                        });

                dialog.show(getActivity().getSupportFragmentManager(), "NgwDeleteConnectionDialog");
                return true;
            }
        };

        mNgwConnWorker = new NgwConnectionWorker();

        mNgwConnWorker.setJsonArrayLoadedListener(
                new NgwConnectionWorker.JsonArrayLoadedListener() {
                    @Override
                    public void onJsonArrayLoaded(final JSONArray jsonArray) {
                        if (jsonArray == null) {
                            // TODO: localization
                            Toast.makeText(mMap.getContext(), "Connection ERROR",
                                    Toast.LENGTH_LONG).show();
                            setHttpRunningView(false);
                            return;
                        }

                        setHttpRunningView(false);

                        try {
                            mCurrNgwRes.addNgwResourcesFromJSONArray(jsonArray, mSelectedResources);
                        } catch (JSONException e) {
                            // TODO: error to Log
                            e.printStackTrace();
                        }

                        // Adding link to parent ("..") to position 0 (after sorting) of mResourceList
                        NgwResource ngwResource = new NgwResource(
                                mCurrConn.getId(),
                                mCurrNgwRes.getParent(),
                                mCurrNgwRes.getId(),
                                Constants.NGWTYPE_PARENT_RESOURCE_GROUP,
                                Constants.JSON_PARENT_DISPLAY_NAME_VALUE);

                        mCurrNgwRes.add(ngwResource);
                        mCurrNgwRes.sort();

                        setJsonView();
                    }
                });

        mNgwConnWorker.setJsonObjectLoadedListener(
                new NgwConnectionWorker.JsonObjectLoadedListener() {
                    @Override
                    public void onJsonObjectLoaded(JSONObject jsonObject) {
                        if (jsonObject == null) {
                            // TODO: localization
                            Toast.makeText(
                                    mMap.getContext(), "Connection ERROR", Toast.LENGTH_LONG).show();
                            dismiss();
                            return;
                        }

                        // TODO: ProgressDialog with Fragment for screen rotation
                        ProgressDialog progressDialog = new ProgressDialog(mMainActivity);
                        progressDialog.setMessage(
                                mMainActivity.getString(R.string.message_loading_progress));
                        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                        progressDialog.setCancelable(true);
                        progressDialog.show();

                        try {
                            switch (mCurrNgwRes.getCls()) {
                                case Constants.NGWTYPE_VECTOR_LAYER:
                                    new NgwVectorLayer(mCurrConn.getId(), mCurrNgwRes.getId())
                                            .create(mMap, mCurrNgwRes.getDisplayName(),
                                                    jsonObject, progressDialog);
                                    break;

                                case Constants.NGWTYPE_RASTER_LAYER:
                                    break;
                            }

                        } catch (JSONException e) {
                            String error = "Error in " + mCurrNgwRes.getDisplayName()
                                    + "\n" + e.getLocalizedMessage();
                            Log.w(Constants.TAG, error);
                            Toast.makeText(mMainActivity, error, Toast.LENGTH_LONG).show();

                        } catch (IOException e) {
                            String error = "Error in " + mCurrNgwRes.getDisplayName()
                                    + "\n" + e.getLocalizedMessage();
                            Log.w(Constants.TAG, error);
                            Toast.makeText(mMainActivity, error, Toast.LENGTH_LONG).show();
                        }

                        if (mSelResIterator.hasNext()) {
                            mCurrNgwRes = mSelResIterator.next();
                            mCurrConn.setLoadGeoJsonObject(mCurrNgwRes);
                            mNgwConnWorker.loadNgwJson(mCurrConn);
                        } else {
                            dismiss();
                        }
                    }
                });
    }

    @Override
    public void onDestroyView() {
        if (getDialog() != null && getRetainInstance())
            getDialog().setOnDismissListener(null);
        super.onDestroyView();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.ngw_resources_dialog, container);

        mDialogTitleText = (TextView) view.findViewById(R.id.dialog_title_text);
        mHttpProgressBar = (ProgressBar) view.findViewById(R.id.http_progress_bar);
        mButtonBar = (RelativeLayout) view.findViewById(R.id.button_nar);

        mAddConnectionButton = (ImageButton) view.findViewById(R.id.btn_add_connection);
        mAddConnectionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NgwAddConnectionDialog dialog = new NgwAddConnectionDialog();

                dialog.setOnAddConnectionListener(
                        new NgwAddConnectionDialog.OnAddConnectionListener() {
                            @Override
                            public void onAddConnection(NgwConnection connection) {
                                mNgwConnections.add(connection);
                                NgwConnection.saveNgwConnections(mNgwConnections, mMap.getMapPath());
                                mNgwResRoots.add(new NgwResource(connection.getId()));
                                ((BaseAdapter) mResourceList.getAdapter()).notifyDataSetChanged();
                            }
                        });

                dialog.show(getActivity().getSupportFragmentManager(), "NgwAddConnectionDialog");
            }
        });

        mOkButton = (ImageButton) view.findViewById(R.id.btn_ok_res);
        mOkButton.setEnabled(!mSelectedResources.isEmpty());
        mOkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSelResIterator = mSelectedResources.iterator();

                if (!mSelResIterator.hasNext()) {
                    dismiss();
                }

                setLoadResourcesView(true);

                while (mSelResIterator.hasNext()) {

                    mCurrNgwRes = mSelResIterator.next();

                    switch (mCurrNgwRes.getCls()) {
                        case Constants.NGWTYPE_VECTOR_LAYER:
                            continue;

                        case Constants.NGWTYPE_RASTER_LAYER:
                            try {
                                new NgwRasterLayer(mCurrConn.getId(), mCurrNgwRes.getId())
                                        .create(mMap, mCurrNgwRes.getDisplayName(),
                                                mCurrConn.getUrl()
                                                        + "resource/" + mCurrNgwRes.getId()
                                                        + "/tms?z={z}&x={x}&y={y}",
                                                GeoConstants.TMSTYPE_OSM);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                    }
                }

                mSelResIterator = mSelectedResources.iterator();

                while (mSelResIterator.hasNext()) {
                    mCurrNgwRes = mSelResIterator.next();

                    switch (mCurrNgwRes.getCls()) {
                        case Constants.NGWTYPE_VECTOR_LAYER:
                            mCurrConn.setLoadGeoJsonObject(mCurrNgwRes);
                            mNgwConnWorker.loadNgwJson(mCurrConn);
                            break;

                        case Constants.NGWTYPE_RASTER_LAYER:
                            continue;
                    }
                    break;
                }

                dismiss();
            }
        });

        mCancelButton = (ImageButton) view.findViewById(R.id.btn_cancel_res);
        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mNgwConnWorker.cancel();
                dismiss();
            }
        });

        mResourceList = (ListView) view.findViewById(R.id.ngw_resources_list);

        if (mIsConnectionView) {
            setConnectionView();
        } else {
            setJsonView();
        }

        setHttpRunningView(mIsHttpRunning);
        return view;
    }

    protected void setHttpRunningView(boolean isRunning) {
        mIsHttpRunning = isRunning;
        mHttpProgressBar.setVisibility(mIsHttpRunning ? View.VISIBLE : View.INVISIBLE);
        mAddConnectionButton.setEnabled(!mIsHttpRunning);
        mOkButton.setEnabled(!mIsHttpRunning);
        mResourceList.setEnabled(!mIsHttpRunning);
    }

    protected void setLoadResourcesView(boolean isRunning) {
        mIsHttpRunning = isRunning;
        mHttpProgressBar.setVisibility(mIsHttpRunning ? View.VISIBLE : View.INVISIBLE);
        mAddConnectionButton.setEnabled(!mIsHttpRunning);
        mOkButton.setEnabled(!mIsHttpRunning);
        mResourceList.setVisibility(View.GONE);
    }

    protected void setConnectionView() {
        mIsConnectionView = true;
        mDialogTitleText.setText(mMainActivity.getString(R.string.ngw_connections));

        mAddConnectionButton.setVisibility(View.VISIBLE);

        mResourceList.setAdapter(mConnectionsAdapter);
        mResourceList.setOnItemClickListener(mConnectionOnClickListener);
        mResourceList.setOnItemLongClickListener(mConnectionOnLongClickListener);
    }

    protected void setJsonView() {
        mIsConnectionView = false;

        String titleText = mCurrNgwRes.getDisplayName() == null
                ? "" : "/" + mCurrNgwRes.getDisplayName();

        NgwResource parent = mCurrNgwRes.getParent();
        while (parent != null) {
            titleText = (parent.getDisplayName() == null
                    ? "" : "/" + parent.getDisplayName()) + titleText;
            parent = parent.getParent();
        }

        titleText = "/" + mCurrConn.getName() + titleText;
        mDialogTitleText.setText(titleText);

        mAddConnectionButton.setVisibility(View.GONE);

        NgwJsonArrayAdapter jsonArrayAdapter =
                new NgwJsonArrayAdapter(mMainActivity, mCurrNgwRes);

        jsonArrayAdapter.setOnItemCheckedChangeListener(
                new NgwJsonArrayAdapter.ItemCheckedChangeListener() {
                    @Override
                    public void onItemCheckedChange(NgwResource ngwResource, boolean isChecked) {
                        ngwResource.setSelected(isChecked);

                        if (isChecked) mSelectedResources.add(ngwResource);
                        else mSelectedResources.remove(ngwResource);

                        mOkButton.setEnabled(!mSelectedResources.isEmpty());
                    }
                });

        mResourceList.setAdapter(jsonArrayAdapter);

        mResourceList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                NgwResource ngwResource = mCurrNgwRes.get(position);

                if (position == 0) {

                    if (ngwResource.isRoot()) {
                        mCurrNgwRes = ngwResource;
                        setConnectionView();

                    } else {
                        mCurrNgwRes = mCurrNgwRes.getParent();
                        setJsonView();
                    }

                } else {
                    mCurrNgwRes = ngwResource;

                    if (mCurrNgwRes.size() == 0) {
                        setHttpRunningView(true);
                        mCurrConn.setLoadResourceArray(mCurrNgwRes);
                        mNgwConnWorker.loadNgwJson(mCurrConn);

                    } else {
                        setJsonView();
                    }
                }
            }
        });

        mResourceList.setOnItemLongClickListener(null);
    }

    protected class NgwResourceRoots {
        private Set<NgwResource> mResourceRoots;

        public NgwResourceRoots() {
            mResourceRoots = new TreeSet<NgwResource>();
        }

        public boolean add(NgwResource resource) {
            return mResourceRoots.add(resource);
        }

        public boolean remove(int connectionId) {
            for (NgwResource resourceRoot : mResourceRoots) {
                if (resourceRoot.getConnectionId() == connectionId) {
                    return mResourceRoots.remove(resourceRoot);
                }
            }
            return false;
        }

        public NgwResource get(int connectionId) {
            for (NgwResource resourceRoot : mResourceRoots) {
                if (resourceRoot.getConnectionId() == connectionId) {
                    return resourceRoot;
                }
            }
            return null;
        }
    }
}
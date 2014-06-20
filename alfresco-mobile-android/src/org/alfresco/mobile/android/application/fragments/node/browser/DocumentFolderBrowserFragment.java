/*******************************************************************************
 * Copyright (C) 2005-2014 Alfresco Software Limited.
 *
 * This file is part of Alfresco Mobile for Android.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package org.alfresco.mobile.android.application.fragments.node.browser;

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alfresco.mobile.android.api.model.Document;
import org.alfresco.mobile.android.api.model.Folder;
import org.alfresco.mobile.android.api.model.Node;
import org.alfresco.mobile.android.api.model.Permissions;
import org.alfresco.mobile.android.api.model.Site;
import org.alfresco.mobile.android.api.model.impl.RepositoryVersionHelper;
import org.alfresco.mobile.android.api.session.AlfrescoSession;
import org.alfresco.mobile.android.application.R;
import org.alfresco.mobile.android.application.activity.BaseActivity;
import org.alfresco.mobile.android.application.activity.MainActivity;
import org.alfresco.mobile.android.application.activity.PrivateDialogActivity;
import org.alfresco.mobile.android.application.activity.PublicDispatcherActivity;
import org.alfresco.mobile.android.application.fragments.DisplayUtils;
import org.alfresco.mobile.android.application.fragments.FragmentDisplayer;
import org.alfresco.mobile.android.application.fragments.GridAdapterHelper;
import org.alfresco.mobile.android.application.fragments.MenuFragmentHelper;
import org.alfresco.mobile.android.application.fragments.actions.AbstractActions;
import org.alfresco.mobile.android.application.fragments.actions.AbstractActions.onFinishModeListerner;
import org.alfresco.mobile.android.application.fragments.actions.NodeActions;
import org.alfresco.mobile.android.application.fragments.builder.ListingFragmentBuilder;
import org.alfresco.mobile.android.application.fragments.menu.MenuActionItem;
import org.alfresco.mobile.android.application.fragments.node.create.AddContentDialogFragment;
import org.alfresco.mobile.android.application.fragments.node.create.AddFolderDialogFragment;
import org.alfresco.mobile.android.application.fragments.node.create.CreateFolderDialogFragment;
import org.alfresco.mobile.android.application.fragments.node.details.NodeDetailsActionMode;
import org.alfresco.mobile.android.application.fragments.node.details.NodeDetailsFragment;
import org.alfresco.mobile.android.application.fragments.search.SearchFragment;
import org.alfresco.mobile.android.application.intent.RequestCode;
import org.alfresco.mobile.android.application.managers.ActionUtils;
import org.alfresco.mobile.android.async.OperationRequest;
import org.alfresco.mobile.android.async.OperationRequest.OperationBuilder;
import org.alfresco.mobile.android.async.Operator;
import org.alfresco.mobile.android.async.node.browse.NodeChildrenEvent;
import org.alfresco.mobile.android.async.node.create.CreateDocumentEvent;
import org.alfresco.mobile.android.async.node.create.CreateDocumentRequest;
import org.alfresco.mobile.android.async.node.create.CreateFolderEvent;
import org.alfresco.mobile.android.async.node.delete.DeleteNodeEvent;
import org.alfresco.mobile.android.async.node.download.DownloadEvent;
import org.alfresco.mobile.android.async.node.favorite.FavoriteNodeEvent;
import org.alfresco.mobile.android.async.node.update.UpdateNodeEvent;
import org.alfresco.mobile.android.async.utils.ContentFileProgressImpl;
import org.alfresco.mobile.android.async.utils.NodePlaceHolder;
import org.alfresco.mobile.android.platform.exception.AlfrescoAppException;
import org.alfresco.mobile.android.platform.intent.PrivateIntent;
import org.alfresco.mobile.android.platform.utils.AccessibilityUtils;
import org.alfresco.mobile.android.platform.utils.AndroidVersion;
import org.alfresco.mobile.android.platform.utils.BundleUtils;
import org.alfresco.mobile.android.platform.utils.ConnectivityUtils;
import org.alfresco.mobile.android.platform.utils.SessionUtils;
import org.alfresco.mobile.android.ui.fragments.BaseListAdapter;
import org.alfresco.mobile.android.ui.node.browse.NodeBrowserFragment;
import org.alfresco.mobile.android.ui.node.browse.NodeBrowserTemplate;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.impl.JSONConverter;

import android.app.ActionBar;
import android.app.ActionBar.OnNavigationListener;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.SpinnerAdapter;

import com.squareup.otto.Subscribe;

/**
 * Display a dialogFragment to retrieve information about the content of a
 * specific folder.
 * 
 * @author Jean Marie Pascal
 */
public class DocumentFolderBrowserFragment extends NodeBrowserFragment
{
    public static final String TAG = DocumentFolderBrowserFragment.class.getName();

    private boolean shortcutAlreadyVisible = false;

    private Folder importFolder;

    private File createFile;

    private long lastModifiedDate;

    private Button validationButton;

    private static final String ARGUMENT_IS_SHORTCUT = "isShortcut";

    private AbstractActions<Node> nActions;

    private File tmpFile;

    private DocumentFolderPickerCallback fragmentPick;

    private Map<String, Document> selectedMapItems = new HashMap<String, Document>(0);

    private int displayMode = GridAdapterHelper.DISPLAY_GRID;

    private MenuItem displayMenuItem;

    // //////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS
    // //////////////////////////////////////////////////////////////////////
    public DocumentFolderBrowserFragment()
    {
        displayAsList = false;
        /** By default, the fragment is in Listing mode. */
        mode = MODE_LISTING;
    }

    public static DocumentFolderBrowserFragment newInstanceByTemplate(Bundle b)
    {
        DocumentFolderBrowserFragment cbf = new DocumentFolderBrowserFragment();
        cbf.setArguments(b);
        b.putBoolean(ARGUMENT_BASED_ON_TEMPLATE, true);
        return cbf;
    }

    // //////////////////////////////////////////////////////////////////////
    // LIFE CYCLE
    // //////////////////////////////////////////////////////////////////////
    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        // In case of Import mode, we disable thumbnails.
        if (getActivity() instanceof PublicDispatcherActivity)
        {
            mode = MODE_IMPORT;
            setActivateThumbnail(false);
        }
        else if (getActivity() instanceof PrivateDialogActivity)
        {
            mode = MODE_PICK;
            fragmentPick = ((PrivateDialogActivity) getActivity()).getOnPickDocumentFragment();
        }

        super.onActivityCreated(savedInstanceState);

        if (getSession() == null)
        {

        }
        else if (RepositoryVersionHelper.isAlfrescoProduct(getSession()))
        {
            setActivateThumbnail(true);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View v = null;
        // In case of Import mode, we wrap the listing with buttons.
        if (getActivity() instanceof PublicDispatcherActivity || getActivity() instanceof PrivateDialogActivity)
        {
            v = inflater.inflate(R.layout.app_browser_import, container, false);
            init(v, emptyListMessageId);

            validationButton = (Button) v.findViewById(R.id.action_validation);
            GridView gridView = (GridView) v.findViewById(R.id.gridview);
            if (getActivity() instanceof PrivateDialogActivity)
            {
                validationButton.setText(R.string.done);
                gridView.setChoiceMode(GridView.CHOICE_MODE_MULTIPLE);
            }
            else
            {
                gridView.setChoiceMode(GridView.CHOICE_MODE_SINGLE);
            }
            gridView.setClickable(true);
        }
        else
        {
            v = super.onCreateView(inflater, container, savedInstanceState);

            GridView gridView = (GridView) v.findViewById(R.id.gridview);
            gridView.setChoiceMode(GridView.CHOICE_MODE_SINGLE);
            gridView.setClickable(true);

            gridView.setBackgroundColor(getResources().getColor(R.color.grey_lighter));
        }

        return v;
    }

    @Override
    public void onResume()
    {
        super.onResume();

        int titleId = R.string.app_name;
        if (getActivity() instanceof PublicDispatcherActivity)
        {
            mode = MODE_IMPORT;
            titleId = R.string.import_document_title;
            checkValidationButton();
        }
        else if (getActivity() instanceof PrivateDialogActivity)
        {
            mode = MODE_PICK;
            titleId = R.string.picker_document_title;
            checkValidationButton();
        }

        // If the fragment is resumed after user content creation action, we
        // have to check if the file has been modified or not. Depending on
        // result we prompt the upload dialog or we do nothing (no modification
        // / blank file)
        if (createFile != null)
        {
            if (createFile.length() > 0 && lastModifiedDate < createFile.lastModified())
            {
                tmpFile = createFile;
            }
            else
            {
                if (!createFile.delete())
                {
                    Log.w(TAG, createFile.getName() + "is not deleted.");
                }
                createFile = null;
            }
        }

        if (tmpFile != null)
        {
            importFolder = ((MainActivity) getActivity()).getImportParent();
            createFile(tmpFile);
        }

        if (getActivity().getActionBar() != null)
        {
            getActivity().getActionBar().setDisplayHomeAsUpEnabled(true);
            getActivity().getActionBar().setDisplayShowCustomEnabled(false);
            getActivity().setTitle(titleId);
            AccessibilityUtils.sendAccessibilityEvent(getActivity());
            if (shortcutAlreadyVisible)
            {
                displayPathShortcut();
            }
        }

        refreshListView();
    }

    @Override
    public void onStop()
    {
        if (nActions != null)
        {
            nActions.finish();
        }
        super.onStop();
    }

    // //////////////////////////////////////////////////////////////////////
    // PATH
    // //////////////////////////////////////////////////////////////////////
    private void displayPathShortcut()
    {
        // /QUICK PATH
        if (parentFolder != null && getActivity().getActionBar() != null)
        {
            //
            getActivity().getActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
            String pathValue = parentFolder.getName();
            if (parentFolder.getProperty(PropertyIds.PATH) != null)
            {
                pathValue = parentFolder.getProperty(PropertyIds.PATH).getValue();
            }

            boolean fromSite = false;
            if (getActivity() instanceof MainActivity)
            {
                fromSite = site != null;
            }

            List<String> listFolder = getPath(pathValue, fromSite);

            SpinnerAdapter adapter = new FolderPathAdapter(getActivity(),
                    android.R.layout.simple_spinner_dropdown_item, listFolder);

            OnNavigationListener mOnNavigationListener = new OnNavigationListener()
            {

                @Override
                public boolean onNavigationItemSelected(int itemPosition, long itemId)
                {
                    if (itemPosition == 0) { return true; }

                    if (isShortcut())
                    {
                        boolean fromSite = false;
                        if (getActivity() instanceof MainActivity)
                        {
                            fromSite = site != null;
                        }

                        // Determine the path
                        String pathValue = parentFolder.getProperty(PropertyIds.PATH).getValue();
                        List<String> listFolder = getPath(pathValue, fromSite);

                        List<String> subPath = listFolder.subList(itemPosition, listFolder.size());
                        Collections.reverse(subPath);
                        String path = subPath.remove(0);
                        for (String string : subPath)
                        {
                            path += string + "/";
                        }

                        DocumentFolderBrowserFragment.with(getActivity()).path(path).display();
                    }
                    else
                    {
                        for (int i = 0; i < itemPosition; i++)
                        {
                            getFragmentManager().popBackStack();
                        }
                    }

                    return true;
                }

            };

            getActivity().getActionBar().setListNavigationCallbacks(adapter, mOnNavigationListener);

            shortcutAlreadyVisible = true;
        }
    }

    private List<String> getPath(String pathValue, boolean fromSite)
    {
        String[] path = pathValue.split("/");
        if (path.length == 0)
        {
            path = new String[] { "/" };
        }

        String tmpPath = "";

        List<String> listFolder = new ArrayList<String>(path.length);
        for (int i = path.length - 1; i > -1; i--)
        {
            tmpPath = path[i];

            if (tmpPath.isEmpty())
            {
                tmpPath = "/";
            }
            listFolder.add(tmpPath);
        }

        if (fromSite && listFolder.size() > 3)
        {
            for (int i = 0; i < 3; i++)
            {
                listFolder.remove(listFolder.size() - 1);
            }
            listFolder.add(listFolder.size() - 1, site.getTitle());
            listFolder.remove(listFolder.size() - 1);
        }

        return listFolder;
    }

    // //////////////////////////////////////////////////////////////////////
    // LIST ACTIONS
    // //////////////////////////////////////////////////////////////////////
    @Override
    public void onListItemClick(GridView l, View v, int position, long id)
    {
        Node item = (Node) l.getItemAtPosition(position);

        if (item instanceof NodePlaceHolder)
        {
            l.setItemChecked(position, false);
            return;
        }

        // In case of import mode, we disable selection of document.
        // It's only possible to select a folder for navigation purpose.
        if (mode == MODE_IMPORT && getActivity() instanceof PublicDispatcherActivity)
        {
            l.setChoiceMode(GridView.CHOICE_MODE_NONE);
            if (item.isFolder())
            {
                DocumentFolderBrowserFragment.with(getActivity()).folder((Folder) item).display();
            }
            return;
        }

        // In case of pick mode, we allow multiSelection
        if (mode == MODE_PICK && getActivity() instanceof PrivateDialogActivity && item.isDocument())
        {
            l.setChoiceMode(GridView.CHOICE_MODE_MULTIPLE);
            if (selectedMapItems.containsKey(item.getIdentifier()))
            {
                selectedMapItems.remove(item.getIdentifier());
            }
            else
            {
                selectedMapItems.put(item.getIdentifier(), (Document) item);
            }
            l.setItemChecked(position, true);
            checkValidationButton();
            return;
        }

        // In other case, listing mode
        Boolean hideDetails = false;
        if (!selectedItems.isEmpty())
        {
            hideDetails = selectedItems.get(0).getIdentifier().equals(item.getIdentifier());
        }
        l.setItemChecked(position, true);

        if (nActions != null && nActions.hasMultiSelectionEnabled())
        {
            nActions.selectNode(item);
            if (selectedItems.size() == 0)
            {
                hideDetails = true;
            }
        }
        else
        {
            selectedItems.clear();
            if (!hideDetails && item.isDocument() && DisplayUtils.hasCentralPane(getActivity()))
            {
                selectedItems.add(item);
            }
        }

        if (hideDetails)
        {
            FragmentDisplayer.clearCentralPane(getActivity());
            if (nActions != null && !nActions.hasMultiSelectionEnabled())
            {
                nActions.finish();
            }
        }
        else if (nActions == null || (nActions != null && !nActions.hasMultiSelectionEnabled()))
        {
            if (item.isFolder())
            {
                FragmentDisplayer.clearCentralPane(getActivity());
                DocumentFolderBrowserFragment.with(getActivity()).site(site).folder((Folder) item)
                        .shortcut(isShortcut()).display();
            }
            else
            {
                NodeDetailsFragment.with(getActivity()).parentFolder(parentFolder).node(item).display();
            }
        }
    }

    public boolean onListItemLongClick(GridView l, View v, int position, long id)
    {
        // We disable long click during import mode.
        if (mode == MODE_IMPORT || mode == MODE_PICK) { return false; }

        if (nActions != null && nActions instanceof NodeDetailsActionMode)
        {
            nActions.finish();
        }

        Node n = (Node) l.getItemAtPosition(position);
        boolean b = true;
        if (n instanceof NodePlaceHolder)
        {
            getActivity().startActivity(
                    new Intent(PrivateIntent.ACTION_DISPLAY_OPERATIONS).putExtra(PrivateIntent.EXTRA_ACCOUNT_ID,
                            SessionUtils.getAccount(getActivity()).getId()));
            b = false;
        }
        else
        {
            l.setItemChecked(position, true);
            b = startSelection(n);
            if (DisplayUtils.hasCentralPane(getActivity()))
            {
                FragmentDisplayer.with(getActivity()).remove(DisplayUtils.getCentralFragmentId(getActivity()));
                FragmentDisplayer.with(getActivity()).remove(android.R.id.tabcontent);
            }
        }
        return b;
    };

    private boolean startSelection(Node item)
    {
        if (nActions != null) { return false; }

        selectedItems.clear();
        selectedItems.add(item);

        // Start the CAB using the ActionMode.Callback defined above
        nActions = new NodeActions(DocumentFolderBrowserFragment.this, selectedItems);
        nActions.setOnFinishModeListerner(new onFinishModeListerner()
        {
            @Override
            public void onFinish()
            {
                nActions = null;
                unselect();
                refreshListView();
            }
        });
        getActivity().startActionMode(nActions);
        return true;
    }

    // //////////////////////////////////////////////////////////////////////
    // REQUEST & RESULTS
    // //////////////////////////////////////////////////////////////////////
    @Override
    @Subscribe
    public void onResult(NodeChildrenEvent event)
    {
        if (getActivity() instanceof MainActivity && ((MainActivity) getActivity()).getCurrentNode() != null)
        {
            selectedItems.clear();
            selectedItems.add(((MainActivity) getActivity()).getCurrentNode());
        }

        if (event.parentFolder != null)
        {
            parentFolder = event.parentFolder;
            importFolder = parentFolder;
        }

        if (adapter == null)
        {
            adapter = onAdapterCreation();
            ((BaseListAdapter) adapter).setFragmentSettings(getArguments());
        }

        if (event.hasException)
        {
            if (adapter.getCount() == 0)
            {
                ev.setVisibility(View.VISIBLE);
            }
            onResultError(event.exception);
        }
        else
        {
            displayData(event);
        }
        ((NodeAdapter) adapter).setActivateThumbnail(hasActivateThumbnail());
        getActivity().invalidateOptionsMenu();
        displayPathShortcut();
        checkValidationButton();

        // Hide Loading progress
        refreshHelper.setRefreshComplete();
    }

    @Override
    protected ArrayAdapter<?> onAdapterCreation()
    {
        if (mode == MODE_PICK && adapter == null)
        {
            selectedMapItems = fragmentPick.retrieveDocumentSelection();
            return new ProgressNodeAdapter(getActivity(), GridAdapterHelper.getDisplayItemLayout(getActivity(), gv,
                    displayMode), parentFolder, new ArrayList<Node>(0), selectedMapItems);
        }
        else if (adapter == null) { return new ProgressNodeAdapter(getActivity(),
                GridAdapterHelper.getDisplayItemLayout(getActivity(), gv, displayMode), parentFolder,
                new ArrayList<Node>(0), selectedItems, mode); }
        return null;
    }

    // //////////////////////////////////////////////////////////////////////
    // ACTIONS
    // //////////////////////////////////////////////////////////////////////
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch (requestCode)
        {
            case RequestCode.FILEPICKER:
                if (data != null && PrivateIntent.ACTION_PICK_FILE.equals(data.getAction()))
                {
                    ActionUtils.actionPickFile(getFragmentManager().findFragmentByTag(TAG), RequestCode.FILEPICKER);
                }
                else if (data != null && data.getData() != null)
                {
                    String tmpPath = ActionUtils.getPath(getActivity(), data.getData());
                    if (tmpPath != null)
                    {
                        tmpFile = new File(tmpPath);
                    }
                    else
                    {
                        // Error case : Unable to find the file path associated
                        // to user pick.
                        // Sample : Picasa image case
                        ActionUtils.actionDisplayError(this, new AlfrescoAppException(
                                getString(R.string.error_unknown_filepath), true));
                    }
                }
                else if (data != null && data.getExtras() != null && data.getExtras().containsKey(Intent.EXTRA_STREAM))
                {
                    List<File> files = new ArrayList<File>();
                    List<Uri> uris = data.getExtras().getParcelableArrayList(Intent.EXTRA_STREAM);
                    for (Uri uri : uris)
                    {
                        files.add(new File(ActionUtils.getPath(getActivity(), uri)));
                    }
                    createFiles(files);
                }
                break;
            default:
                break;
        }
    }

    public void createFile(File f)
    {
        // Create and show the dialog.
        AddContentDialogFragment newFragment = AddContentDialogFragment.newInstance(importFolder, f,
                (createFile != null));
        newFragment.show(getActivity().getFragmentManager(), AddContentDialogFragment.TAG);
        tmpFile = null;
        createFile = null;
    }

    public void createFiles(List<File> files)
    {
        if (files.size() == 1)
        {
            createFile(files.get(0));
            return;
        }
        else
        {
            List<OperationBuilder> requestsBuilder = new ArrayList<OperationBuilder>(selectedItems.size());
            for (File file : files)
            {
                requestsBuilder.add(new CreateDocumentRequest.Builder(importFolder, file.getName(),
                        new ContentFileProgressImpl(file))
                        .setNotificationVisibility(OperationRequest.VISIBILITY_DIALOG));
            }
            String operationId = Operator.with(getActivity(), getAccount()).load(requestsBuilder);

            if (getActivity() instanceof PublicDispatcherActivity)
            {
                getActivity().finish();
            }
        }
        tmpFile = null;
        createFile = null;
    }

    public void createFolder()
    {
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment prev = getFragmentManager().findFragmentByTag(CreateFolderDialogFragment.TAG);
        if (prev != null)
        {
            ft.remove(prev);
        }
        ft.addToBackStack(null);

        // Create and show the dialog.
        AddFolderDialogFragment.newInstance(parentFolder).show(ft, CreateFolderDialogFragment.TAG);
    }

    public void refresh()
    {
        if (!ConnectivityUtils.hasNetwork((BaseActivity) getActivity())) { return; }

        if (parentFolder == null)
        {
            parentFolder = SessionUtils.getSession(getActivity()).getRootFolder();
        }
        super.refresh();

        // Display Refresh Progress
        refreshHelper.setRefreshing();
    }

    // //////////////////////////////////////////////////////////////////////
    // MENU
    // //////////////////////////////////////////////////////////////////////
    public void getMenu(Menu menu)
    {
        if (parentFolder == null) { return; }

        if (getActivity() instanceof MainActivity)
        {
            getMenu(getSession(), menu, parentFolder);

            if (hasDocument())
            {
                displayMenuItem = menu.add(Menu.NONE, MenuActionItem.MENU_DISPLAY_GALLERY, Menu.FIRST
                        + MenuActionItem.MENU_DISPLAY_GALLERY, R.string.display_gallery);
                displayMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            }
        }
        else if (getActivity() instanceof PublicDispatcherActivity)
        {
            Permissions permission = getSession().getServiceRegistry().getDocumentFolderService()
                    .getPermissions(parentFolder);

            if (permission.canAddChildren())
            {
                MenuItem mi = menu.add(Menu.NONE, MenuActionItem.MENU_CREATE_FOLDER, Menu.FIRST
                        + MenuActionItem.MENU_CREATE_FOLDER, R.string.folder_create);
                mi.setIcon(R.drawable.ic_add_folder);
                mi.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            }
        }

        MenuFragmentHelper.getMenu(getActivity(), menu);
    }

    public static void getMenu(AlfrescoSession session, Menu menu, Folder parentFolder)
    {
        MenuItem mi;

        if (parentFolder == null) { return; }
        Permissions permission = null;
        try
        {
            permission = session.getServiceRegistry().getDocumentFolderService().getPermissions(parentFolder);
        }
        catch (Exception e)
        {
            return;
        }

        mi = menu.add(Menu.NONE, MenuActionItem.MENU_SEARCH_FOLDER, Menu.FIRST + MenuActionItem.MENU_SEARCH_FOLDER,
                R.string.search);
        mi.setIcon(R.drawable.ic_search);
        mi.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        if (permission.canAddChildren())
        {
            mi = menu.add(Menu.NONE, MenuActionItem.MENU_CREATE_FOLDER, Menu.FIRST + MenuActionItem.MENU_CREATE_FOLDER,
                    R.string.folder_create);
            mi.setIcon(R.drawable.ic_add_folder);
            mi.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

            SubMenu createMenu = menu.addSubMenu(Menu.NONE, MenuActionItem.MENU_DEVICE_CAPTURE, Menu.FIRST
                    + MenuActionItem.MENU_DEVICE_CAPTURE, R.string.add_menu);
            createMenu.setIcon(android.R.drawable.ic_menu_add);
            createMenu.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

            createMenu.add(Menu.NONE, MenuActionItem.MENU_UPLOAD, Menu.FIRST + MenuActionItem.MENU_UPLOAD,
                    R.string.upload_title);

            createMenu.add(Menu.NONE, MenuActionItem.MENU_CREATE_DOCUMENT, Menu.FIRST
                    + MenuActionItem.MENU_CREATE_DOCUMENT, R.string.create_document);

            createMenu.add(Menu.NONE, MenuActionItem.MENU_DEVICE_CAPTURE_CAMERA_PHOTO, Menu.FIRST
                    + MenuActionItem.MENU_DEVICE_CAPTURE_CAMERA_PHOTO, R.string.take_photo);

            if (AndroidVersion.isICSOrAbove())
            {
                createMenu.add(Menu.NONE, MenuActionItem.MENU_DEVICE_CAPTURE_CAMERA_VIDEO, Menu.FIRST
                        + MenuActionItem.MENU_DEVICE_CAPTURE_CAMERA_VIDEO, R.string.make_video);
            }

            createMenu.add(Menu.NONE, MenuActionItem.MENU_DEVICE_CAPTURE_MIC_AUDIO, Menu.FIRST
                    + MenuActionItem.MENU_DEVICE_CAPTURE_MIC_AUDIO, R.string.record_audio);
            
            createMenu.add(Menu.NONE, MenuActionItem.MENU_DEVICE_SCAN_DOCUMENT, Menu.FIRST
                    + MenuActionItem.MENU_DEVICE_SCAN_DOCUMENT, R.string.scan_document);
        }
    }

    // //////////////////////////////////////////////////////////////////////
    // LIST MANAGEMENT UTILS
    // //////////////////////////////////////////////////////////////////////
    public void unselect()
    {
        selectedItems.clear();
    }

    /**
     * Remove a site object inside the listing without requesting an HTTP call.
     * 
     * @param site : site to remove
     */
    public void remove(Node node)
    {
        if (adapter != null)
        {
            ((ProgressNodeAdapter) adapter).remove(node.getName());
            if (adapter.isEmpty())
            {
                displayEmptyView();
            }
        }
    }

    public void selectAll()
    {
        if (nActions != null && adapter != null)
        {
            nActions.selectNodes(((ProgressNodeAdapter) adapter).getNodes());
            adapter.notifyDataSetChanged();
        }
    }

    public void select(Node updatedNode)
    {
        selectedItems.add(updatedNode);
    }

    public void highLight(Node updatedNode)
    {
        selectedItems.add(updatedNode);
        adapter.notifyDataSetChanged();
    }

    public List<Node> getNodes()
    {
        if (((ProgressNodeAdapter) adapter) != null)
        {
            return ((ProgressNodeAdapter) adapter).getNodes();
        }
        else
        {
            return null;
        }
    }

    private boolean hasDocument()
    {
        if (((ProgressNodeAdapter) adapter) != null)
        {
            for (Node node : ((ProgressNodeAdapter) adapter).getNodes())
            {
                if (node.isDocument()) { return true; }
            }
        }
        return false;
    }

    public Node getSelectedNodes()
    {
        return (selectedItems != null && !selectedItems.isEmpty()) ? selectedItems.get(0) : null;
    }

    // //////////////////////////////////////////////////////////////////////
    // UTILS
    // //////////////////////////////////////////////////////////////////////
    public void search()
    {
        SearchFragment.with(getActivity()).folder(folderParameter).site(site).display();
    }

    public void setCreateFile(File newFile)
    {
        this.createFile = newFile;
        this.lastModifiedDate = newFile.lastModified();
    }

    public Folder getImportFolder()
    {
        return importFolder;
    }

    @Override
    public int getMode()
    {
        return mode;
    }

    /**
     * Helper method to enable/disable the import button depending on mode and
     * permission.
     */
    private void checkValidationButton()
    {
        boolean enable = false;
        if (mode == MODE_IMPORT)
        {
            if (parentFolder != null)
            {
                Permissions permission = getSession().getServiceRegistry().getDocumentFolderService()
                        .getPermissions(parentFolder);
                enable = permission.canAddChildren();
            }
            validationButton.setEnabled(enable);
        }
        else if (mode == MODE_PICK && selectedItems != null)
        {
            validationButton.setText(String.format(
                    MessageFormat.format(getString(R.string.picker_attach_document), selectedMapItems.size()),
                    selectedMapItems.size()));
            validationButton.setEnabled(!selectedMapItems.isEmpty());
            validationButton.setOnClickListener(new OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    fragmentPick.onSelectDocument(new ArrayList<Document>(selectedMapItems.values()));
                }
            });
        }
    }

    public boolean isShortcut()
    {
        if (getArguments().containsKey(ARGUMENT_IS_SHORTCUT))
        {
            return (Boolean) getArguments().get(ARGUMENT_IS_SHORTCUT);
        }
        else
        {
            return true;
        }
    }

    // ///////////////////////////////////////////////////////////////////////////
    // EVENTS RECEIVER
    // ///////////////////////////////////////////////////////////////////////////
    @Subscribe
    public void onDocumentUpdated(UpdateNodeEvent event)
    {
        if (event.hasException) { return; }
        Node updatedNode = event.data;
        remove(event.initialNode);
        ((ProgressNodeAdapter) adapter).replaceNode(updatedNode);
        if (getActivity() instanceof BaseActivity)
        {
            ((BaseActivity) getActivity()).removeWaitingDialog();
        }
    }

    @Subscribe
    public void onNodeDeleted(DeleteNodeEvent event)
    {
        if (event.data == null) { return; }
        remove(event.data);
    }

    @Subscribe
    public void onDocumentDownloaded(DownloadEvent event)
    {
        if (event.hasException) { return; }
        if (parentFolder != null && parentFolder.getIdentifier() == event.parentFolder.getIdentifier())
        {
            ((ProgressNodeAdapter) adapter).replaceNode(event.document);
        }
    }

    @Subscribe
    public void onDocumentCreated(CreateDocumentEvent event)
    {
        if (event.hasException) { return; }
        if (parentFolder != null && parentFolder.getIdentifier() == event.parentFolder.getIdentifier())
        {
            ((ProgressNodeAdapter) adapter).replaceNode(event.data);
        }
    }

    @Subscribe
    public void onFolderCreated(CreateFolderEvent event)
    {
        Node node = event.data;
        ((ProgressNodeAdapter) adapter).replaceNode(node);
        if (getActivity() instanceof BaseActivity)
        {
            ((BaseActivity) getActivity()).removeWaitingDialog();
        }
    }

    @Subscribe
    public void onDocumentFavorited(FavoriteNodeEvent event)
    {
        if (event.hasException) { return; }
        ((ProgressNodeAdapter) adapter).refreshOperations();
    }

    // ///////////////////////////////////////////////////////////////////////////
    // BUILDER
    // ///////////////////////////////////////////////////////////////////////////
    public static Builder with(Activity appActivity)
    {
        return new Builder(appActivity);
    }

    public static class Builder extends ListingFragmentBuilder
    {
        // ///////////////////////////////////////////////////////////////////////////
        // CONSTRUCTORS
        // ///////////////////////////////////////////////////////////////////////////
        public Builder(Activity activity)
        {
            super(activity);
            this.extraConfiguration = new Bundle();
        }

        /** By Folder Object. */
        public Builder(Activity activity, Folder folder)
        {
            this(activity, folder, null, null, null);
        }

        /** By Folder PATH. */
        public Builder(Activity activity, String folderPath)
        {
            this(activity, null, folderPath, null, null);
        }

        /** By SITE Object. */
        public Builder(Activity activity, Site site)
        {
            this(activity, null, null, site, null);
        }

        /** By Folder Type ID. */
        public Builder(Activity activity, int folderId)
        {
            this(activity, null, null, null, folderId);
        }

        protected Builder(Activity activity, Folder parentFolder, String pathFolder, Site site, Integer folderId)
        {
            this(activity);
            BundleUtils.addIfNotEmpty(extraConfiguration, createBundleArgs(parentFolder, pathFolder, site));
            BundleUtils.addIfNotNull(extraConfiguration, ARGUMENT_FOLDER_TYPE_ID, folderId);
        }

        public Builder(Activity appActivity, Map<String, Object> configuration)
        {
            super(appActivity, configuration);
            this.extraConfiguration = new Bundle();

            this.menuIconId = R.drawable.ic_repository_light;
            this.menuTitleId = R.string.menu_browse_root;
            if (configuration != null && configuration.containsKey(NodeBrowserTemplate.ARGUMENT_FOLDER_TYPE_ID))
            {
                String folderTypeValue = JSONConverter.getString(configuration,
                        NodeBrowserTemplate.ARGUMENT_FOLDER_TYPE_ID);
                if (NodeBrowserTemplate.FOLDER_TYPE_SHARED.equalsIgnoreCase(folderTypeValue))
                {
                    this.menuIconId = R.drawable.ic_shared_light;
                    this.menuTitleId = R.string.menu_browse_shared;
                    folderIdentifier(FOLDER_TYPE_SHARED);
                }
                else if (NodeBrowserTemplate.FOLDER_TYPE_USERHOME.equalsIgnoreCase(folderTypeValue))
                {
                    this.menuIconId = R.drawable.ic_myfiles_light;
                    this.menuTitleId = R.string.menu_browse_userhome;
                    folderIdentifier(FOLDER_TYPE_USERHOME);
                }
            }

            this.templateArguments = new String[] { ARGUMENT_FOLDER_NODEREF, ARGUMENT_SITE_SHORTNAME, ARGUMENT_PATH,
                    ARGUMENT_FOLDER_TYPE_ID, ARGUMENT_FOLDER, ARGUMENT_SITE, ARGUMENT_IS_SHORTCUT };
        }

        // ///////////////////////////////////////////////////////////////////////////
        // SETTERS
        // ///////////////////////////////////////////////////////////////////////////
        public Builder folderIdentifier(String folderIdentifier)
        {
            extraConfiguration.putString(ARGUMENT_FOLDER_NODEREF, folderIdentifier);
            return this;
        }

        public Builder site(Site site)
        {
            extraConfiguration.putSerializable(ARGUMENT_SITE, site);
            return this;
        }

        public Builder siteShortName(String siteShortName)
        {
            extraConfiguration.putSerializable(ARGUMENT_SITE_SHORTNAME, siteShortName);
            return this;
        }

        public Builder folder(Folder folder)
        {
            extraConfiguration.putSerializable(ARGUMENT_FOLDER, folder);
            return this;
        }

        public Builder path(String pathFolder)
        {
            extraConfiguration.putSerializable(ARGUMENT_PATH, pathFolder);
            return this;
        }

        public Builder shortcut(boolean isShortCut)
        {
            extraConfiguration.putSerializable(ARGUMENT_IS_SHORTCUT, isShortCut);
            return this;
        }

        // ///////////////////////////////////////////////////////////////////////////
        // CLICK
        // ///////////////////////////////////////////////////////////////////////////
        protected Fragment createFragment(Bundle b)
        {
            return newInstanceByTemplate(b);
        };
    }
}

package com.xm.vbrowser.app.activity;

import android.Manifest;
import android.animation.Animator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.provider.Settings;
import androidx.annotation.NonNull;

import android.text.TextUtils;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.daimajia.androidanimations.library.Techniques;
import com.daimajia.androidanimations.library.YoYo;
import com.xm.vbrowser.app.MainApplication;
import com.xm.vbrowser.app.R;
import com.xm.vbrowser.app.VideoSniffer;
import com.xm.vbrowser.app.entity.DetectedVideoInfo;
import com.xm.vbrowser.app.entity.DownloadTask;
import com.xm.vbrowser.app.entity.VideoFormat;
import com.xm.vbrowser.app.entity.VideoInfo;
import com.xm.vbrowser.app.event.AddNewDownloadTaskEvent;
import com.xm.vbrowser.app.event.NewVideoItemDetectedEvent;
import com.xm.vbrowser.app.event.RefreshGoBackButtonStateEvent;
import com.xm.vbrowser.app.event.ShowToastMessageEvent;
import com.xm.vbrowser.app.event.WebViewProgressUpdateEvent;
import com.xm.vbrowser.app.util.*;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

import org.xwalk.core.*;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;
import q.rorbin.badgeview.Badge;
import q.rorbin.badgeview.QBadgeView;

public class MainActivity extends Activity implements EasyPermissions.PermissionCallbacks{
    /**
     * Regular Android menu item that contains a title and an icon if icon is specified.
     */
    private static final int STANDARD_MENU_ITEM = 0;

    /**
     * Menu item that has two buttons, the first one is a title and the second one is an icon.
     * It is different from the regular menu item because it contains two separate buttons.
     */
    private static final int TITLE_BUTTON_MENU_ITEM = 1;

    /**
     * Menu item that has three buttons. Every one of these buttons is displayed as an icon.
     */
    private static final int THREE_BUTTON_MENU_ITEM = 2;

    /**
     * Menu item that has four buttons. Every one of these buttons is displayed as an icon.
     */
    private static final int FOUR_BUTTON_MENU_ITEM = 3;

    /**
     * Menu item that has five buttons. Every one of these buttons is displayed as an icon.
     */
    private static final int FIVE_BUTTON_MENU_ITEM = 4;

    /**
     * The number of view types specified above.  If you add a view type you MUST increment this.
     */
    private static final int VIEW_TYPE_COUNT = 5;

    /** IDs of all of the buttons in icon_row_menu_item.xml. */
    private static final int[] BUTTON_IDS = {
            R.id.button_one,
            R.id.button_two,
            R.id.button_three,
            R.id.button_four,
            R.id.button_five
    };

    private ListPopupWindow mListPop;
    private Menu mMenu;
    private ImageView mBtnMenu;

    private static final String HOME_URL = "http://go.uc.cn/page/subpage/shipin?uc_param_str=dnfrpfbivecpbtntla";
    private static final String IPHONE_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 9_1 like Mac OS X) AppleWebKit/601.1.46 (KHTML, like Gecko) Version/9.0 Mobile/13B143 Safari/601.1";

    private XWalkView mainWebView;
    private View itemBadgeView;
    private Badge badge;
    private View bottomGoBackButton;
    private View bottomNewItemButton;
    private View bottomDownloadButton;
    private View bottomRefreshButton;
    private View bottomHomeButton;
    private View newItemPage;
    private View newItemPageMainView;
    private ListView newItemListView;
    private View newItemBottomCancelButton;
    private View pageTitleButton;
    private TextView pageTitleView;
    private View searchInputPage;
    private View searchInputPageCancelButton;
    private TextView searchInput;
    private View webViewProgressVIew;
    private View forwardButton;
    private View fabButton;


    private Thread refreshGoBackButtonStateThread;

    private LinkedBlockingQueue<DetectedVideoInfo> detectedTaskUrlQueue = new LinkedBlockingQueue<DetectedVideoInfo>();
    private SortedMap<String, VideoInfo> foundVideoInfoMap = Collections.synchronizedSortedMap(new TreeMap<String, VideoInfo>());
    private VideoSniffer videoSniffer;

    private String currentTitle = "";
    private String currentUrl = "";

    private boolean pageAnimationLock = false;

    /**
     * 为权限赋予一个唯一的标示码
     */
    public static final int WRITE_EXTERNAL_STORAGE = 1001;

    private boolean initReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
        mBtnMenu = findViewById(R.id.main_menu);
        mBtnMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mListPop.show();
            }
        });
        initPopView();

    }

    private void initPopView()
    {
        if (mMenu == null)
        {
            PopupMenu tempMenu = new PopupMenu(this, mBtnMenu);
            tempMenu.inflate(R.menu.main_menu);
            mMenu = tempMenu.getMenu();
        }

        int numItems = mMenu.size();
        List<MenuItem> menuItems = new ArrayList<MenuItem>();
        for (int i = 0; i < numItems; ++i) {
            MenuItem item = mMenu.getItem(i);
            if (item.isVisible()) {
                menuItems.add(item);
            }
        }

        LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
        mListPop = new ListPopupWindow(this, null, android.R.attr.popupMenuStyle);
        mListPop.setModal(true);//设置是否是模式
        mListPop.setAnchorView(mBtnMenu);//设置ListPopupWindow的锚点，即关联PopupWindow的显示位置和这个锚点
        mListPop.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        Rect bgPadding = new Rect();
        mListPop.getBackground().getPadding(bgPadding);

        int popupWidth = this.getResources().getDimensionPixelSize(R.dimen.menu_width)
                + bgPadding.left + bgPadding.right;

        mListPop.setWidth(popupWidth);
        BaseAdapter listAdapter = new TypedListAdapter(this, menuItems, inflater);
        mListPop.setAdapter(listAdapter);
    }

    private class TypedListAdapter extends BaseAdapter
    {
        private List<MenuItem> mMenuItems;
        private LayoutInflater mInflater;
        private Activity mActive;
        public TypedListAdapter(Activity active, List<MenuItem> datas, LayoutInflater inflater)
        {
            mActive = active;
            mInflater = inflater;
            mMenuItems = datas;
        }
        @Override
        public int getViewTypeCount()
        {
            return VIEW_TYPE_COUNT;
        }
        @Override
        public int getItemViewType(int position)
        {
            MenuItem item = getItem(position);
            int viewCount = item.hasSubMenu() ? item.getSubMenu().size() : 1;

            if (viewCount == 5) {
                return FIVE_BUTTON_MENU_ITEM;
            } else if (viewCount == 4) {
                return FOUR_BUTTON_MENU_ITEM;
            } else if (viewCount == 3) {
                return THREE_BUTTON_MENU_ITEM;
            } else if (viewCount == 2) {
                return TITLE_BUTTON_MENU_ITEM;
            }
            return STANDARD_MENU_ITEM;
        }
        @Override
        public int getCount()
        {
            return mMenuItems.size();
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).getItemId();
        }

        @Override
        public MenuItem getItem(int position) {
            if (position == ListView.INVALID_POSITION) return null;
            assert position >= 0;
            assert position < mMenuItems.size();
            return mMenuItems.get(position);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            int type = getItemViewType(position);
            final MenuItem item = getItem(position);
            switch (getItemViewType(position)) {
                case STANDARD_MENU_ITEM: {
                    StandardMenuItemViewHolder holder = null;
                    if (convertView == null
                            || !(convertView.getTag() instanceof StandardMenuItemViewHolder)) {
                        holder = new StandardMenuItemViewHolder();
                        convertView = mInflater.inflate(R.layout.menu_item, parent, false);
                        holder.text = (TextView) convertView.findViewById(R.id.menu_item_text);
                        holder.image = (ImageView) convertView.findViewById(R.id.menu_item_icon);
                        convertView.setTag(holder);
                    } else {
                        holder = (StandardMenuItemViewHolder) convertView.getTag();
                    }

                    setupStandardMenuItemViewHolder(holder, convertView, item);
                    break;
                }
                case FIVE_BUTTON_MENU_ITEM: {
                    convertView = createMenuItemRow(convertView, parent, item, 5);
                    break;
                }
                default:
                    assert false : "Unexpected MenuItem type";
            }
            return convertView;
        }

        private void setupStandardMenuItemViewHolder(StandardMenuItemViewHolder holder,
                                                     View convertView, final MenuItem item) {
            // Set up the icon.
            Drawable icon = item.getIcon();
            holder.image.setImageDrawable(icon);
            holder.image.setVisibility(icon == null ? View.GONE : View.VISIBLE);
            holder.text.setText(item.getTitle());
            holder.text.setContentDescription(item.getTitleCondensed());

            boolean isEnabled = item.isEnabled();
            // Set the text color (using a color state list).
            holder.text.setEnabled(isEnabled);
            // This will ensure that the item is not highlighted when selected.
            convertView.setEnabled(isEnabled);

            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //                   mAppMenu.onItemClick(item);
                    mActive.onOptionsItemSelected(item);
                }
            });
        }

        private View createMenuItemRow(
                View convertView, ViewGroup parent, MenuItem item, int numItems) {
            RowItemViewHolder holder = null;
            if (convertView == null
                    || !(convertView.getTag() instanceof RowItemViewHolder)
                    || ((RowItemViewHolder) convertView.getTag()).buttons.length != numItems) {
                holder = new RowItemViewHolder(numItems);
                convertView = mInflater.inflate(R.layout.icon_row_menu_item, parent, false);

                // Save references to all the buttons.
                for (int i = 0; i < numItems; i++) {
                    holder.buttons[i] =
                            (ImageView) convertView.findViewById(BUTTON_IDS[i]);
                }

                // Remove unused menu items.
                for (int j = numItems; j < 5; j++) {
                    ((ViewGroup) convertView).removeView(convertView.findViewById(BUTTON_IDS[j]));
                }

                convertView.setTag(holder);
            } else {
                holder = (RowItemViewHolder) convertView.getTag();
            }

            for (int i = 0; i < numItems; i++) {
                setupImageButton(holder.buttons[i], item.getSubMenu().getItem(i));
            }
            convertView.setFocusable(false);
            convertView.setEnabled(false);
            return convertView;
        }

        private void setupImageButton(ImageView button, final MenuItem item) {
            // Store and recover the level of image as button.setimageDrawable
            // resets drawable to default level.
            int currentLevel = item.getIcon().getLevel();
            button.setImageDrawable(item.getIcon());
            item.getIcon().setLevel(currentLevel);
            button.setEnabled(item.isEnabled());
            button.setFocusable(item.isEnabled());
            button.setContentDescription(item.getTitleCondensed());

            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
//                    mAppMenu.onItemClick(item);
                    mActive.onOptionsItemSelected(item);
                }
            });

            // Menu items may be hidden by command line flags before they get to this point.
            button.setVisibility(item.isVisible() ? View.VISIBLE : View.GONE);
        }

    }


    static class StandardMenuItemViewHolder
    {
        public TextView text;
        public ImageView image;
    }

    private static class RowItemViewHolder
    {
        public ImageView[] buttons;

        RowItemViewHolder(int numButtons) {
            buttons = new ImageView[numButtons];
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @AfterPermissionGranted(WRITE_EXTERNAL_STORAGE)
    private void requireAllPermissionForInit() {
        //可以只获取写或者读权限，同一个权限Group下只要有一个权限申请通过了就都可以用了
        String[] perms = {Manifest.permission.WRITE_EXTERNAL_STORAGE};
        if (EasyPermissions.hasPermissions(this, perms)) {
            // Already have permission, do the thing
            if(!initReady){
                mainInit();
                initReady = true;
            }
            if(videoSniffer != null) {
                videoSniffer.startSniffer();
            }
            if (mainWebView != null) {
                mainWebView.resumeTimers();
                mainWebView.onShow();
            }
            startRefreshGoBackButtonStateThread();
        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(this, "下载需要读写外部存储权限",
                    WRITE_EXTERNAL_STORAGE, perms);
        }
    }



    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {
        //如果不使用AfterPermissionGranted注解，就在这里调用
    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            ViewUtil.openConfirmDialog(this,
                    "必需权限",
                    "没有该权限，此应用程序可能无法正常工作。打开应用设置屏幕以修改应用权限",
                    "去设置", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            startActivity(
                                    new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                            .setData(Uri.fromParts("package", getPackageName(), null)));
                        }
                    },
                    "退出",  new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    }
            );
            return;
        }
        ViewUtil.openConfirmDialog(this,
                "必需权限",
                "没有该权限，此应用程序可能无法正常工作。",
                "再试一次", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        requireAllPermissionForInit();
                    }
                },
                "退出",  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                }
        );
    }

    private void initView() {

        mainWebView = (XWalkView)findViewById(R.id.mainWebView);
        itemBadgeView = findViewById(R.id.itemBadgeView);
        bottomGoBackButton = findViewById(R.id.bottomGoBackButton);
        bottomNewItemButton = findViewById(R.id.bottomNewItemButton);
        bottomDownloadButton = findViewById(R.id.bottomDownloadButton);
        bottomRefreshButton = findViewById(R.id.bottomRefreshButton);
        bottomHomeButton = findViewById(R.id.bottomHomeButton);
        newItemListView = (ListView)findViewById(R.id.newItemListView);
        newItemPage = findViewById(R.id.newItemPage);
        newItemPageMainView = findViewById(R.id.newItemPageMainView);
        newItemBottomCancelButton = findViewById(R.id.newItemBottomCancelButton);
        pageTitleButton =  findViewById(R.id.pageTitleButton);
        pageTitleView = (TextView) findViewById(R.id.pageTitleView);
        searchInputPage = findViewById(R.id.searchInputPage);
        searchInputPageCancelButton = findViewById(R.id.searchInputPageCancelButton);
        searchInput = (TextView) findViewById(R.id.searchInput);
        webViewProgressVIew = findViewById(R.id.webViewProgressVIew);
        forwardButton = findViewById(R.id.forward_menu_id);
        fabButton = findViewById(R.id.fab);

    }

    private void mainInit() {
        initWebView();

        badge = new QBadgeView(this).bindTarget(itemBadgeView).setBadgeGravity(Gravity.CENTER).setBadgeNumber(0);

        bottomGoBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!mainWebView.getNavigationHistory().canGoBack()){
                    refreshGoBackButtonStatus();
                    return;
                }else{
                    mainWebView.getNavigationHistory().navigate(XWalkNavigationHistory.Direction.BACKWARD, 1);
                    refreshGoBackButtonStatus();
                }
            }
        });

        bottomRefreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mainWebView.reload(XWalkView.RELOAD_IGNORE_CACHE);
            }
        });

        bottomHomeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadOrSearch(HOME_URL);
                mainWebView.getNavigationHistory().clear();
            }
        });

        bottomNewItemButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(foundVideoInfoMap.isEmpty()){
                    return;
                }
                NewItemAdapter newItemAdapter = (NewItemAdapter)newItemListView.getAdapter();
                newItemAdapter.notifyDataSetChanged();
                hideNewItemPage(false);
            }
        });
        bottomNewItemButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                foundVideoInfoMap.clear();
                refreshNewItemButtonStatus();
                return true;
            }
        });

        videoSniffer=new VideoSniffer(detectedTaskUrlQueue, foundVideoInfoMap, MainApplication.appConfig.videoSnifferThreadNum, MainApplication.appConfig.videoSnifferRetryCountOnFail);

//        newItemListView.setAdapter();
        newItemListView.setAdapter(new NewItemAdapter(this, foundVideoInfoMap));

        newItemBottomCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hideNewItemPage(true);
            }
        });

        pageTitleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hideSearchPage(false);
            }
        });

        searchInputPageCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hideSearchPage(true);
            }
        });

        searchInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_GO || (keyEvent != null && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    //do something;
                    Map<String, String> quickStart = new HashMap<String, String>();
                    quickStart.put("tbl", "https://tumblr.com/");
                    quickStart.put("avp", "http://www.avpapa.co/");
                    quickStart.put("ytb", "https://www.youtube.com/");
                    quickStart.put("5s", "http://dy.lol5s.com/");
                    quickStart.put("sm", "http://wap.smdyy.cc/");
                    if(quickStart.containsKey(textView.getText().toString())){
                        loadOrSearch(quickStart.get(textView.getText().toString()));
                    }else{
                        loadOrSearch(textView.getText().toString());
                    }
                    hideSearchPage(true);
                    return true;
                }
                return false;
            }
        });

        bottomDownloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, DownloadCenterActivity.class);
                startActivity(intent);
            }
        });

        fabButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(foundVideoInfoMap.isEmpty()){
                    return;
                }
                NewItemAdapter newItemAdapter = (NewItemAdapter)newItemListView.getAdapter();
                newItemAdapter.notifyDataSetChanged();
                hideNewItemPage(false);
            }

        });

        fabButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                foundVideoInfoMap.clear();
                refreshNewItemButtonStatus();
                return true;
            }
        });

    }

    private void initWebView() {
        //开启调式,支持谷歌浏览器调式
        XWalkPreferences.setValue(XWalkPreferences.REMOTE_DEBUGGING, true);

        XWalkSettings webSettings = mainWebView.getSettings();
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setUserAgentString(IPHONE_UA);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);

        mainWebView.requestFocus();

        mainWebView.setResourceClient(new MainXWalkResourceClient(mainWebView));
        mainWebView.setUIClient(new MainXWalkUIClient(mainWebView));
        XWalkCookieManager xm = new XWalkCookieManager();
        xm.setAcceptCookie(true);

        loadOrSearch(HOME_URL);
    }

    private void loadOrSearch(String content){
        if(TextUtils.isEmpty(content)){
            return;
        }
        pageTitleView.setText(content);
        searchInput.setText(content);
        if(content.startsWith("http")){
            mainWebView.loadUrl(content);
            return;
        }
        pageTitleView.setText(content);
        String encodedContent = "";
        try {
            encodedContent = URLEncoder.encode(content, "utf-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        mainWebView.loadUrl("https://m.baidu.com/s?word="+encodedContent);
    }

    private void startRefreshGoBackButtonStateThread(){
        stopRefreshGoBackButtonStateThread();
        refreshGoBackButtonStateThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d("MainActivity", "RefreshGoBackButtonStateThread thread (" + Thread.currentThread().getId() +") :start");
                while(!Thread.currentThread().isInterrupted()){
                    try {
                        EventBus.getDefault().post(new RefreshGoBackButtonStateEvent());
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        Log.d("MainActivity", "RefreshGoBackButtonStateThread thread (" + Thread.currentThread().getId() +") :Interrupted");
                        return;
                    }
                }
                Log.d("MainActivity", "RefreshGoBackButtonStateThread thread (" + Thread.currentThread().getId() +") :exit");
            }
        });
        refreshGoBackButtonStateThread.start();
    }

    private void stopRefreshGoBackButtonStateThread(){
        try {
            refreshGoBackButtonStateThread.interrupt();
        }catch (Exception e){
            Log.d("MainActivity", "RefreshGoBackButtonStateThread线程已中止, Pass");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        requireAllPermissionForInit();
    }

    @Override
    protected void onStop() {
        stopRefreshGoBackButtonStateThread();
        if (mainWebView != null) {
            mainWebView.pauseTimers();
            mainWebView.onHide();
        }
        if(videoSniffer!=null) {
            videoSniffer.stopSniffer();
        }
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onNewVideoItemDetectedEvent(NewVideoItemDetectedEvent newVideoItemDetectedEvent){
        refreshNewItemButtonStatus();

        NewItemAdapter newItemAdapter = (NewItemAdapter)newItemListView.getAdapter();
        newItemAdapter.notifyDataSetChanged();
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshGoBackButtonStateEvent(RefreshGoBackButtonStateEvent refreshGoBackButtonStateEvent){
        refreshGoBackButtonStatus();
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onWebViewProgressUpdateEvent(WebViewProgressUpdateEvent webViewProgressUpdateEvent){
        int percent = webViewProgressUpdateEvent.getProgress();
        if(percent==100){
            webViewProgressVIew.setVisibility(View.INVISIBLE);
        }else{
            webViewProgressVIew.setVisibility(View.VISIBLE);
        }
        float weight = (100-percent)>0?((float)percent/(100-percent)):999999;
        webViewProgressVIew.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight));

        if(HOME_URL.equals(mainWebView.getUrl())){
            pageTitleView.setText("搜索或输入网址");
            searchInput.setText("");
        }else{
            pageTitleView.setText(TextUtils.isEmpty(currentTitle) ? (TextUtils.isEmpty(currentUrl) ? "搜索或输入网址" : currentUrl) : currentTitle);
            searchInput.setText(currentUrl);
        }
    }
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onAddNewDownloadTaskEvent(AddNewDownloadTaskEvent addNewDownloadTaskEvent){
        VideoInfo videoInfo = addNewDownloadTaskEvent.getVideoInfo();
        DownloadTask downloadTask = new DownloadTask(
                UUIDUtil.genUUID(),videoInfo.getFileName(),
                ("m3u8".equals(videoInfo.getVideoFormat().getName())?"m3u8":"normal"),
                videoInfo.getVideoFormat().getName(),
                videoInfo.getUrl(),
                videoInfo.getSourcePageUrl(),
                videoInfo.getSourcePageTitle(),
                videoInfo.getSize());
        MainApplication.downloadManager.addTask(downloadTask);
    }


    private void refreshGoBackButtonStatus() {
        boolean canGoBack = mainWebView.getNavigationHistory().canGoBack();
        if(canGoBack){
            updateBottomButtonStatus(bottomGoBackButton, false);
        }else{
            updateBottomButtonStatus(bottomGoBackButton, true);
        }
    }

    private void refreshForwardButtonStatus() {
        boolean canForward= mainWebView.getNavigationHistory().canGoForward();
        if(canForward){
            updateBottomButtonStatus(forwardButton, false);
        }else{
            updateBottomButtonStatus(forwardButton, true);
        }
    }

    private void refreshNewItemButtonStatus() {
        int newItemCount = foundVideoInfoMap.size();
        badge.setBadgeNumber(newItemCount);
        if(newItemCount>0) {
            updateBottomButtonStatus(bottomNewItemButton, false);
        }else{
            updateBottomButtonStatus(bottomNewItemButton, true);
        }
    }

    private void updateBottomButtonStatus(View buttonView, boolean isDisabled){
        if (!(buttonView instanceof ViewGroup)) {
            return;
        }
        ViewGroup viewGroup = (ViewGroup) buttonView;
        if(viewGroup.getChildCount()<2){
            return;
        }
        if(isDisabled){
            viewGroup.getChildAt(0).setVisibility(View.INVISIBLE);
            viewGroup.getChildAt(1).setVisibility(View.VISIBLE);
        }else{
            viewGroup.getChildAt(0).setVisibility(View.VISIBLE);
            viewGroup.getChildAt(1).setVisibility(View.INVISIBLE);
        }
    }

    private void hideNewItemPage(boolean hide){
        if(pageAnimationLock){
            return;
        }
        pageAnimationLock = true;
        if(hide){
            if(newItemPage.getVisibility() != View.VISIBLE){
                pageAnimationLock = false;
                return;
            }
            YoYo.with(Techniques.SlideOutDown)
                    .duration(500).onEnd(new YoYo.AnimatorCallback() {
                @Override
                public void call(Animator animator) {
                    newItemPage.setVisibility(View.GONE);
                    pageAnimationLock = false;
                }
            }).playOn(newItemPageMainView);
        }else{
            if(newItemPage.getVisibility() == View.VISIBLE){
                pageAnimationLock = false;
                return;
            }
            YoYo.with(Techniques.SlideInUp).onStart(new YoYo.AnimatorCallback() {
                @Override
                public void call(Animator animator) {
                    newItemPage.setVisibility(View.VISIBLE);
                }
            }).onEnd(new YoYo.AnimatorCallback() {
                @Override
                public void call(Animator animator) {
                    pageAnimationLock = false;
                }
            }).duration(500).playOn(newItemPageMainView);
        }
    }


    private void hideSearchPage(boolean hide){
        if(hide){
            if(searchInputPage.getVisibility() != View.VISIBLE){
                return;
            }
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0); //强制隐藏键盘
            searchInputPage.setVisibility(View.GONE);
        }else{
            if(searchInputPage.getVisibility() == View.VISIBLE){
                return;
            }
            searchInputPage.setVisibility(View.VISIBLE);
            searchInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(searchInput,InputMethodManager.SHOW_FORCED);
        }
    }

    class MainXWalkResourceClient extends XWalkResourceClient{

        public MainXWalkResourceClient(XWalkView view) {
            super(view);
        }

        @Override
        public void onDocumentLoadedInFrame(XWalkView view, long frameId) {
            super.onDocumentLoadedInFrame(view, frameId);
        }

        @Override
        public void onLoadStarted(XWalkView view, String url) {
            super.onLoadStarted(view, url);
            Log.d("MainActivity", "onLoadStarted url:" + url);

            WeakReference<LinkedBlockingQueue> detectedTaskUrlQueueWeakReference = new WeakReference<LinkedBlockingQueue>(detectedTaskUrlQueue);
            Log.d("MainActivity", "shouldInterceptLoadRequest hint url:" + url);
            LinkedBlockingQueue  detectedTaskUrlQueue = detectedTaskUrlQueueWeakReference.get();
            if(detectedTaskUrlQueue != null){
                detectedTaskUrlQueue.add(new DetectedVideoInfo(url,currentUrl,currentTitle));
                Log.d("MainActivity", "shouldInterceptLoadRequest detectTaskUrlList.add(url):" + url);
            }
        }

        @Override
        public void onLoadFinished(XWalkView view, String url) {
            super.onLoadFinished(view, url);
        }

        @Override
        public void onProgressChanged(XWalkView view, int progressInPercent) {
            super.onProgressChanged(view, progressInPercent);
            Log.d("MainActivity", "onProgressChanged progressInPercent=" + progressInPercent);
            EventBus.getDefault().post(new WebViewProgressUpdateEvent(progressInPercent));
        }

        @Override
        public XWalkWebResourceResponse shouldInterceptLoadRequest(XWalkView view, XWalkWebResourceRequest request) {
            XWalkWebResourceResponse xWalkWebResourceResponse = super.shouldInterceptLoadRequest(view, request);
            String url = request.getUrl().toString();
            Log.d("MainActivity", "shouldInterceptLoadRequest url:" + url);
//
//            if(VideoFormatUtil.detectVideoUrl(url)){
//                Log.d("MainActivity", "shouldInterceptLoadRequest detectVideoUrl url:" + url);
//                try {
//                    xWalkWebResourceResponse = createXWalkWebResourceResponse("text/html","utf-8",new ByteArrayInputStream("".getBytes("UTF-8")),404,"blocked", new HashMap<String, String>());
//                } catch (UnsupportedEncodingException e) {
//                    e.printStackTrace();
//                    Log.d("MainActivity", "shouldInterceptLoadRequest UnsupportedEncodingException url:" + url);
//                }
//            }
            return xWalkWebResourceResponse;
        }

        @Override
        public boolean shouldOverrideUrlLoading(XWalkView view, String url) {
            if (!(url.startsWith("http") || url.startsWith("https"))) {
                //非http https协议 不动作
                return true;
            }

            //http https协议 在本webView中加载

            String extension = MimeTypeMap.getFileExtensionFromUrl(url);
            if(VideoFormatUtil.containsVideoExtension(extension)){
                detectedTaskUrlQueue.add(new DetectedVideoInfo(url,currentUrl,currentTitle));
                Log.d("MainActivity", "shouldOverrideUrlLoading detectTaskUrlList.add(url):" + url);
                return true;
            }

            Log.d("MainActivity", "shouldOverrideUrlLoading url="+url);
            view.loadUrl(url);
            return true;
        }

        @Override
        public void onReceivedSslError(XWalkView view, ValueCallback<Boolean> callback, SslError error) {
            callback.onReceiveValue(true);
        }
    }

    class MainXWalkUIClient extends XWalkUIClient{

        public MainXWalkUIClient(XWalkView view) {
            super(view);
        }

        @Override
        public void onReceivedTitle(XWalkView view, String title) {
            super.onReceivedTitle(view, title);
            Log.d("MainActivity", "onReceivedTitle title=" + title);
            currentTitle = title;
        }

        @Override
        public void onPageLoadStarted(XWalkView view, String url) {
            super.onPageLoadStarted(view, url);
            Log.d("MainActivity", "onPageLoadStarted url=" + url);
            currentUrl = url;
        }

        @Override
        public void onPageLoadStopped(XWalkView view, String url, LoadStatus status) {
            super.onPageLoadStopped(view, url, status);
            Log.d("MainActivity", "onPageLoadStopped url=" + url);
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            super.onShowCustomView(view, callback);
        }

        @Override
        public void onHideCustomView() {
            super.onHideCustomView();
        }


    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch(keyCode){
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS://耳机三个按键是的上键，注意并不是耳机上的三个按键的物理位置的上下。
                Log.d("MainActivity", "onKeyDown KEYCODE_MEDIA_PREVIOUS");
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE://耳机单按键的按键或三按键耳机的中间按键。
                Log.d("MainActivity", "onKeyDown KEYCODE_MEDIA_PLAY_PAUSE");
            case KeyEvent.KEYCODE_HEADSETHOOK://耳机单按键的按键或三按键耳机的中间按键。与上面的按键可能是相同的，具体得看驱动定义。
                Log.d("MainActivity", "onKeyDown KEYCODE_HEADSETHOOK");
            case KeyEvent.KEYCODE_MEDIA_NEXT://耳机三个按键是的下键。
                Log.d("MainActivity", "onKeyDown KEYCODE_MEDIA_NEXT");
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            if(searchInputPage.getVisibility() == View.VISIBLE){
                hideSearchPage(true);
                return true;
            }
            if(newItemPage.getVisibility() == View.VISIBLE){
                hideNewItemPage(true);
                return true;
            }
            if(mainWebView.getNavigationHistory().canGoBack()){
                mainWebView.getNavigationHistory().navigate(XWalkNavigationHistory.Direction.BACKWARD, 1);
                refreshGoBackButtonStatus();
                return true;
            }
            // 创建退出对话框
            AlertDialog exitAlertDialog = new AlertDialog.Builder(this).create();
            // 设置对话框标题
            exitAlertDialog.setTitle("系统提示");
            // 设置对话框消息
            exitAlertDialog.setMessage("确定要退出吗?");
            // 添加选择按钮并注册监听
            exitAlertDialog.setButton("确定", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    MainApplication.mainApplication.stopDownloadForegroundService();
                    if (mainWebView != null) {
                        mainWebView.onDestroy();
                    }
                    android.os.Process.killProcess(android.os.Process.myPid());
                }
            });
            exitAlertDialog.setButton2("取消", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                }
            });
            // 显示对话框
            exitAlertDialog.show();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    private static class ViewHolder{
        TextView itemNewItemTitle;
        TextView itemNewItemVideoType;
        TextView itemNewItemFileInfo;
        View itemNewItemDownloadImageView;
        View itemNewItemDoneImageView;
        View itemNewItemDownloadButton;
    }


    public class NewItemAdapter extends BaseAdapter {

        private LayoutInflater mInflater;
        private SortedMap<String, VideoInfo> foundVideoInfoMap;
        private String[] foundVideoInfoMapKeyArray;

        public NewItemAdapter(Context context, SortedMap<String, VideoInfo> foundVideoInfoMap){
            this.mInflater = LayoutInflater.from(context);
            this.foundVideoInfoMap = foundVideoInfoMap;
            prepareData();
        }

        @Override
        public void notifyDataSetChanged() {
            prepareData();
            super.notifyDataSetChanged();
        }

        @Override
        public void notifyDataSetInvalidated() {
            prepareData();
            super.notifyDataSetInvalidated();
        }

        private void prepareData(){
            Set<String> strings = this.foundVideoInfoMap.keySet();
            this.foundVideoInfoMapKeyArray = strings.toArray(new String[strings.size()]);
        }

        @Override
        public int getCount() {
            return foundVideoInfoMapKeyArray.length;
        }

        @Override
        public Object getItem(int arg0) {
            return foundVideoInfoMapKeyArray[arg0];
        }

        @Override
        public long getItemId(int arg0) {
            return foundVideoInfoMapKeyArray[arg0].hashCode();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            ViewHolder holder = null;
            if (convertView == null) {

                holder=new ViewHolder();

                convertView = mInflater.inflate(R.layout.item_new_item, null);
                holder.itemNewItemTitle = (TextView) convertView.findViewById(R.id.itemNewItemTitle);
                holder.itemNewItemFileInfo = (TextView) convertView.findViewById(R.id.itemNewItemFileInfo);
                holder.itemNewItemDownloadImageView = convertView.findViewById(R.id.itemNewItemDownloadImageView);
                holder.itemNewItemDoneImageView = convertView.findViewById(R.id.itemNewItemDoneImageView);
                holder.itemNewItemDownloadButton = convertView.findViewById(R.id.downloadingItemDeleteButton);
                holder.itemNewItemVideoType = (TextView)convertView.findViewById(R.id.itemNewItemVideoType);
                convertView.setTag(holder);
            }else {
                holder = (ViewHolder)convertView.getTag();
            }

            VideoInfo videoInfo = foundVideoInfoMap.get(foundVideoInfoMapKeyArray[position]);
            VideoFormat videoFormat = videoInfo.getVideoFormat();
            holder.itemNewItemTitle.setText(TextUtils.isEmpty(videoInfo.getSourcePageTitle())?videoInfo.getFileName()+"."+videoFormat.getName():videoInfo.getSourcePageTitle()+"."+videoFormat.getName());
            holder.itemNewItemVideoType.setText(videoFormat.getName().toUpperCase());
            if("m3u8".equals(videoFormat.getName())){
                holder.itemNewItemFileInfo.setText(TimeUtil.formatTime((int)videoInfo.getDuration()));
            }else{
                holder.itemNewItemFileInfo.setText(FileUtil.getFormatedFileSize(videoInfo.getSize()));
            }

            holder.itemNewItemDownloadButton.setTag(videoInfo);
            holder.itemNewItemDownloadButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    VideoInfo videoInfo = (VideoInfo) v.getTag();
                    EventBus.getDefault().post(new AddNewDownloadTaskEvent(videoInfo));
                    EventBus.getDefault().post(new ShowToastMessageEvent("下载任务添加成功"));
                }
            });

            return convertView;
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId())
        {
            case R.id.offline_page_id:
                Intent intent = new Intent(MainActivity.this, DownloadCenterActivity.class);
                startActivity(intent);
                break;

            case R.id.forward_menu_id:
                if(!mainWebView.getNavigationHistory().canGoForward()){
                    refreshForwardButtonStatus();
                }else{
                    mainWebView.getNavigationHistory().navigate(XWalkNavigationHistory.Direction.FORWARD, 1);
                    refreshForwardButtonStatus();
                }
                break;

            case R.id.reload_menu_id:
                mainWebView.reload(XWalkView.RELOAD_IGNORE_CACHE);
                break;

            default:
                break;

        }
        return true;
//        return super.onOptionsItemSelected(item);
    }
}

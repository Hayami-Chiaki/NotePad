/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.notepad;

import com.example.android.notepad.NotePad;

import android.app.ListActivity;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ImageView;
import android.graphics.Color;
import android.view.View;
import android.content.ContentValues;
import android.content.ContentUris;
import android.widget.SearchView;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import java.util.Locale;
import java.util.Date;
import java.util.TimeZone;
import java.text.SimpleDateFormat;


/**
 * 显示笔记列表。如果传入的 Intent 携带了 {@link Uri}，则显示该 URI 对应的笔记；
 * 否则默认显示 {@link NotePadProvider} 的内容。
 *
 * 注意：本 Activity 中的提供者操作是在 UI 线程上执行的，这并非最佳实践，仅为使代码更易读。
 * 真实应用应使用 {@link android.content.AsyncQueryHandler} 或 {@link android.os.AsyncTask}
 * 在单独线程中异步执行操作。
 */
public class NotesList extends ListActivity {

    // 用于日志记录和调试
    private static final String TAG = "NotesList";

    /**
     * The columns needed by the cursor adapter
     */
    // 列表查询投影：ID、标题、最后修改时间
    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID, // 0
            NotePad.Notes.COLUMN_NAME_TITLE, // 1
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, // 2
    };

    /** 标题列在 Cursor 中的索引 */
    private static final int COLUMN_INDEX_TITLE = 1;

    // 列表适配器引用，便于在搜索时更新游标
    private SimpleCursorAdapter mAdapter;
    private Long mCurrentCategoryId = null;

    /**
     * 当 Android 从零启动此 Activity 时会调用 onCreate。
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 用户无需长按按键即可使用菜单快捷键。
        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        // 设置包含底部导航的自定义布局（包含 @android:id/list）
        setContentView(R.layout.activity_notes_list);

        /* If no data is given in the Intent that started this Activity, then this Activity
         * was started when the intent filter matched a MAIN action. We should use the default
         * provider URI.
         */
        // 获取启动此 Activity 的 Intent。
        Intent intent = getIntent();

        // 如果 Intent 没有关联数据，则将其设置为默认 URI，即访问笔记列表。
        if (intent.getData() == null) {
            intent.setData(NotePad.Notes.CONTENT_URI);
        }

        /*
         * Sets the callback for context menu activation for the ListView. The listener is set
         * to be this Activity. The effect is that context menus are enabled for items in the
         * ListView, and the context menu is handled by a method in NotesList.
         */
        getListView().setOnCreateContextMenuListener(this);

        /* 执行受管查询。需要时由 Activity 负责关闭并重新查询 Cursor。
         *
         * 请参考开头关于在 UI 线程执行提供者操作的说明。
         */
        Cursor cursor = managedQuery(
            getIntent().getData(),            // 使用提供者的默认内容 URI。
            PROJECTION,                       // 返回每条笔记的 ID 和标题。
            null,                             // 不使用 where 子句，返回所有记录。
            null,                             // 不使用 where 子句，因此没有 where 参数。
            NotePad.Notes.DEFAULT_SORT_ORDER  // 使用默认排序。
        );

        /*
         * 下面两个数组在游标列与 ListView 项的视图 ID 之间建立映射：
         * dataColumns 的每个元素代表一列名；viewIDs 的每个元素代表一个视图 ID。
         * SimpleCursorAdapter 按顺序将它们对应，以确定每个列值显示的位置。
         */

        // 需要在视图中显示的游标列名（标题与最后修改时间）
        String[] dataColumns = { NotePad.Notes.COLUMN_NAME_TITLE, NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE } ;

        // 显示游标列的视图 ID（title_text 与 timestamp_text）
        int[] viewIDs = { R.id.title_text, R.id.timestamp_text };

        // 创建 ListView 的适配器。
        SimpleCursorAdapter adapter
            = new SimpleCursorAdapter(
                      this,                             // ListView 的上下文
                      R.layout.noteslist_item,          // 列表项的 XML
                      cursor,                           // 数据来源的游标
                      dataColumns,
                      viewIDs
              );

        // 设置时间戳格式为北京时间（UTC+8）
        adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                if (view.getId() == R.id.timestamp_text) {
                    int idx = cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE);
                    long millis = cursor.getLong(idx);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
                    sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
                    ((TextView) view).setText(sdf.format(new Date(millis)));
                    return true;
                }
                return false;
            }
        });

        // 为 ListView 设置刚创建的游标适配器，并保存引用。
        mAdapter = adapter;
        setListAdapter(mAdapter);

        // 渲染分类条
        renderCategoriesBar();

        // 底部导航美化：图标+文字，设置选中态
        ImageView tabNotesIcon = (ImageView) findViewById(R.id.tab_notes_icon);
        TextView tabNotesLabel = (TextView) findViewById(R.id.tab_notes_label);
        ImageView tabTodosIcon = (ImageView) findViewById(R.id.tab_todos_icon);
        TextView tabTodosLabel = (TextView) findViewById(R.id.tab_todos_label);

        final int selectedColor = Color.parseColor("#2196F3");
        final int unselectedColor = Color.parseColor("#9E9E9E");

        if (tabNotesIcon != null && tabNotesLabel != null) {
            tabNotesIcon.setColorFilter(selectedColor);
            tabNotesLabel.setTextColor(selectedColor);
        }
        if (tabTodosIcon != null && tabTodosLabel != null) {
            tabTodosIcon.setColorFilter(unselectedColor);
            tabTodosLabel.setTextColor(unselectedColor);
        }

        View tabNotes = findViewById(R.id.tab_notes);
        View tabTodos = findViewById(R.id.tab_todos);
        if (tabNotes != null) {
            tabNotes.setOnClickListener(v -> {
                // 当前页面为笔记列表，无需跳转
            });
        }
        if (tabTodos != null) {
            tabTodos.setOnClickListener(v -> {
                startActivity(new Intent(Intent.ACTION_VIEW, NotePad.Todos.CONTENT_URI));
            });
        }
    }

    /**
     * 当用户首次为此 Activity 点击设备的菜单键时调用。Android 传入一个已填充条目的 Menu 对象。
     *
     * 构建包含“插入”选项以及一组替代操作的菜单。其他希望处理笔记的应用可以通过提供
     * 包含 ALTERNATIVE 类别且 MIME 类型为 NotePad.Notes.CONTENT_TYPE 的 Intent 过滤器来注册。
     * 这样 onCreateOptionsMenu() 会把它们加入菜单选项，向用户提供可处理笔记的其他应用。
     * @param menu Menu 对象，用于添加菜单项。
     * @return 始终为 true，表示应显示菜单。
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 从 XML 资源填充菜单
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_options_menu, menu);

        // 配置搜索框：根据标题或内容进行查询
        final MenuItem searchItem = menu.findItem(R.id.menu_search);
        final SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint("搜索标题或内容");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                applyFilter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                applyFilter(newText);
                return true;
            }
        });

        // 关闭搜索时恢复全量列表
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                applyFilter("");
                return true;
            }
        });

        // 生成可在整个列表上执行的附加操作。正常安装下此处通常没有附加操作，
        // 但这允许其他应用用它们的操作扩展我们的菜单。
        Intent intent = new Intent(null, getIntent().getData());
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);

        return super.onCreateOptionsMenu(menu);
    }

    // 根据查询内容过滤笔记（标题或内容模糊匹配），空字符串恢复全量
    private void applyFilter(String query) {
        Uri uri = getIntent().getData();
        String selection = null;
        String[] selectionArgs = null;
        if (query != null) {
            query = query.trim();
        }
        if (query != null && query.length() > 0) {
            selection = NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ? OR " +
                    NotePad.Notes.COLUMN_NAME_NOTE + " LIKE ?";
            String like = "%" + query + "%";
            selectionArgs = new String[] { like, like };
        }
        if (mCurrentCategoryId != null) {
            String catWhere = NotePad.Notes.COLUMN_NAME_CATEGORY_ID + " = ?";
            if (selection == null) {
                selection = catWhere;
                selectionArgs = new String[] { String.valueOf(mCurrentCategoryId) };
            } else {
                // 合并查询与分类
                String[] newArgs = new String[selectionArgs.length + 1];
                System.arraycopy(selectionArgs, 0, newArgs, 0, selectionArgs.length);
                newArgs[newArgs.length - 1] = String.valueOf(mCurrentCategoryId);
                selectionArgs = newArgs;
                selection = "(" + selection + ") AND " + catWhere;
            }
        }

        Cursor c = getContentResolver().query(
                uri,
                PROJECTION,
                selection,
                selectionArgs,
                NotePad.Notes.DEFAULT_SORT_ORDER
        );
        // 更新适配器游标，自动关闭旧游标
        mAdapter.changeCursor(c);
    }

    private void renderCategoriesBar() {
        LinearLayout bar = (LinearLayout) findViewById(R.id.categories_bar);
        if (bar == null) return;
        bar.removeAllViews();

        addCategoryChip(bar, null, "全部", true);

        Cursor cats = getContentResolver().query(
                NotePad.Categories.CONTENT_URI,
                new String[]{ NotePad.Categories._ID, NotePad.Categories.COLUMN_NAME_NAME },
                null, null, NotePad.Categories.DEFAULT_SORT_ORDER);
        if (cats != null) {
            while (cats.moveToNext()) {
                long id = cats.getLong(0);
                String name = cats.getString(1);
                addCategoryChip(bar, id, name, false);
            }
            cats.close();
        }
        addAddCategoryChip(bar);
        highlightSelectedCategory(bar);
    }

    private void addCategoryChip(LinearLayout bar, Long id, String text, boolean isAll) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14f);
        tv.setPadding(24, 12, 24, 12);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(24f);
        bg.setColor(0xFFF5F5F5);
        tv.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(8, 8, 8, 8);
        tv.setLayoutParams(lp);
        tv.setTag(id);
        tv.setOnClickListener(v -> {
            mCurrentCategoryId = isAll ? null : id;
            renderCategoriesBar();
            applyFilter("");
        });
        if (!isAll && id != null) {
            tv.setOnLongClickListener(v -> {
                showCategoryActions(id, text);
                return true;
            });
        }
        bar.addView(tv);
    }

    private void addAddCategoryChip(LinearLayout bar) {
        TextView add = new TextView(this);
        add.setText("+");
        add.setTextSize(18f);
        add.setPadding(24, 12, 24, 12);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(24f);
        bg.setColor(0xFFE8F5E9);
        add.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(8, 8, 8, 8);
        add.setLayoutParams(lp);
        add.setOnClickListener(v -> showCreateCategoryDialog());
        bar.addView(add);
    }

    private void highlightSelectedCategory(LinearLayout bar) {
        final int count = bar.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = bar.getChildAt(i);
            if (!(child instanceof TextView)) continue;
            TextView tv = (TextView) child;
            String t = tv.getText().toString();
            boolean isAll = "全部".equals(t);
            boolean selected = (mCurrentCategoryId == null && isAll)
                    || (!isAll && tagEqualsId(tv.getTag(), mCurrentCategoryId));
            tv.setTextColor(selected ? Color.parseColor("#2196F3") : Color.parseColor("#333333"));
        }
    }

    private boolean tagEqualsId(Object tag, Long id) {
        if (tag == null || id == null) return false;
        if (tag instanceof Long) return ((Long) tag).equals(id);
        try { return Long.parseLong(String.valueOf(tag)) == id; } catch (Exception e) { return false; }
    }

    private void showCreateCategoryDialog() {
        final EditText input = new EditText(this);
        input.setHint("分类名称");
        new AlertDialog.Builder(this)
                .setTitle("新建分类")
                .setView(input)
                .setPositiveButton("确定", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.length() > 0) {
                        ContentValues v = new ContentValues();
                        v.put(NotePad.Categories.COLUMN_NAME_NAME, name);
                        getContentResolver().insert(NotePad.Categories.CONTENT_URI, v);
                        renderCategoriesBar();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCategoryActions(long id, String currentName) {
        final CharSequence[] items = new CharSequence[]{ "重命名", "删除" };
        new AlertDialog.Builder(this)
                .setTitle(currentName)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        final EditText input = new EditText(this);
                        input.setText(currentName);
                        new AlertDialog.Builder(this)
                                .setTitle("重命名分类")
                                .setView(input)
                                .setPositiveButton("确定", (d, w) -> {
                                    String name = input.getText().toString().trim();
                                    if (name.length() > 0) {
                                        ContentValues v = new ContentValues();
                                        v.put(NotePad.Categories.COLUMN_NAME_NAME, name);
                                        getContentResolver().update(ContentUris.withAppendedId(NotePad.Categories.CONTENT_URI, id), v, null, null);
                                        renderCategoriesBar();
                                    }
                                })
                                .setNegativeButton("取消", null)
                                .show();
                    } else {
                        // 删除分类前，将该分类下笔记的分类置空
                        ContentValues v = new ContentValues();
                        v.putNull(NotePad.Notes.COLUMN_NAME_CATEGORY_ID);
                        getContentResolver().update(NotePad.Notes.CONTENT_URI, v, NotePad.Notes.COLUMN_NAME_CATEGORY_ID + "=?", new String[]{ String.valueOf(id) });
                        getContentResolver().delete(ContentUris.withAppendedId(NotePad.Categories.CONTENT_URI, id), null, null);
                        if (mCurrentCategoryId != null && mCurrentCategoryId == id) {
                            mCurrentCategoryId = null;
                        }
                        renderCategoriesBar();
                        applyFilter("");
                    }
                })
                .show();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        // 当剪贴板有数据时启用“粘贴”菜单项。
        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);


        MenuItem mPasteItem = menu.findItem(R.id.menu_paste);

        // 如果剪贴板包含项目，则启用菜单中的“粘贴”选项。
        if (clipboard.hasPrimaryClip()) {
            mPasteItem.setEnabled(true);
        } else {
            // 如果剪贴板为空，则禁用菜单中的“粘贴”选项。
            mPasteItem.setEnabled(false);
        }

        // 获取当前显示的笔记数量。
        final boolean haveItems = getListAdapter().getCount() > 0;

        // 如果列表中有笔记（意味着选中了某一项），则需要生成可对当前选择执行的操作。
        // 这将是我们自有操作与任何可用扩展操作的组合。
        if (haveItems) {

            // 这是被选中的项目。
            Uri uri = ContentUris.withAppendedId(getIntent().getData(), getSelectedItemId());

            // 创建一个仅含一个元素的 Intent 数组，用于根据所选菜单项发送 Intent。
            Intent[] specifics = new Intent[1];

            // 将数组中的 Intent 设置为对所选笔记 URI 执行 EDIT 操作。
            specifics[0] = new Intent(Intent.ACTION_EDIT, uri);

            // 创建一个仅含一个元素的菜单项数组，用于放置 EDIT 选项。
            MenuItem[] items = new MenuItem[1];

            // 创建一个未指定具体操作的 Intent，数据为所选笔记的 URI。
            Intent intent = new Intent(null, uri);

            /* 为该 Intent 添加 ALTERNATIVE 类别，数据为笔记 ID 的 URI。
             * 这使其成为菜单中用于分组替代选项的位置。
             */
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);

            /*
             * 向菜单添加替代选项
             */
            menu.addIntentOptions(
                Menu.CATEGORY_ALTERNATIVE,  // 将这些 Intent 作为“替代”分组中的选项。
                Menu.NONE,                  // 不需要唯一的条目 ID。
                Menu.NONE,                  // 替代项无需排序。
                null,                       // 调用者名称不从分组中排除。
                specifics,                  // 这些特定选项应首先出现。
                intent,                     // 这些 Intent 映射到 specifics 中的选项。
                Menu.NONE,                  // 不需要标志位。
                items                       // 由 specifics 到 Intent 的映射生成的菜单项
            );
                // 如果存在 Edit 菜单项，则为其添加快捷键。
                if (items[0] != null) {
                    // 将 Edit 菜单项的快捷键设置为数字 "1"、字母 "e"
                    items[0].setShortcut('1', 'e');
                }
            } else {
                // 如果列表为空，从菜单中移除所有已有的替代操作
                menu.removeGroup(Menu.CATEGORY_ALTERNATIVE);
            }

        // 显示菜单
        return true;
    }

    /**
     * 当用户在未选择列表项的情况下从菜单中选择一个选项时调用。
     * 如果选择的是 INSERT，则发送一个 ACTION_INSERT 的新 Intent，并带上传入 Intent 的数据，
     * 实际效果是触发 NotePad 应用中的 NoteEditor。
     *
     * 如果不是 INSERT，则很可能是其他应用的替代选项，调用父方法进行默认处理。
     * @param item 用户选择的菜单项
     * @return 若选择了 INSERT 返回 true；否则返回父方法的处理结果。
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_add) {
            Intent add = new Intent(Intent.ACTION_INSERT, getIntent().getData());
            if (mCurrentCategoryId != null) {
                add.putExtra("extra_category_id", mCurrentCategoryId);
            }
            startActivity(add);
            return true;
        } else if (item.getItemId() == R.id.menu_paste) {
            /*
             * 使用 Intent 启动新的 Activity。该 Activity 的过滤器需包含 ACTION_PASTE。
             * 未设置类别，默认视为 DEFAULT。效果是启动 NotePad 中的 NoteEditor。
             */
            startActivity(new Intent(Intent.ACTION_PASTE, getIntent().getData()));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    

    /**
     * 当用户在列表中对某条笔记进行上下文点击（长按）时调用。NotesList 在 onCreate() 中
     * 将自己注册为其 ListView 上下文菜单的处理者。
     *
     * 可用的选项只有复制和删除。
     *
     * @param menu 要添加项目的 ContextMenu 对象
     * @param view 构建上下文菜单的视图
     * @param menuInfo 与视图相关的数据
     * @throws ClassCastException
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {

        // 菜单项附带的数据。
        AdapterView.AdapterContextMenuInfo info;

        // 尝试获取长按项在 ListView 中的位置。
        try {
            // 将传入的数据对象转换为 AdapterView 类型。
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            // 若无法转换该对象，记录错误日志。
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        /*
         * 获取选中位置项所关联的数据。getItem() 返回 ListView 适配器与该项关联的内容。
         * 在 NotesList 中，适配器将笔记的所有数据与列表项关联，因此 getItem() 返回一个 Cursor。
         */
        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);

        // 如果 Cursor 为空，说明适配器无法从提供者获取数据，直接返回。
        if (cursor == null) {
            // 由于某种原因该项不可用，不做处理
            return;
        }

        // 从 XML 资源填充菜单
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_context_menu, menu);

        // 将菜单标题设置为所选笔记的标题。
        menu.setHeaderTitle(cursor.getString(COLUMN_INDEX_TITLE));

        // 将可处理该数据的其他 Activity 的菜单项也附加上。
        // 这会在系统中查询实现 ALTERNATIVE_ACTION 的 Activity，并为每个结果添加菜单项。
        Intent intent = new Intent(null, Uri.withAppendedPath(getIntent().getData(), 
                                        Integer.toString((int) info.id) ));
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);
    }

    /**
     * 当用户从上下文菜单选择某项（见 onCreateContextMenu()）时调用。
     * 实际处理的菜单项只有删除和复制，其余为替代选项，执行默认处理。
     *
     * @param item 被选择的菜单项
     * @return 若为删除返回 true（无需默认处理），否则返回 false 以触发默认处理。
     * @throws ClassCastException
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // 菜单项附带的数据。
        AdapterView.AdapterContextMenuInfo info;

        /*
         * 获取菜单项的附加信息。当列表中的笔记被长按时会出现上下文菜单，其菜单项会自动
         * 获取该笔记的关联数据，数据来自为列表提供支持的提供者。
         *
         * 笔记数据以 ContextMenuInfo 对象传递给菜单创建过程。
         * 当点击某个上下文菜单项时，同样的数据连同笔记 ID 通过参数传递到 onContextItemSelected()。
         */
        try {
            // Casts the data object in the item into the type for AdapterView objects.
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {

            // 若无法转换该对象，记录错误日志
            Log.e(TAG, "bad menuInfo", e);

            // 触发菜单项的默认处理。
            return false;
        }
        // 将所选笔记的 ID 追加到传入 Intent 的 URI。
        Uri noteUri = ContentUris.withAppendedId(getIntent().getData(), info.id);

        /*
         * 获取菜单项的 ID，并与已知操作进行比较。
         */
        int id = item.getItemId();
        if (id == R.id.context_open) {
            // 启动 Activity 查看/编辑当前选中的项目
            startActivity(new Intent(Intent.ACTION_EDIT, noteUri));
            return true;
        } else if (id == R.id.context_copy) { //BEGIN_INCLUDE(copy)
            // 获取剪贴板服务句柄。
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);

            // 将笔记的 URI 复制到剪贴板。实际上这相当于复制了笔记本身
            clipboard.setPrimaryClip(ClipData.newUri(   // 新的剪贴板项，保存一个 URI
                    getContentResolver(),               // 用于获取 URI 信息的 resolver
                    "Note",                             // 剪贴项的标签
                    noteUri));                          // 该 URI

            // 返回调用方并跳过后续处理。
            return true;
            //END_INCLUDE(copy)
        } else if (id == R.id.context_delete) {
            // 通过传入笔记 ID 格式的 URI 从提供者中删除该笔记。
            // 请参考开头关于在 UI 线程执行提供者操作的说明。
            getContentResolver().delete(
                    noteUri,  // 提供者的 URI
                    null,     // 不需要 where 子句，因为只处理单个笔记 ID
                    // 传入的。
                    null      // 未使用 where 子句，因此不需要 where 参数。
            );

            // 返回调用方并跳过后续处理。
            return true;
        }
        return super.onContextItemSelected(item);
    }

    /**
     * 当用户点击列表中的某条笔记时调用。
     *
     * 处理传入的 PICK（从提供者获取数据）或 GET_CONTENT（获取或创建数据）操作。
     * 若传入操作为 EDIT，则发送新的 Intent 启动 NoteEditor。
     * @param l 包含被点击项的 ListView
     * @param v 单个条目的视图
     * @param position 该视图在列表中的位置
     * @param id 被点击项的行 ID
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {

        // 根据传入的 URI 与行 ID 构造新的 URI
        Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);

        // 从传入的 Intent 获取操作类型
        String action = getIntent().getAction();

        // 处理获取笔记数据的请求
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {

            // 将结果设置为返回给调用此 Activity 的组件，结果包含新的 URI
            setResult(RESULT_OK, new Intent().setData(uri));
        } else {

            // 发送 Intent 启动可处理 ACTION_EDIT 的 Activity，数据为笔记 ID 的 URI，
            // 实际效果是调用 NoteEdit。
            startActivity(new Intent(Intent.ACTION_EDIT, uri));
        }
    }
}

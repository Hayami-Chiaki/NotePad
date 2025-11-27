package com.example.android.notepad;

import android.app.ListActivity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ImageView;
import android.graphics.Color;

public class TodoList extends ListActivity {

    private static final String[] PROJECTION = new String[] {
            NotePad.Todos._ID,
            NotePad.Todos.COLUMN_NAME_TITLE,
            NotePad.Todos.COLUMN_NAME_CONTENT,
            NotePad.Todos.COLUMN_NAME_COMPLETED,
            NotePad.Todos.COLUMN_NAME_MODIFICATION_DATE,
    };

    private static final int COLUMN_INDEX_TITLE = 1;
    private static final int COLUMN_INDEX_CONTENT = 2;
    private static final int COLUMN_INDEX_COMPLETED = 3;

    private SimpleCursorAdapter mAdapter;
    private int mFilter = 0; // 0 all, 1 active, 2 completed

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        // 设置包含底部导航的自定义布局（包含 @android:id/list）
        setContentView(R.layout.activity_todo_list);

        Intent intent = getIntent();
        if (intent.getData() == null) {
            intent.setData(NotePad.Todos.CONTENT_URI);
        }

        getListView().setOnCreateContextMenuListener(this);
        // 取消多选模式，避免系统选择指示器覆盖并固定在列表左侧

        reload(null);
    }

    private void reload(String query) {
        Uri uri = getIntent().getData();
        String selection = null;
        String[] args = null;

        if (query != null) {
            query = query.trim();
        }
        if (!TextUtils.isEmpty(query)) {
            selection = NotePad.Todos.COLUMN_NAME_TITLE + " LIKE ? OR " +
                    NotePad.Todos.COLUMN_NAME_CONTENT + " LIKE ?";
            String like = "%" + query + "%";
            args = new String[] { like, like };
        }

        if (mFilter == 1) {
            selection = appendWhere(selection, NotePad.Todos.COLUMN_NAME_COMPLETED + "=0");
        } else if (mFilter == 2) {
            selection = appendWhere(selection, NotePad.Todos.COLUMN_NAME_COMPLETED + "=1");
        }

        Cursor c = managedQuery(uri, PROJECTION, selection, args, NotePad.Todos.DEFAULT_SORT_ORDER);

        String[] dataColumns = {
                NotePad.Todos.COLUMN_NAME_TITLE,
                NotePad.Todos.COLUMN_NAME_CONTENT,
                NotePad.Todos.COLUMN_NAME_COMPLETED
        };
        int[] viewIDs = { R.id.todo_title, R.id.todo_content, R.id.todo_check };

        SimpleCursorAdapter adapter = new SimpleCursorAdapter(
                this,
                R.layout.todoslist_item,
                c,
                dataColumns,
                viewIDs
        );

        adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                if (view.getId() == R.id.todo_check) {
                    boolean checked = cursor.getInt(COLUMN_INDEX_COMPLETED) == 1;
                    CheckBox cb = (CheckBox) view;
                    long rowId = cursor.getLong(cursor.getColumnIndex(NotePad.Todos._ID));
                    cb.setTag(rowId);
                    cb.setOnCheckedChangeListener(null);
                    cb.setChecked(checked);
                    cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        Object tag = buttonView.getTag();
                        if (tag instanceof Long) {
                            long id = (Long) tag;
                            ContentValues v = new ContentValues();
                            v.put(NotePad.Todos.COLUMN_NAME_COMPLETED, isChecked ? 1 : 0);
                            v.put(NotePad.Todos.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());
                            getContentResolver().update(ContentUris.withAppendedId(NotePad.Todos.CONTENT_URI, id), v, null, null);
                        }
                        View parent = (View) buttonView.getParent();
                        toggleStrike(parent, isChecked);
                    });
                    View parent = (View) cb.getParent();
                    toggleStrike(parent, checked);
                    return true;
                }
                return false;
            }
        });

        mAdapter = adapter;
        setListAdapter(mAdapter);

        // 底部导航美化：图标+文字，设置选中态
        ImageView tabNotesIcon = (ImageView) findViewById(R.id.tab_notes_icon);
        TextView tabNotesLabel = (TextView) findViewById(R.id.tab_notes_label);
        ImageView tabTodosIcon = (ImageView) findViewById(R.id.tab_todos_icon);
        TextView tabTodosLabel = (TextView) findViewById(R.id.tab_todos_label);

        final int selectedColor = Color.parseColor("#2196F3");
        final int unselectedColor = Color.parseColor("#9E9E9E");

        if (tabNotesIcon != null && tabNotesLabel != null) {
            tabNotesIcon.setColorFilter(unselectedColor);
            tabNotesLabel.setTextColor(unselectedColor);
        }
        if (tabTodosIcon != null && tabTodosLabel != null) {
            tabTodosIcon.setColorFilter(selectedColor);
            tabTodosLabel.setTextColor(selectedColor);
        }

        View tabNotes = findViewById(R.id.tab_notes);
        View tabTodos = findViewById(R.id.tab_todos);
        if (tabNotes != null) {
            tabNotes.setOnClickListener(v -> {
                startActivity(new Intent(Intent.ACTION_VIEW, NotePad.Notes.CONTENT_URI));
            });
        }
        if (tabTodos != null) {
            tabTodos.setOnClickListener(v -> {
                // 当前页面为待办列表，无需跳转
            });
        }
    }

    private static String appendWhere(String base, String add) {
        if (TextUtils.isEmpty(base)) return add;
        return base + " AND " + add;
    }

    private void toggleStrike(View itemView, boolean strike) {
        TextView title = (TextView) itemView.findViewById(R.id.todo_title);
        if (title != null) {
            int flags = title.getPaintFlags();
            if (strike) {
                title.setPaintFlags(flags | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                title.setPaintFlags(flags & (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.todo_list_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_add) {
            startActivity(new Intent(Intent.ACTION_INSERT, NotePad.Todos.CONTENT_URI));
            return true;
        } else if (id == R.id.menu_filter_all) {
            mFilter = 0; reload(null); return true;
        } else if (id == R.id.menu_filter_active) {
            mFilter = 1; reload(null); return true;
        } else if (id == R.id.menu_filter_completed) {
            mFilter = 2; reload(null); return true;
        } else if (id == R.id.menu_batch_delete_completed) {
            getContentResolver().delete(NotePad.Todos.CONTENT_URI, NotePad.Todos.COLUMN_NAME_COMPLETED + "=1", null);
            reload(null);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);
        startActivity(new Intent(Intent.ACTION_EDIT, uri));
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            return;
        }
        Uri uri = ContentUris.withAppendedId(getIntent().getData(), info.id);
        Intent[] specifics = new Intent[1];
        specifics[0] = new Intent(Intent.ACTION_EDIT, uri);
        MenuItem[] items = new MenuItem[1];
        Intent intent = new Intent(null, uri);
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0, null, specifics, intent, 0, items);
        if (items[0] != null) {
            items[0].setShortcut('1', 'e');
        }
        menu.add(0, Menu.FIRST, 0, R.string.menu_delete);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            return false;
        }
        Uri noteUri = ContentUris.withAppendedId(getIntent().getData(), info.id);
        switch (item.getItemId()) {
            case Menu.FIRST:
                getContentResolver().delete(noteUri, null, null);
                return true;
        }
        return super.onContextItemSelected(item);
    }
}

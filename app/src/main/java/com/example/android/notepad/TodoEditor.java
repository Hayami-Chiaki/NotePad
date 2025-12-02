package com.example.android.notepad;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

public class TodoEditor extends Activity {
    private Uri mUri;
    private EditText mTitle;
    private EditText mContent;
    private CheckBox mCompleted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        final String action = intent.getAction();
        if (Intent.ACTION_EDIT.equals(action) || Intent.ACTION_VIEW.equals(action)) {
            mUri = intent.getData();
        } else if (Intent.ACTION_INSERT.equals(action)) {
            // 延迟创建：不立即插入占位记录，待保存时再插入
            mUri = null;
        }

        setContentView(R.layout.todo_editor);
        mTitle = (EditText) findViewById(R.id.edit_title);
        mContent = (EditText) findViewById(R.id.edit_content);
        mCompleted = (CheckBox) findViewById(R.id.edit_completed);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mUri != null) {
            Cursor c = managedQuery(mUri, new String[] {
                    NotePad.Todos.COLUMN_NAME_TITLE,
                    NotePad.Todos.COLUMN_NAME_CONTENT,
                    NotePad.Todos.COLUMN_NAME_COMPLETED
            }, null, null, null);
            if (c != null && c.moveToFirst()) {
                mTitle.setText(c.getString(0));
                mContent.setText(c.getString(1));
                mCompleted.setChecked(c.getInt(2) == 1);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 返回时不自动保存
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.todo_editor_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_save) {
            String title = mTitle.getText().toString();
            String content = mContent.getText().toString();
            if (TextUtils.isEmpty(title)) {
                Toast.makeText(this, R.string.title_required, Toast.LENGTH_SHORT).show();
                return true;
            }
            ContentValues values = new ContentValues();
            values.put(NotePad.Todos.COLUMN_NAME_TITLE, title);
            values.put(NotePad.Todos.COLUMN_NAME_CONTENT, content);
            values.put(NotePad.Todos.COLUMN_NAME_COMPLETED, mCompleted.isChecked() ? 1 : 0);
            values.put(NotePad.Todos.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());
            if (mUri == null) {
                mUri = getContentResolver().insert(NotePad.Todos.CONTENT_URI, values);
            } else {
                getContentResolver().update(mUri, values, null, null);
            }
            finish();
            return true;
        } else if (item.getItemId() == R.id.menu_delete) {
            if (mUri != null) {
                getContentResolver().delete(mUri, null, null);
                finish();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // 不做校验与保存，直接返回
        super.onBackPressed();
    }
}

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

import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

/**
 * 此 Activity 允许用户编辑笔记的标题。它显示一个包含 EditText 的浮动窗口。
 *
 * 注意：本 Activity 中的提供者操作是在 UI 线程执行的，这并非最佳实践，仅为使代码更易读。
 * 真实应用应使用 {@link android.content.AsyncQueryHandler} 或 {@link android.os.AsyncTask}
 * 在单独线程中异步执行操作。
 */
public class TitleEditor extends Activity {

    /**
     * 特殊的 Intent 动作，表示“编辑笔记标题”。
     */
    public static final String EDIT_TITLE_ACTION = "com.android.notepad.action.EDIT_TITLE";

    // 创建一个投影，返回笔记 ID 与标题内容。
    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID, // 0
            NotePad.Notes.COLUMN_NAME_TITLE, // 1
    };

    // 标题列在提供者返回的 Cursor 中的位置。
    private static final int COLUMN_INDEX_TITLE = 1;

    // 用于保存查询提供者返回结果的 Cursor。
    private Cursor mCursor;

    // 用于保存编辑后标题的 EditText。
    private EditText mText;

    // 当前正在编辑标题的笔记 URI。
    private Uri mUri;

    /**
     * 当 Activity 首次启动时由 Android 调用。通过传入的 Intent 判断所需的编辑类型并执行。
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置此 Activity 的界面视图。
        setContentView(R.layout.title_editor);

        // 获取触发此 Activity 的 Intent，并从中取得需要编辑标题的笔记 URI。
        mUri = getIntent().getData();

        /*
         * 使用触发 Intent 传入的 URI 获取笔记。
         *
         * 注意：此操作在 UI 线程进行，会阻塞线程直到查询完成。在示例应用中基于本地数据库
         * 的简单提供者，阻塞只是短暂的；在真实应用中应使用 AsyncQueryHandler 或 AsyncTask。
         */

        mCursor = managedQuery(
            mUri,        // The URI for the note that is to be retrieved.
            PROJECTION,  // The columns to retrieve
            null,        // No selection criteria are used, so no where columns are needed.
            null,        // No where columns are used, so no where values are needed.
            null         // No sort order is needed.
        );

        // 获取 EditText 的视图 ID
        mText = (EditText) this.findViewById(R.id.title);
    }

    /**
     * 当 Activity 即将进入前台时调用：包括位于任务栈顶部或首次启动。
     *
     * 显示所选笔记的当前标题。
     */
    @Override
    protected void onResume() {
        super.onResume();

        // 验证 onCreate() 中的查询是否成功：成功则 Cursor 非空；空游标满足 mCursor.getCount() == 0。
        if (mCursor != null) {

            // 光标刚被取回，其索引指向首条记录之前，调用 moveToFirst() 移动到首条记录。
            mCursor.moveToFirst();

            // 在 EditText 中显示当前标题文本。
            mText.setText(mCursor.getString(COLUMN_INDEX_TITLE));
        }
    }

    /**
     * 当 Activity 失去焦点时调用。
     *
     * 对于编辑信息的 Activity，onPause() 往往是保存更改的唯一位置。Android 的应用模型强调
     * “保存”和“退出”不应是必须的操作；当用户离开 Activity 时，应自动保存并让 Activity 处于可被销毁的状态。
     *
     * 用当前文本框中的内容更新笔记。
     */
    @Override
    protected void onPause() {
        super.onPause();

        // 验证 onCreate() 中的查询是否成功：成功则 Cursor 非空；空游标满足 mCursor.getCount() == 0。

        if (mCursor != null) {

            // 创建用于更新提供者的值映射。
            ContentValues values = new ContentValues();

            // 在值映射中将标题设置为编辑框当前内容。
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, mText.getText().toString());

            /*
             * 使用新标题更新提供者。
             * 注意：此操作在 UI 线程进行，会阻塞线程直到更新完成。示例应用中阻塞时间短，实际应用中
             * 应使用 AsyncQueryHandler 或 AsyncTask。
             */
            getContentResolver().update(
                mUri,    // The URI for the note to update.
                values,  // The values map containing the columns to update and the values to use.
                null,    // No selection criteria is used, so no "where" columns are needed.
                null     // No "where" columns are used, so no "where" values are needed.
            );

        }
    }

    public void onClickOk(View v) {
        finish();
    }
}

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

import static com.example.android.notepad.R.*;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;

/**
 * 此 Activity 负责“编辑”笔记：包括响应 {@link Intent#ACTION_VIEW}（查看数据）、
 * {@link Intent#ACTION_EDIT}（编辑）、{@link Intent#ACTION_INSERT}（创建），以及
 * 从剪贴板当前内容创建新笔记 {@link Intent#ACTION_PASTE}。
 *
 * 注意：本 Activity 中的提供者操作在 UI 线程执行，并非最佳实践，仅为使代码更易读。
 * 真实应用应使用 {@link android.content.AsyncQueryHandler} 或 {@link android.os.AsyncTask}
 * 在单独线程中异步执行。
 */
public class NoteEditor extends Activity {
    // 用于日志记录与调试
    private static final String TAG = "NoteEditor";

    /*
     * 创建一个投影，返回笔记 ID、标题与内容。
     */
    private static final String[] PROJECTION =
        new String[] {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_NOTE
    };

    // Activity 保存状态的键名
    private static final String ORIGINAL_CONTENT = "origContent";

    // 此 Activity 可由多种动作启动，每种动作对应一个状态常量
    private static final int STATE_EDIT = 0;
    private static final int STATE_INSERT = 1;

    // Global mutable variables
    private int mState;
    private Uri mUri;
    private Cursor mCursor;
    private EditText mText;
    private String mOriginalContent;

    /**
     * 自定义 EditText：在每行文本之间绘制横线。
     */
    public static class LinedEditText extends EditText {
        private Rect mRect;
        private Paint mPaint;

        // 该构造函数由 LayoutInflater 使用
        public LinedEditText(Context context, AttributeSet attrs) {
            super(context, attrs);

            // 创建 Rect 与 Paint，并设置画笔样式与颜色。
            mRect = new Rect();
            mPaint = new Paint();
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(0x800000FF);
        }

        /**
         * 绘制带横线的 EditText
         * @param canvas 绘制背景的画布
         */
        @Override
        protected void onDraw(Canvas canvas) {

            // 获取视图中的文本行数。
            int count = getLineCount();

            // 获取全局的 Rect 与 Paint 对象
            Rect r = mRect;
            Paint paint = mPaint;

            /*
             * 为 EditText 中的每一行文本在矩形中绘制一条线
             */
            for (int i = 0; i < count; i++) {

                // 获取当前文本行的基线坐标
                int baseline = getLineBounds(i, r);

                /*
                 * 在背景中从矩形左侧到右侧绘制一条线，位置在基线下方 1dp，
                 * 使用 paint 指定细节。
                 */
                canvas.drawLine(r.left, baseline + 1, r.right, baseline + 1, paint);
            }

            // 调用父类方法完成绘制
            super.onDraw(canvas);
        }
    }

    /**
     * 当 Activity 首次启动时由 Android 调用。根据传入的 Intent 判断所需的编辑类型并执行。
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*
         * 创建一个 Intent，用于当 Activity 的结果返回给调用者时使用。
         */
        final Intent intent = getIntent();

        /*
         * 根据传入 Intent 的动作，设置编辑流程。
         */

        // 获取触发此 Activity 过滤器的动作
        final String action = intent.getAction();

        // 编辑动作：
        if (Intent.ACTION_EDIT.equals(action)) {

            // 设置 Activity 状态为 EDIT，并获取待编辑数据的 URI。
            mState = STATE_EDIT;
            mUri = intent.getData();

        // 插入或粘贴动作：
        } else if (Intent.ACTION_INSERT.equals(action)
                || Intent.ACTION_PASTE.equals(action)) {

            // 将状态设为 INSERT，取得通用笔记 URI，并在提供者中插入一条空记录。
            mState = STATE_INSERT;
            mUri = getContentResolver().insert(intent.getData(), null);

            /*
             * 若插入新笔记失败，则关闭此 Activity。若调用方请求结果，将收到 RESULT_CANCELED。
             * 同时记录失败日志。
             */
            if (mUri == null) {

                // 写入日志标识、消息及失败的 URI。
                Log.e(TAG, "Failed to insert new note into " + getIntent().getData());

                // 关闭 Activity。
                finish();
                return;
            }

            // 由于已创建新条目，设置需返回的结果。
            setResult(RESULT_OK, (new Intent()).setAction(mUri.toString()));

        // 若动作不是 EDIT 或 INSERT：
        } else {

            // 记录无法识别的动作错误，结束 Activity，并返回 RESULT_CANCELED 给调用方。
            Log.e(TAG, "Unknown action, exiting");
            finish();
            return;
        }

        /*
         * 使用触发 Intent 传入的 URI，从提供者获取笔记。
         * 注意：此查询在 UI 线程执行会阻塞至完成。示例应用中阻塞时间短，实际应用应使用
         * AsyncQueryHandler 或 AsyncTask。
         */
        mCursor = managedQuery(
            mUri,         // The URI that gets multiple notes from the provider.
            PROJECTION,   // A projection that returns the note ID and note content for each note.
            null,         // No "where" clause selection criteria.
            null,         // No "where" clause selection values.
            null          // Use the default sort order (modification date, descending)
        );

        // 对于粘贴，从剪贴板初始化数据。（必须在初始化 mCursor 后进行）
        if (Intent.ACTION_PASTE.equals(action)) {
            // 执行粘贴
            performPaste();
            // 切换为 EDIT 状态以便可以修改标题。
            mState = STATE_EDIT;
        }

        // 设置 Activity 的布局，参见 res/layout/note_editor.xml
        setContentView(R.layout.note_editor);

        // 获取布局中的 EditText 句柄。
        mText = (EditText) findViewById(R.id.note);

        /*
         * 若 Activity 之前停止过，其状态保存在 ORIGINAL_CONTENT 键中，这里恢复该状态。
         */
        if (savedInstanceState != null) {
            mOriginalContent = savedInstanceState.getString(ORIGINAL_CONTENT);
        }
    }

    /**
     * 当 Activity 即将进入前台时调用：位于任务栈顶部或首次启动。
     * 移动到第一条记录，根据当前状态设置窗口标题，将笔记内容放入 TextView，并保存原始文本备份。
     */
    @Override
    protected void onResume() {
        super.onResume();

        /*
         * mCursor is initialized, since onCreate() always precedes onResume for any running
         * process. This tests that it's not null, since it should always contain data.
         */
        if (mCursor != null) {
            // 重新查询，以防暂停期间（例如标题）发生变化。
            mCursor.requery();

            /*
             * 移动到第一条记录。首次访问 Cursor 数据前应调用 moveToFirst()。
             * Cursor 创建时内部索引指向第一条记录之前的位置。
             */
            mCursor.moveToFirst();

            // 根据当前状态调整 Activity 的窗口标题。
            if (mState == STATE_EDIT) {
                // 将笔记标题包含到 Activity 的标题中
                int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                String title = mCursor.getString(colTitleIndex);
                Resources res = getResources();
                String text = String.format(res.getString(R.string.title_edit), title);
                setTitle(text);
            // 插入状态下将标题设置为“创建”
            } else if (mState == STATE_INSERT) {
                setTitle(getText(R.string.title_create));
            }

            /*
             * onResume() 可能发生在 Activity 失去焦点（暂停）之后。用户暂停时可能在编辑或创建笔记。
             * Activity 应重新显示先前获取的文本，但不移动文本光标，便于继续编辑或输入。
             */

            // 从 Cursor 获取笔记文本并放入 TextView，而不改变文本光标位置。
            int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
            String note = mCursor.getString(colNoteIndex);
            mText.setTextKeepState(note);

            // 保存原始笔记文本，以便用户撤销更改。
            if (mOriginalContent == null) {
                mOriginalContent = note;
            }

        /*
         * 异常情况：Cursor 应总是包含数据。用错误标题与消息提示。
         */
        } else {
            setTitle(getText(R.string.error_title));
            mText.setText(getText(R.string.error_message));
        }
    }

    /**
     * 当 Activity 在正常运行中失去焦点并随后被系统杀死时调用，给予保存状态的机会以便系统恢复。
     * 注意：此方法不属于常规生命周期；若用户仅是导航离开，不会调用。
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // 保存原始文本，以防暂停期间 Activity 被杀死时仍可恢复。
        outState.putString(ORIGINAL_CONTENT, mOriginalContent);
    }

    /**
     * 当 Activity 失去焦点时调用。
     * 对于编辑型 Activity，onPause() 可能是保存更改的地方。Android 模型认为无需“保存/退出”动作；
     * 离开时应自动保存并让 Activity 处于可销毁状态。
     * 若用户没有输入内容，则删除或清空笔记；否则将编辑结果写入提供者。
     */
    @Override
    protected void onPause() {
        super.onPause();

        /*
         * 检查查询是否未失败（见 onCreate()）。即使无记录返回，除非发生异常，Cursor 也会存在。
         */
        if (mCursor != null) {

            // Get the current note text.
            String text = mText.getText().toString();
            int length = text.length();

            /*
             * 若 Activity 正在结束且当前笔记无文本，则返回 RESULT_CANCELED 并删除该笔记，
             * 即使是编辑状态也视为用户希望清空（删除）。
             */
            if (isFinishing() && (length == 0)) {
                setResult(RESULT_CANCELED);
                deleteNote();

                /*
                 * 将编辑写入提供者。笔记已被编辑的判定包括：编辑了现有笔记，或插入了新笔记。
                 * 后者中 onCreate() 已插入空笔记，当前正在编辑该新笔记。
                 */
            } else if (mState == STATE_EDIT) {
                // Creates a map to contain the new values for the columns
                updateNote(text, null);
            } else if (mState == STATE_INSERT) {
                updateNote(text, text);
                mState = STATE_EDIT;
          }
        }
    }

    /**
     * 当用户首次为此 Activity 点击设备菜单键时调用。Android 传入一个已填充条目的 Menu 对象。
     * 构建编辑与插入的菜单，并添加已注册处理本应用 MIME 类型的替代操作。
     * @param menu 用于添加条目的 Menu
     * @return 返回 true 以显示菜单。
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 从 XML 资源填充菜单
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.editor_options_menu, menu);

        // 仅为已保存的笔记添加额外菜单项
        if (mState == STATE_EDIT) {
            // 将可处理该数据的其他 Activity 的菜单项也附加上。
            // 在系统中查询实现 ALTERNATIVE_ACTION 的 Activity，并为每个结果添加菜单项。
            Intent intent = new Intent(null, mUri);
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
            menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                    new ComponentName(this, NoteEditor.class), null, intent, 0, null);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // 检查笔记是否有变更，并启用/禁用“还原”选项
        int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
        String savedNote = mCursor.getString(colNoteIndex);
        String currentNote = mText.getText().toString();
        if (savedNote.equals(currentNote)) {
            menu.findItem(R.id.menu_revert).setVisible(false);
        } else {
            menu.findItem(R.id.menu_revert).setVisible(true);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * 当用户选择某个菜单项时调用。根据选择调用相应方法执行动作。
     * @param item 被选择的菜单项
     * @return 返回 true 表示已处理；返回 false 继续默认处理。
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle all of the possible menu actions.
        int id = item.getItemId();
        if(id== R.id.menu_save) {
            String text = mText.getText().toString();
            updateNote(text, null);
            finish();
        } else if (id == R.id.menu_delete) {
            deleteNote();
            finish();
        } else if (id == R.id.menu_revert) {
            cancelNote();
        }
        return super.onOptionsItemSelected(item);
    }

//BEGIN_INCLUDE(paste)
    /**
     * 辅助方法：使用剪贴板内容替换笔记数据。
     */
    private final void performPaste() {

        // 获取剪贴板管理器句柄
        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);

        // 获取内容解析器实例
        ContentResolver cr = getContentResolver();

        // 从剪贴板获取数据
        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null) {

            String text=null;
            String title=null;

            // 获取剪贴板数据的第一个条目
            ClipData.Item item = clip.getItemAt(0);

            // 尝试将该条目作为指向笔记的 URI 获取其内容
            Uri uri = item.getUri();

            // 检查该条目是否为 URI，且其内容 URI 的 MIME 类型与 NotePad 提供者支持的类型一致。
            if (uri != null && NotePad.Notes.CONTENT_ITEM_TYPE.equals(cr.getType(uri))) {

                // 剪贴板持有一个笔记 MIME 类型的数据引用，这里进行复制。
                Cursor orig = cr.query(
                        uri,            // URI for the content provider
                        PROJECTION,     // Get the columns referred to in the projection
                        null,           // No selection variables
                        null,           // No selection variables, so no criteria are needed
                        null            // Use the default sort order
                );

                // 若 Cursor 非空且至少包含一条记录（moveToFirst() 返回 true），则取出笔记数据。
                if (orig != null) {
                    if (orig.moveToFirst()) {
                        int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
                        int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                        text = orig.getString(colNoteIndex);
                        title = orig.getString(colTitleIndex);
                    }

                    // Closes the cursor.
                    orig.close();
                }
            }

            // 若剪贴板内容不是笔记引用，则将其转换为文本。
            if (text == null) {
                text = item.coerceToText(this).toString();
            }

            // 使用取得的标题与文本更新当前笔记。
            updateNote(text, title);
        }
    }
//END_INCLUDE(paste)

    /**
     * 用参数提供的文本与标题替换当前笔记内容。
     * @param text 新的笔记文本
     * @param title 新的笔记标题
     */
    private final void updateNote(String text, String title) {

        // 构建用于更新提供者的值映射。
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());

        // 若为插入新笔记，则为其创建初始标题。
        if (mState == STATE_INSERT) {

            // 若未提供标题参数，则从笔记文本生成一个标题。
            if (title == null) {
  
                // 获取笔记长度
                int length = text.length();

                // 通过截取文本子串设置标题：最多 30 字符，或笔记长度两者取小。
                title = text.substring(0, Math.min(30, length));
  
                // 若结果长度超过 30，则去除末尾空格
                if (length > 30) {
                    int lastSpace = title.lastIndexOf(' ');
                    if (lastSpace > 0) {
                        title = title.substring(0, lastSpace);
                    }
                }
            }
            // 在值映射中设置标题
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        } else if (title != null) {
            // 在值映射中设置标题
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        }

        // 将目标笔记文本放入映射。
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, text);

        /*
         * 使用映射中的新值更新提供者。ListView 会自动更新：提供者为查询 Cursor 设置了通知 URI，
         * 当该 URI 的 Cursor 变化时内容解析器会自动收到通知，从而更新 UI。
         * 注意：此操作在 UI 线程上进行，会阻塞直至完成。实际应用应使用 AsyncQueryHandler 或 AsyncTask。
         */
        getContentResolver().update(
                mUri,    // The URI for the record to update.
                values,  // The map of column names and new values to apply to them.
                null,    // No selection criteria are used, so no where columns are necessary.
                null     // No where columns are used, so no where arguments are necessary.
            );


    }

    /**
     * 辅助方法：取消对笔记的更改。若是新建则删除，否则恢复为原始文本。
     */
    private final void cancelNote() {
        if (mCursor != null) {
            if (mState == STATE_EDIT) {
                // 将原始文本写回数据库
                mCursor.close();
                mCursor = null;
                ContentValues values = new ContentValues();
                values.put(NotePad.Notes.COLUMN_NAME_NOTE, mOriginalContent);
                getContentResolver().update(mUri, values, null, null);
            } else if (mState == STATE_INSERT) {
                // 之前插入了空笔记，确保删除
                deleteNote();
            }
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    /**
     * 删除笔记：直接删除该条目。
     */
    private final void deleteNote() {
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
            getContentResolver().delete(mUri, null, null);
            mText.setText("");
        }
    }
}

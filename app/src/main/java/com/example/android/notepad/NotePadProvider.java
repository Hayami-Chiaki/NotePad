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

import android.content.ClipDescription;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.ContentProvider.PipeDataWriter;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.LiveFolders;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;

/**
 * 提供对笔记数据库的访问。每条笔记包含标题、正文、创建时间与修改时间。
 */
public class NotePadProvider extends ContentProvider implements PipeDataWriter<Cursor> {
    // 用于调试与日志记录
    private static final String TAG = "NotePadProvider";

    /**
     * 提供者使用的底层数据存储数据库名称
     */
    private static final String DATABASE_NAME = "note_pad.db";

    /**
     * 数据库版本
     */
    private static final int DATABASE_VERSION = 4;

    /**
     * 从数据库选择列所用的投影映射
     */
    private static HashMap<String, String> sNotesProjectionMap;

    /**
     * 处理实时文件夹所用的投影映射
     */
    private static HashMap<String, String> sLiveFolderProjectionMap;
    private static HashMap<String, String> sTodosProjectionMap;
    private static HashMap<String, String> sCategoriesProjectionMap;

    /**
     * 读取单条笔记的标准投影。
     */
    private static final String[] READ_NOTE_PROJECTION = new String[] {
            NotePad.Notes._ID,               // Projection position 0, the note's id
            NotePad.Notes.COLUMN_NAME_NOTE,  // Projection position 1, the note's content
            NotePad.Notes.COLUMN_NAME_TITLE, // Projection position 2, the note's title
    };
    private static final int READ_NOTE_NOTE_INDEX = 1;
    private static final int READ_NOTE_TITLE_INDEX = 2;

    /*
     * UriMatcher 根据传入 URI 的模式选择操作所用的常量
     */
    // 传入 URI 匹配 Notes 模式
    private static final int NOTES = 1;

    // 传入 URI 匹配 Note ID 模式
    private static final int NOTE_ID = 2;

    // 传入 URI 匹配 Live Folder 模式
    private static final int LIVE_FOLDER_NOTES = 3;
    private static final int TODOS = 4;
    private static final int TODO_ID = 5;
    private static final int CATEGORIES = 6;
    private static final int CATEGORY_ID = 7;

    /**
     * UriMatcher 实例
     */
    private static final UriMatcher sUriMatcher;

    // DatabaseHelper 句柄
    private DatabaseHelper mOpenHelper;


    /**
     * 初始化静态对象的代码块
     */
    static {

        /*
         * 创建并初始化 URI 匹配器
         */
        // 创建新实例
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

        // 添加以 "notes" 结尾的模式，路由到 NOTES 操作
        sUriMatcher.addURI(NotePad.AUTHORITY, "notes", NOTES);

        // 添加以 "notes" 加整数结尾的模式，路由到 NOTE_ID 操作
        sUriMatcher.addURI(NotePad.AUTHORITY, "notes/#", NOTE_ID);

        // 添加以 live_folders/notes 结尾的模式，路由到 LIVE_FOLDER_NOTES 操作
        sUriMatcher.addURI(NotePad.AUTHORITY, "live_folders/notes", LIVE_FOLDER_NOTES);
        sUriMatcher.addURI(NotePad.AUTHORITY, "todos", TODOS);
        sUriMatcher.addURI(NotePad.AUTHORITY, "todos/#", TODO_ID);
        sUriMatcher.addURI(NotePad.AUTHORITY, "categories", CATEGORIES);
        sUriMatcher.addURI(NotePad.AUTHORITY, "categories/#", CATEGORY_ID);

        /*
         * 创建并初始化返回所有列的投影映射
         */

        // 创建新的投影映射：以列名字符串为键，值通常与键相同。
        sNotesProjectionMap = new HashMap<String, String>();

        // 将字符串 "_ID" 映射到列名 "_ID"
        sNotesProjectionMap.put(NotePad.Notes._ID, NotePad.Notes._ID);

        // 将 "title" 映射到 "title"
        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_TITLE, NotePad.Notes.COLUMN_NAME_TITLE);

        // 将 "note" 映射到 "note"
        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_NOTE, NotePad.Notes.COLUMN_NAME_NOTE);

        // 将 "created" 映射到 "created"
        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE,
                NotePad.Notes.COLUMN_NAME_CREATE_DATE);

        // 将 "modified" 映射到 "modified"
        sNotesProjectionMap.put(
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE);
        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_CATEGORY_ID, NotePad.Notes.COLUMN_NAME_CATEGORY_ID);

        /*
         * 创建并初始化用于 Live Folders 的投影映射
         */

        // 创建新的投影映射实例
        sLiveFolderProjectionMap = new HashMap<String, String>();

        // 在实时文件夹中将 "_ID" 映射为 "_ID AS _ID"
        sLiveFolderProjectionMap.put(LiveFolders._ID, NotePad.Notes._ID + " AS " + LiveFolders._ID);

        // 将 "NAME" 映射为 "title AS NAME"
        sLiveFolderProjectionMap.put(LiveFolders.NAME, NotePad.Notes.COLUMN_NAME_TITLE + " AS " +
            LiveFolders.NAME);
        sTodosProjectionMap = new HashMap<String, String>();
        sTodosProjectionMap.put(NotePad.Todos._ID, NotePad.Todos._ID);
        sTodosProjectionMap.put(NotePad.Todos.COLUMN_NAME_TITLE, NotePad.Todos.COLUMN_NAME_TITLE);
        sTodosProjectionMap.put(NotePad.Todos.COLUMN_NAME_CONTENT, NotePad.Todos.COLUMN_NAME_CONTENT);
        sTodosProjectionMap.put(NotePad.Todos.COLUMN_NAME_COMPLETED, NotePad.Todos.COLUMN_NAME_COMPLETED);
        sTodosProjectionMap.put(NotePad.Todos.COLUMN_NAME_CREATE_DATE, NotePad.Todos.COLUMN_NAME_CREATE_DATE);
        sTodosProjectionMap.put(NotePad.Todos.COLUMN_NAME_MODIFICATION_DATE, NotePad.Todos.COLUMN_NAME_MODIFICATION_DATE);
        sCategoriesProjectionMap = new HashMap<String, String>();
        sCategoriesProjectionMap.put(NotePad.Categories._ID, NotePad.Categories._ID);
        sCategoriesProjectionMap.put(NotePad.Categories.COLUMN_NAME_NAME, NotePad.Categories.COLUMN_NAME_NAME);
        sCategoriesProjectionMap.put(NotePad.Categories.COLUMN_NAME_CREATE_DATE, NotePad.Categories.COLUMN_NAME_CREATE_DATE);
        sCategoriesProjectionMap.put(NotePad.Categories.COLUMN_NAME_MODIFICATION_DATE, NotePad.Categories.COLUMN_NAME_MODIFICATION_DATE);
    }

    /**
    *
    * 此类用于打开、创建与升级数据库文件。为测试目的设置为包可见。
    */
   static class DatabaseHelper extends SQLiteOpenHelper {

       DatabaseHelper(Context context) {

       // 调用父构造函数，请求默认游标工厂。
           super(context, DATABASE_NAME, null, DATABASE_VERSION);
       }

       /**
        * 创建底层数据库，表名与列名取自 NotePad 类。
        */
       @Override
       public void onCreate(SQLiteDatabase db) {
           db.execSQL("CREATE TABLE " + NotePad.Notes.TABLE_NAME + " ("
                   + NotePad.Notes._ID + " INTEGER PRIMARY KEY,"
                   + NotePad.Notes.COLUMN_NAME_TITLE + " TEXT,"
                   + NotePad.Notes.COLUMN_NAME_NOTE + " TEXT,"
                   + NotePad.Notes.COLUMN_NAME_CATEGORY_ID + " INTEGER,"
                   + NotePad.Notes.COLUMN_NAME_CREATE_DATE + " INTEGER,"
                   + NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE + " INTEGER"
                   + ");");
           db.execSQL("CREATE TABLE " + NotePad.Todos.TABLE_NAME + " ("
                   + NotePad.Todos._ID + " INTEGER PRIMARY KEY,"
                   + NotePad.Todos.COLUMN_NAME_TITLE + " TEXT NOT NULL,"
                   + NotePad.Todos.COLUMN_NAME_CONTENT + " TEXT,"
                   + NotePad.Todos.COLUMN_NAME_COMPLETED + " INTEGER DEFAULT 0,"
                   + NotePad.Todos.COLUMN_NAME_CREATE_DATE + " INTEGER,"
                   + NotePad.Todos.COLUMN_NAME_MODIFICATION_DATE + " INTEGER"
                   + ");");
           db.execSQL("CREATE TABLE " + NotePad.Categories.TABLE_NAME + " ("
                   + NotePad.Categories._ID + " INTEGER PRIMARY KEY,"
                   + NotePad.Categories.COLUMN_NAME_NAME + " TEXT NOT NULL,"
                   + NotePad.Categories.COLUMN_NAME_CREATE_DATE + " INTEGER,"
                   + NotePad.Categories.COLUMN_NAME_MODIFICATION_DATE + " INTEGER"
                   + ");");
       }

       /**
        * 展示当底层数据存储发生变化时提供者需要考虑的处理方式。
        * 示例中通过清空现有数据来升级数据库；真实应用应就地升级。
        */
      @Override
          public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
          Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

          if (oldVersion < 3) {
              db.execSQL("CREATE TABLE IF NOT EXISTS " + NotePad.Todos.TABLE_NAME + " ("
                      + NotePad.Todos._ID + " INTEGER PRIMARY KEY,"
                      + NotePad.Todos.COLUMN_NAME_TITLE + " TEXT NOT NULL,"
                      + NotePad.Todos.COLUMN_NAME_CONTENT + " TEXT,"
                      + NotePad.Todos.COLUMN_NAME_COMPLETED + " INTEGER DEFAULT 0,"
                      + NotePad.Todos.COLUMN_NAME_CREATE_DATE + " INTEGER,"
                      + NotePad.Todos.COLUMN_NAME_MODIFICATION_DATE + " INTEGER"
                      + ");");
          }
          if (oldVersion < 4) {
              db.execSQL("ALTER TABLE " + NotePad.Notes.TABLE_NAME + " ADD COLUMN " + NotePad.Notes.COLUMN_NAME_CATEGORY_ID + " INTEGER");
              db.execSQL("CREATE TABLE IF NOT EXISTS " + NotePad.Categories.TABLE_NAME + " ("
                      + NotePad.Categories._ID + " INTEGER PRIMARY KEY,"
                      + NotePad.Categories.COLUMN_NAME_NAME + " TEXT NOT NULL,"
                      + NotePad.Categories.COLUMN_NAME_CREATE_DATE + " INTEGER,"
                      + NotePad.Categories.COLUMN_NAME_MODIFICATION_DATE + " INTEGER"
                      + ");");
          }
       }
   }

   /**
    *
    * 通过创建新的 DatabaseHelper 来初始化提供者。onCreate() 在系统因解析请求创建提供者时自动调用。
    */
   @Override
   public boolean onCreate() {

       // 创建新的辅助对象。注意：数据库仅在被访问时才会打开，且仅在不存在时才会创建。
       mOpenHelper = new DatabaseHelper(getContext());

       // 失败将通过抛出异常进行报告。
       return true;
   }

    /**
     * 当客户端调用 {@link android.content.ContentResolver#query(Uri, String[], String, String[], String)} 时触发。
     * 执行数据库查询并返回结果 Cursor。
     * @return 查询结果的 Cursor；查询无结果或异常时返回空游标。
     * @throws IllegalArgumentException 若传入的 URI 模式非法。
     */
   @Override
   public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
           String sortOrder) {

       // 构造新的查询构建器并设置表名
       SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
       qb.setTables(NotePad.Notes.TABLE_NAME);

       /**
        * 根据 URI 模式选择投影并调整 where 子句。
        */
       int match = sUriMatcher.match(uri);
       switch (match) {
           // 若传入 URI 为 notes，使用 Notes 的投影
           case NOTES:
               qb.setProjectionMap(sNotesProjectionMap);
               break;

           /* 若传入 URI 为按 ID 指定的单条笔记，选择 Notes 的投影，并在 where 子句追加
            * "_ID = <noteID>" 以只选择该笔记。
            */
           case NOTE_ID:
               qb.setProjectionMap(sNotesProjectionMap);
               qb.appendWhere(
                   NotePad.Notes._ID +    // the name of the ID column
                   "=" +
                   // the position of the note ID itself in the incoming URI
                   uri.getPathSegments().get(NotePad.Notes.NOTE_ID_PATH_POSITION));
               break;

           case LIVE_FOLDER_NOTES:
               // 若来自实时文件夹，选择其投影。
               qb.setProjectionMap(sLiveFolderProjectionMap);
               break;

           case TODOS:
               qb.setTables(NotePad.Todos.TABLE_NAME);
               qb.setProjectionMap(sTodosProjectionMap);
               break;

           case TODO_ID:
               qb.setTables(NotePad.Todos.TABLE_NAME);
               qb.setProjectionMap(sTodosProjectionMap);
               qb.appendWhere(
                       NotePad.Todos._ID + "=" + uri.getPathSegments().get(NotePad.Todos.TODO_ID_PATH_POSITION));
               break;
            case CATEGORIES:
                qb.setTables(NotePad.Categories.TABLE_NAME);
                qb.setProjectionMap(sCategoriesProjectionMap);
                break;
            case CATEGORY_ID:
                qb.setTables(NotePad.Categories.TABLE_NAME);
                qb.setProjectionMap(sCategoriesProjectionMap);
                qb.appendWhere(
                        NotePad.Categories._ID + "=" + uri.getPathSegments().get(NotePad.Categories.CATEGORY_ID_PATH_POSITION));
                break;
           default:
               // 若 URI 不匹配任何已知模式，抛出异常。
               throw new IllegalArgumentException("Unknown URI " + uri);
       }


       String orderBy;
       // 若未指定排序，使用默认排序
       if (TextUtils.isEmpty(sortOrder)) {
           if (match == TODOS || match == TODO_ID) {
               orderBy = NotePad.Todos.DEFAULT_SORT_ORDER;
           } else if (match == CATEGORIES || match == CATEGORY_ID) {
               orderBy = NotePad.Categories.DEFAULT_SORT_ORDER;
           } else {
               orderBy = NotePad.Notes.DEFAULT_SORT_ORDER;
           }
       } else {
           // 否则使用传入的排序
           orderBy = sortOrder;
       }

       // 以只读模式打开数据库对象，因为无需写入。
       SQLiteDatabase db = mOpenHelper.getReadableDatabase();

       /*
        * 执行查询。若读取数据库无问题则返回 Cursor；否则为 null。若无记录则 Cursor 为空且 getCount()==0。
        */
       Cursor c = qb.query(
           db,            // The database to query
           projection,    // The columns to return from the query
           selection,     // The columns for the where clause
           selectionArgs, // The values for the where clause
           null,          // don't group the rows
           null,          // don't filter by row groups
           orderBy        // The sort order
       );

       // 告知 Cursor 监听哪个 URI，以便源数据发生变化时收到通知
       c.setNotificationUri(getContext().getContentResolver(), uri);
       return c;
   }

    /**
     * 当客户端调用 {@link android.content.ContentResolver#getType(Uri)} 时触发。
     * 返回参数 URI 的 MIME 类型。
     * @param uri 需要查询 MIME 类型的 URI
     * @return 该 URI 的 MIME 类型
     * @throws IllegalArgumentException 若传入的 URI 模式非法。
     */
   @Override
   public String getType(Uri uri) {

       /**
        * 根据传入 URI 模式选择返回的 MIME 类型
        */
       switch (sUriMatcher.match(uri)) {

          // 若模式为 notes 或 live folders，返回目录类型。
           case NOTES:
           case LIVE_FOLDER_NOTES:
               return NotePad.Notes.CONTENT_TYPE;

          // 若模式为 note IDs，返回单项类型。
           case NOTE_ID:
               return NotePad.Notes.CONTENT_ITEM_TYPE;

           case TODOS:
               return NotePad.Todos.CONTENT_TYPE;
           case TODO_ID:
               return NotePad.Todos.CONTENT_ITEM_TYPE;

            case CATEGORIES:
                return NotePad.Categories.CONTENT_TYPE;
            case CATEGORY_ID:
                return NotePad.Categories.CONTENT_ITEM_TYPE;

          // 若 URI 模式不匹配任何允许的模式，抛出异常。
           default:
               throw new IllegalArgumentException("Unknown URI " + uri);
       }
    }

//BEGIN_INCLUDE(stream)
    /**
     * 描述打开笔记 URI 为数据流时所支持的 MIME 类型。
     */
    static ClipDescription NOTE_STREAM_TYPES = new ClipDescription(null,
            new String[] { ClipDescription.MIMETYPE_TEXT_PLAIN });

    /**
     * 返回可用的数据流类型。支持指向具体笔记的 URI，应用可将其转换为纯文本流。
     * @param uri 待分析的 URI
     * @param mimeTypeFilter 要匹配的 MIME 类型，仅返回与过滤器匹配的类型（当前仅 text/plain）
     * @return 数据流 MIME 类型（当前仅返回 text/plain）
     * @throws IllegalArgumentException 若 URI 模式不受支持。
     */
    @Override
    public String[] getStreamTypes(Uri uri, String mimeTypeFilter) {
        /**
         * 根据传入 URI 模式选择数据流类型。
         */
        switch (sUriMatcher.match(uri)) {

            // 若模式为 notes 或 live folders，则返回 null（不支持数据流）。
            case NOTES:
            case LIVE_FOLDER_NOTES:
                return null;

            // 若模式为 note IDs 且过滤器为 text/plain，返回 text/plain
            case NOTE_ID:
                return NOTE_STREAM_TYPES.filterMimeTypes(mimeTypeFilter);

                // 若 URI 模式不匹配任何允许模式，抛出异常。
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
            }
    }


    /**
     * 为每种受支持的类型返回数据流。本方法会查询传入的 URI，然后使用
     * {@link android.content.ContentProvider#openPipeHelper(Uri, String, Bundle, Object, PipeDataWriter)}
     * 启动线程以将数据转换为流。
     * @param uri 指向数据流的 URI 模式
     * @param mimeTypeFilter MIME 类型过滤器，尝试获取该类型的数据流
     * @param opts 调用方提供的附加选项，由内容提供者自行解释
     * @return AssetFileDescriptor 文件句柄
     * @throws FileNotFoundException 若传入 URI 未关联到文件
     */
    @Override
    public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter, Bundle opts)
            throws FileNotFoundException {

        // 检查过滤器是否匹配受支持的 MIME 类型。
        String[] mimeTypes = getStreamTypes(uri, mimeTypeFilter);

        // 若 MIME 类型受支持
        if (mimeTypes != null) {

            // 为该 URI 检索笔记。使用本提供者的 query 方法，而非直接数据库查询。
            Cursor c = query(
                    uri,                    // The URI of a note
                    READ_NOTE_PROJECTION,   // Gets a projection containing the note's ID, title,
                                            // and contents
                    null,                   // No WHERE clause, get all matching records
                    null,                   // Since there is no WHERE clause, no selection criteria
                    null                    // Use the default sort order (modification date,
                                            // descending
            );


            // 查询失败或光标为空则停止
            if (c == null || !c.moveToFirst()) {

                // 若光标为空，直接关闭并返回
                if (c != null) {
                    c.close();
                }

                // 若光标为 null，抛出异常
                throw new FileNotFoundException("Unable to query " + uri);
            }

            // 启动新线程将流数据通过管道返回给调用者。
            return new AssetFileDescriptor(
                    openPipeHelper(uri, mimeTypes[0], opts, c, this), 0,
                    AssetFileDescriptor.UNKNOWN_LENGTH);
        }

        // 若 MIME 类型不支持，返回只读文件句柄。
        return super.openTypedAssetFile(uri, mimeTypeFilter, opts);
    }

    /**
     * {@link android.content.ContentProvider.PipeDataWriter} 的实现：将 Cursor 中的数据
     * 转换为客户端可读取的数据流。
     */
    @Override
    public void writeDataToPipe(ParcelFileDescriptor output, Uri uri, String mimeType,
            Bundle opts, Cursor c) {
        // 目前仅支持将单条笔记转换为文本，无需进行 Cursor 类型检查。
        FileOutputStream fout = new FileOutputStream(output.getFileDescriptor());
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new OutputStreamWriter(fout, "UTF-8"));
            pw.println(c.getString(READ_NOTE_TITLE_INDEX));
            pw.println("");
            pw.println(c.getString(READ_NOTE_NOTE_INDEX));
        } catch (UnsupportedEncodingException e) {
            Log.w(TAG, "Ooops", e);
        } finally {
            c.close();
            if (pw != null) {
                pw.flush();
            }
            try {
                fout.close();
            } catch (IOException e) {
            }
        }
    }
//END_INCLUDE(stream)

    /**
     * 当客户端调用 {@link android.content.ContentResolver#insert(Uri, ContentValues)} 时触发。
     * 向数据库插入一条记录。为未在传入映射中包含的列设置默认值。
     * 若插入成功，通知监听者数据发生变化。
     * @return 新记录的行 ID
     * @throws SQLException 插入失败时抛出
     */
    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {

        // 验证传入的 URI。插入仅允许使用完整的提供者 URI。
        int match = sUriMatcher.match(uri);
        if (match != NOTES && match != TODOS && match != CATEGORIES) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // 保存新记录值的映射。
        ContentValues values;

        // 若传入的值映射非空，直接使用之。
        if (initialValues != null) {
            values = new ContentValues(initialValues);

        } else {
            // 否则创建新的值映射
            values = new ContentValues();
        }

        // 获取当前系统时间（毫秒）
        Long now = Long.valueOf(System.currentTimeMillis());

        if (match == NOTES) {
            if (values.containsKey(NotePad.Notes.COLUMN_NAME_CREATE_DATE) == false) {
                values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, now);
            }
            if (values.containsKey(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE) == false) {
                values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, now);
            }
            if (values.containsKey(NotePad.Notes.COLUMN_NAME_TITLE) == false) {
                Resources r = Resources.getSystem();
                values.put(NotePad.Notes.COLUMN_NAME_TITLE, r.getString(android.R.string.untitled));
            }
            if (values.containsKey(NotePad.Notes.COLUMN_NAME_NOTE) == false) {
                values.put(NotePad.Notes.COLUMN_NAME_NOTE, "");
            }
        } else if (match == TODOS) {
            if (values.containsKey(NotePad.Todos.COLUMN_NAME_CREATE_DATE) == false) {
                values.put(NotePad.Todos.COLUMN_NAME_CREATE_DATE, now);
            }
            if (values.containsKey(NotePad.Todos.COLUMN_NAME_MODIFICATION_DATE) == false) {
                values.put(NotePad.Todos.COLUMN_NAME_MODIFICATION_DATE, now);
            }
            if (values.containsKey(NotePad.Todos.COLUMN_NAME_TITLE) == false) {
                values.put(NotePad.Todos.COLUMN_NAME_TITLE, "");
            }
            if (values.containsKey(NotePad.Todos.COLUMN_NAME_CONTENT) == false) {
                values.put(NotePad.Todos.COLUMN_NAME_CONTENT, "");
            }
            if (values.containsKey(NotePad.Todos.COLUMN_NAME_COMPLETED) == false) {
                values.put(NotePad.Todos.COLUMN_NAME_COMPLETED, 0);
            }
        } else {
            if (values.containsKey(NotePad.Categories.COLUMN_NAME_CREATE_DATE) == false) {
                values.put(NotePad.Categories.COLUMN_NAME_CREATE_DATE, now);
            }
            if (values.containsKey(NotePad.Categories.COLUMN_NAME_MODIFICATION_DATE) == false) {
                values.put(NotePad.Categories.COLUMN_NAME_MODIFICATION_DATE, now);
            }
            if (values.containsKey(NotePad.Categories.COLUMN_NAME_NAME) == false) {
                values.put(NotePad.Categories.COLUMN_NAME_NAME, "");
            }
        }

        // 以写入模式打开数据库对象。
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        // 执行插入并返回新笔记的 ID。
        long rowId;
        Uri baseUri;
        if (match == NOTES) {
            rowId = db.insert(
                NotePad.Notes.TABLE_NAME,
                NotePad.Notes.COLUMN_NAME_NOTE,
                values
            );
            baseUri = NotePad.Notes.CONTENT_ID_URI_BASE;
        } else if (match == TODOS) {
            rowId = db.insert(
                NotePad.Todos.TABLE_NAME,
                NotePad.Todos.COLUMN_NAME_CONTENT,
                values
            );
            baseUri = NotePad.Todos.CONTENT_ID_URI_BASE;
        } else {
            rowId = db.insert(
                NotePad.Categories.TABLE_NAME,
                NotePad.Categories.COLUMN_NAME_NAME,
                values
            );
            baseUri = NotePad.Categories.CONTENT_ID_URI_BASE;
        }

        // If the insert succeeded, the row ID exists.
        if (rowId > 0) {
            // 创建符合笔记 ID 模式、并附加新行 ID 的 URI。
            Uri noteUri = ContentUris.withAppendedId(baseUri, rowId);

            // 通知对此提供者注册的观察者数据已改变。
            getContext().getContentResolver().notifyChange(noteUri, null);
            return noteUri;
        }

        // 插入失败则行 ID <= 0，抛出异常。
        throw new SQLException("Failed to insert row into " + uri);
    }

    /**
     * 当客户端调用 {@link android.content.ContentResolver#delete(Uri, String, String[])} 时触发。
     * 从数据库删除记录：若 URI 匹配 Note ID 模式，删除指定 ID 的单条记录；否则按 where 与 whereArgs 删除集合。
     * 若删除成功，通知监听者数据变化。
     * @return 若使用 where 子句，返回受影响行数；否则返回 0。要删除所有行并返回计数，可使用 "1" 作为 where。
     * @throws IllegalArgumentException 若 URI 模式非法。
     */
    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {

        // 以写入模式打开数据库对象。
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        String finalWhere;

        int count;

        // 根据传入 URI 模式执行删除。
        switch (sUriMatcher.match(uri)) {

            // 若为 notes 通用模式，则按传入的 where 列与参数删除。
            case NOTES:
                count = db.delete(
                    NotePad.Notes.TABLE_NAME,  // The database table name
                    where,                     // The incoming where clause column names
                    whereArgs                  // The incoming where clause values
                );
                break;

                // 若为单个 note ID，则按传入数据删除，但将 where 子句限制为该 ID。
            case NOTE_ID:
                /*
                 * 构建最终 WHERE 子句：限定到目标 note ID。
                 */
                finalWhere =
                        NotePad.Notes._ID +                              // The ID column name
                        " = " +                                          // test for equality
                        uri.getPathSegments().                           // the incoming note ID
                            get(NotePad.Notes.NOTE_ID_PATH_POSITION)
                ;

                // 若存在更多选择条件，追加到最终 WHERE 子句
                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }

                // 执行删除。
                count = db.delete(
                    NotePad.Notes.TABLE_NAME,  // The database table name.
                    finalWhere,                // The final WHERE clause
                    whereArgs                  // The incoming where clause values.
                );
                break;

            case TODOS:
                count = db.delete(
                        NotePad.Todos.TABLE_NAME,
                        where,
                        whereArgs
                );
                break;

            case TODO_ID:
                finalWhere = NotePad.Todos._ID + " = " + uri.getPathSegments().get(NotePad.Todos.TODO_ID_PATH_POSITION);
                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }
                count = db.delete(NotePad.Todos.TABLE_NAME, finalWhere, whereArgs);
                break;
            case CATEGORIES:
                count = db.delete(NotePad.Categories.TABLE_NAME, where, whereArgs);
                break;
            case CATEGORY_ID:
                finalWhere = NotePad.Categories._ID + " = " + uri.getPathSegments().get(NotePad.Categories.CATEGORY_ID_PATH_POSITION);
                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }
                count = db.delete(NotePad.Categories.TABLE_NAME, finalWhere, whereArgs);
                break;
            // 若传入模式非法，抛出异常。
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        /* 获取当前上下文的内容解析器句柄并通知其传入的 URI 已变化；解析框架会转发该通知，注册的观察者会收到。 */
        getContext().getContentResolver().notifyChange(uri, null);

        // 返回删除的行数。
        return count;
    }

    /**
     * 当客户端调用 {@link android.content.ContentResolver#update(Uri,ContentValues,String,String[])} 时触发。
     * 更新数据库记录：values 中的键为列名、值为新数据。若 URI 匹配 note ID 模式则仅更新对应记录；
     * 否则更新集合。记录需匹配 where 与 whereArgs 指定的条件。若更新成功，则通知监听者数据变化。
     * @param uri 要匹配并更新的 URI 模式
     * @param values 列名与新值的映射
     * @param where SQL WHERE 子句，按列值筛选记录；为 null 则匹配所有符合 URI 模式的记录
     * @param whereArgs WHERE 子句的参数数组，若 where 含占位符 "?"，按顺序替换
     * @return 更新的行数
     * @throws IllegalArgumentException 若 URI 模式非法。
     */
    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {

        // 以写入模式打开数据库对象。
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        String finalWhere;

        // 根据传入 URI 模式执行更新
        switch (sUriMatcher.match(uri)) {

            // 若为 notes 通用模式，则根据传入数据执行更新。
            case NOTES:

                // Does the update and returns the number of rows updated.
                count = db.update(
                    NotePad.Notes.TABLE_NAME, // The database table name.
                    values,                   // A map of column names and new values to use.
                    where,                    // The where clause column names.
                    whereArgs                 // The where clause column values to select on.
                );
                break;

            // 若为单个 note ID，则根据传入数据更新，并将 where 子句限制为该 ID。
            case NOTE_ID:
                // From the incoming URI, get the note ID
                String noteId = uri.getPathSegments().get(NotePad.Notes.NOTE_ID_PATH_POSITION);

                /*
                 * 构建最终 WHERE 子句：限定到传入的 note ID。
                 */
                finalWhere =
                        NotePad.Notes._ID +                              // The ID column name
                        " = " +                                          // test for equality
                        uri.getPathSegments().                           // the incoming note ID
                            get(NotePad.Notes.NOTE_ID_PATH_POSITION)
                ;

                // 若存在更多选择条件，追加到最终 WHERE 子句
                if (where !=null) {
                    finalWhere = finalWhere + " AND " + where;
                }


                // 执行更新并返回更新行数。
                count = db.update(
                    NotePad.Notes.TABLE_NAME, // The database table name.
                    values,                   // A map of column names and new values to use.
                    finalWhere,               // The final WHERE clause to use
                                              // placeholders for whereArgs
                    whereArgs                 // The where clause column values to select on, or
                                              // null if the values are in the where argument.
                );
                break;
            case TODOS:
                count = db.update(
                        NotePad.Todos.TABLE_NAME,
                        values,
                        where,
                        whereArgs
                );
                break;
            case TODO_ID:
                finalWhere = NotePad.Todos._ID + " = " + uri.getPathSegments().get(NotePad.Todos.TODO_ID_PATH_POSITION);
                if (where !=null) {
                    finalWhere = finalWhere + " AND " + where;
                }
                count = db.update(
                        NotePad.Todos.TABLE_NAME,
                        values,
                        finalWhere,
                        whereArgs
                );
                break;
            case CATEGORIES:
                count = db.update(NotePad.Categories.TABLE_NAME, values, where, whereArgs);
                break;
            case CATEGORY_ID:
                finalWhere = NotePad.Categories._ID + " = " + uri.getPathSegments().get(NotePad.Categories.CATEGORY_ID_PATH_POSITION);
                if (where !=null) {
                    finalWhere = finalWhere + " AND " + where;
                }
                count = db.update(NotePad.Categories.TABLE_NAME, values, finalWhere, whereArgs);
                break;
            // 若传入模式非法，抛出异常。
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        /* 获取当前上下文的内容解析器句柄并通知其传入的 URI 已变化；解析框架会转发该通知，注册的观察者会收到。 */
        getContext().getContentResolver().notifyChange(uri, null);

        // Returns the number of rows updated.
        return count;
    }

    /**
     * 测试包可调用此方法获取 NotePadProvider 底层数据库的句柄，以便插入测试数据。
     * 测试用例负责在测试环境中实例化该提供者；{@link android.test.ProviderTestCase2} 会在 setUp() 中完成。
     * @return 提供者数据的数据库帮助对象句柄。
     */
    DatabaseHelper getOpenHelperForTest() {
        return mOpenHelper;
    }
}

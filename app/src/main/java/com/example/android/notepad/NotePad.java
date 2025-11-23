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

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * 定义 NotePad 内容提供者与其客户端之间的契约。契约描述客户端以一个或多个数据表形式
 * 访问提供者所需的信息。契约类是公开且不可扩展（final），仅包含列名与 URI 等常量；
 * 设计良好的客户端应只依赖这些常量。
 */
public final class NotePad {
    public static final String AUTHORITY = "com.google.provider.NotePad";

    // 此类不可被实例化
    private NotePad() {
    }

    /**
     * Notes table contract
     */
    public static final class Notes implements BaseColumns {

        // This class cannot be instantiated
        private Notes() {}

        /**
         * 提供者所提供的表名
         */
        public static final String TABLE_NAME = "notes";

        /*
         * URI 定义
         */

        /**
         * 提供者 URI 的 scheme 部分
         */
        private static final String SCHEME = "content://";

        /**
         * URI 的路径部分
         */

        /**
         * Notes URI 的路径部分
         */
        private static final String PATH_NOTES = "/notes";

        /**
         * Note ID URI 的路径部分
         */
        private static final String PATH_NOTE_ID = "/notes/";

        /**
         * Note ID URI 路径中，note ID 片段的 0 起始位置
         */
        public static final int NOTE_ID_PATH_POSITION = 1;

        /**
         * Live Folder URI 的路径部分
         */
        private static final String PATH_LIVE_FOLDER = "/live_folders/notes";

        /**
         * 此表的 content:// 形式 URL
         */
        public static final Uri CONTENT_URI =  Uri.parse(SCHEME + AUTHORITY + PATH_NOTES);

        /**
         * 单条笔记的内容 URI 基础；调用方需将数值 ID 追加到该 URI 以检索笔记。
         */
        public static final Uri CONTENT_ID_URI_BASE
            = Uri.parse(SCHEME + AUTHORITY + PATH_NOTE_ID);

        /**
         * 指定单条笔记（按 ID）的内容 URI 匹配模式，用于匹配传入 URI 或构造 Intent。
         */
        public static final Uri CONTENT_ID_URI_PATTERN
            = Uri.parse(SCHEME + AUTHORITY + PATH_NOTE_ID + "/#");

        /**
         * 供实时文件夹使用的笔记列表内容 URI 模式
         */
        public static final Uri LIVE_FOLDER_URI
            = Uri.parse(SCHEME + AUTHORITY + PATH_LIVE_FOLDER);

        /*
         * MIME 类型定义
         */

        /**
         * {@link #CONTENT_URI} 的 MIME 类型（笔记目录）。
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.google.note";

        /**
         * 单条笔记的 {@link #CONTENT_URI} 子目录 MIME 类型。
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.google.note";

        /**
         * 此表的默认排序
         */
        public static final String DEFAULT_SORT_ORDER = "modified DESC";

        /*
         * 列定义
         */

        /**
         * 笔记标题列名
         * <P>类型: TEXT</P>
         */
        public static final String COLUMN_NAME_TITLE = "title";

        /**
         * 笔记内容列名
         * <P>类型: TEXT</P>
         */
        public static final String COLUMN_NAME_NOTE = "note";

        /**
         * 创建时间戳列名
         * <P>类型: INTEGER（System.currentTimeMillis() 的 long）</P>
         */
        public static final String COLUMN_NAME_CREATE_DATE = "created";

        /**
         * 修改时间戳列名
         * <P>类型: INTEGER（System.currentTimeMillis() 的 long）</P>
         */
        public static final String COLUMN_NAME_MODIFICATION_DATE = "modified";
    }
}

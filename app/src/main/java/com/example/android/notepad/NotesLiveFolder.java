/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.app.Activity;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.os.Bundle;
import android.provider.LiveFolders;

/**
 * 此 Activity 创建一个实时文件夹（Live Folder）的 Intent 并返回给 HOME。
 * HOME 根据该 Intent 的数据创建实时文件夹并在主页显示其图标。
 * 当用户点击图标时，HOME 使用 Intent 中的数据从内容提供者检索信息并在视图中显示。
 *
 * 此 Activity 的 Intent 过滤器设置为 ACTION_CREATE_LIVE_FOLDER，HOME 会在长按并选择
 * “Live Folder”后发送该动作。
 */
public class NotesLiveFolder extends Activity {

    /**
     * 所有工作都在 onCreate() 中完成。该 Activity 实际上不显示任何 UI，
     * 而是构造一个 Intent 并返回给调用方（HOME Activity）。
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*
         * 获取传入的 Intent 及其动作。若动作为 ACTION_CREATE_LIVE_FOLDER，则创建并返回一个
         * 带必要数据的 Intent，并设置结果为 OK；否则设置结果为 CANCEL。
         */
        final Intent intent = getIntent();
        final String action = intent.getAction();

        if (LiveFolders.ACTION_CREATE_LIVE_FOLDER.equals(action)) {

            // 创建新的 Intent。
            final Intent liveFolderIntent = new Intent();

            /*
             * 以下语句向返回的 Intent 填充数据。详见 {@link android.provider.LiveFolders}。
             * HOME 会根据这些数据创建实时文件夹。
             */
            // 设置实时文件夹背后内容提供者的 URI 模式。
            liveFolderIntent.setData(NotePad.Notes.LIVE_FOLDER_URI);

            // 以 Extra 字符串形式添加实时文件夹显示名称。
            String foldername = getString(R.string.live_folder_name);
            liveFolderIntent.putExtra(LiveFolders.EXTRA_LIVE_FOLDER_NAME, foldername);

            // 以 Extra 资源形式添加实时文件夹显示图标。
            ShortcutIconResource foldericon =
                Intent.ShortcutIconResource.fromContext(this, R.drawable.live_folder_notes);
            liveFolderIntent.putExtra(LiveFolders.EXTRA_LIVE_FOLDER_ICON, foldericon);

            // 以整数形式添加实时文件夹的显示模式。指定的模式将使其以列表方式显示。
            liveFolderIntent.putExtra(
                    LiveFolders.EXTRA_LIVE_FOLDER_DISPLAY_MODE,
                    LiveFolders.DISPLAY_MODE_LIST);

            /*
             * 为实时文件夹列表中的条目添加基础动作（Intent）。当用户点击列表中的某条笔记时，
             * 实时文件夹会触发该 Intent。
             * 动作为 ACTION_EDIT，因此会启动 Note Editor；数据为按 ID 指定的单条笔记 URI 模式，
             * 实时文件夹会自动在该模式后附加所选条目的 ID。
             * 最终 Note Editor 被触发，并按 ID 获取单条笔记。
             */
            Intent returnIntent
                    = new Intent(Intent.ACTION_EDIT, NotePad.Notes.CONTENT_ID_URI_PATTERN);
            liveFolderIntent.putExtra(LiveFolders.EXTRA_LIVE_FOLDER_BASE_INTENT, returnIntent);

            /* 创建返回给 HOME 的结果对象：设置结果为 OK，并返回刚构造的实时文件夹 Intent。 */
            setResult(RESULT_OK, liveFolderIntent);

        } else {

            // 若原始动作不是 ACTION_CREATE_LIVE_FOLDER，则设置结果为 CANCELED，不返回 Intent
            setResult(RESULT_CANCELED);
        }

        // 关闭 Activity，结果对象会返回给调用者。
        finish();
    }
}

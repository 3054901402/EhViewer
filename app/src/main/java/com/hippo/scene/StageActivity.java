/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.scene;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.hippo.ehviewer.R;
import com.hippo.yorozuya.IdIntGenerator;

import java.util.ArrayList;
import java.util.Collections;

public abstract class StageActivity extends AppCompatActivity {

    private static final String TAG = StageActivity.class.getSimpleName();

    private static final String KEY_STAGE_ACTIVITY_SCENE_TAG_LIST = "stage_activity_scene_tag_list";

    // TODO ArrayList or LinkedList
    private ArrayList<String> mSceneTagList = new ArrayList<>();
    private IdIntGenerator mIdGenerator = new IdIntGenerator();

    public abstract int getContainerViewId();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    private <T extends SceneFragment> SceneFragment newSceneInstance(Class<T> clazz) {
        try {
            return clazz.newInstance();
        } catch (InstantiationException e) {
            throw new IllegalStateException("Can't instance " + clazz.getName(), e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("The constructor of " +
                    clazz.getName() + " is not visible", e);
        } catch (ClassCastException e) {
            throw new IllegalStateException(clazz.getName() + " can not cast to scene", e);
        }
    }

    public <T extends SceneFragment> void startScene(Class<T> clazz) {
        startScene(clazz, null, null);
    }

    public <T extends SceneFragment> void startScene(Class<T> clazz, Bundle args) {
        startScene(clazz, args, null);
    }

    public <T extends SceneFragment> void startScene(Class<T> clazz, TransitionHelper transitionHelper) {
        startScene(clazz, null, transitionHelper);
    }

    public <T extends SceneFragment> void startScene(Class<T> clazz,
            Bundle args, TransitionHelper transitionHelper) {
        FragmentManager fragmentManager = getSupportFragmentManager();

        // Old scene
        Fragment old = null;
        if (mSceneTagList.size() > 0) {
            // Get last tag
            String tag = mSceneTagList.get(mSceneTagList.size() - 1);
            old = fragmentManager.findFragmentByTag(tag);
        }

        // Launch mode single top
        if (old instanceof SceneFragment && clazz.isInstance(old)) {
            SceneFragment oldScene = (SceneFragment) old;
            if (oldScene.getLaunchMode() == SceneFragment.LAUNCH_MODE_SINGLE_TOP) {
                if (args != null) {
                    oldScene.onNewArguments(args);
                }
                return;
            }
        }

        // New scene
        SceneFragment scene = newSceneInstance(clazz);
        scene.setArguments(args);
        // New scene tag
        String newTag = Integer.toString(mIdGenerator.nextId());
        mSceneTagList.add(newTag);

        FragmentTransaction transaction = fragmentManager.beginTransaction();
        if (transitionHelper != null && old != null) {
            transitionHelper.onTransition(this, transaction, old, scene);
        } else {
            transaction.setCustomAnimations(R.anim.fragment_translate_in, R.anim.fragment_translate_out);
        }
        transaction.remove(scene);
        if (old != null) {
            transaction.detach(old);
        }
        transaction.add(getContainerViewId(), scene, newTag);
        transaction.commit();

        // Update SoftInputMode
        getWindow().setSoftInputMode(scene.getSoftInputMode());
    }

    int getStackIndex(SceneFragment scene) {
        return getStackIndex(scene.getTag());
    }

    int getStackIndex(String tag) {
        return Collections.binarySearch(mSceneTagList, tag);
    }

    public void finishScene(SceneFragment scene) {
        finishScene(scene.getTag());
    }

    private void finishScene(String tag) {
        FragmentManager fragmentManager = getSupportFragmentManager();

        // Get scene
        Fragment scene = fragmentManager.findFragmentByTag(tag);
        if (scene == null) {
            Log.e(TAG, "finishScene: Can't find scene by tag: " + tag);
            return;
        }

        // FIXME It will fail if mIdGenerator.nextId() called more than Integer.MAX_VALUE times
        // Get scene index
        int index = Collections.binarySearch(mSceneTagList, tag);
        if (index < 0) {
            Log.e(TAG, "finishScene: Can't find the tag in tag list: " + tag);
            return;
        }

        if (mSceneTagList.size() == 1) {
            // It is the last fragment, finish Activity now
            Log.i(TAG, "finishScene: It is the last scene, finish activity now");
            finish();
            return;
        }

        Fragment next = null;
        // It is first fragment, show the next one
        if (index == mSceneTagList.size() - 1) {
            next = fragmentManager.findFragmentByTag(mSceneTagList.get(index - 1));
        }

        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.setCustomAnimations(R.anim.fragment_translate_in, R.anim.fragment_translate_out);
        transaction.remove(scene);
        if (next != null && next.isDetached()) {
            transaction.attach(next);
        }
        transaction.commit();

        // Remove tag
        mSceneTagList.remove(index);

        // Update SoftInputMode
        if (next instanceof SceneFragment) {
            getWindow().setSoftInputMode(((SceneFragment) scene).getSoftInputMode());
        }
    }

    @Override
    public void onBackPressed() {
        int size = mSceneTagList.size();
        String tag = mSceneTagList.get(size - 1);
        SceneFragment scene;
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        if (fragment == null) {
            Log.e(TAG, "onBackPressed: Can't find scene by tag: " + tag);
            return;
        }
        if (!(fragment instanceof SceneFragment)) {
            Log.e(TAG, "onBackPressed: The fragment is not SceneFragment");
            return;
        }

        scene = (SceneFragment) fragment;
        if (!scene.onBackPressed()) {
            if (size > 0) {
                finishScene(tag);
            } else {
                finish();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList(KEY_STAGE_ACTIVITY_SCENE_TAG_LIST, mSceneTagList);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mSceneTagList = savedInstanceState.getStringArrayList(KEY_STAGE_ACTIVITY_SCENE_TAG_LIST);
    }
}
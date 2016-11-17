/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.statusbar;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.android.systemui.Interpolators;
import com.android.systemui.R;
import com.android.systemui.ViewInvertHelper;
import com.android.systemui.statusbar.notification.NotificationUtils;
import com.android.systemui.statusbar.phone.NotificationIconContainer;
import com.android.systemui.statusbar.phone.NotificationPanelView;
import com.android.systemui.statusbar.stack.AmbientState;
import com.android.systemui.statusbar.stack.AnimationProperties;
import com.android.systemui.statusbar.stack.ExpandableViewState;
import com.android.systemui.statusbar.stack.NotificationStackScrollLayout;
import com.android.systemui.statusbar.stack.StackScrollState;

import java.util.ArrayList;
import java.util.WeakHashMap;

/**
 * A notification shelf view that is placed inside the notification scroller. It manages the
 * overflow icons that don't fit into the regular list anymore.
 */
public class NotificationShelf extends ActivatableNotificationView {

    private ViewInvertHelper mViewInvertHelper;
    private boolean mDark;
    private NotificationIconContainer mShelfIcons;
    private ArrayList<StatusBarIconView> mIcons = new ArrayList<>();
    private ShelfState mShelfState;
    private int[] mTmp = new int[2];
    private boolean mHideBackground;
    private int mIconAppearTopPadding;
    private int mStatusBarHeight;
    private int mStatusBarPaddingStart;
    private AmbientState mAmbientState;
    private NotificationStackScrollLayout mHostLayout;
    private int mMaxLayoutHeight;
    private int mPaddingBetweenElements;
    private int mNotGoneIndex;
    private boolean mHasItemsInStableShelf;
    private NotificationIconContainer mCollapsedIcons;

    public NotificationShelf(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mShelfIcons = (NotificationIconContainer) findViewById(R.id.content);
        mShelfIcons.setClipChildren(false);
        mShelfIcons.setClipToPadding(false);

        setClipToActualHeight(false);
        setClipChildren(false);
        setClipToPadding(false);
        mShelfIcons.setShowAllIcons(false);
        mViewInvertHelper = new ViewInvertHelper(mShelfIcons,
                NotificationPanelView.DOZE_ANIMATION_DURATION);
        mShelfState = new ShelfState();
        initDimens();
    }

    public void bind(AmbientState ambientState, NotificationStackScrollLayout hostLayout) {
        mAmbientState = ambientState;
        mHostLayout = hostLayout;
    }

    private void initDimens() {
        mIconAppearTopPadding = getResources().getDimensionPixelSize(
                R.dimen.notification_icon_appear_padding);
        mStatusBarHeight = getResources().getDimensionPixelOffset(R.dimen.status_bar_height);
        mStatusBarPaddingStart = getResources().getDimensionPixelOffset(
                R.dimen.status_bar_padding_start);
        mPaddingBetweenElements = getResources().getDimensionPixelSize(
                R.dimen.notification_divider_height);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        initDimens();
    }

    @Override
    public void setDark(boolean dark, boolean fade, long delay) {
        super.setDark(dark, fade, delay);
        if (mDark == dark) return;
        mDark = dark;
        if (fade) {
            mViewInvertHelper.fade(dark, delay);
        } else {
            mViewInvertHelper.update(dark);
        }
    }

    @Override
    protected View getContentView() {
        return mShelfIcons;
    }

    public NotificationIconContainer getShelfIcons() {
        return mShelfIcons;
    }

    @Override
    public ExpandableViewState createNewViewState(StackScrollState stackScrollState) {
        return mShelfState;
    }

    public void updateState(StackScrollState resultState,
            AmbientState ambientState) {
        View lastView = ambientState.getLastVisibleBackgroundChild();
        if (lastView != null) {
            float maxShelfEnd = ambientState.getInnerHeight() + ambientState.getTopPadding()
                    + ambientState.getStackTranslation();
            ExpandableViewState lastViewState = resultState.getViewStateForView(lastView);
            float viewEnd = lastViewState.yTranslation + lastViewState.height;
            mShelfState.copyFrom(lastViewState);
            mShelfState.height = getIntrinsicHeight();
            mShelfState.yTranslation = Math.max(Math.min(viewEnd, maxShelfEnd) - mShelfState.height,
                    getFullyClosedTranslation());
            mShelfState.zTranslation = ambientState.getBaseZHeight();
            float openedAmount = (mShelfState.yTranslation - getFullyClosedTranslation())
                    / (getIntrinsicHeight() * 2);
            openedAmount = Math.min(1.0f, openedAmount);
            mShelfState.openedAmount = openedAmount;
            mShelfState.clipTopAmount = 0;
            mShelfState.alpha = 1.0f;
            mShelfState.belowSpeedBump = false;
            mShelfState.shadowAlpha = 1.0f;
            mShelfState.isBottomClipped = false;
            mShelfState.hideSensitive = false;
            mShelfState.xTranslation = getTranslationX();
            if (mNotGoneIndex != -1) {
                mShelfState.notGoneIndex = Math.min(mShelfState.notGoneIndex, mNotGoneIndex);
            }
            mShelfState.hasItemsInStableShelf = lastViewState.inShelf;
        } else {
            mShelfState.hidden = true;
            mShelfState.location = ExpandableViewState.LOCATION_GONE;
            mShelfState.hasItemsInStableShelf = false;
        }
    }

    /**
     * Update the shelf appearance based on the other notifications around it. This transforms
     * the icons from the notification area into the shelf.
     */
    public void updateAppearance() {
        WeakHashMap<View, NotificationIconContainer.IconState> iconStates =
                mShelfIcons.resetViewStates();
        float numViewsInShelf = 0.0f;
        View lastChild = mAmbientState.getLastVisibleBackgroundChild();
        mNotGoneIndex = -1;
        float interpolationStart = mMaxLayoutHeight - getIntrinsicHeight() * 2;
        float expandAmount = 0.0f;
        if (getTranslationY() >= interpolationStart) {
            expandAmount = (getTranslationY() - interpolationStart) / getIntrinsicHeight();
            expandAmount = Math.min(1.0f, expandAmount);
        }
        //  find the first view that doesn't overlap with the shelf
        int notificationIndex = 0;
        int notGoneIndex = 0;
        boolean backgroundForceHidden = false;
        if (mHideBackground && !mShelfState.hasItemsInStableShelf) {
            backgroundForceHidden = true;
        }
        while (notificationIndex < mHostLayout.getChildCount()) {
            ExpandableView child = (ExpandableView) mHostLayout.getChildAt(notificationIndex);
            notificationIndex++;
            if (!(child instanceof ExpandableNotificationRow)
                    || child.getVisibility() == GONE) {
                continue;
            }
            ExpandableNotificationRow row = (ExpandableNotificationRow) child;
            StatusBarIconView icon = row.getEntry().expandedIcon;
            NotificationIconContainer.IconState iconState = iconStates.get(icon);
            float notificationClipEnd;
            float shelfStart = getTranslationY();
            boolean aboveShelf = row.getTranslationZ() > mAmbientState.getBaseZHeight();
            if (child == lastChild || aboveShelf || backgroundForceHidden) {
                notificationClipEnd = shelfStart + getIntrinsicHeight();
            } else {
                notificationClipEnd = shelfStart - mPaddingBetweenElements;
                float height = notificationClipEnd - row.getTranslationY();
                if (height <= getNotificationMergeSize()) {
                    // We want the gap to close when we reached the minimum size and only shrink
                    // before
                    notificationClipEnd = Math.min(shelfStart,
                            row.getTranslationY() + getNotificationMergeSize());
                }
            }
            updateNotificationClipHeight(row, notificationClipEnd);
            numViewsInShelf += updateIconAppearance(row, iconState, icon, expandAmount);
            if (row.getTranslationY() >= getTranslationY() && mNotGoneIndex == -1) {
                mNotGoneIndex = notGoneIndex;
            }
            if (notGoneIndex != 0 || !aboveShelf) {
                row.setAboveShelf(false);
            }
            notGoneIndex++;
        }
        mShelfIcons.calculateIconTranslations();
        mShelfIcons.applyIconStates();
        setVisibility(numViewsInShelf != 0.0f && mAmbientState.isShadeExpanded()
                ? VISIBLE
                : INVISIBLE);
        boolean hideBackground = numViewsInShelf < 1.0f;
        setHideBackground(hideBackground || backgroundForceHidden);
        if (mNotGoneIndex == -1) {
            mNotGoneIndex = notGoneIndex;
        }
    }

    private void updateNotificationClipHeight(ExpandableNotificationRow row,
            float notificationClipEnd) {
        float viewEnd = row.getTranslationY() + row.getActualHeight();
        if (viewEnd > notificationClipEnd
                && (mAmbientState.isShadeExpanded()
                        || (!row.isPinned() && !row.isHeadsUpAnimatingAway()))) {
            row.setClipBottomAmount((int) (viewEnd - notificationClipEnd));
        } else {
            row.setClipBottomAmount(0);
        }
    }

    private float updateIconAppearance(ExpandableNotificationRow row,
            NotificationIconContainer.IconState iconState, StatusBarIconView icon,
            float expandAmount) {
        // Let calculate how much the view is in the shelf
        float viewStart = row.getTranslationY();
        int transformHeight = row.getActualHeight() + mPaddingBetweenElements;
        float viewEnd = viewStart + transformHeight;
        float iconAppearAmount;
        float yTranslation;
        float alpha = 1.0f;
        if (viewEnd >= getTranslationY() && (mAmbientState.isShadeExpanded()
                || (!row.isPinned() && !row.isHeadsUpAnimatingAway()))) {
            if (viewStart < getTranslationY()) {
                float linearAmount = (getTranslationY() - viewStart) / transformHeight;
                float interpolatedAmount =  Interpolators.ACCELERATE_DECELERATE.getInterpolation(
                        linearAmount);
                interpolatedAmount = NotificationUtils.interpolate(
                        interpolatedAmount, linearAmount, expandAmount);
                iconAppearAmount = 1.0f - interpolatedAmount;
            } else {
                iconAppearAmount = 1.0f;
            }
        } else {
            iconAppearAmount = 0.0f;
        }

        // Lets now calculate how much of the transformation has already happened. This is different
        // from the above, since we only start transforming when the view is already quite a bit
        // pushed in.
        View rowIcon = row.getNotificationIcon();
        float notificationIconPosition = viewStart;
        float notificationIconSize = 0.0f;
        int iconTopPadding;
        if (rowIcon != null) {
            iconTopPadding = getIconTopPadding(rowIcon);
            notificationIconSize = rowIcon.getHeight();
        } else {
            iconTopPadding = mIconAppearTopPadding;
        }
        notificationIconPosition += iconTopPadding;
        float shelfIconPosition = getTranslationY() + icon.getTop();
        shelfIconPosition += ((1.0f - icon.getIconScale()) * icon.getHeight()) / 2.0f;
        float transitionDistance = getIntrinsicHeight() * 1.5f;
        float transformationStartPosition = getTranslationY() - transitionDistance;
        float transitionAmount = 0.0f;
        if (viewStart < transformationStartPosition
                || (!mAmbientState.isShadeExpanded()
                        && (row.isPinned() || row.isHeadsUpAnimatingAway()))) {
            // We simply place it on the icon of the notification
            yTranslation = notificationIconPosition - shelfIconPosition;
        } else {
            transitionAmount = (viewStart - transformationStartPosition)
                    / transitionDistance;
            float startPosition = transformationStartPosition + iconTopPadding;
            yTranslation = NotificationUtils.interpolate(
                    startPosition - shelfIconPosition, 0, transitionAmount);
            // If we are merging into the shelf, lets make sure the shelf is at least on our height,
            // otherwise the icons won't be visible.
            setTranslationZ(Math.max(getTranslationZ(), row.getTranslationZ()));
        }
        float shelfIconSize = icon.getHeight() * icon.getIconScale();
        if (!row.isShowingIcon()) {
            // The view currently doesn't have an icon, lets transform it in!
            alpha = transitionAmount;
            notificationIconSize = shelfIconSize / 2.0f;
        }
        // The notification size is different from the size in the shelf / statusbar
        float newSize = NotificationUtils.interpolate(notificationIconSize, shelfIconSize,
                transitionAmount);
        row.setIconTransformationAmount(transitionAmount);
        if (iconState != null) {
            iconState.scaleX = newSize / icon.getHeight() / icon.getIconScale();
            iconState.scaleY = iconState.scaleX;
            iconState.hidden = transitionAmount == 0.0f;
            iconState.iconAppearAmount = iconAppearAmount;
            iconState.alpha = alpha;
            iconState.yTranslation = yTranslation;
            icon.setVisibility(transitionAmount == 0.0f ? INVISIBLE : VISIBLE);
            if (row.isInShelf() && !row.isTransformingIntoShelf()) {
                iconState.iconAppearAmount = 1.0f;
                iconState.alpha = 1.0f;
                iconState.scaleX = 1.0f;
                iconState.scaleY = 1.0f;
                iconState.hidden = false;
            }
        }
        return iconAppearAmount;
    }

    private float getFullyClosedTranslation() {
        return - (getIntrinsicHeight() - mStatusBarHeight) / 2;
    }

    private int getIconTopPadding(View icon) {
        View view = icon;
        int topPadding = 0;
        while (view.getParent() instanceof ViewGroup) {
            topPadding += view.getTop();
            view = (View) view.getParent();
            if (view instanceof ExpandableNotificationRow) {
                return topPadding;
            }
        }
        return topPadding;
    }

    public int getNotificationMergeSize() {
        return getIntrinsicHeight();
    }

    @Override
    public boolean hasNoContentHeight() {
        return true;
    }

    private void setHideBackground(boolean hideBackground) {
        mHideBackground = hideBackground;
        updateBackground();
        updateOutline();
    }

    public boolean hidesBackground() {
        return mHideBackground;
    }

    @Override
    protected boolean needsOutline() {
        return !mHideBackground && super.needsOutline();
    }

    @Override
    protected boolean shouldHideBackground() {
        return super.shouldHideBackground() || mHideBackground;
    }

    private void setOpenedAmount(float openedAmount) {
        mCollapsedIcons.getLocationOnScreen(mTmp);
        int start = mTmp[0];
        if (isLayoutRtl()) {
            start = getWidth() - start - mCollapsedIcons.getWidth();
        }
        int width = (int) NotificationUtils.interpolate(start + mCollapsedIcons.getWidth(),
                mShelfIcons.getWidth(),
                openedAmount);
        mShelfIcons.setActualLayoutWidth(width);
        float padding = NotificationUtils.interpolate(mCollapsedIcons.getPaddingEnd(),
                mShelfIcons.getPaddingEnd(),
                openedAmount);
        mShelfIcons.setActualPaddingEnd(padding);
        float paddingStart = NotificationUtils.interpolate(start,
                mShelfIcons.getPaddingStart(), openedAmount);
        mShelfIcons.setActualPaddingStart(paddingStart);
    }

    public void setMaxLayoutHeight(int maxLayoutHeight) {
        mMaxLayoutHeight = maxLayoutHeight;
    }

    /**
     * @return the index of the notification at which the shelf visually resides
     */
    public int getNotGoneIndex() {
        return mNotGoneIndex;
    }

    private void setHasItemsInStableShelf(boolean hasItemsInStableShelf) {
        mHasItemsInStableShelf = hasItemsInStableShelf;
    }

    /**
     * @return whether the shelf has any icons in it when a potential animation has finished, i.e
     *         if the current state would be applied right now
     */
    public boolean hasItemsInStableShelf() {
        return mHasItemsInStableShelf;
    }

    public void setCollapsedIcons(NotificationIconContainer collapsedIcons) {
        mCollapsedIcons = collapsedIcons;
    }

    private class ShelfState extends ExpandableViewState {
        private float openedAmount;
        private boolean hasItemsInStableShelf;

        @Override
        public void applyToView(View view) {
            super.applyToView(view);
            updateAppearance();
            setOpenedAmount(openedAmount);
            setHasItemsInStableShelf(hasItemsInStableShelf);
        }

        @Override
        public void animateTo(View child, AnimationProperties properties) {
            super.animateTo(child, properties);
            setOpenedAmount(openedAmount);
            updateAppearance();
            setHasItemsInStableShelf(hasItemsInStableShelf);
        }
    }
}

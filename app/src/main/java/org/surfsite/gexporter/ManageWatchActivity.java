package org.surfsite.gexporter;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lists the courses and locations gimporter stored on the watch. Swipe an
 * entry, or long-press to select several and tap delete; the deletion is
 * sent to the watch only once the "Undo" snackbar is gone (Material's undo
 * pattern instead of a confirmation dialog). gimporter must be running on
 * the watch to answer, so opening this screen asks the watch to open it.
 * The gimporter widget owns the tracks it downloaded itself; it is asked as
 * well and answers once the user opens it, which can be much later.
 */
public class ManageWatchActivity extends AppCompatActivity implements WatchLink.Listener {
    private static final long REPLY_TIMEOUT_MS = 20000;

    /** Items removed from the list whose deletion is not yet sent to the watch. */
    private static final class PendingDelete {
        final List<WatchContent.Item> items;
        boolean done = false;

        PendingDelete(List<WatchContent.Item> items) {
            this.items = items;
        }
    }

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mTimeout = this::onTimeout;
    private final List<WatchContent.Item> mItems = new ArrayList<>();
    // the latest reply of app and widget, each owning its own content
    private final Map<String, List<WatchContent.Item>> mByOwner = new LinkedHashMap<>();
    // position in the watch's reply, to put undone items back in place
    private final Map<WatchContent.Item, Integer> mOrder = new IdentityHashMap<>();
    private final Set<WatchContent.Item> mSelected = new HashSet<>();
    private final List<PendingDelete> mPending = new ArrayList<>();
    private boolean mWaiting = false;
    private ActionMode mActionMode;

    private View mRoot;
    private LinearProgressIndicator mProgress;
    private TextView mStatus;
    private MaterialButton mRetry;
    private RecyclerView mList;
    private final ItemAdapter mAdapter = new ItemAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_manage_watch);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mRoot = findViewById(R.id.manage_root);
        mProgress = findViewById(R.id.manage_progress);
        mStatus = findViewById(R.id.manage_status);
        mRetry = findViewById(R.id.manage_retry);
        mList = findViewById(R.id.manage_list);

        mList.setLayoutManager(new LinearLayoutManager(this));
        mList.setAdapter(mAdapter);
        new ItemTouchHelper(new SwipeToDelete()).attachToRecyclerView(mList);
        mRetry.setOnClickListener(v -> requestList());

        WatchLink.addListener(this);
        requestList();
    }

    @Override
    protected void onStop() {
        // Leaving the screen confirms what is still waiting for "Undo".
        sendPendingDeletes();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        WatchLink.removeListener(this);
        mHandler.removeCallbacks(mTimeout);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.manage_watch, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.manage_refresh) {
            requestList();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void requestList() {
        if (!WatchLink.sendToAll(WatchContent.listRequest())) {
            showStatus(R.string.watch_not_connected, true);
            return;
        }
        WatchLink.openApp();
        showWaiting();
    }

    // --- deleting with undo ---

    private void removeWithUndo(List<WatchContent.Item> items) {
        if (items.isEmpty()) {
            return;
        }
        PendingDelete pending = new PendingDelete(items);
        mPending.add(pending);
        mItems.removeAll(items);
        mSelected.removeAll(items);
        notifyListReplaced();
        showContentState();

        String message = items.size() == 1
                ? getString(R.string.deleted_one, items.get(0).name)
                : getString(R.string.deleted_many, items.size());
        Snackbar.make(mRoot, message, Snackbar.LENGTH_LONG)
                .setAction(R.string.undo, v -> undo(pending))
                .addCallback(new Snackbar.Callback() {
                    @Override
                    public void onDismissed(Snackbar bar, int event) {
                        if (event != DISMISS_EVENT_ACTION) {
                            sendDelete(pending);
                        }
                    }
                })
                .show();
    }

    private void undo(PendingDelete pending) {
        if (pending.done) {
            return;
        }
        pending.done = true;
        mPending.remove(pending);
        mItems.addAll(pending.items);
        Collections.sort(mItems, (a, b) -> Integer.compare(order(a), order(b)));
        notifyListReplaced();
        showContentState();
    }

    private int order(WatchContent.Item item) {
        Integer i = mOrder.get(item);
        return i != null ? i : Integer.MAX_VALUE;
    }

    private void sendDelete(PendingDelete pending) {
        if (pending.done) {
            return;
        }
        pending.done = true;
        mPending.remove(pending);
        // app and widget can only delete what they stored themselves
        Map<String, List<WatchContent.Item>> byOwner = new LinkedHashMap<>();
        for (WatchContent.Item item : pending.items) {
            List<WatchContent.Item> list = byOwner.get(item.owner);
            if (list == null) {
                list = new ArrayList<>();
                byOwner.put(item.owner, list);
            }
            list.add(item);
        }
        boolean sent = false;
        for (Map.Entry<String, List<WatchContent.Item>> e : byOwner.entrySet()) {
            sent |= WatchLink.send(e.getKey(), WatchContent.deleteRequest(e.getValue(), System.currentTimeMillis()));
        }
        if (sent) {
            // the watch answers with the updated list
            showWaiting();
        } else {
            showStatus(R.string.watch_not_connected, true);
        }
    }

    private void sendPendingDeletes() {
        for (PendingDelete pending : new ArrayList<>(mPending)) {
            sendDelete(pending);
        }
    }

    /** Swiping an entry left or right deletes it. */
    private class SwipeToDelete extends ItemTouchHelper.SimpleCallback {
        SwipeToDelete() {
            super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        }

        @Override
        public int getSwipeDirs(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
            // no swiping while selecting
            return mActionMode != null ? 0 : super.getSwipeDirs(rv, vh);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                              @NonNull RecyclerView.ViewHolder target) {
            return false;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
            int pos = vh.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                List<WatchContent.Item> one = new ArrayList<>();
                one.add(mItems.get(pos));
                removeWithUndo(one);
            }
        }
    }

    // --- selection mode (long press) ---

    private final ActionMode.Callback mSelectionMode = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.manage_watch_selection, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (item.getItemId() == R.id.manage_delete_selected) {
                List<WatchContent.Item> selected = new ArrayList<>();
                for (WatchContent.Item i : mItems) {
                    if (mSelected.contains(i)) {
                        selected.add(i);
                    }
                }
                mode.finish();
                removeWithUndo(selected);
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mActionMode = null;
            mSelected.clear();
            notifyListReplaced();
        }
    };

    private void toggleSelection(WatchContent.Item item) {
        if (!mSelected.remove(item)) {
            mSelected.add(item);
        }
        if (mSelected.isEmpty()) {
            mActionMode.finish();
            return;
        }
        mActionMode.setTitle(getString(R.string.selected_count, mSelected.size()));
        notifyListReplaced();
    }

    // --- watch replies ---

    @Override
    public void onWatchContent(String owner, List<WatchContent.Item> items) {
        // Replies are merged whenever they come: the widget only answers once
        // the user opens it on the watch, possibly long after the app.
        mWaiting = false;
        mHandler.removeCallbacks(mTimeout);
        mProgress.setVisibility(View.GONE);
        if (mActionMode != null) {
            mActionMode.finish();
        }
        mByOwner.put(owner, items);

        mItems.clear();
        mOrder.clear();
        for (List<WatchContent.Item> list : mByOwner.values()) {
            for (WatchContent.Item item : list) {
                mOrder.put(item, mOrder.size());
                if (!isPendingDelete(item)) {
                    mItems.add(item);
                }
            }
        }
        notifyListReplaced();
        showContentState();
    }

    /** The list, or why it is empty; plus a hint while the widget has not answered. */
    private void showContentState() {
        boolean widgetPending = WatchLink.hasWidget() && !widgetAnswered();
        if (mItems.isEmpty()) {
            showStatus(widgetPending ? R.string.widget_hint : R.string.watch_no_content, false);
            return;
        }
        mList.setVisibility(View.VISIBLE);
        mRetry.setVisibility(View.GONE);
        if (widgetPending) {
            mStatus.setText(R.string.widget_hint);
            mStatus.setVisibility(View.VISIBLE);
        } else {
            mStatus.setVisibility(View.GONE);
        }
    }

    private boolean widgetAnswered() {
        for (String owner : mByOwner.keySet()) {
            if (WatchLink.isWidget(owner)) {
                return true;
            }
        }
        return false;
    }

    /** Removed locally but not yet sent: keep it hidden when a fresh list arrives. */
    private boolean isPendingDelete(WatchContent.Item item) {
        for (PendingDelete pending : mPending) {
            for (WatchContent.Item p : pending.items) {
                if (p.sameAs(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Every reply replaces the whole list, so there is no finer change event.
    @SuppressLint("NotifyDataSetChanged")
    private void notifyListReplaced() {
        mAdapter.notifyDataSetChanged();
    }

    private void onTimeout() {
        if (mWaiting) {
            mWaiting = false;
            showStatus(R.string.watch_no_answer, true);
        }
    }

    private void showWaiting() {
        mWaiting = true;
        mHandler.removeCallbacks(mTimeout);
        mHandler.postDelayed(mTimeout, REPLY_TIMEOUT_MS);
        mProgress.setVisibility(View.VISIBLE);
        mStatus.setText(R.string.waiting_for_watch);
        mStatus.setVisibility(mItems.isEmpty() ? View.VISIBLE : View.GONE);
        mRetry.setVisibility(View.GONE);
    }

    private void showStatus(int text, boolean retry) {
        mProgress.setVisibility(View.GONE);
        mStatus.setText(text);
        mStatus.setVisibility(View.VISIBLE);
        mRetry.setVisibility(retry ? View.VISIBLE : View.GONE);
        if (retry || mItems.isEmpty()) {
            mList.setVisibility(View.GONE);
        }
    }

    private class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.Holder> {
        class Holder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView name;
            final TextView kind;
            final MaterialCheckBox check;

            Holder(View v) {
                super(v);
                icon = v.findViewById(R.id.item_icon);
                name = v.findViewById(R.id.item_name);
                kind = v.findViewById(R.id.item_kind);
                check = v.findViewById(R.id.item_check);
                v.setOnClickListener(x -> {
                    WatchContent.Item item = itemAt();
                    if (item != null && mActionMode != null) {
                        toggleSelection(item);
                    }
                });
                v.setOnLongClickListener(x -> {
                    WatchContent.Item item = itemAt();
                    if (item == null) {
                        return false;
                    }
                    if (mActionMode == null) {
                        mActionMode = startSupportActionMode(mSelectionMode);
                    }
                    toggleSelection(item);
                    return true;
                });
            }

            WatchContent.Item itemAt() {
                int pos = getBindingAdapterPosition();
                return pos == RecyclerView.NO_POSITION ? null : mItems.get(pos);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_watch_content, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            WatchContent.Item item = mItems.get(position);
            h.icon.setImageResource(item.isLocation() ? R.drawable.ic_location : R.drawable.ic_track);
            h.name.setText(item.name);
            String kind = getString(item.isLocation() ? R.string.kind_location : R.string.kind_track);
            // the widget's own downloads, deleted through the widget
            h.kind.setText(WatchLink.isWidget(item.owner) ? kind + " · Widget" : kind);
            // checkboxes only in selection mode, as in other Android apps
            h.check.setVisibility(mActionMode != null ? View.VISIBLE : View.GONE);
            h.check.setChecked(mSelected.contains(item));
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }
    }
}

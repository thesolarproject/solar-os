package com.solar.launcher;

import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListAdapter;

/**
 * 2026-07-05: Wraps any ListAdapter and forces AbsListView.LayoutParams on every getView row.
 * Reversal: delete wrapper when all adapters emit correct params natively.
 */
final class AbsListViewRowParamsWrapper implements ListAdapter, Filterable {

    private final ListAdapter delegate;

    AbsListViewRowParamsWrapper(ListAdapter delegate) {
        this.delegate = delegate;
    }

    ListAdapter delegate() {
        return delegate;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = delegate.getView(position, convertView, parent);
        ListViewRowParams.ensure(row);
        return row;
    }

    @Override
    public int getCount() {
        return delegate.getCount();
    }

    @Override
    public Object getItem(int position) {
        return delegate.getItem(position);
    }

    @Override
    public long getItemId(int position) {
        return delegate.getItemId(position);
    }

    @Override
    public int getItemViewType(int position) {
        return delegate.getItemViewType(position);
    }

    @Override
    public int getViewTypeCount() {
        return delegate.getViewTypeCount();
    }

    @Override
    public boolean hasStableIds() {
        return delegate.hasStableIds();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean areAllItemsEnabled() {
        return delegate.areAllItemsEnabled();
    }

    @Override
    public boolean isEnabled(int position) {
        return delegate.isEnabled(position);
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        delegate.registerDataSetObserver(observer);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        delegate.unregisterDataSetObserver(observer);
    }

    @Override
    public Filter getFilter() {
        if (delegate instanceof Filterable) {
            return ((Filterable) delegate).getFilter();
        }
        return null;
    }
}

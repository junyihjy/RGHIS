package sg.rghis.android.views.adapters;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.turingtechnologies.materialscrollbar.INameableAdapter;

import butterknife.Bind;
import butterknife.ButterKnife;
import sg.rghis.android.R;
import sg.rghis.android.models.EmergencyTopic;

public class EmergencyTopicAdapter extends RealmRecyclerViewAdapter<EmergencyTopic, EmergencyTopicAdapter.SimpleViewHolder>
        implements INameableAdapter {

    private final Context context;

    public static class SimpleViewHolder extends RecyclerView.ViewHolder {
        @Bind(R.id.title)
        TextView title;

        public SimpleViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }
    }

    public EmergencyTopicAdapter(Context context) {
        this.context = context;
    }

    @Override
    public SimpleViewHolder onCreateViewHolder(ViewGroup parent, int i) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.emergency_info_item, parent, false);
        return new SimpleViewHolder(v);
    }

    @Override
    public void onBindViewHolder(SimpleViewHolder simpleViewHolder, int i) {
        EmergencyTopic emergencyTopic = getItem(i);
        simpleViewHolder.title.setText(emergencyTopic.getTitle());
    }

    /* The inner RealmBaseAdapter
     * view count is applied here.
     *
     * getRealmAdapter is defined in RealmRecyclerViewAdapter.
     */
    @Override
    public int getItemCount() {
        if (getRealmAdapter() != null) {
            return getRealmAdapter().getCount();
        }
        return 0;
    }

    @Override
    public Character getCharacterForElement(int i) {
        return getItem(i).getTitle().charAt(0);
    }
}
package sg.rghis.android.fragments;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TextInputLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.mobsandgeeks.saripaar.ValidationError;
import com.mobsandgeeks.saripaar.Validator;
import com.mobsandgeeks.saripaar.annotation.NotEmpty;
import com.parse.ParseException;
import com.parse.ParsePush;
import com.parse.SendCallback;

import java.util.List;

import javax.inject.Inject;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import sg.rghis.android.R;
import sg.rghis.android.disqus.adapters.PostsAdapter;
import sg.rghis.android.disqus.models.PaginatedList;
import sg.rghis.android.disqus.models.Post;
import sg.rghis.android.disqus.models.ResponseItem;
import sg.rghis.android.disqus.models.Thread;
import sg.rghis.android.disqus.services.PostsService;
import sg.rghis.android.disqus.services.ThreadsService;
import sg.rghis.android.utils.DisqusUtils;
import sg.rghis.android.utils.SystemUtils;
import sg.rghis.android.utils.UserManager;
import sg.rghis.android.views.RecyclerItemClickListener;
import sg.rghis.android.views.widgets.DividerItemDecoration;
import timber.log.Timber;

public class PostListFragment extends BaseDisqusFragment implements Validator.ValidationListener {
    public static final String ARG_THREAD = "thread";
    public static final String PREFIX_ADAPTER = ".PostsFragment.MyAdapter";

    @Inject
    ThreadsService threadsService;

    @Inject
    PostsService postsService;

    @Bind(R.id.recycler_view)
    RecyclerView recyclerView;
    @Bind(R.id.fab)
    FloatingActionButton floatingActionButton;

    private Subscription subscription;
    private Subscription createThreadSubscription;
    private PostsAdapter postsAdapter;
    private Thread thread;
    private MaterialDialog dialogInstance = null;
    private final DialogViewHolder dialogViewHolder = new DialogViewHolder();
    private Validator validator;

    static class DialogViewHolder {

        @NotEmpty
        @Nullable
        @Bind(R.id.message_edit_text)
        EditText messageEditText;

        @Nullable
        @Bind(R.id.message_text_input_layout)
        TextInputLayout messageTextInputLayout;
    }

    public static PostListFragment newInstance(Thread thread) {
        Bundle args = new Bundle();
        args.putParcelable(ARG_THREAD, thread);

        PostListFragment fragment = new PostListFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle bundle = getArguments();
        if (bundle == null || !bundle.containsKey(ARG_THREAD)) {
            throw new IllegalStateException("Category ID must be provided.");
        } else {
            thread = bundle.getParcelable(ARG_THREAD);
        }

        postsAdapter = new PostsAdapter(thread);

        if (savedInstanceState != null) {
            postsAdapter.onRestoreInstanceState(PREFIX_ADAPTER, savedInstanceState);
        } else {
            Observable<PaginatedList<Post>> observable =
                    threadsService.listPosts(thread.id);
            subscription = observable
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(this.<PaginatedList<Post>>bindToLifecycle())
                    .subscribe(new GetPostsObserver());
        }

    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.post_list_fragment, container, false);
        ButterKnife.bind(this, view);
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        recyclerView.setHasFixedSize(false);
        LinearLayoutManager manager = new LinearLayoutManager(getContext(),
                LinearLayoutManager.VERTICAL, false);
//        manager.setStackFromEnd(true);
        recyclerView.setLayoutManager(manager);
        recyclerView.addItemDecoration(new DividerItemDecoration(getContext()));
        recyclerView.setAdapter(postsAdapter);
        recyclerView.addOnItemTouchListener(new RecyclerItemClickListener(getActivity(),
                new RecyclerItemClickListener.OnItemClickListener() {
                    @Override
                    public void onItemClick(View view, int position) {

                    }
                }));

    }

    @OnClick(R.id.fab)
    public void showCreateThreadDialog() {
        if (!SystemUtils.promptLoginIfNecessary()) {
            if (dialogInstance != null)
                dialogInstance.cancel();

            MaterialDialog.Builder dialogBuilder = getDialogBuilder();

            dialogInstance = dialogBuilder.build();
            bindDialogViews(dialogInstance, dialogViewHolder);

            dialogInstance.show();
        }
    }


    private MaterialDialog.Builder getDialogBuilder() {

        boolean wrapInScrollView = true;
        MaterialDialog.Builder dialogBuilder = new MaterialDialog.Builder(getActivity())
                .backgroundColorRes(R.color.white)
                .title("Reply")
                .customView(R.layout.dialog_create_post, wrapInScrollView)
                .positiveText("OK")
                .negativeText("Cancel")
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        String message = dialogViewHolder.messageEditText.getText().toString();

                        Observable<ResponseItem<Post>> observable =
                                postsService.create(thread.id, message);
                        createThreadSubscription = observable
                                .observeOn(AndroidSchedulers.mainThread())
                                .compose(PostListFragment.this.<ResponseItem<Post>>bindToLifecycle())
                                .subscribe(new CreatePostObserver());

                    }

                    @Override
                    public void onNegative(MaterialDialog dialog) {
                        super.onNegative(dialog);
                    }
                })
                .cancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        dialogInstance = null;
                    }
                })
                .dismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        dialogInstance = null;
                    }
                });

        return dialogBuilder;
    }

    private void bindDialogViews(MaterialDialog dialog, final DialogViewHolder dialogViewHolder) {
        dialog.getActionButton(DialogAction.POSITIVE).setEnabled(false);

        ButterKnife.bind(dialogViewHolder, dialog);

        validator = new Validator(dialogViewHolder);
        validator.setValidationListener(this);

        if (dialogViewHolder.messageEditText == null) {
            return;
        }

        dialogViewHolder.messageEditText.setTag(dialogViewHolder.messageTextInputLayout);

        dialogViewHolder.messageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                dialogViewHolder.messageTextInputLayout.setErrorEnabled(false);
                validator.validate();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    @Override
    public void onValidationSucceeded() {
        if (dialogInstance != null) {
            View postiveButton = dialogInstance.getActionButton(DialogAction.POSITIVE);
            if (postiveButton != null) {
                postiveButton.setEnabled(true);
            }
        }
    }

    @Override
    public void onValidationFailed(List<ValidationError> errors) {
        if (dialogInstance != null) {
            View postiveButton = dialogInstance.getActionButton(DialogAction.POSITIVE);
            if (postiveButton != null) {
                postiveButton.setEnabled(false);
            }
        }
    }

    private class GetPostsObserver implements Observer<PaginatedList<Post>> {
        @Override
        public void onCompleted() {
        }

        @Override
        public void onError(Throwable e) {
            Timber.e(Log.getStackTraceString(e));
        }

        @Override
        public void onNext(PaginatedList<Post> postPaginatedList) {
            postsAdapter.addList(postPaginatedList);
            postsAdapter.notifyDataSetChanged();
        }
    }

    private class CreatePostObserver implements Observer<ResponseItem<Post>> {
        @Override
        public void onCompleted() {
        }

        @Override
        public void onError(Throwable e) {
            Timber.e(Log.getStackTraceString(e));
        }

        @Override
        public void onNext(ResponseItem<Post> postResponseItem) {
            postsAdapter.addItem(postResponseItem.getResponse());
            postsAdapter.notifyDataSetChanged();
            recyclerView.smoothScrollToPosition(1);
            String username = UserManager.getCurrentUser().getUsername();
            String authorName = DisqusUtils.disqusNameToParse(postResponseItem
                    .getResponse().getAuthor().getName()).first;
            if (!username.equals(authorName)) {
                String message = String.format("@%s just replied to a thread you subscribed to.",
                        authorName);
                ParsePush push = new ParsePush();
                push.setChannel(DisqusUtils.getChannelName(thread.id));
                push.setMessage(message);
                push.sendInBackground(new SendCallback() {
                    @Override
                    public void done(ParseException e) {
                        if (e != null)
                            Timber.d(Log.getStackTraceString(e));
                    }
                });
            }
        }
    }

    private String ellipsize(String text) {
        if (text.length() > 20) {
            return text.substring(0, 20) + "...";
        }
        return text;
    }
}

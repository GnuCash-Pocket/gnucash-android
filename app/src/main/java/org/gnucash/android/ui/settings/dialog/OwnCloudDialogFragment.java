package org.gnucash.android.ui.settings.dialog;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;

import com.owncloud.android.lib.common.OwnCloudClient;
import com.owncloud.android.lib.common.OwnCloudClientFactory;
import com.owncloud.android.lib.common.OwnCloudCredentialsFactory;
import com.owncloud.android.lib.common.operations.OnRemoteOperationListener;
import com.owncloud.android.lib.common.operations.RemoteOperation;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.resources.files.FileUtils;
import com.owncloud.android.lib.resources.status.GetRemoteStatusOperation;
import com.owncloud.android.lib.resources.users.GetRemoteUserInfoOperation;

import org.gnucash.android.R;
import org.gnucash.android.databinding.DialogOwncloudAccountBinding;
import org.gnucash.android.ui.settings.OwnCloudPreferences;
import org.gnucash.android.ui.util.dialog.VolatileDialogFragment;

import timber.log.Timber;

/**
 * A fragment for adding an ownCloud account.
 */
public class OwnCloudDialogFragment extends VolatileDialogFragment {

    /**
     * ownCloud vars
     */
    private String mOC_server;
    private String mOC_username;
    private String mOC_password;
    private String mOC_dir;

    private EditText mServer;
    private EditText mUsername;
    private EditText mPassword;
    private EditText mDir;

    private TextView mServerError;
    private TextView mUsernameError;
    private TextView mDirError;

    private OwnCloudPreferences preferences;

    private TwoStatePreference ocCheckBox;
    private final Handler handler = new Handler();

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment OwnCloudDialogFragment.
     */
    public static OwnCloudDialogFragment newInstance(@Nullable Preference pref) {
        OwnCloudDialogFragment fragment = new OwnCloudDialogFragment();
        fragment.ocCheckBox = (TwoStatePreference) pref;
        return fragment;
    }

    public OwnCloudDialogFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Context context = requireContext();
        preferences = new OwnCloudPreferences(context);

        mOC_server = preferences.getServer();
        mOC_username = preferences.getUsername();
        mOC_password = preferences.getPassword();
        mOC_dir = preferences.getDir();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        DialogOwncloudAccountBinding binding = DialogOwncloudAccountBinding.inflate(getLayoutInflater());
        Context context = binding.getRoot().getContext();

        mServer = binding.owncloudHostname;
        mUsername = binding.owncloudUsername;
        mPassword = binding.owncloudPassword;
        mDir = binding.owncloudDir;

        mServer.setText(mOC_server);
        mDir.setText(mOC_dir);
        mPassword.setText(mOC_password); // TODO: Remove - debugging only
        mUsername.setText(mOC_username);

        mServerError = binding.owncloudHostnameInvalid;
        mUsernameError = binding.owncloudUsernameInvalid;
        mDirError = binding.owncloudDirInvalid;
        mServerError.setVisibility(View.GONE);
        mUsernameError.setVisibility(View.GONE);
        mDirError.setVisibility(View.GONE);

        final AlertDialog dialog = new AlertDialog.Builder(context, getTheme())
            .setTitle("ownCloud")
            .setView(binding.getRoot())
            .setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // Dismisses itself.
                }
            })
            .setNeutralButton(R.string.btn_test, null)
            .setPositiveButton(R.string.btn_save, null)
            .create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        checkData();
                    }
                });
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (TextUtils.equals(mOC_server, mServer.getText()) &&
                            TextUtils.equals(mOC_username, mUsername.getText()) &&
                            TextUtils.equals(mOC_password, mPassword.getText()) &&
                            TextUtils.equals(mOC_dir, mDir.getText()) &&
                            TextUtils.equals(mDirError.getText(), context.getString(R.string.owncloud_dir_ok)) &&
                            TextUtils.equals(mUsernameError.getText(), context.getString(R.string.owncloud_user_ok)) &&
                            TextUtils.equals(mServerError.getText(), context.getString(R.string.owncloud_server_ok))
                        ) {
                            save();
                            dismiss();
                        }
                    }
                });
            }
        });

        return dialog;
    }

    private void save() {
        preferences.setServer(mOC_server);
        preferences.setUsername(mOC_username);
        preferences.setPassword(mOC_password);
        preferences.setDir(mOC_dir);
        preferences.setSync(true);

        if (ocCheckBox != null) ocCheckBox.setChecked(true);
    }

    private void checkData() {
        final Context context = mServer.getContext();
        mServerError.setVisibility(View.GONE);
        mUsernameError.setVisibility(View.GONE);
        mDirError.setVisibility(View.GONE);

        mOC_server = mServer.getText().toString().trim();
        mOC_username = mUsername.getText().toString().trim();
        mOC_password = mPassword.getText().toString().trim();
        mOC_dir = mDir.getText().toString().trim();

        if (FileUtils.isValidPath(mOC_dir, false)) {
            mDirError.setTextColor(ContextCompat.getColor(context, R.color.account_green));
            mDirError.setText(R.string.owncloud_dir_ok);
            mDirError.setVisibility(View.VISIBLE);
        } else {
            mDirError.setTextColor(ContextCompat.getColor(context, R.color.design_default_color_error));
            mDirError.setText(R.string.owncloud_dir_invalid);
            mDirError.setVisibility(View.VISIBLE);
        }

        Uri serverUri = Uri.parse(mOC_server);
        OwnCloudClient client = OwnCloudClientFactory.createOwnCloudClient(serverUri, context, true);
        client.setCredentials(
            OwnCloudCredentialsFactory.newBasicCredentials(mOC_username, mOC_password)
        );

        OnRemoteOperationListener listener = new OnRemoteOperationListener() {
            @Override
            public void onRemoteOperationFinish(RemoteOperation caller, RemoteOperationResult result) {
                if (result.isSuccess()) {
                    if (caller instanceof GetRemoteStatusOperation) {
                        mServerError.setTextColor(ContextCompat.getColor(context, R.color.account_green));
                        mServerError.setText(R.string.owncloud_server_ok);
                        mServerError.setVisibility(View.VISIBLE);
                    } else if (caller instanceof GetRemoteUserInfoOperation) {
                        mUsernameError.setTextColor(ContextCompat.getColor(context, R.color.account_green));
                        mUsernameError.setText(R.string.owncloud_user_ok);
                        mUsernameError.setVisibility(View.VISIBLE);
                    }
                } else {
                    Timber.e(result.getException(), result.getLogMessage());

                    if (caller instanceof GetRemoteStatusOperation) {
                        mServerError.setTextColor(ContextCompat.getColor(context, R.color.design_default_color_error));
                        mServerError.setText(R.string.owncloud_server_invalid);
                        mServerError.setVisibility(View.VISIBLE);
                    } else if (caller instanceof GetRemoteUserInfoOperation) {
                        mUsernameError.setTextColor(ContextCompat.getColor(context, R.color.design_default_color_error));
                        mUsernameError.setText(R.string.owncloud_user_invalid);
                        mUsernameError.setVisibility(View.VISIBLE);
                    }
                }
            }
        };

        GetRemoteStatusOperation statusOperation = new GetRemoteStatusOperation(context);
        statusOperation.execute(client, listener, handler);

        GetRemoteUserInfoOperation userInfoOperation = new GetRemoteUserInfoOperation();
        userInfoOperation.execute(client, listener, handler);
    }
}

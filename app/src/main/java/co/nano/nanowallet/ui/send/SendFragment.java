package co.nano.nanowallet.ui.send;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.databinding.BindingAdapter;
import android.databinding.DataBindingUtil;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.Guideline;
import android.support.v4.content.ContextCompat;
import android.text.InputType;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;

import com.hwangjr.rxbus.annotation.Subscribe;

import java.math.BigInteger;

import javax.inject.Inject;

import co.nano.nanowallet.R;
import co.nano.nanowallet.bus.RxBus;
import co.nano.nanowallet.bus.SendInvalidAmount;
import co.nano.nanowallet.databinding.FragmentSendBinding;
import co.nano.nanowallet.model.Address;
import co.nano.nanowallet.model.NanoWallet;
import co.nano.nanowallet.network.AccountService;
import co.nano.nanowallet.network.model.response.ErrorResponse;
import co.nano.nanowallet.network.model.response.WorkResponse;
import co.nano.nanowallet.ui.common.ActivityWithComponent;
import co.nano.nanowallet.ui.common.BaseFragment;
import co.nano.nanowallet.ui.common.UIUtil;
import co.nano.nanowallet.ui.scan.ScanActivity;
import co.nano.nanowallet.util.NumberUtil;

import static android.app.Activity.RESULT_OK;

/**
 * Send Screen
 */
public class SendFragment extends BaseFragment {
    private FragmentSendBinding binding;
    public static String TAG = SendFragment.class.getSimpleName();
    private boolean localCurrencyActive = false;
    private String work = null;

    @Inject
    NanoWallet wallet;

    @Inject
    AccountService accountService;

    @BindingAdapter("layout_constraintGuide_percent")
    public static void setLayoutConstraintGuidePercent(Guideline guideline, float percent) {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) guideline.getLayoutParams();
        params.guidePercent = percent;
        guideline.setLayoutParams(params);
    }

    /**
     * Create new instance of the fragment (handy pattern if any data needs to be passed to it)
     *
     * @return
     */
    public static SendFragment newInstance() {
        Bundle args = new Bundle();
        SendFragment fragment = new SendFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_send, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.send_camera:
                startScanActivity(getString(R.string.scan_send_instruction_label), false);
                return true;
        }

        return false;
    }

    public void hideSoftKeyboard() {
        //Hides the SoftKeyboard
        InputMethodManager inputMethodManager = (InputMethodManager) getActivity().getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(getActivity().getCurrentFocus().getWindowToken(), 0);
        }
        binding.setShowAmount(true);
    }

    public void setupUI(View view) {
        String s = "inside";
        //Set up touch listener for non-text box views to hide keyboard.
        if (!(view instanceof EditText && view.getId() == R.id.send_address)) {
            view.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent event) {
                    hideSoftKeyboard();
                    return false;
                }
            });
        } else {
            view.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent event) {
                    binding.setShowAmount(false);
                    return false;
                }
            });
        }


        //If a layout container, iterate over children and seed recursion.
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                View innerView = ((ViewGroup) view).getChildAt(i);
                setupUI(innerView);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // init dependency injection
        if (getActivity() instanceof ActivityWithComponent) {
            ((ActivityWithComponent) getActivity()).getActivityComponent().inject(this);
        }

        // subscribe to bus
        RxBus.get().register(this);

        // change keyboard mode
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        // inflate the view
        binding = DataBindingUtil.inflate(
                inflater, R.layout.fragment_send, container, false);
        View view = binding.getRoot();
        binding.setHandlers(new ClickHandlers());

        setStatusBarBlue();
        setBackEnabled(true);
        setTitle(getString(R.string.send_title));
        setTitleDrawable(R.drawable.ic_send);

        // hide keyboard for edittext fields
        binding.sendAmountNano.setInputType(InputType.TYPE_NULL);
        binding.sendAmountLocalcurrency.setInputType(InputType.TYPE_NULL);

        // set active and inactive states for edittext fields
        binding.sendAmountNano.setOnFocusChangeListener((view1, b) -> toggleFieldFocus((EditText) view1, b, false));
        binding.sendAmountLocalcurrency.setOnFocusChangeListener((view1, b) -> toggleFieldFocus((EditText) view1, b, true));
        binding.sendAmountLocalcurrencySymbol.setText(wallet.getLocalCurrency().getCurrencySymbol());
        binding.setShowAmount(true);

        binding.sendAddress.setOnFocusChangeListener((view12, hasFocus) -> {
            binding.setShowAmount(!hasFocus);
        });

        setupUI(view);

        // make a work request
        accountService.requestWorkSend(wallet.getFrontierBlock());

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // unregister from bus
        RxBus.get().unregister(this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check to make sure we are responding to camera result
        if (requestCode == SCAN_RESULT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                Bundle res = data.getExtras();
                if (res != null) {
                    // set to scanned value
                    binding.sendAddress.setText(res.getString(ScanActivity.QR_CODE_RESULT));
                }
            }
        }
    }

    /**
     * Event that occurs if an amount entered is invalid
     *
     * @param sendInvalidAmount
     */
    @Subscribe
    public void receiveInvalidAmount(SendInvalidAmount sendInvalidAmount) {
        // reset amount to max in wallet
        wallet.setSendNanoAmount(wallet.getLongerAccountBalanceNano());
        binding.setWallet(wallet);

        // show alert with a message to the user letting them know the amount they entered
        showError(R.string.send_amount_too_large_alert_title, R.string.send_amount_too_large_alert_message);
    }

    /**
     * Catch errors from the service
     *
     * @param errorResponse
     */
    @Subscribe
    public void receiveServiceError(ErrorResponse errorResponse) {
        // show alert with a message to the user letting them know the amount they entered
        showError(R.string.send_error_alert_title, errorResponse.getError());
    }

    @Subscribe
    public void receiveWorkResponse(WorkResponse workResponse) {
        work = workResponse.getWork();
    }

    private boolean validateRequest() {
        // check for valid address
        Address destination = new Address(binding.sendAddress.getText().toString());
        if (!destination.isValidAddress()) {
            showError(R.string.send_error_alert_title, R.string.send_error_alert_message);
            return false;
        }

        // check that amount being sent is less than or equal to account balance
        if (wallet.getSendNanoAmount().isEmpty()) {
            return false;
        }
        BigInteger balance = NumberUtil.getAmountAsRawBigInteger(wallet.getSendNanoAmount());
        if (balance.compareTo(wallet.getAccountBalanceNanoRaw().toBigInteger()) > 0) {
            showError(R.string.send_error_alert_title, R.string.send_error_alert_message);
            return false;
        }

        // check that we have a frontier block
        if (wallet.getFrontierBlock() == null) {
            showError(R.string.send_error_alert_title, R.string.send_error_alert_message);
            return false;
        }

        if (work == null) {
            showError(R.string.send_error_alert_title, R.string.send_error_alert_message);
            return false;
        }

        return true;
    }

    private void showError(int title, int message) {
        AlertDialog.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder = new AlertDialog.Builder(getContext(), android.R.style.Theme_Material_Light_Dialog_Alert);
        } else {
            builder = new AlertDialog.Builder(getContext());
        }
        builder.setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.send_amount_too_large_alert_cta, (dialog, which) -> {
                })
                .show();
    }

    private void showError(int title, String message) {
        AlertDialog.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder = new AlertDialog.Builder(getContext(), android.R.style.Theme_Material_Light_Dialog_Alert);
        } else {
            builder = new AlertDialog.Builder(getContext());
        }
        builder.setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.send_amount_too_large_alert_cta, (dialog, which) -> {
                })
                .show();
    }

    /**
     * Helper to set focus size and color on fields
     *
     * @param v
     * @param hasFocus
     * @param isLocalCurrency
     */
    private void toggleFieldFocus(EditText v, boolean hasFocus, boolean isLocalCurrency) {
        localCurrencyActive = isLocalCurrency;

        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, hasFocus ? 20f : 16f);
        binding.sendAmountNanoSymbol.setTextSize(TypedValue.COMPLEX_UNIT_SP, hasFocus && isLocalCurrency ? 16f : 14f);
        binding.sendAmountNanoSymbol.setAlpha(hasFocus && !isLocalCurrency ? 1.0f : 0.5f);
        binding.sendAmountLocalcurrencySymbol.setTextSize(TypedValue.COMPLEX_UNIT_SP, hasFocus && isLocalCurrency ? 16f : 14f);
        binding.sendAmountLocalcurrencySymbol.setTextColor(hasFocus && isLocalCurrency ?
                ContextCompat.getColor(getContext(), R.color.bright_white) : ContextCompat.getColor(getContext(), R.color.semitranslucent_white));

        // clear amounts
        wallet.clearSendAmounts();
        binding.setWallet(wallet);
    }

    /**
     * Update amount strings based on input processed
     *
     * @param value String value of character pressed
     */
    private void updateAmount(CharSequence value) {
        if (value.equals(getString(R.string.send_keyboard_delete))) {
            // delete last character
            if (localCurrencyActive) {
                if (wallet.getLocalCurrencyAmount().length() > 0) {
                    wallet.setLocalCurrencyAmount(wallet.getLocalCurrencyAmount().substring(0, wallet.getLocalCurrencyAmount().length() - 1));
                }
            } else {
                if (wallet.getSendNanoAmount().length() > 0) {
                    wallet.setSendNanoAmount(wallet.getSendNanoAmount().substring(0, wallet.getSendNanoAmount().length() - 1));
                }
            }
        } else if (value.equals(getString(R.string.send_keyboard_decimal))) {
            // decimal point
            if (localCurrencyActive) {
                if (!wallet.getLocalCurrencyAmount().contains(value)) {
                    wallet.setLocalCurrencyAmount(wallet.getLocalCurrencyAmount() + value);
                }
            } else {
                if (!wallet.getSendNanoAmount().contains(value)) {
                    wallet.setSendNanoAmount(wallet.getSendNanoAmount() + value);
                }
            }
        } else {
            // digits
            if (localCurrencyActive) {
                wallet.setLocalCurrencyAmount(wallet.getLocalCurrencyAmount() + value);
            } else {
                wallet.setSendNanoAmount(wallet.getSendNanoAmount() + value);
            }
        }
        binding.setWallet(wallet);
    }

    public class ClickHandlers {
        /**
         * Listener for styling updates when text changes
         *
         * @param s
         * @param start
         * @param before
         * @param count
         */
        public void onAddressTextChanged(CharSequence s, int start, int before, int count) {
            // set background to active or not
            binding.sendAddress.setBackgroundResource(s.length() > 0 ? R.drawable.bg_seed_input_active : R.drawable.bg_seed_input);

            // colorize input string
            UIUtil.colorizeSpannable(binding.sendAddress.getText(), getContext());
        }

        public void onClickSend(View view) {
            if (!validateRequest()) {
                return;
            }
            Address destination = new Address(binding.sendAddress.getText().toString());
            BigInteger sendAmount = NumberUtil.getAmountAsRawBigInteger(wallet.getSendNanoAmount());
            BigInteger balance = wallet.getAccountBalanceNanoRaw().toBigInteger().subtract(sendAmount);

            accountService.requestSend(wallet.getFrontierBlock(), destination, balance, work);
        }

        public void onClickMax(View view) {
            wallet.setSendNanoAmount(wallet.getLongerAccountBalanceNano());
            binding.setWallet(wallet);
        }

        public void onClickNumKeyboard(View view) {
            updateAmount(((Button) view).getText());
        }

    }
}

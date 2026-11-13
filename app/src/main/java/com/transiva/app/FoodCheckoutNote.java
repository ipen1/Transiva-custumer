package com.transiva.app;

import android.app.Activity;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONException;
import org.json.JSONObject;

final class FoodCheckoutNote extends LinearLayout {
    interface OnSaved {
        void save(String note);
    }

    private static final int MAX_LENGTH = 300;
    private static final String EXAMPLE = "Misalnya, pisahkan saus atau hubungi saat tiba.";

    private final Button edit;
    private final boolean compact;
    private final TextView preview;
    private String value;

    FoodCheckoutNote(Activity activity, String value, OnSaved onSaved) {
        this(activity, value, false, onSaved);
    }

    FoodCheckoutNote(Activity activity, String value, boolean compact, OnSaved onSaved) {
        super(activity);
        this.value = value;
        this.compact = compact;
        setOrientation(VERTICAL);
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        if (compact) {
            android.widget.ImageView icon = new android.widget.ImageView(activity);
            icon.setImageResource(R.drawable.ic_checkout_note);
            icon.setColorFilter(0xff60758e);
            icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            LayoutParams iconParams = new LayoutParams(dp(20), dp(20));
            iconParams.setMarginEnd(dp(9));
            header.addView(icon, iconParams);
        }
        header.addView(RepeatOrderUi.text(activity, compact ? "Catatan pengantaran" : "Catatan pesanan (opsional)",
                13, "#0F172A", !compact), new LayoutParams(0, -2, 1));
        edit = compact ? inlineButton(activity) : RepeatOrderUi.outline(activity, "", 12);
        edit.setMinWidth(0);
        edit.setMinimumWidth(0);
        header.addView(edit, new LayoutParams(-2, -2));
        addView(header);

        preview = RepeatOrderUi.text(activity, "", 13, "#475569", false);
        preview.setMaxLines(3);
        preview.setEllipsize(TextUtils.TruncateAt.END);
        preview.setPadding(0, dp(4), 0, dp(4));
        addView(preview, new LayoutParams(-1, -2));
        updatePreview();

        edit.setOnClickListener(v -> showEditor(activity, compact ? "Catatan pengantaran" : "Catatan pesanan",
                compact ? "Misalnya, hubungi saat tiba." : EXAMPLE, this.value, note -> {
            this.value = note;
            onSaved.save(note);
            updatePreview();
        }));
    }

    static Button itemButton(Activity activity, String menuName, String note, OnSaved onSaved) {
        Button button = RepeatOrderUi.outline(activity, "", 10);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(CustomerUiPrimitives.dp(activity, 6), 0, CustomerUiPrimitives.dp(activity, 6), 0);
        updateItemButton(button, menuName, note);
        button.setTag(note);
        button.setOnClickListener(v -> showEditor(activity, "Catatan · " + menuName,
                "Misalnya, tanpa es atau saus dipisah.", (String) button.getTag(), value -> {
                    onSaved.save(value);
                    button.setTag(value);
                    updateItemButton(button, menuName, value);
                }));
        return button;
    }

    static Button inlineItemButton(Activity activity, String menuName, String note, OnSaved onSaved) {
        Button button = inlineButton(activity);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        int inset = CustomerUiPrimitives.dp(activity, 2);
        button.setPadding(0, inset, inset, inset);
        button.setMaxLines(3);
        button.setEllipsize(TextUtils.TruncateAt.END);
        android.graphics.drawable.Drawable pencil = activity.getDrawable(R.drawable.ic_checkout_edit);
        int size = CustomerUiPrimitives.dp(activity, 14);
        pencil.setBounds(0, 0, size, size);
        button.setCompoundDrawablesRelative(pencil, null, null, null);
        button.setCompoundDrawablePadding(CustomerUiPrimitives.dp(activity, 5));
        button.setText(note.isEmpty() ? "Tambah catatan" : note);
        button.setContentDescription(note.isEmpty() ? "Tambah catatan untuk " + menuName
                : "Ubah catatan untuk " + menuName + ". " + note);
        button.setOnClickListener(v -> showEditor(activity, "Catatan · " + menuName,
                "Misalnya, tanpa es atau saus dipisah.", note, onSaved));
        return button;
    }

    private static Button inlineButton(Activity activity) {
        Button button = new Button(activity);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(0xff0967db);
        button.setStateListAnimator(null);
        int touch = CustomerUiPrimitives.dp(activity, 44);
        button.setMinimumHeight(touch);
        button.setMinHeight(touch);
        button.setMinimumWidth(touch);
        button.setMinWidth(touch);
        int padding = CustomerUiPrimitives.dp(activity, 5);
        button.setPadding(padding, 0, padding, 0);
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x22096bd9), null,
                new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)));
        return button;
    }

    private static void updateItemButton(Button button, String menuName, String note) {
        String action = note.isEmpty() ? "Tambah catatan" : "Ubah catatan";
        button.setText(action);
        button.setContentDescription(action + " untuk " + menuName);
    }

    private static void showEditor(Activity activity, String title, String hint, String note, OnSaved onSaved) {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(VERTICAL);
        int side = CustomerUiPrimitives.dp(activity, 20);
        int top = CustomerUiPrimitives.dp(activity, 8);
        form.setPadding(side, top, side, top);
        form.addView(RepeatOrderUi.text(activity,
                "Merchant dan driver akan melihat catatan ini. Maksimal 300 karakter.",
                13, "#475569", false));
        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(3);
        input.setMaxLines(5);
        input.setFilters(new InputFilter[]{FoodCheckoutNote::limitLength});
        input.setTextSize(15);
        input.setHint(hint);
        input.setText(note);
        input.setSelection(input.length());
        form.addView(input, new LayoutParams(-1, -2));

        new TransivaAlertDialogBuilder(activity)
                .setTitle(title)
                .setView(form)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", (dialog, which) -> onSaved.save(clean(input.getText().toString())))
                .show();
    }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        edit.setEnabled(enabled);
    }

    private void updatePreview() {
        edit.setText(value.isEmpty() ? "Tambah" : "Ubah");
        edit.setContentDescription(value.isEmpty() ? "Tambah catatan pesanan" : "Ubah catatan pesanan");
        preview.setText(value.isEmpty() && !compact ? EXAMPLE : value);
        preview.setVisibility(compact && value.isEmpty() ? GONE : VISIBLE);
    }

    private int dp(int value) {
        return CustomerUiPrimitives.dp(getContext(), value);
    }

    private static CharSequence limitLength(CharSequence source, int start, int end,
                                            android.text.Spanned dest, int replaceStart, int replaceEnd) {
        int used = Character.codePointCount(dest, 0, replaceStart)
                + Character.codePointCount(dest, replaceEnd, dest.length());
        int available = MAX_LENGTH - used;
        if (Character.codePointCount(source, start, end) <= available) return null;
        if (available <= 0) return "";
        int cut = Character.offsetByCodePoints(source, start, available);
        return source.subSequence(start, cut);
    }

    private static String clean(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("\\A[\\s\\p{Z}]+|[\\s\\p{Z}]+\\z", "");
    }

    static void addTo(JSONObject payload, String value) throws JSONException {
        if (value != null && !value.isEmpty()) payload.put("customer_note", value);
    }
}
